package com.example.backendwekily.service;

import com.example.backendwekily.dto.TransactionRequestDTO;
import com.example.backendwekily.dto.TransactionResponseDTO;
import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.Transaction;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AgentRepository       agentRepository;
    private final AgentTokenRepository  agentTokenRepository;
    private final AgencyRepository      agencyRepository;
    private final CommissionService     commissionService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    @Transactional
    public TransactionResponseDTO createTransaction(Long agentId, TransactionRequestDTO req) {

        log.info("💰 createTransaction agentId={} type={} amount={} agencyId={}",
                agentId, req.getType(), req.getAmount(), req.getAgencyId());

        // 1. Récupérer l'agent
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé: " + agentId));

        // 2. Calculer le montant (toujours positif en DB, signe selon type)
        Transaction.TransactionType type;
        try {
            type = Transaction.TransactionType.valueOf(
                    req.getType() != null ? req.getType().toUpperCase() : "DEPOSIT");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Type invalide: " + req.getType());
        }

        double rawAmount = req.getAmount() != null ? Math.abs(req.getAmount()) : 0.0;
        BigDecimal amount = BigDecimal.valueOf(rawAmount);
        if (type == Transaction.TransactionType.WITHDRAWAL) {
            amount = amount.negate();
        }

        // 3. Chercher l'agence si fournie
        Agency agency = null;
        String agencyName = null;
        if (req.getAgencyId() != null) {
            agency = agencyRepository.findById(req.getAgencyId()).orElse(null);
            if (agency != null) { agencyName = agency.getName(); }
        }

        // 4. Créer la transaction
        // Récupérer la commission manuelle si fournie
        BigDecimal manualComm = (req.getManualCommission() != null && req.getManualCommission() > 0)
                ? BigDecimal.valueOf(req.getManualCommission()) : BigDecimal.ZERO;

        Transaction tx = Transaction.builder()
                .agent(agent)
                .clientName(req.getClientName() != null ? req.getClientName() : "—")
                .amount(amount)
                .type(type)
                .status(Transaction.TransactionStatus.COMPLETED)
                .agencyName(agencyName)
                .agency(agency)
                .notes(req.getNotes())
                .manualCommission(manualComm) // ✅ Commission manuelle
                .build();

        transactionRepository.save(tx);
        log.info("✅ Transaction sauvegardée: {}", tx.getReference());

        // ── Commission automatique ──────────────────────────────
        // WITHDRAWAL → commission retrait ✅
        // DEPOSIT    → commission dépôt ✅
        // TRANSFER   → JAMAIS de commission ❌ (كل أنواع التعبئة)
        try {
            if (type == Transaction.TransactionType.WITHDRAWAL) {
                Integer bankId = agency != null ? getBankIdFromName(agency.getName()) : 1;
                BigDecimal fee  = commissionService.calcWithdrawalFee(BigDecimal.valueOf(rawAmount), bankId);
                BigDecimal rate = agency != null
                        ? commissionService.getAgencyRate(agentId, agency.getId())
                        : new BigDecimal("50");
                if (fee.compareTo(BigDecimal.ZERO) > 0) {
                    // حساب عمولة الوكيل الفعلية
                    BigDecimal agentComm = fee.multiply(rate)
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    commissionService.recordCommission(agentId,
                            agency != null ? agency.getId() : null,
                            tx.getId(), "WITHDRAWAL",
                            BigDecimal.valueOf(rawAmount), fee, rate);
                    // ✅ زيادة رصيد الوكالة بمبلغ العمولة (إلا مصرفي)
                    addCommissionToAgencyBalance(agency, agentComm);
                    log.info("✅ Commission WITHDRAWAL: fee={} rate={} agentComm={}", fee, rate, agentComm);
                }

            } else if (type == Transaction.TransactionType.DEPOSIT) {
                Integer bankId = agency != null ? getBankIdFromName(agency.getName()) : 1;
                BigDecimal fee  = commissionService.calcDepositCommission(BigDecimal.valueOf(rawAmount), bankId);
                if (fee.compareTo(BigDecimal.ZERO) > 0) {
                    commissionService.recordCommission(agentId,
                            agency != null ? agency.getId() : null,
                            tx.getId(), "DEPOSIT",
                            BigDecimal.valueOf(rawAmount), fee, BigDecimal.valueOf(100));
                    // ✅ زيادة رصيد الوكالة بمبلغ العمولة (إلا مصرفي)
                    addCommissionToAgencyBalance(agency, fee);
                    log.info("✅ Commission DEPOSIT: fee={}", fee);
                }

            } else {
                // TRANSFER → pas de commission
                log.info("ℹ️ TRANSFER → pas de commission");
            }

        } catch (Exception e) {
            log.error("❌ COMMISSION FAILED: {}", e.getMessage());
        }

        // 5. ✅ Mettre à jour cashBalance — SAUF pour TRANSFER
        // TRANSFER est géré par TransfertController directement (avec conversion devise MRU)
        // Si on met à jour ici, le montant CFA s'ajouterait wrongement au cashBalance MRU
        if (type != Transaction.TransactionType.TRANSFER) {
            BigDecimal cash = agent.getCashBalance() != null ? agent.getCashBalance() : BigDecimal.ZERO;
            agent.setCashBalance(cash.add(amount));
            log.info("✅ cashBalance {} → {} (opération: {} {})",
                    cash, agent.getCashBalance(), type, amount);
            BigDecimal current = agent.getTotalBalance() != null ? agent.getTotalBalance() : BigDecimal.ZERO;
            agent.setTotalBalance(current.add(amount));
            agentRepository.save(agent);
        } else {
            log.info("ℹ️ TRANSFER — cashBalance géré par TransfertController (pas de mise à jour ici)");
        }

        // 6. ✅ Mettre à jour le solde de l'agence — INVERSE du cashBalance agent
        // DEPOSIT    : agent reçoit cash (+cashBalance)  → agence donne du solde virtuel (-agencyBalance)
        // WITHDRAWAL : agent donne cash (-cashBalance)   → agence reçoit du solde virtuel (+agencyBalance)
        // TRANSFER   : agence toujours débitée (comme avant)
        if (agency != null) {
            BigDecimal agBal = agency.getCurrentBalance() != null ? agency.getCurrentBalance() : BigDecimal.ZERO;
            BigDecimal newAgBal;
            if (type == Transaction.TransactionType.TRANSFER) {
                newAgBal = agBal.subtract(BigDecimal.valueOf(rawAmount));
            } else {
                // ✅ Inverse exact de cashBalance :
                // amount = +rawAmount pour DEPOSIT    → agBal - rawAmount (diminue)
                // amount = -rawAmount pour WITHDRAWAL → agBal + rawAmount (augmente)
                newAgBal = agBal.subtract(amount);
            }
            agency.setCurrentBalance(newAgBal);
            agencyRepository.save(agency);
            log.info("✅ Solde agence {} : {} → {} (type={})", agency.getName(), agBal, newAgBal, type);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM");
        return TransactionResponseDTO.builder()
                .id(tx.getId())
                .reference(tx.getReference())
                .amount(tx.getAmount())
                .type(tx.getType().name())
                .status(tx.getStatus().name())
                .clientName(tx.getClientName())
                .agencyName(tx.getAgencyName())
                .date(tx.getCreatedAt().format(fmt))
                .manualCommission(tx.getManualCommission() != null
                        ? tx.getManualCommission().doubleValue() : 0.0) // ✅
                .build();
    }

    public List<TransactionResponseDTO> getTransactions(Long agentId) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM");
        return transactionRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                .stream()
                .map(t -> TransactionResponseDTO.builder()
                        .id(t.getId())
                        .reference(t.getReference())
                        .amount(t.getAmount())
                        .type(t.getType().name())
                        .status(t.getStatus().name())
                        .clientName(t.getClientName())
                        .agencyName(t.getAgencyName())
                        .date(t.getCreatedAt().format(fmt))
                        .manualCommission(t.getManualCommission() != null
                                ? t.getManualCommission().doubleValue() : 0.0) // ✅
                        .build())
                .collect(Collectors.toList());
    }

    // ── Trouver bank_id depuis le nom de l'agence ────────────

    // ✅ زيادة رصيد الوكالة بمبلغ العمولة — إلا مصرفي (تزداد شهرياً)
    private void addCommissionToAgencyBalance(Agency agency, BigDecimal commission) {
        if (agency == null || commission == null || commission.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // مصرفي → لا تزيد رصيده فوراً (تزداد شهرياً)
        String name = (agency.getName() != null ? agency.getName() : "").toLowerCase();
        String icon = (agency.getIcon() != null ? agency.getIcon() : "").toLowerCase();
        boolean isMasrvi = name.contains("مصرفي") || icon.contains("masrvi");
        if (isMasrvi) {
            log.info("ℹ️ مصرفي → عمولة {} MRU لن تُضاف الآن (تُضاف شهرياً)", commission);
            return;
        }
        // باقي الوكالات → تزداد رصيدها فوراً
        BigDecimal current = agency.getCurrentBalance() != null
                ? agency.getCurrentBalance() : BigDecimal.ZERO;
        agency.setCurrentBalance(current.add(commission));
        agencyRepository.save(agency);
        log.info("✅ رصيد {} زاد بـ {} MRU → رصيد جديد: {}",
                agency.getName(), commission, current.add(commission));
    }

    // ✅ Mettre à jour commission manuelle + table commissions + solde agence + cashBalance agent
    @Transactional
    public void updateManualCommission(Long agentId, Long txId, double commission) {
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new RuntimeException("العملية غير موجودة"));
        if (!tx.getAgent().getId().equals(agentId)) {
            throw new RuntimeException("غير مصرح بهذه العملية");
        }

        BigDecimal oldComm = tx.getManualCommission() != null ? tx.getManualCommission() : BigDecimal.ZERO;
        BigDecimal newComm = BigDecimal.valueOf(commission);
        // Différence entre ancienne et nouvelle commission
        BigDecimal diff    = newComm.subtract(oldComm);

        // 1. Mettre à jour transaction
        tx.setManualCommission(newComm);
        transactionRepository.save(tx);

        // 2. Mettre à jour la table commissions
        Long agencyId = tx.getAgency() != null ? tx.getAgency().getId() : null;
        if (agencyId != null) {
            try {
                Integer exists = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM commissions WHERE transaction_id=?",
                        Integer.class, txId
                );
                if (exists != null && exists > 0) {
                    jdbc.update(
                            "UPDATE commissions SET agent_commission=? WHERE transaction_id=? AND agent_id=?",
                            newComm, txId, agentId
                    );
                } else {
                    jdbc.update(
                            "INSERT INTO commissions (agent_id, agency_id, transaction_id, type, amount, fee, rate, agent_commission, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                            agentId, agencyId, txId,
                            tx.getType().name(),
                            tx.getAmount().abs(),
                            newComm, BigDecimal.valueOf(100), newComm
                    );
                }
            } catch (Exception e) {
                log.warn("⚠️ commissions table: {}", e.getMessage());
            }

            // 3. ✅ Mettre à jour le solde de l'agence (agencies.current_balance)
            // La commission est un gain de l'agent — elle s'ajoute au solde de l'agence
            try {
                Agency agency = agencyRepository.findById(agencyId).orElse(null);
                if (agency != null) {
                    BigDecimal currentBal = agency.getCurrentBalance() != null
                            ? agency.getCurrentBalance() : BigDecimal.ZERO;
                    agency.setCurrentBalance(currentBal.add(diff));
                    agencyRepository.save(agency);
                    log.info("✅ Solde agence {} : {} → {}",
                            agency.getName(), currentBal, currentBal.add(diff));
                }
            } catch (Exception e) {
                log.warn("⚠️ agencies balance: {}", e.getMessage());
            }
        }

        // 4. ✅ Mettre à jour cashBalance de l'agent (solde Dashboard)
        try {
            Agent agent = agentRepository.findById(agentId).orElse(null);
            if (agent != null) {
                BigDecimal cash = agent.getCashBalance() != null
                        ? agent.getCashBalance() : BigDecimal.ZERO;
                agent.setCashBalance(cash.add(diff));
                agentRepository.save(agent);
                log.info("✅ cashBalance agent: {} → {}", cash, cash.add(diff));
            }
        } catch (Exception e) {
            log.warn("⚠️ cashBalance: {}", e.getMessage());
        }

        log.info("✅ Commission manuelle tx={} : {} → {} (diff={})", txId, oldComm, newComm, diff);
    }

    private Integer getBankIdFromName(String name) {
        if (name == null) { return 1; }
        String n = name.toLowerCase();
        if (n.contains("بنكيلي") || n.contains("bankily")) { return 1; }
        if (n.contains("مصرفي")  || n.contains("masrvi"))  { return 2; }
        if (n.contains("سداد")   || n.contains("sedad"))   { return 3; }
        if (n.contains("موف")    || n.contains("moov"))    { return 4; }
        if (n.contains("بيم")    || n.contains("bimbank")) { return 5; }
        if (n.contains("أمانتي") || n.contains("amanty"))  { return 6; }
        if (n.contains("كليك")   || n.contains("click"))   { return 7; }
        return 1; // bankily par défaut
    }
}