package com.example.backendwekily.service;

import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.entity.Expense;
import com.example.backendwekily.entity.Transaction;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AgentRepository       agentRepository;
    private final AgentTokenRepository  agentTokenRepository;
    private final JdbcTemplate          jdbc;
    private final AgencyRepository      agencyRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository     expenseRepository;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    public Map<String, Object> getDashboardData(Long agentId) {
        var agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        LocalDateTime beginOfTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime startOfDay  = LocalDate.now().atStartOfDay();
        DateTimeFormatter fmt     = DateTimeFormatter.ofPattern("HH:mm dd/MM");

        // ── ✅ Agences réelles avec dépôts/retraits du jour ─
        List<Agency> agencies = agencyRepository.findByAgentIdAndActiveTrue(agentId);
        List<Map<String, Object>> agencyList = agencies.stream().map(ag -> {
            String agNm = ag.getName() != null ? ag.getName() : "";
            BigDecimal dep = safeQ(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions " +
                            "WHERE agent_id=? AND (agency_id=? OR agency_name=?) " +
                            "AND type='DEPOSIT' AND DATE(created_at)=CURDATE()",
                    agentId, ag.getId(), agNm);
            BigDecimal wit = safeQ(
                    "SELECT COALESCE(SUM(ABS(amount)),0) FROM transactions " +
                            "WHERE agent_id=? AND (agency_id=? OR agency_name=?) " +
                            "AND type='WITHDRAWAL' AND DATE(created_at)=CURDATE()",
                    agentId, ag.getId(), agNm);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",               ag.getId());
            m.put("name",             ag.getName());
            m.put("balance",          ag.getCurrentBalance() != null ? ag.getCurrentBalance() : BigDecimal.ZERO);
            m.put("todayDeposits",    dep);   // ✅ vert
            m.put("todayWithdrawals", wit);   // ✅ rouge
            m.put("todayChange",      dep.subtract(wit));
            return m;
        }).collect(Collectors.toList());

        // ── Solde mis à jour dynamiquement ────────────────────
        BigDecimal totalBalance = safe(agent.getTotalBalance());

        // ✅ Modification clé : Le solde cash (cashBalance) est égal à la caisse de l'agence (currentBalance)
        BigDecimal cashBalance = agent.getCashBalance() != null ? agent.getCashBalance() : BigDecimal.ZERO;
        if (agencies != null && !agencies.isEmpty()) {
            Agency primaryAgency = agencies.get(0);
            if (primaryAgency.getCurrentBalance() != null) {
                cashBalance = primaryAgency.getCurrentBalance();
            }
        }

        // ── Totaux globaux (carte de solde) ───────────────────
        BigDecimal totalDeposits    = safe(transactionRepository.sumDailyDeposits(agentId, beginOfTime));
        BigDecimal totalWithdrawals = safe(transactionRepository.sumDailyWithdrawals(agentId, beginOfTime));
        int        totalTxCount     = safeInt(transactionRepository.countDailyTransactions(agentId, beginOfTime));

        // ── Stats du jour (cartes إحصائيات) ──────────────────
        BigDecimal todayDeposits    = safe(transactionRepository.sumDailyDeposits(agentId, startOfDay));
        BigDecimal todayWithdrawals = safe(transactionRepository.sumDailyWithdrawals(agentId, startOfDay));
        BigDecimal todayTransfers   = safe(transactionRepository.sumDailyTransfers(agentId, startOfDay));
        int depositCount    = safeInt(transactionRepository.countDailyDeposits(agentId, startOfDay));
        int withdrawalCount = safeInt(transactionRepository.countDailyWithdrawals(agentId, startOfDay));
        int transferCount   = safeInt(transactionRepository.countDailyTransfers(agentId, startOfDay));
        int pending         = safeInt(transactionRepository.countPendingTransactions(agentId));

        log.info("📊 Dashboard agent {} → {} agences trouvées. Caisse synchronisée: {}", agentId, agencyList.size(), cashBalance);

        // ── 10 dernières opérations (tx + expenses) ──────────
        List<Transaction> recentTx = transactionRepository
                .findTop10ByAgentIdOrderByCreatedAtDesc(agentId);
        List<Expense> recentExp = expenseRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId);

        List<Map<String, Object>> allRecent = new ArrayList<>();
        for (Transaction t : recentTx) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       t.getId());
            m.put("name",     t.getClientName() != null ? t.getClientName() : "—");
            m.put("amount",   t.getAmount());
            m.put("agency",   t.getAgencyName() != null ? t.getAgencyName() : "—");
            m.put("date",     t.getCreatedAt() != null ? t.getCreatedAt().format(fmt) : "—");
            m.put("type",     t.getType().name());
            m.put("sortDate", t.getCreatedAt() != null ? t.getCreatedAt() : LocalDateTime.MIN);
            allRecent.add(m);
        }
        for (Expense e : recentExp) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       "EXP-" + e.getId());
            m.put("name",     e.getDescription() != null ? e.getDescription() : e.getCategory());
            m.put("amount",   e.getAmount());
            m.put("agency",   e.getCategory());
            m.put("date",     e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "—");
            m.put("type",     "EXPENSE");
            m.put("sortDate", e.getCreatedAt() != null ? e.getCreatedAt() : LocalDateTime.MIN);
            allRecent.add(m);
        }
        allRecent.sort((a, b) -> ((LocalDateTime) b.get("sortDate"))
                .compareTo((LocalDateTime) a.get("sortDate")));
        List<Map<String, Object>> top10 = allRecent.stream()
                .limit(10).peek(m -> m.remove("sortDate"))
                .collect(Collectors.toList());

        // ── Stats ─────────────────────────────────────────────
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("deposits",       todayDeposits);
        stats.put("depositCount",   depositCount);
        stats.put("withdrawals",    todayWithdrawals);
        stats.put("withdrawalCount",withdrawalCount);
        stats.put("pending",        pending);
        stats.put("delegations",    todayTransfers);
        stats.put("delegationCount",transferCount);

        // ── Réponse ───────────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalBalance",       totalBalance);
        response.put("cashBalance",        cashBalance); // ✅ Retourne à présent le solde de la caisse de l'agence
        response.put("totalDeposits",      totalDeposits);
        response.put("totalWithdrawals",   totalWithdrawals);
        response.put("todayDeposits",      totalDeposits);
        response.put("todayWithdrawals",   totalWithdrawals);
        response.put("transactionCount",   totalTxCount);
        response.put("agencies",           agencyList);
        response.put("stats",              stats);
        response.put("recentTransactions", top10);

        return response;
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private int safeInt(Integer v)        { return v != null ? v : 0; }

    private BigDecimal safeQ(String sql, Object... args) {
        try {
            BigDecimal r = jdbc.queryForObject(sql, BigDecimal.class, args);
            return r != null ? r : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("⚠️ safeQ: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}