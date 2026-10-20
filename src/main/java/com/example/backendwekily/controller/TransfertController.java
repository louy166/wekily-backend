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
    private final TransactionRepository             transactionRepository;

    // ── GET /api/transferts/correspondants ─────────────────────────────
    @GetMapping("/correspondants")
    public ResponseEntity<?> getCorrespondants(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);
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
    @PostMapping
    @Transactional
    public ResponseEntity<?> transferer(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            log.info("📥 Transfert: {}", body);
            Long idEmetteur = extractAgentId(authHeader);
            String telephone    = body.get("telephoneRecepteur").toString().trim();
            double montantEnv   = Double.parseDouble(body.get("montantEnvoye").toString());
            String devEnv       = (String) body.get("devEnv");
            String devRet       = (String) body.get("devRet");

            // ✅ Commission du transfert
            double commission = 0;
            if (body.containsKey("manualCommission") && body.get("manualCommission") != null) {
                commission = Double.parseDouble(body.get("manualCommission").toString());
            }
            log.info("💰 Commission transfert: {} {}", commission, devEnv);

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
            // ÉMETTEUR : solde diminue
            // ══════════════════════════════════════════════════════
            updateDeviseBalance(emetteur, devEnv, bdMontantEnv.negate());

            if ("MRU".equals(devEnv)) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantEnv, montantEnv, emetteur.getId()
                );
                log.info("📤 Émetteur {} : cash_balance MRU - {} (JDBC)", emetteur.getFullName(), montantEnv);
            } else {
                log.info("📤 Émetteur {} : envoie {} {} — cash_balance MRU INCHANGÉ", emetteur.getFullName(), montantEnv, devEnv);
            }

            // ✅ Commission transfert : l'agent gagne la commission du client
            if (commission > 0) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance + ?, total_balance = total_balance + ? WHERE id = ?",
                        commission, commission, emetteur.getId()
                );
                log.info("💰 Commission +{} {} ajoutée à agent {}", commission, devEnv, emetteur.getFullName());
            }

            // ══════════════════════════════════════════════════════
            // RECEVEUR : solde devise augmente
            // ══════════════════════════════════════════════════════
            updateDeviseBalance(receveur, devRet, bdMontantRetire);

            if ("MRU".equals(devRet)) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance + ?, total_balance = total_balance + ? WHERE id = ?",
                        montantRetire, montantRetire, receveur.getId()
                );
                log.info("📥 Receveur {} : cash_balance + {} MRU (JDBC)", receveur.getFullName(), montantRetire);
            } else {
                log.info("📥 Receveur {} : +{} {} en devise — cash_balance MRU INCHANGÉ", receveur.getFullName(), montantRetire, devRet);
            }

            // ✅ Références uniques
            String reference  = String.format("%04d", (int)(Math.random() * 9000) + 1000);
            String referenceR = String.format("%04d", (int)(Math.random() * 9000) + 1000);
            while (referenceR.equals(reference)) {
                referenceR = String.format("%04d", (int)(Math.random() * 9000) + 1000);
            }

            // ✅ Numéro du client
            String clientPhone = "";
            if (body.containsKey("clientPhone") && body.get("clientPhone") != null) {
                clientPhone = body.get("clientPhone").toString().trim();
            }
            log.info("📱 clientPhone reçu: '{}'", clientPhone);

            // ✅ Transaction émetteur — avec commission
            Transaction txEmetteur = Transaction.builder()
                    .agent(emetteur)
                    .clientName(clientPhone.isEmpty() ? receveur.getFullName() : clientPhone)
                    .amount(bdMontantEnv.negate())
                    .type(Transaction.TransactionType.TRANSFER)
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .agencyName(devEnv + "→" + devRet)
                    .notes("TRF#" + reference + " → " + receveur.getFullName())
                    .manualCommission(BigDecimal.valueOf(commission))
                    .build();
            txEmetteur.setReference(reference);
            transactionRepository.save(txEmetteur);

            // Transaction receveur
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

            log.info("✅ Transfert {} {} → {} {} | {} → {} | Commission: {} | Réf: {}",
                    montantEnv, devEnv, montantRetire, devRet,
                    emetteur.getFullName(), receveur.getFullName(), commission, reference);

            return ResponseEntity.ok(Map.of(
                    "reference",     reference,
                    "montantEnvoye", montantEnv,
                    "montantRetire", montantRetire,
                    "devEnv",        devEnv,
                    "devRet",        devRet,
                    "commission",    commission,
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
    @GetMapping("/historique")
    public ResponseEntity<?> getHistorique(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long agentId = extractAgentId(authHeader);
        try {
            String sql =
                    "SELECT t.id, t.reference, ABS(t.amount) AS montant, t.amount AS amount_raw, " +
                            "t.notes, t.agency_name, " +
                            "t.client_name AS client_name, " +
                            "t.manual_commission, " +
                            "t.created_at, t.retrait_status, t.retrait_method " +
                            "FROM transactions t " +
                            "WHERE t.agent_id = ? AND t.type = 'TRANSFER' " +
                            "ORDER BY t.created_at DESC LIMIT 50";

            var rows = jdbc.queryForList(sql, agentId);
            List<Map<String, Object>> result = new ArrayList<>();

            for (var row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();

                String ref = String.valueOf(row.getOrDefault("reference", "0000"));
                if (ref.length() > 4) ref = ref.substring(ref.length() - 4);

                double amountRaw = ((Number) row.getOrDefault("amount_raw", 0)).doubleValue();
                boolean isEnvoi  = amountRaw < 0;

                String agencyName = String.valueOf(row.getOrDefault("agency_name", "MRU→MRU"));
                String[] devises  = agencyName.contains("→")
                        ? agencyName.split("→") : new String[]{"MRU", "MRU"};
                String devEnv = devises.length > 0 ? devises[0].trim() : "MRU";
                String devRet = devises.length > 1 ? devises[1].trim() : "MRU";

                double montant = Math.abs(amountRaw);

                Object createdAt = row.get("created_at");
                String date = createdAt != null
                        ? createdAt.toString().substring(0, Math.min(16, createdAt.toString().length()))
                        : "—";

                // ✅ Commission
                double commissionVal = 0;
                if (row.get("manual_commission") != null) {
                    commissionVal = ((Number) row.get("manual_commission")).doubleValue();
                }

                m.put("id",            row.get("id"));
                m.put("reference",     ref);
                m.put("montantEnvoye", montant);
                m.put("montantRetire", montant);
                m.put("devEnv",        devEnv);
                m.put("devRet",        devRet);
                m.put("sens",          isEnvoi ? "envoi" : "reception");
                m.put("commission",    commissionVal);

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
    @PostMapping("/{id}/retrait")
    @Transactional
    public ResponseEntity<?> retirerTransfert(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);

            var rows = jdbc.queryForList(
                    "SELECT t.id, t.amount, t.type, t.agency_name, t.retrait_status, t.agent_id " +
                            "FROM transactions t WHERE t.id = ?", id
            );
            if (rows.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "العملية غير موجودة"));
            }
            var row = rows.get(0);

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

            String method  = (String) body.getOrDefault("method", "CASH");
            double montant = Double.parseDouble(body.getOrDefault("montant", txAmount).toString());

            String devRet = (String) body.getOrDefault("devise", "CFA");
            if (devRet == null || devRet.isBlank()) devRet = "CFA";

            log.info("📤 Retrait: agentId={} txId={} montant={} {} via {}", agentId, id, montant, devRet, method);

            double montantMRU = montant;
            if (!"MRU".equals(devRet) && !"DEVISE".equals(method)) {
                var tauxRows = jdbc.queryForList(
                        "SELECT tau FROM taux_change WHERE dev_env = 'MRU' AND dev_ret = ?", devRet
                );
                if (!tauxRows.isEmpty()) {
                    double t = parseTaux((String) tauxRows.get(0).get("tau"));
                    if (t > 0) montantMRU = montant / t;
                }
                log.info("💱 {} {} → {} MRU", montant, devRet, montantMRU);
            }

            if ("DEVISE".equals(method)) {
                jdbc.update(
                        "UPDATE agent_devise_balance SET balance = balance - ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                        montant, agentId, devRet
                );
                log.info("✅ DEVISE : solde {} - {}", devRet, montant);

            } else if ("CASH".equals(method)) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantMRU, montantMRU, agentId
                );
                log.info("✅ CASH : cash_balance - {} MRU | solde {} INCHANGÉ", montantMRU, devRet);

            } else {
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

            if ("DEVISE".equals(method)) {
                jdbc.update(
                        "UPDATE agent_devise_balance SET balance = balance - ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                        montant, agentId, devRet
                );
            } else if ("CASH".equals(method)) {
                jdbc.update(
                        "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                        montantMRU, montantMRU, agentId
                );
            } else {
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
    @PostMapping("/{id}/annuler")
    public ResponseEntity<?> annulerTransfert(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long agentId = extractAgentId(authHeader);

            var rows = jdbc.queryForList(
                    "SELECT t.id, t.amount, t.type, t.agency_name, t.retrait_status, " +
                            "t.agent_id, t.reference, t.manual_commission FROM transactions t WHERE t.id = ?", id
            );
            if (rows.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "العملية غير موجودة"));
            }
            var row = rows.get(0);

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

            String agencyName = (String) row.getOrDefault("agency_name", "MRU→MRU");
            String[] parts = (agencyName != null && agencyName.contains("→"))
                    ? agencyName.split("→") : new String[]{"MRU","MRU"};
            String devEnv = parts[0].trim();
            String devRet = parts.length > 1 ? parts[1].trim() : "MRU";

            double rawAmount = ((Number) row.get("amount")).doubleValue();
            boolean isEmetteur = rawAmount < 0;

            var twinRows = jdbc.queryForList(
                    "SELECT id, agent_id, amount FROM transactions " +
                            "WHERE reference = ? AND id != ? AND type = 'TRANSFER'", reference, id
            );

            if (isEmetteur) {
                if ("MRU".equals(devEnv)) {
                    jdbc.update(
                            "UPDATE agents SET cash_balance = cash_balance + ?, total_balance = total_balance + ? WHERE id = ?",
                            amount, amount, agentId
                    );
                }
                jdbc.update(
                        "UPDATE agent_devise_balance SET balance = balance + ? WHERE agent_id = ? AND UPPER(devise) = UPPER(?)",
                        amount, agentId, devEnv
                );

                // ✅ Annuler la commission si elle a été versée
                double commAnnulee = 0;
                if (row.get("manual_commission") != null) {
                    commAnnulee = ((Number) row.get("manual_commission")).doubleValue();
                }
                if (commAnnulee > 0) {
                    jdbc.update(
                            "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                            commAnnulee, commAnnulee, agentId
                    );
                    log.info("💰 Commission annulée: -{} pour agent {}", commAnnulee, agentId);
                }

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
                    jdbc.update(
                            "UPDATE transactions SET retrait_status = 'CANCEL' WHERE id = ?",
                            twinRows.get(0).get("id")
                    );
                }
            }

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

    private Long extractAgentId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("token_manquant");
        }
        String token = authHeader.substring(7).trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

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