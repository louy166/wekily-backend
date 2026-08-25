package com.example.backendwekily.service;

import com.example.backendwekily.entity.Transaction;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AgentTokenRepository  agentTokenRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository     expenseRepository;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // ── Générer les notifications depuis les transactions ─────
    public List<Map<String, Object>> getNotifications(Long agentId) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM");
        List<Map<String, Object>> notifs = new ArrayList<>();

        // Dernières 20 transactions → notifications
        List<Transaction> txs = transactionRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId)
                .stream().limit(20).collect(Collectors.toList());

        for (Transaction tx : txs) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id",      tx.getId());
            n.put("type",    tx.getType().name());
            n.put("title",   getTxTitle(tx));
            n.put("message", getTxMessage(tx));
            n.put("amount",  tx.getAmount());
            n.put("date",    tx.getCreatedAt() != null ? tx.getCreatedAt().format(fmt) : "—");
            n.put("read",    true); // Les transactions passées = lues
            notifs.add(n);
        }

        // Dernières 10 dépenses → notifications
        expenseRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                .stream().limit(10).forEach(exp -> {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("id",      "EXP-" + exp.getId());
                    n.put("type",    "EXPENSE");
                    n.put("title",   "تم تسجيل مصروف");
                    n.put("message", (exp.getDescription() != null ? exp.getDescription() : exp.getCategory()) + " - " + Math.abs(exp.getAmount().doubleValue()) + " أوقية");
                    n.put("amount",  exp.getAmount());
                    n.put("date",    exp.getCreatedAt() != null ? exp.getCreatedAt().format(fmt) : "—");
                    n.put("read",    true);
                    notifs.add(n);
                });

        // Trier par date décroissante
        notifs.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));

        return notifs.stream().limit(30).collect(Collectors.toList());
    }

    public void markRead(Long agentId, Long notifId) {
        // Dans cette implémentation simple, les notifications sont générées dynamiquement
        // Une vraie implémentation utiliserait une table notifications
        log.info("Notification {} marquée lue pour agent {}", notifId, agentId);
    }

    public void markAllRead(Long agentId) {
        log.info("Toutes les notifications marquées lues pour agent {}", agentId);
    }

    private String getTxTitle(Transaction tx) {
        return switch (tx.getType()) {
            case DEPOSIT    -> "تم الإيداع بنجاح";
            case WITHDRAWAL -> "سحب ناجح";
            case TRANSFER   -> "تعبئة رصيد";
        };
    }

    private String getTxMessage(Transaction tx) {
        BigDecimal abs = tx.getAmount() != null ? tx.getAmount().abs() : BigDecimal.ZERO;
        String agency = tx.getAgencyName() != null ? " عبر " + tx.getAgencyName() : "";
        String client = tx.getClientName() != null && !tx.getClientName().equals("—")
                ? " · " + tx.getClientName() : "";
        return switch (tx.getType()) {
            case DEPOSIT    -> "تم إيداع " + abs.toPlainString() + " أوقية" + agency + client;
            case WITHDRAWAL -> "تم سحب " + abs.toPlainString() + " أوقية" + agency + client;
            case TRANSFER   -> "تمت تعبئة " + abs.toPlainString() + " أوقية" + agency;
        };
    }
}