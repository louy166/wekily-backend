package com.example.backendwekily.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ✅ Répare automatiquement les tables MySQL corrompues au démarrage
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRepairService {

    private final JdbcTemplate jdbc;

    // Tables à vérifier et réparer au démarrage
    private static final String[] TABLES = {
            "expenses", "transactions", "agents", "agencies",
            "agent_tokens", "commissions", "bank_tiers"
    };

    @PostConstruct
    public void repairTablesIfNeeded() {
        log.info("🔧 Vérification de l'intégrité des tables MySQL...");
        for (String table : TABLES) {
            repairIfCrashed(table);
        }
        log.info("✅ Vérification terminée");
    }

    private void repairIfCrashed(String tableName) {
        try {
            // 1. Vérifier la table
            var result = jdbc.queryForList("CHECK TABLE " + tableName);
            String status = result.isEmpty() ? "OK"
                    : String.valueOf(result.get(result.size() - 1).getOrDefault("Msg_text", "OK"));

            if ("OK".equalsIgnoreCase(status) || "Table is already up to date".equalsIgnoreCase(status)) {
                log.debug("✅ Table {} : OK", tableName);
                return;
            }

            // 2. Table corrompue → réparer
            log.warn("⚠️ Table {} corrompue ({}), réparation en cours...", tableName, status);
            jdbc.execute("REPAIR TABLE " + tableName);
            log.info("✅ Table {} réparée avec succès", tableName);

        } catch (Exception e) {
            // Table inexistante ou autre erreur → ignorer
            if (e.getMessage() != null && e.getMessage().contains("crashed")) {
                try {
                    log.warn("🔨 Tentative de réparation forcée de {}", tableName);
                    jdbc.execute("REPAIR TABLE " + tableName);
                    log.info("✅ Table {} réparée", tableName);
                } catch (Exception e2) {
                    log.error("❌ Impossible de réparer {} : {}", tableName, e2.getMessage());
                }
            } else {
                log.debug("Table {} : {}", tableName, e.getMessage());
            }
        }
    }
}