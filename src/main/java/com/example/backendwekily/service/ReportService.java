package com.example.backendwekily.service;

import com.example.backendwekily.entity.Transaction;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // ✅ Ancienne signature — garde la compatibilité
    public Map<String, Object> getReport(Long agentId, String period) {
        return getReport(agentId, period, null, null);
    }

    // ✅ Nouvelle signature avec from et to
    public Map<String, Object> getReport(Long agentId, String period, String from, String to) {

        // ── Calcul des dates ──────────────────────────────────
        LocalDateTime start;
        LocalDateTime end = LocalDateTime.now();

        if ("مخصص".equals(period) && from != null && to != null) {
            // ✅ Fلتر مخصص — from et to depuis le frontend
            start = LocalDate.parse(from).atStartOfDay();
            end   = LocalDate.parse(to).atTime(23, 59, 59);
            log.info("📅 Rapport مخصص : {} → {}", from, to);
        } else {
            // فترات جاهزة
            start = getStartDate(period);
            log.info("📅 Rapport {} : {} → {}", period, start, end);
        }

        // ── Stats globales ────────────────────────────────────
        BigDecimal totalDeposits    = safe(transactionRepository.sumDepositsInRange(agentId, start, end));
        BigDecimal totalWithdrawals = safe(transactionRepository.sumWithdrawalsInRange(agentId, start, end));
        BigDecimal totalTransfers   = safe(transactionRepository.sumDailyTransfers(agentId, start));
        int depositCount    = safeInt(transactionRepository.countDailyDeposits(agentId, start));
        int withdrawalCount = safeInt(transactionRepository.countDailyWithdrawals(agentId, start));
        int transferCount   = safeInt(transactionRepository.countDailyTransfers(agentId, start));

        // ── Graphique ─────────────────────────────────────────
        List<Map<String, Object>> chartData = buildChartData(agentId, period, start, end);

        // ── Répartition par agence ────────────────────────────
        List<Map<String, Object>> agencyBreakdown = buildAgencyBreakdown(agentId, start, end);

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

    // ✅ Graphique avec start et end dynamiques
    private List<Map<String, Object>> buildChartData(
            Long agentId, String period,
            LocalDateTime start, LocalDateTime end) {

        List<Map<String, Object>> data = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        // Nombre de jours entre start et end
        long totalDays = Duration.between(start, end).toDays() + 1;
        int points = (int) Math.min(totalDays, 7);

        for (int i = points - 1; i >= 0; i--) {
            LocalDateTime from = end.toLocalDate().minusDays(i).atStartOfDay();
            LocalDateTime to   = from.plusDays(1);
            // Ne pas dépasser start
            if (from.isBefore(start)) { from = start; }

            BigDecimal dep = safe(transactionRepository.sumDepositsInRange(agentId, from, to));
            BigDecimal wit = safe(transactionRepository.sumWithdrawalsInRange(agentId, from, to));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label",       from.format(fmt));
            m.put("deposits",    dep);
            m.put("withdrawals", wit);
            data.add(m);
        }
        return data;
    }

    // ✅ Répartition par agence avec end dynamique
    private List<Map<String, Object>> buildAgencyBreakdown(
            Long agentId, LocalDateTime start, LocalDateTime end) {

        List<Transaction> txs = transactionRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId)
                .stream()
                .filter(t -> t.getCreatedAt() != null
                        && t.getCreatedAt().isAfter(start)
                        && t.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());

        Map<String, Long> countByAgency = txs.stream()
                .filter(t -> t.getAgencyName() != null)
                .collect(Collectors.groupingBy(Transaction::getAgencyName, Collectors.counting()));

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
                    return m;
                })
                .collect(Collectors.toList());
    }

    private LocalDateTime getStartDate(String period) {
        return switch (period) {
            case "اليوم"   -> LocalDate.now().atStartOfDay();
            case "الأسبوع" -> LocalDate.now().minusDays(7).atStartOfDay();
            case "السنة"   -> LocalDate.now().minusYears(1).atStartOfDay();
            default         -> LocalDate.now().minusDays(30).atStartOfDay();
        };
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private int safeInt(Integer v)        { return v != null ? v : 0; }
}