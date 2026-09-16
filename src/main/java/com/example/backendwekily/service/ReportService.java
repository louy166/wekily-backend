package com.example.backendwekily.service;

import com.example.backendwekily.entity.Transaction;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AgentTokenRepository  agentTokenRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository     expenseRepository;
    private final JdbcTemplate             jdbc;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    public Map<String, Object> getReport(Long agentId, String period) {
        LocalDateTime start = getStartDate(period);
        LocalDateTime now   = LocalDateTime.now();

        // ── Stats globales ────────────────────────────────────
        BigDecimal totalDeposits    = safe(transactionRepository.sumDailyDeposits(agentId, start));
        BigDecimal totalWithdrawals = safe(transactionRepository.sumDailyWithdrawals(agentId, start));
        BigDecimal totalTransfers   = safe(transactionRepository.sumDailyTransfers(agentId, start));
        int depositCount    = safeInt(transactionRepository.countDailyDeposits(agentId, start));
        int withdrawalCount = safeInt(transactionRepository.countDailyWithdrawals(agentId, start));
        int transferCount   = safeInt(transactionRepository.countDailyTransfers(agentId, start));

        // ── Données pour le graphique (7 derniers jours) ─────
        List<Map<String, Object>> chartData = buildChartData(agentId, period);

        // ── Répartition par agence ────────────────────────────
        List<Map<String, Object>> agencyBreakdown = buildAgencyBreakdown(agentId, start);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalDeposits",    totalDeposits);
        response.put("totalWithdrawals", totalWithdrawals);
        response.put("totalTransfers",   totalTransfers);
        response.put("depositCount",     depositCount);
        response.put("withdrawalCount",  withdrawalCount);
        response.put("transferCount",    transferCount);
        response.put("chartData",        chartData);
        response.put("agencyBreakdown",  agencyBreakdown);
        return response;
    }

    private List<Map<String, Object>> buildChartData(Long agentId, String period) {
        List<Map<String, Object>> data = new ArrayList<>();
        int days = period.equals("اليوم") ? 1 : period.equals("الأسبوع") ? 7
                : period.equals("الشهر") ? 30 : 365;
        int points = Math.min(days, 7);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = points - 1; i >= 0; i--) {
            LocalDateTime from = LocalDate.now().minusDays(i).atStartOfDay();
            LocalDateTime to   = from.plusDays(1);
            BigDecimal dep = safe(transactionRepository.sumDepositsInRange(agentId, from, to));
            BigDecimal wit = safe(transactionRepository.sumWithdrawalsInRange(agentId, from, to));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label",       LocalDate.now().minusDays(i).format(fmt));
            m.put("deposits",    dep);
            m.put("withdrawals", wit);
            data.add(m);
        }
        return data;
    }

    private List<Map<String, Object>> buildAgencyBreakdown(Long agentId, LocalDateTime start) {
        // Grouper par agency_name
        List<Transaction> txs = transactionRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                .stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(start))
                .collect(Collectors.toList());

        Map<String, Long> countByAgency = txs.stream()
                .filter(t -> t.getAgencyName() != null)
                .collect(Collectors.groupingBy(Transaction::getAgencyName, Collectors.counting()));

        // ✅ Total du montant par agence
        Map<String, BigDecimal> totalByAgency = txs.stream()
                .filter(t -> t.getAgencyName() != null && t.getAmount() != null)
                .collect(Collectors.groupingBy(
                        Transaction::getAgencyName,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        long total = txs.size();
        if (total == 0) { return Collections.emptyList(); }

        return countByAgency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",    e.getKey());
                    m.put("count",   e.getValue());
                    m.put("percent", BigDecimal.valueOf(e.getValue() * 100.0 / total)
                            .setScale(0, RoundingMode.HALF_UP));
                    // ✅ إجمالي المبلغ لكل وكالة
                    m.put("total",   totalByAgency.getOrDefault(e.getKey(), BigDecimal.ZERO)
                            .abs().setScale(2, RoundingMode.HALF_UP));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private LocalDateTime getStartDate(String period) {
        return switch (period) {
            case "اليوم"    -> LocalDate.now().atStartOfDay();
            case "الأسبوع"  -> LocalDate.now().minusDays(7).atStartOfDay();
            case "السنة"    -> LocalDate.now().minusYears(1).atStartOfDay();
            default          -> LocalDate.now().minusDays(30).atStartOfDay();
        };
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private int safeInt(Integer v)        { return v != null ? v : 0; }

    // ✅ الرصيد الحالي — النقد، التطبيقات، العمولات، الديون، الودائع، الأرباح، المصاريف
    public Map<String, Object> getCurrentBalances(Long agentId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. النقد — cash_balance de l'agent
        var agentRows = jdbc.queryForList(
                "SELECT cash_balance, total_balance FROM agents WHERE id = ?", agentId
        );
        double cashBalance = 0;
        if (!agentRows.isEmpty()) {
            cashBalance = ((Number) agentRows.get(0).getOrDefault("cash_balance", 0)).doubleValue();
        }
        result.put("cash", cashBalance);

        // 2. التطبيقات — somme des soldes des agences (bankily, masrvi, sedad...)
        var agencyRows = jdbc.queryForList(
                "SELECT COALESCE(name, icon) AS name, COALESCE(current_balance, 0) AS balance " +
                        "FROM agencies WHERE agent_id = ? AND active = 1 ORDER BY name", agentId
        );
        double totalApps = 0;
        List<Map<String, Object>> apps = new ArrayList<>();
        for (var row : agencyRows) {
            double bal = ((Number) row.getOrDefault("balance", 0)).doubleValue();
            totalApps += bal;
            Map<String, Object> app = new LinkedHashMap<>();
            app.put("name", row.get("name"));
            app.put("balance", bal);
            apps.add(app);
        }
        result.put("apps", totalApps);
        result.put("appsDetail", apps);

        // 3. العمولات — somme des commissions
        var commRows = jdbc.queryForList(
                "SELECT COALESCE(SUM(commission_amount), 0) AS total FROM commissions WHERE agent_id = ?", agentId
        );
        double totalComm = commRows.isEmpty() ? 0
                : ((Number) commRows.get(0).getOrDefault("total", 0)).doubleValue();
        result.put("commissions", totalComm);

        // 4. الديون — pas encore activé
        result.put("debts", 0);

        // 5. الودائع — somme des dépôts actifs
        var depRows = jdbc.queryForList(
                "SELECT COALESCE(SUM(amount), 0) AS total FROM client_deposits " +
                        "WHERE agent_id = ? AND status = 'ACTIVE'", agentId
        );
        double totalDeposits = depRows.isEmpty() ? 0
                : ((Number) depRows.get(0).getOrDefault("total", 0)).doubleValue();
        result.put("deposits", totalDeposits);

        // 6. الأرباح — pas encore activé
        result.put("profits", 0);

        // 7. المصاريف — somme des dépenses
        var expRows = jdbc.queryForList(
                "SELECT COALESCE(SUM(amount), 0) AS total FROM expenses WHERE agent_id = ?", agentId
        );
        double totalExpenses = expRows.isEmpty() ? 0
                : ((Number) expRows.get(0).getOrDefault("total", 0)).doubleValue();
        result.put("expenses", totalExpenses);

        // Total global
        result.put("totalBalance", cashBalance + totalApps + totalComm + totalDeposits - totalExpenses);

        log.info("📊 Balances agent {}: cash={} apps={} comm={} dep={} exp={}",
                agentId, cashBalance, totalApps, totalComm, totalDeposits, totalExpenses);

        return result;
    }

}