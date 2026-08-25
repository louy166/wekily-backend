package com.example.backendwekily.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final JdbcTemplate jdbc;

    // ── Config Admin ───────────────────────────────────────────
    private static final String ADMIN_USER  = "admin";
    private static final String ADMIN_PASS  = "admin2024";
    private static final String ADMIN_TOKEN = "wekily-admin-secret-2024";

    // ── Login ─────────────────────────────────────────────────
    public ResponseEntity<?> login(String username, String password) {
        log.info("🔐 [AdminService] login: {}", username);
        if (ADMIN_USER.equals(username) && ADMIN_PASS.equals(password)) {
            log.info("✅ [AdminService] Admin connecté");
            return ResponseEntity.ok(Map.of(
                    "token",   ADMIN_TOKEN,
                    "message", "مرحباً بك"
            ));
        }
        log.warn("⚠️ [AdminService] Mauvais credentials");
        return ResponseEntity.status(401).body(Map.of(
                "message", "بيانات الدخول غير صحيحة"
        ));
    }

    public boolean isValidToken(String auth) {
        if (auth == null) { return false; }
        return ADMIN_TOKEN.equals(auth.replace("Bearer ", "").trim());
    }

    // ── Stats ─────────────────────────────────────────────────
    public Map<String, Object> getStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalAgents",   safeInt("SELECT COUNT(*) FROM agents"));
        m.put("activeAgents",  safeInt("SELECT COUNT(*) FROM agents WHERE active=1 AND COALESCE(blocked,0)=0"));
        m.put("blockedAgents", safeInt("SELECT COUNT(*) FROM agents WHERE COALESCE(blocked,0)=1"));
        m.put("totalBanks",    safeInt("SELECT COUNT(*) FROM agencies GROUP BY name") );
        m.put("totalTx",       safeInt("SELECT COUNT(*) FROM transactions"));
        return m;
    }

    // ── Agents ────────────────────────────────────────────────
    public List<Map<String, Object>> getAllAgents() {
        return jdbc.queryForList(
                "SELECT a.id, a.full_name AS fullName, a.phone, a.city, a.region, " +
                        "COALESCE(a.active,1) AS active, COALESCE(a.blocked,0) AS blocked, " +
                        "(SELECT COUNT(*) FROM agencies ag WHERE ag.agent_id=a.id) AS agencyCount " +
                        "FROM agents a ORDER BY a.id DESC"
        );
    }

    public void setAgentBlocked(Long agentId, boolean blocked) {
        // Ajouter colonne si elle n'existe pas encore
        try {
            jdbc.execute("ALTER TABLE agents ADD COLUMN blocked TINYINT(1) DEFAULT 0");
            log.info("✅ Colonne blocked créée");
        } catch (Exception ignored) {}

        jdbc.update("UPDATE agents SET blocked=? WHERE id=?", blocked ? 1 : 0, agentId);
        log.info("✅ Agent {} blocked={}", agentId, blocked);
    }

    // ── Banks ─────────────────────────────────────────────────
    public List<Map<String, Object>> getAllBanks() {
        // 1. Essayer table banks
        try {
            List<Map<String,Object>> banks = jdbc.queryForList(
                    "SELECT id, name, icon, 0 AS agentCount FROM banks ORDER BY id"
            );
            if (!banks.isEmpty()) { return banks; }
        } catch (Exception ignored) {}

        // 2. Fallback : agences distinctes (toujours disponible)
        try {
            return jdbc.queryForList(
                    "SELECT MIN(id) AS id, name, icon, " +
                            "COUNT(DISTINCT agent_id) AS agentCount " +
                            "FROM agencies GROUP BY name, icon ORDER BY name"
            );
        } catch (Exception e) {
            log.warn("getAllBanks: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> addBank(String name, String icon) {
        createBanksTableIfNeeded();
        jdbc.update("INSERT INTO banks (name, icon) VALUES (?, ?)", name, icon);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        log.info("✅ Banque ajoutée: {} ({})", name, icon);
        return Map.of("id", id, "name", name, "icon", icon);
    }

    public Map<String, Object> updateBank(Long id, String name, String icon) {
        jdbc.update("UPDATE banks SET name=?, icon=? WHERE id=?", name, icon, id);
        return Map.of("id", id, "name", name, "icon", icon);
    }

    public void deleteBank(Long id) {
        jdbc.update("DELETE FROM banks WHERE id=?", id);
    }

    // ── Tiers ─────────────────────────────────────────────────
    public List<Map<String, Object>> getAllTiers() {
        // ✅ Même requête que CommissionService — tables réelles du projet
        try {
            List<Map<String,Object>> tiers = jdbc.queryForList(
                    "SELECT w.id, w.bank_id AS bankId, " +
                            "b.name AS bankName, b.icon AS bankIcon, " +
                            "w.amount_from AS amountFrom, w.amount_to AS amountTo, " +
                            "w.fee AS withdrawalFee, " +
                            "COALESCE(d.commission, 0) AS depositCommission " +
                            "FROM banks b " +
                            "JOIN withdrawal_fee_tiers w ON w.bank_id = b.id " +
                            "LEFT JOIN deposit_commission_tiers d " +
                            "  ON d.bank_id = b.id AND d.amount_from = w.amount_from " +
                            "ORDER BY b.id, w.amount_from"
            );
            log.info("✅ getAllTiers: {} lignes", tiers.size());
            return tiers;
        } catch (Exception e) {
            log.warn("❌ getAllTiers: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> addTier(Map<String, Object> req) {
        Long       bankId = toLong(req.get("bankId"));
        BigDecimal from   = toBD(req.get("amountFrom"));
        BigDecimal to     = toBD(req.get("amountTo"));
        BigDecimal wFee   = toBD(req.get("withdrawalFee"));
        BigDecimal dFee   = toBD(req.get("depositCommission"));

        // ✅ Insérer dans les 2 tables réelles
        jdbc.update(
                "INSERT INTO withdrawal_fee_tiers (bank_id, amount_from, amount_to, fee) VALUES (?,?,?,?)",
                bankId, from, to, wFee
        );
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update(
                "INSERT INTO deposit_commission_tiers (bank_id, amount_from, amount_to, commission) VALUES (?,?,?,?)",
                bankId, from, to, dFee
        );

        log.info("✅ Tier ajouté: banque {} [{}-{}]", bankId, from, to);
        return Map.of("id", id);
    }

    public Map<String, Object> updateTier(Long id, Map<String, Object> req) {
        Long       bankId = toLong(req.get("bankId"));
        BigDecimal from   = toBD(req.get("amountFrom"));
        BigDecimal to     = toBD(req.get("amountTo"));
        BigDecimal wFee   = toBD(req.get("withdrawalFee"));
        BigDecimal dFee   = toBD(req.get("depositCommission"));

        // Récupérer l'ancienne valeur amount_from pour mettre à jour deposit_commission_tiers
        try {
            Map<String,Object> old = jdbc.queryForMap(
                    "SELECT bank_id, amount_from FROM withdrawal_fee_tiers WHERE id=?", id
            );
            // Mettre à jour withdrawal_fee_tiers
            jdbc.update(
                    "UPDATE withdrawal_fee_tiers SET bank_id=?, amount_from=?, amount_to=?, fee=? WHERE id=?",
                    bankId, from, to, wFee, id
            );
            // Mettre à jour deposit_commission_tiers
            jdbc.update(
                    "UPDATE deposit_commission_tiers SET bank_id=?, amount_from=?, amount_to=?, commission=? " +
                            "WHERE bank_id=? AND amount_from=?",
                    bankId, from, to, dFee,
                    old.get("bank_id"), old.get("amount_from")
            );
        } catch (Exception e) {
            log.warn("updateTier: {}", e.getMessage());
        }
        return Map.of("id", id);
    }

    public void deleteTier(Long id) {
        try {
            // Récupérer bank_id et amount_from avant suppression
            Map<String,Object> tier = jdbc.queryForMap(
                    "SELECT bank_id, amount_from FROM withdrawal_fee_tiers WHERE id=?", id
            );
            // Supprimer des 2 tables
            jdbc.update("DELETE FROM withdrawal_fee_tiers WHERE id=?", id);
            jdbc.update(
                    "DELETE FROM deposit_commission_tiers WHERE bank_id=? AND amount_from=?",
                    tier.get("bank_id"), tier.get("amount_from")
            );
            log.info("✅ Tier {} supprimé", id);
        } catch (Exception e) {
            log.warn("deleteTier: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private void createBanksTableIfNeeded() {
        try {
            jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS banks (" +
                            "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(100) NOT NULL," +
                            "icon VARCHAR(50)  NOT NULL)"
            );
        } catch (Exception ignored) {}
    }

    private int safeInt(String sql) {
        try {
            Integer r = jdbc.queryForObject(sql, Integer.class);
            return r != null ? r : 0;
        } catch (Exception e) { return 0; }
    }

    private Long toLong(Object v) {
        if (v == null) { return null; }
        return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
    }

    private BigDecimal toBD(Object v) {
        if (v == null) { return BigDecimal.ZERO; }
        return v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString());
    }
}