package com.example.backendwekily.controller;

import com.example.backendwekily.entity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/transferts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransfertController {

    private final TauxChangeRepository          tauxChangeRepository;
    private final AgentRepository               agentRepository;
    private final AgentDeviseBalanceRepository    deviseBalanceRepository;
    private final AgentCorrespondanceRepository   correspondanceRepository;
    private final AgentTokenRepository              agentTokenRepository;
    private final JdbcTemplate                      jdbc;
    private final TransactionRepository             transactionRepository; // ✅ pour sauvegarder l'historique

    // ── GET /api/transferts/correspondants ─────────────────────────────
    // ✅ Liste les agents de la table agent_correspondance de l'agent connecté
    @GetMapping("/correspondants")
    public ResponseEntity<?> getCorrespondants(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);
            // Récupérer les agents liés via agent_correspondance (émetteur OU receveur)
            // ✅ Seulement les agents liés directement à l'agent connecté
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT DISTINCT a.id, a.full_name, a.phone " +
                            "FROM agent_correspondance ac " +
                            "JOIN agents a ON ( " +
                            "   (ac.id_agent_emetteur = ? AND a.id = ac.id_agent_recepteur) OR " +
                            "   (ac.id_agent_recepteur = ? AND a.id = ac.id_agent_emetteur) " +
                            ") " +
                            "WHERE a.id != ? AND (a.blocked IS NULL OR a.blocked = 0) " +
                            "ORDER BY a.full_name",
                    agentId, agentId, agentId
            );
            List<Map<String, Object>> result = rows.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       r.get("id"));
                m.put("fullName", r.getOrDefault("full_name", "—"));
                m.put("phone",    r.getOrDefault("phone", "—"));
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("⚠️ correspondants: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // ── GET /api/transferts/acces ───────────────────────────────────────
    // ✅ Extrait l'agentId depuis le token → vérification individuelle
    @GetMapping("/acces")
    public ResponseEntity<?> verifierAcces(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long agentId;
        try { agentId = extractAgentId(authHeader); }
        catch (Exception e) {
            return ResponseEntity.ok(Map.of("autorise", false, "message", "token_manquant"));
        }
        if (!agentRepository.existsById(agentId)) {
            return ResponseEntity.ok(Map.of("autorise", false, "message", "الوكيل غير موجود"));
        }
        boolean autorise = correspondanceRepository.existsByAgentId(agentId);
        List<AgentDeviseBalance> soldes = deviseBalanceRepository.findByAgentId(agentId);
        List<Map<String, Object>> soldesMap = soldes.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("devise",  s.getDevise());
            m.put("balance", s.getBalance());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of(
                "autorise", autorise,
                "soldes",   soldesMap,
                "message",  autorise ? "مفعّل" : "هذا الوكيل غير مشترك في خدمة التحويل"
        ));
    }

    // ── GET /api/transferts/soldes ──────────────────────────────────────
    // ✅ Extrait l'agentId depuis le token → chaque agent voit ses propres soldes
    @GetMapping("/soldes")
    public ResponseEntity<?> getSoldes(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long agentId = extractAgentId(authHeader);
        List<AgentDeviseBalance> soldes = deviseBalanceRepository.findByAgentId(agentId);
        List<Map<String, Object>> result = soldes.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("devise",  s.getDevise());
            m.put("balance", s.getBalance());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // ── GET /api/transferts/calcul ─────────────────────────────────────
    @GetMapping("/calcul")
    public ResponseEntity<?> calculer(
            @RequestParam String devEnv,
            @RequestParam String devRet,
            @RequestParam double montant) {

        Optional<TauxChangeEntity> tauxOpt = tauxChangeRepository.findByDevEnvAndDevRet(devEnv, devRet);
        if (tauxOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "لا يوجد سعر صرف لهذا الزوج: " + devEnv + "/" + devRet
            ));
        }
        double tauxValeur   = parseTaux(tauxOpt.get().getTau());
        double montantRetire = BigDecimal.valueOf(montant * tauxValeur)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        return ResponseEntity.ok(Map.of(
                "montantEnvoye", montant,
                "montantRetire", montantRetire,
                "devEnv",        devEnv,
                "devRet",        devRet,
                "taux",          tauxValeur,
                "tauxBrut",      tauxOpt.get().getTau()
        ));
    }

    // ── POST /api/transferts ───────────────────────────────────────────
    // ✅ L'agentId émetteur est extrait du token — pas de body.idAgentEmetteur
    @PostMapping
    @Transactional
    public ResponseEntity<?> transferer(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("📥 Transfert: {}", body);
            // ✅ Toujours l'agent connecté — impossible de tricher
            Long idEmetteur = extractAgentId(authHeader);
            String telephone    = body.get("telephoneRecepteur").toString().trim();
            double montantEnv   = Double.parseDouble(body.get("montantEnvoye").toString());
            String devEnv       = (String) body.get("devEnv");
            String devRet       = (String) body.get("devRet");

            if (montantEnv <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "المبلغ يجب أن يكون أكبر من صفر"));
            }

            // 1. Vérifier agent émetteur
            Agent emetteur = agentRepository.findById(idEmetteur)
                    .orElseThrow(() -> new RuntimeException("الوكيل المرسل غير موجود"));

            // 2. Trouver agent receveur par téléphone
            Agent receveur = agentRepository.findByPhone(telephone)
                    .orElseThrow(() -> new RuntimeException(
                            "لم يتم العثور على وكيل بهذا الرقم: " + telephone));

            if (idEmetteur.equals(receveur.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "لا يمكن إرسال تحويل لنفسك"));
            }

            // 3. Calculer montant à recevoir
            double montantRetire = montantEnv;
            Optional<TauxChangeEntity> tauxOpt = tauxChangeRepository.findByDevEnvAndDevRet(devEnv, devRet);
            if (tauxOpt.isPresent()) {
                double tauxVal = parseTaux(tauxOpt.get().getTau());
                montantRetire = BigDecimal.valueOf(montantEnv * tauxVal)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }

            BigDecimal bdMontantEnv   = BigDecimal.valueOf(montantEnv);
            BigDecimal bdMontantRetire = BigDecimal.valueOf(montantRetire);

            // ══════════════════════════════════════════════════════
            // ÉMETTEUR : solde diminue — JDBC pur (évite les conflits JPA)
            // ══════════════════════════════════════════════════════
            // 4a. Solde devise MRU de l'émetteur diminue
            updateDeviseBalance(emetteur, devEnv, bdMontantEnv.negate());

            // 4b. cashBalance MRU diminue SEULEMENT si l'émetteur envoie du MRU
            // Si devEnv = CFA/EUR/... → cashBalance MRU inchangé (seul agent_devise_balance CFA diminue)
            if ("MRU".equals(devEnv)) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantEnv, montantEnv, emetteur.getId()
                );
                log.info("📤 Émetteur {} : cash_balance MRU - {} (JDBC)", emetteur.getFullName(), montantEnv);
            } else {
                log.info("📤 Émetteur {} : envoie {} {} — cash_balance MRU INCHANGÉ", emetteur.getFullName(), montantEnv, devEnv);
            }

            // ══════════════════════════════════════════════════════
            // RECEVEUR : solde devise augmente — cashBalance MRU INCHANGÉ si devise ≠ MRU
            // ══════════════════════════════════════════════════════
            // 5a. Solde devise reçue (ex: CFA) augmente
            updateDeviseBalance(receveur, devRet, bdMontantRetire);

            // 5b. ✅ cashBalance MRU du Dashboard : SEULEMENT si la devise reçue est MRU
            //     Si CFA/EUR/... → NE PAS toucher cash_balance / total_balance MRU
            if ("MRU".equals(devRet)) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance + ?, total_balance = total_balance + ? WHERE id = ?",
                        montantRetire, montantRetire, receveur.getId()
                );
                log.info("📥 Receveur {} : cash_balance + {} MRU (JDBC)", receveur.getFullName(), montantRetire);
            } else {
                // Devise étrangère (CFA, EUR...) → solde devise uniquement, PAS le Dashboard MRU
                log.info("📥 Receveur {} : +{} {} en devise — cash_balance MRU INCHANGÉ", receveur.getFullName(), montantRetire, devRet);
            }

            // ✅ 2 références uniques — contrainte UNIQUE sur transactions.reference
            String reference  = String.format("%04d", (int)(Math.random() * 9000) + 1000);
            String referenceR = String.format("%04d", (int)(Math.random() * 9000) + 1000);
            // S'assurer que les deux sont différentes
            while (referenceR.equals(reference)) {
                referenceR = String.format("%04d", (int)(Math.random() * 9000) + 1000);
            }
            // ✅ Lire le numéro du client envoyé par le frontend
            String clientPhone = "";
            if (body.containsKey("clientPhone") && body.get("clientPhone") != null) {
                clientPhone = body.get("clientPhone").toString().trim();
            }
            log.info("📱 clientPhone reçu: '{}'", clientPhone);

            // ✅ Stocker clientPhone pour LES 2 agents (émetteur ET receveur)
            // Émetteur voit : numéro du client
            Transaction txEmetteur = Transaction.builder()
                    .agent(emetteur)
                    .clientName(clientPhone.isEmpty() ? receveur.getFullName() : clientPhone)
                    .amount(bdMontantEnv.negate())
                    .type(Transaction.TransactionType.TRANSFER)
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .agencyName(devEnv + "→" + devRet)
                    .notes("TRF#" + reference + " → " + receveur.getFullName())
                    .build();
            txEmetteur.setReference(reference);
            transactionRepository.save(txEmetteur);

            // Receveur voit aussi : numéro du client
            Transaction txReceveur = Transaction.builder()
                    .agent(receveur)
                    .clientName(clientPhone.isEmpty() ? emetteur.getFullName() : clientPhone)
                    .amount(bdMontantRetire)
                    .type(Transaction.TransactionType.TRANSFER)
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .agencyName(devEnv + "→" + devRet)
                    .notes("TRF#" + referenceR + " ← " + emetteur.getFullName())
                    .build();
            txReceveur.setReference(referenceR);
            transactionRepository.save(txReceveur);

            log.info("✅ Transfert {} {} → {} {} | {} → {} | Réf: {}",
                    montantEnv, devEnv, montantRetire, devRet,
                    emetteur.getFullName(), receveur.getFullName(), reference);

            return ResponseEntity.ok(Map.of(
                    "reference",     reference,
                    "montantEnvoye", montantEnv,
                    "montantRetire", montantRetire,
                    "devEnv",        devEnv,
                    "devRet",        devRet,
                    "receveur",      receveur.getFullName(),
                    "message",       "تم التحويل بنجاح"
            ));

        } catch (RuntimeException e) {
            log.warn("⚠️ Transfert refusé: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Erreur transfert: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "خطأ في معالجة التحويل"));
        }
    }

    // ── POST /api/transferts/soldes/ajouter ────────────────────────────
    // ✅ Alimenter manuellement un solde devise (CFA, EUR...) pour l'agent connecté
    @PostMapping("/soldes/ajouter")
    @Transactional
    public ResponseEntity<?> ajouterSolde(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);
            String devise = (String) body.get("devise");
            if (devise == null || devise.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "العملة مطلوبة"));
            }
            // Refuser MRU — cette action est réservée aux devises étrangères
            if ("MRU".equalsIgnoreCase(devise)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "لا يمكن إضافة الأوقية هنا — استخدم الإيداع"));
            }
            double montant = Double.parseDouble(body.getOrDefault("montant", "0").toString());
            if (montant <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "المبلغ يجب أن يكون أكبر من 0"));
            }

            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new RuntimeException("الوكيل غير موجود"));

            // Créer ou mettre à jour le solde devise
            updateDeviseBalance(agent, devise.toUpperCase(), BigDecimal.valueOf(montant));

            log.info("✅ Ajout manuel {} {} pour agent {}", montant, devise, agent.getFullName());
            return ResponseEntity.ok(Map.of(
                    "message", "تمت إضافة الرصيد بنجاح",
                    "devise",  devise.toUpperCase(),
                    "montant", montant
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── GET /api/transferts/historique ──────────────────────────────────
    // ✅ Lit l'historique depuis la table transactions (type=TRANSFER)
    @GetMapping("/historique")
    public ResponseEntity<?> getHistorique(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long agentId = extractAgentId(authHeader);
        try {
            // ✅ Lire depuis transactions — sauvegardées lors de chaque transfert
            // notes format: "TRF#XXXX → NomAgent" (envoi) ou "TRF#XXXX ← NomAgent" (reception)
            String sql =
                    "SELECT t.id, t.reference, ABS(t.amount) AS montant, t.amount AS amount_raw, " +
                            "t.notes, t.agency_name, " +
                            "t.client_name AS client_name, " +  // alias explicite
                            "t.created_at, t.retrait_status, t.retrait_method " +
                            "FROM transactions t " +
                            "WHERE t.agent_id = ? AND t.type = 'TRANSFER' " +
                            "ORDER BY t.created_at DESC LIMIT 50";

            var rows = jdbc.queryForList(sql, agentId);
            List<Map<String, Object>> result = new ArrayList<>();

            for (var row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();

                // Référence 4 chiffres
                String ref = String.valueOf(row.getOrDefault("reference", "0000"));
                if (ref.length() > 4) ref = ref.substring(ref.length() - 4);

                // Sens : amount négatif = envoi, positif = réception
                double amountRaw = ((Number) row.getOrDefault("amount_raw", 0)).doubleValue();
                boolean isEnvoi  = amountRaw < 0;

                // Devises depuis agency_name ("MRU→CFA")
                String agencyName = String.valueOf(row.getOrDefault("agency_name", "MRU→MRU"));
                String[] devises  = agencyName.contains("→")
                        ? agencyName.split("→") : new String[]{"MRU", "MRU"};
                String devEnv = devises.length > 0 ? devises[0].trim() : "MRU";
                String devRet = devises.length > 1 ? devises[1].trim() : "MRU";

                double montant = Math.abs(amountRaw);

                // Date formatée
                Object createdAt = row.get("created_at");
                String date = createdAt != null
                        ? createdAt.toString().substring(0, Math.min(16, createdAt.toString().length()))
                        : "—";

                m.put("id",            row.get("id"));
                m.put("reference",     ref);
                m.put("montantEnvoye", montant);
                m.put("montantRetire", montant);
                m.put("devEnv",        devEnv);
                m.put("devRet",        devRet);
                m.put("sens",          isEnvoi ? "envoi" : "reception");
                // ✅ Lire client_name (snake_case ou camelCase selon config JPA)
                String clientNameVal = null;
                if (row.get("client_name") != null)
                    clientNameVal = row.get("client_name").toString();
                else if (row.get("clientName") != null)
                    clientNameVal = row.get("clientName").toString();
                else if (row.get("clientname") != null)
                    clientNameVal = row.get("clientname").toString();
                m.put("autreAgent", clientNameVal != null ? clientNameVal : "—");
                log.info("📋 autreAgent pour tx {}: '{}'", row.get("id"), clientNameVal);
                m.put("date",          date);
                m.put("retraitStatus", row.getOrDefault("retrait_status", null));
                m.put("retraitMethod", row.getOrDefault("retrait_method", null));
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("⚠️ historique: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // ── POST /api/transferts/{id}/retrait ──────────────────────────────
    // ✅ Retirer un montant reçu par transfert (en devise ou converti en MRU)
    @PostMapping("/{id}/retrait")
    @Transactional
    public ResponseEntity<?> retirerTransfert(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);

            // 1. Lire la transaction directement via JDBC (évite Hibernate session)
            var rows = jdbc.queryForList(
                    "SELECT t.id, t.amount, t.type, t.agency_name, t.retrait_status, t.agent_id " +
                            "FROM transactions t WHERE t.id = ?", id
            );
            if (rows.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "العملية غير موجودة"));
            }
            var row = rows.get(0);

            // 2. Vérifications
            long txAgentId = ((Number) row.get("agent_id")).longValue();
            if (txAgentId != agentId) {
                return ResponseEntity.badRequest().body(Map.of("message", "غير مصرح بهذه العملية"));
            }
            double txAmount = ((Number) row.get("amount")).doubleValue();
            if (!"TRANSFER".equals(row.get("type")) || txAmount <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "هذه العملية ليست تحويلاً مستلماً"));
            }
            if ("DONE".equals(row.get("retrait_status"))) {
                return ResponseEntity.badRequest().body(Map.of("message", "تم سحب هذه العملية مسبقاً"));
            }

            // 3. Paramètres
            String method  = (String) body.getOrDefault("method", "CASH");
            double montant = Double.parseDouble(body.getOrDefault("montant", txAmount).toString());

            // 4. ✅ Devise reçue = directement depuis le body envoyé par le frontend
            // Le frontend envoie : { devise: txToRetrait.devRet } ex: "CFA"
            String devRet = (String) body.getOrDefault("devise", "CFA");
            if (devRet == null || devRet.isBlank()) devRet = "CFA";

            log.info("📤 Retrait: agentId={} txId={} montant={} {} via {}", agentId, id, montant, devRet, method);

            // 5. Calcul du montant MRU équivalent (avant de décider qui change)
            double montantMRU = montant;
            if (!"MRU".equals(devRet) && !"DEVISE".equals(method)) {
                var tauxRows = jdbc.queryForList(
                        "SELECT tau FROM taux_change WHERE dev_env = 'MRU' AND dev_ret = ?", devRet
                );
                if (!tauxRows.isEmpty()) {
                    double t = parseTaux((String) tauxRows.get(0).get("tau")); // 0.7
                    if (t > 0) montantMRU = montant / t; // 70 / 0.7 = 100 MRU
                }
                log.info("💱 {} {} → {} MRU", montant, devRet, montantMRU);
            }

            // 6. ✅ Logique selon la méthode :
            // DEVISE     → CFA diminue, MRU inchangé
            // CASH/BANK  → MRU diminue, CFA INCHANGÉ (ne pas toucher la CFA)
            if ("DEVISE".equals(method)) {
                // Retrait en devise pure → seulement la CFA diminue
                jdbc.update(
                        "UPDATE agent_devise_balance SET balance = balance - ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                        montant, agentId, devRet
                );
                log.info("✅ DEVISE : solde {} - {}", devRet, montant);

            } else if ("CASH".equals(method)) {
                // ✅ CASH MRU : seulement cash_balance MRU diminue — CFA INCHANGÉE
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantMRU, montantMRU, agentId
                );
                log.info("✅ CASH : cash_balance - {} MRU | solde {} INCHANGÉ", montantMRU, devRet);

            } else {
                // ✅ BANK : cash_balance MRU diminue + solde agence augmente — CFA INCHANGÉE
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantMRU, montantMRU, agentId
                );
                jdbc.update(
                        "UPDATE agencies SET current_balance = current_balance + ? " +
                                "WHERE agent_id = ? AND UPPER(icon) = UPPER(?) AND active = 1 LIMIT 1",
                        montantMRU, agentId, method
                );
                log.info("✅ BANK {} : cash_balance - {} MRU, agence + {} MRU | solde {} INCHANGÉ",
                        method, montantMRU, montantMRU, devRet);
            }

            // 8. Marquer la transaction comme retirée
            jdbc.update(
                    "UPDATE transactions SET retrait_status = 'DONE', retrait_method = ? WHERE id = ?",
                    method, id
            );

            return ResponseEntity.ok(Map.of(
                    "message",       "تم السحب بنجاح",
                    "montant",       montant,
                    "montantMRU",    montantMRU,
                    "devise",        devRet,
                    "method",        method,
                    "retraitStatus", "DONE"
            ));

        } catch (Exception e) {
            log.warn("⚠️ Retrait échoué: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── POST /api/transferts/soldes/retirer ─────────────────────────────
    // ✅ Retrait libre depuis le solde devise (sans lien à un transfert)
    @PostMapping("/soldes/retirer")
    public ResponseEntity<?> retirerSoldeLibre(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);
            String devRet  = (String) body.getOrDefault("devise",  "CFA");
            String method  = (String) body.getOrDefault("method",  "CASH");
            double montant = Double.parseDouble(body.getOrDefault("montant", "0").toString());

            if (montant <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "المبلغ يجب أن يكون أكبر من 0"));
            }

            // Vérifier solde disponible
            var soldeRows = jdbc.queryForList(
                    "SELECT balance FROM agent_devise_balance WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                    agentId, devRet
            );
            double soldeActuel = soldeRows.isEmpty() ? 0
                    : ((Number) soldeRows.get(0).get("balance")).doubleValue();

            if (soldeActuel < montant) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "الرصيد غير كافٍ — متوفر: " + soldeActuel + " " + devRet
                ));
            }

            // Calcul MRU équivalent
            double montantMRU = montant;
            if (!"MRU".equals(devRet) && !"DEVISE".equals(method)) {
                var tauxRows = jdbc.queryForList(
                        "SELECT tau FROM taux_change WHERE dev_env = 'MRU' AND dev_ret = ?", devRet
                );
                if (!tauxRows.isEmpty()) {
                    double t = parseTaux((String) tauxRows.get(0).get("tau"));
                    if (t > 0) montantMRU = montant / t;
                }
            }

            // Appliquer selon la méthode
            if ("DEVISE".equals(method)) {
                // Retrait en devise → solde CFA diminue
                jdbc.update(
                        "UPDATE agent_devise_balance SET balance = balance - ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                        montant, agentId, devRet
                );
            } else if ("CASH".equals(method)) {
                // CASH MRU → cash_balance diminue, CFA inchangée
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantMRU, montantMRU, agentId
                );
            } else {
                // BANK → cash_balance diminue + solde agence augmente, CFA inchangée
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantMRU, montantMRU, agentId
                );
                jdbc.update(
                        "UPDATE agencies SET current_balance = current_balance + ? " +
                                "WHERE agent_id = ? AND UPPER(icon) = UPPER(?) AND active = 1 LIMIT 1",
                        montantMRU, agentId, method
                );
            }

            log.info("✅ Retrait libre {} {} via {} → {} MRU (agent {})", montant, devRet, method, montantMRU, agentId);

            return ResponseEntity.ok(Map.of(
                    "message",    "تم السحب بنجاح",
                    "montant",    montant,
                    "montantMRU", montantMRU,
                    "devise",     devRet,
                    "method",     method
            ));
        } catch (Exception e) {
            log.warn("⚠️ Retrait libre: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── POST /api/transferts/{id}/annuler ──────────────────────────────
    // ✅ Annule un transfert et restaure les soldes des 2 agents
    @PostMapping("/{id}/annuler")
    public ResponseEntity<?> annulerTransfert(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);

            // 1. Lire la transaction via JDBC
            var rows = jdbc.queryForList(
                    "SELECT t.id, t.amount, t.type, t.agency_name, t.retrait_status, " +
                            "t.agent_id, t.reference FROM transactions t WHERE t.id = ?", id
            );
            if (rows.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "العملية غير موجودة"));
            }
            var row = rows.get(0);

            // 2. Vérifications
            long txAgentId = ((Number) row.get("agent_id")).longValue();
            if (txAgentId != agentId) {
                return ResponseEntity.badRequest().body(Map.of("message", "غير مصرح بهذه العملية"));
            }
            if (!"TRANSFER".equals(row.get("type"))) {
                return ResponseEntity.badRequest().body(Map.of("message", "هذه العملية ليست تحويلاً"));
            }
            String status = (String) row.getOrDefault("retrait_status", null);
            if ("CANCEL".equals(status)) {
                return ResponseEntity.badRequest().body(Map.of("message", "هذا التحويل ملغى مسبقاً"));
            }
            if ("DONE".equals(status)) {
                return ResponseEntity.badRequest().body(Map.of("message", "لا يمكن إلغاء تحويل تم سحبه"));
            }

            double amount    = Math.abs(((Number) row.get("amount")).doubleValue());
            String reference = (String) row.getOrDefault("reference", "");

            // Devise depuis agency_name
            String agencyName = (String) row.getOrDefault("agency_name", "MRU→MRU");
            String[] parts = (agencyName != null && agencyName.contains("→"))
                    ? agencyName.split("→") : new String[]{"MRU","MRU"};
            String devEnv = parts[0].trim();
            String devRet = parts.length > 1 ? parts[1].trim() : "MRU";

            // Signe : négatif = émetteur, positif = receveur
            double rawAmount = ((Number) row.get("amount")).doubleValue();
            boolean isEmetteur = rawAmount < 0;

            // 3. Trouver la transaction jumelle (même référence, autre agent)
            var twinRows = jdbc.queryForList(
                    "SELECT id, agent_id, amount FROM transactions " +
                            "WHERE reference = ? AND id != ? AND type = 'TRANSFER'", reference, id
            );

            // 4. ✅ Restaurer les soldes — JDBC pur
            if (isEmetteur) {
                // Je suis l'émetteur → je récupère mon MRU
                if ("MRU".equals(devEnv)) {
                    jdbc.update(
                            "UPDATE agents SET cash_balance = cash_balance + ?, total_balance = total_balance + ? WHERE id = ?",
                            amount, amount, agentId
                    );
                }
                // Diminuer le solde devise de l'émetteur (annuler l'ajout)
                jdbc.update(
                        "UPDATE agent_devise_balance SET balance = balance + ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                        amount, agentId, devEnv
                );
                // Diminuer le solde devise du receveur (annuler la réception)
                if (!twinRows.isEmpty()) {
                    long twinAgentId = ((Number) twinRows.get(0).get("agent_id")).longValue();
                    double twinAmount = Math.abs(((Number) twinRows.get(0).get("amount")).doubleValue());
                    jdbc.update(
                            "UPDATE agent_devise_balance SET balance = balance - ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                            twinAmount, twinAgentId, devRet
                    );
                    if ("MRU".equals(devRet)) {
                        jdbc.update(
                                "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                                twinAmount, twinAmount, twinAgentId
                        );
                    }
                    // Marquer la transaction jumelle comme annulée
                    jdbc.update(
                            "UPDATE transactions SET retrait_status = 'CANCEL' WHERE id = ?",
                            twinRows.get(0).get("id")
                    );
                }
            }

            // 5. Marquer la transaction courante comme annulée
            jdbc.update(
                    "UPDATE transactions SET retrait_status = 'CANCEL' WHERE id = ?", id
            );

            log.info("✅ Transfert #{} annulé par agent {}", reference, agentId);
            return ResponseEntity.ok(Map.of(
                    "message",   "تم إلغاء التحويل بنجاح",
                    "reference", reference,
                    "status",    "CANCEL"
            ));

        } catch (Exception e) {
            log.warn("⚠️ Annulation échouée: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    // ✅ Extraire l'agentId depuis le header Authorization Bearer {token}
    private Long extractAgentId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("token_manquant");
        }
        String token = authHeader.substring(7).trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // Mettre à jour ou créer le solde d'une devise pour un agent
    private void updateDeviseBalance(Agent agent, String devise, BigDecimal delta) {
        AgentDeviseBalance solde = deviseBalanceRepository
                .findByAgentIdAndDevise(agent.getId(), devise)
                .orElseGet(() -> AgentDeviseBalance.builder()
                        .agent(agent).devise(devise).balance(BigDecimal.ZERO).build());

        solde.setBalance(solde.getBalance().add(delta));
        deviseBalanceRepository.save(solde);
        log.info("✅ Solde {} agent {}: {} → {}",
                devise, agent.getFullName(), solde.getBalance().subtract(delta), solde.getBalance());
    }

    // ✅ Parseur "3500/5000" → divise réellement : 3500/5000 = 0.7
    private double parseTaux(String tau) {
        if (tau == null || tau.isBlank()) return 1.0;
        try {
            if (tau.contains("/")) {
                String[] p = tau.split("/");
                double num = Double.parseDouble(p[0].trim());
                double den = Double.parseDouble(p[1].trim());
                return den > 0 ? num / den : num;
            }
            return Double.parseDouble(tau.trim());
        } catch (NumberFormatException e) {
            log.warn("⚠️ parseTaux invalide: {}", tau);
            return 1.0;
        }
    }
}