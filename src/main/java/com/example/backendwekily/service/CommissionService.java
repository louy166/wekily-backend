package com.example.backendwekily.service;

import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final AgentTokenRepository agentTokenRepository;
    private final JdbcTemplate         jdbc;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // ── bank_id depuis nom agence ─────────────────────────────
    public Integer getBankIdPublic(Long agencyId) { return getBankId(agencyId); }

    private Integer getBankId(Long agencyId) {
        if (agencyId == null) { return 1; }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT name, icon FROM agencies WHERE id=? LIMIT 1", agencyId);
            if (rows.isEmpty()) { return 1; }
            String name = String.valueOf(rows.get(0).get("name")).toLowerCase();
            String icon = String.valueOf(rows.get(0).get("icon")).toLowerCase();
            if (name.contains("بنكيلي") || icon.contains("bankily")) { return 1; }
            if (name.contains("مصرفي")  || icon.contains("masrvi"))  { return 2; }
            if (name.contains("سداد")   || icon.contains("sedad"))   { return 3; }
            if (name.contains("موف")    || icon.contains("moov"))    { return 4; }
            if (name.contains("بيم")    || icon.contains("bimbank")) { return 5; }
            if (name.contains("أمانتي") || icon.contains("amanty"))  { return 6; }
            if (name.contains("كليك")   || icon.contains("click"))   { return 7; }
            return 1;
        } catch (Exception e) {
            log.error("getBankId error: {}", e.getMessage());
            return 1;
        }
    }

    // ── Commission retrait ────────────────────────────────────
    public BigDecimal calcWithdrawalFee(BigDecimal amount, Integer bankId) {
        if (bankId == null) { bankId = 1; }
        log.info("🔍 calcWithdrawalFee: amount={} bankId={}", amount, bankId);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT fee FROM withdrawal_fee_tiers " +
                            "WHERE bank_id=? AND ? BETWEEN amount_from AND amount_to LIMIT 1",
                    bankId, amount);
            if (rows.isEmpty()) {
                rows = jdbc.queryForList(
                        "SELECT fee FROM withdrawal_fee_tiers " +
                                "WHERE ? BETWEEN amount_from AND amount_to LIMIT 1", amount);
            }
            if (rows.isEmpty()) { return BigDecimal.ZERO; }
            BigDecimal fee = new BigDecimal(rows.get(0).get("fee").toString());
            log.info("✅ withdrawal fee: {} MRU", fee);
            return fee;
        } catch (Exception e) {
            log.error("❌ calcWithdrawalFee error: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ── Commission dépôt ──────────────────────────────────────
    public BigDecimal calcDepositCommission(BigDecimal amount, Integer bankId) {
        if (bankId == null) { bankId = 1; }
        log.info("🔍 calcDepositCommission: amount={} bankId={}", amount, bankId);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT commission FROM deposit_commission_tiers " +
                            "WHERE bank_id=? AND ? BETWEEN amount_from AND amount_to LIMIT 1",
                    bankId, amount);
            if (rows.isEmpty()) {
                log.info("🔍 Fallback sans bank_id pour amount={}", amount);
                rows = jdbc.queryForList(
                        "SELECT commission FROM deposit_commission_tiers " +
                                "WHERE ? BETWEEN amount_from AND amount_to LIMIT 1", amount);
            }
            if (rows.isEmpty()) {
                log.warn("⚠️ Aucune tranche dépôt pour amount={}", amount);
                return BigDecimal.ZERO;
            }
            BigDecimal commission = new BigDecimal(rows.get(0).get("commission").toString());
            log.info("✅ deposit commission: {} MRU", commission);
            return commission;
        } catch (Exception e) {
            log.error("❌ calcDepositCommission error: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ── Taux agence (défaut 50%) ──────────────────────────────
    public BigDecimal getAgencyRate(Long agentId, Long agencyId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT withdrawal_rate FROM agency_commission_rate " +
                            "WHERE agent_id=? AND agency_id=? LIMIT 1",
                    agentId, agencyId);
            return rows.isEmpty()
                    ? new BigDecimal("50.00")
                    : new BigDecimal(rows.get(0).get("withdrawal_rate").toString());
        } catch (Exception e) {
            return new BigDecimal("50.00");
        }
    }

    // ── Modifier taux ─────────────────────────────────────────
    public void updateAgencyRate(Long agentId, Long agencyId, BigDecimal rate) {
        try {
            int n = jdbc.update(
                    "UPDATE agency_commission_rate SET withdrawal_rate=? " +
                            "WHERE agent_id=? AND agency_id=?",
                    rate, agentId, agencyId);
            if (n == 0) {
                jdbc.update(
                        "INSERT INTO agency_commission_rate (agent_id, agency_id, withdrawal_rate) " +
                                "VALUES (?,?,?)",
                        agentId, agencyId, rate);
            }
            log.info("✅ Taux agence {} → {}%", agencyId, rate);
        } catch (Exception e) {
            log.error("❌ updateAgencyRate error: {}", e.getMessage());
        }
    }

    // ── Enregistrer commission (MIS À JOUR AVEC OPTION MANUELLE) ────────────────
    public void recordCommission(Long agentId, Long agencyId, Long txId,
                                 String type, BigDecimal amount,
                                 BigDecimal fee, BigDecimal rate) {
        try {
            BigDecimal agentCommission;

            // 🔍 Vérifier d'abord s'il y a déjà une commission manuelle sur cette transaction
            BigDecimal manualComm = BigDecimal.ZERO;
            try {
                manualComm = jdbc.queryForObject(
                        "SELECT COALESCE(manual_commission, 0) FROM transactions WHERE id = ?",
                        BigDecimal.class, txId
                );
            } catch (Exception ex) {
                log.warn("Impossible de lire manual_commission: {}", ex.getMessage());
            }

            // 🛑 Si l'agent a inséré une commission manuelle, on l'utilise directement à 100%
            if (manualComm != null && manualComm.compareTo(BigDecimal.ZERO) > 0) {
                agentCommission = manualComm;
                fee = manualComm; // On harmonise le frais global avec la commission manuelle
                log.info("⭐ Utilisation de la Commission MANUELLE détectée: {} MRU", agentCommission);
            } else {
                // Sinon, calcul automatique classique par défaut
                if ("WITHDRAWAL".equals(type)) {
                    agentCommission = fee.multiply(rate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else {
                    agentCommission = fee; // DEPOSIT : 100%
                }
            }

            log.info("💰 recordCommission: agent={} agency={} type={} amount={} fee={} comm={} rate={}",
                    agentId, agencyId, type, amount, fee, agentCommission, rate);

            jdbc.update(
                    "INSERT INTO commissions " +
                            "(agent_id, agency_id, transaction_id, type, amount, fee, agent_commission, rate, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    agentId, agencyId, txId, type, amount, fee, agentCommission, rate,
                    java.time.LocalDateTime.now());
            log.info("✅ Commission enregistrée: {} MRU", agentCommission);
        } catch (Exception e) {
            log.error("❌ recordCommission FAILED: {} → {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ── Dashboard commissions ─────────────────────────────────
    public Map<String, Object> getCommissionDashboard(Long agentId) {
        log.info("📊 getCommissionDashboard pour agent {}", agentId);

        BigDecimal totalAll   = safeQuery(
                "SELECT COALESCE(SUM(agent_commission),0) FROM commissions WHERE agent_id=?", agentId);
        BigDecimal totalToday = safeQuery(
                "SELECT COALESCE(SUM(agent_commission),0) FROM commissions " +
                        "WHERE agent_id=? AND DATE(created_at) = CURDATE()",
                agentId);
        BigDecimal totalMonth = safeQuery(
                "SELECT COALESCE(SUM(agent_commission),0) FROM commissions " +
                        "WHERE agent_id=? AND YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE())",
                agentId);
        BigDecimal totalWit   = safeQuery(
                "SELECT COALESCE(SUM(agent_commission),0) FROM commissions WHERE agent_id=? AND type='WITHDRAWAL'",
                agentId);
        BigDecimal totalDep   = safeQuery(
                "SELECT COALESCE(SUM(agent_commission),0) FROM commissions WHERE agent_id=? AND type='DEPOSIT'",
                agentId);

        log.info("📊 Totaux: all={} today={} month={} wit={} dep={}",
                totalAll, totalToday, totalMonth, totalWit, totalDep);

        List<Map<String, Object>> agencyStats;
        try {
            agencyStats = jdbc.queryForList(
                    "SELECT a.id, a.name, a.icon, " +
                            "COALESCE(a.current_balance, 0) AS current_balance, " +
                            "COALESCE(SUM(CASE WHEN c.type='WITHDRAWAL' THEN c.agent_commission ELSE 0 END),0) AS withdrawal_commission, " +
                            "COALESCE(SUM(CASE WHEN c.type='DEPOSIT'    THEN c.agent_commission ELSE 0 END),0) AS deposit_commission, " +
                            "COALESCE(SUM(c.agent_commission),0) AS total_commission, " +
                            "COALESCE(r.withdrawal_rate,50) AS rate, " +
                            "COALESCE(COUNT(c.id),0) AS transaction_count, " +
                            "COALESCE(SUM(CASE WHEN DATE(c.created_at)=CURDATE() THEN c.agent_commission ELSE 0 END),0) AS today_commission " +
                            "FROM agencies a " +
                            "LEFT JOIN commissions c ON c.agency_id=a.id AND c.agent_id=? " +
                            "LEFT JOIN agency_commission_rate r ON r.agency_id=a.id AND r.agent_id=? " +
                            "WHERE a.agent_id=? AND a.active=1 " +
                            "GROUP BY a.id, a.name, a.icon, r.withdrawal_rate " +
                            "ORDER BY total_commission DESC",
                    agentId, agentId, agentId);
        } catch (Exception e) {
            log.error("❌ agencyStats error: {}", e.getMessage());
            agencyStats = new ArrayList<>();
        }

        List<Map<String, Object>> bankTiers;
        try {
            bankTiers = jdbc.queryForList(
                    "SELECT b.name AS bank_name, b.icon, " +
                            "w.amount_from, w.amount_to, w.fee AS withdrawal_fee, " +
                            "COALESCE(d.commission, 0) AS deposit_commission " +
                            "FROM banks b " +
                            "JOIN withdrawal_fee_tiers w ON w.bank_id=b.id " +
                            "LEFT JOIN deposit_commission_tiers d " +
                            "  ON d.bank_id=b.id AND d.amount_from=w.amount_from " +
                            "ORDER BY b.id, w.amount_from");
        } catch (Exception e) {
            log.error("❌ bankTiers error: {}", e.getMessage());
            bankTiers = new ArrayList<>();
        }

        List<Map<String, Object>> recent;
        try {
            recent = jdbc.queryForList(
                    "SELECT c.type, c.amount, c.fee, c.agent_commission, c.rate, c.created_at, " +
                            "a.name AS agency_name, a.icon " +
                            "FROM commissions c " +
                            "LEFT JOIN agencies a ON a.id=c.agency_id " +
                            "WHERE c.agent_id=? ORDER BY c.created_at DESC LIMIT 10",
                    agentId);
        } catch (Exception e) {
            log.error("❌ recent error: {}", e.getMessage());
            recent = new ArrayList<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAll",          totalAll);
        result.put("totalToday",        totalToday);
        result.put("totalMonth",        totalMonth);
        result.put("totalWithdrawal",   totalWit);
        result.put("totalDeposit",      totalDep);
        result.put("agencyStats",       agencyStats);
        result.put("bankTiers",         bankTiers);
        result.put("recentCommissions", recent);

        return result;
    }

    private BigDecimal safeQuery(String sql, Object... args) {
        try {
            return safe(jdbc.queryForObject(sql, BigDecimal.class, args));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}