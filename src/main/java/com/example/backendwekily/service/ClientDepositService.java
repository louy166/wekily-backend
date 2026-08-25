package com.example.backendwekily.service;

import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.ClientDeposit;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDepositService {

    private final ClientDepositRepository depositRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final AgentTokenRepository    agentTokenRepository;
    private final AgentRepository         agentRepository;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // ── Créer un dépôt client ─────────────────────────────────
    @Transactional
    public Map<String, Object> createDeposit(Long agentId, Map<String, Object> req) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        String typeStr = req.getOrDefault("type", "CASH").toString().toUpperCase();
        ClientDeposit.DepositType type = ClientDeposit.DepositType.valueOf(typeStr);

        BigDecimal amount = new BigDecimal(req.getOrDefault("amount", "0").toString());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("المبلغ يجب أن يكون أكبر من 0");
        }

        ClientDeposit deposit = ClientDeposit.builder()
                .agent(agent)
                .clientName(req.getOrDefault("clientName", "—").toString())
                .clientPhone(req.getOrDefault("clientPhone", "").toString())
                .amount(amount)
                .type(type)
                .bankName(req.getOrDefault("bankName", "").toString())
                .bankAccount(req.getOrDefault("bankAccount", "").toString())
                .notes(req.getOrDefault("notes", "").toString())
                .status(ClientDeposit.DepositStatus.ACTIVE)
                .build();

        depositRepository.save(deposit);
        log.info("✅ Dépôt créé: {} {} {}", deposit.getClientName(), amount, type);

        // ✅ Ajouter le montant au cashBalance + totalBalance du Dashboard
        jdbc.update(
                "UPDATE agents SET cash_balance = cash_balance + ?, total_balance = total_balance + ? WHERE id = ?",
                amount, amount, agentId
        );
        log.info("📈 cashBalance agent {} +{} (dépôt client)", agentId, amount);

        return toMap(deposit);
    }

    // ── Retirer un dépôt (clôture) ───────────────────────────
    @Transactional
    public Map<String, Object> withdrawDeposit(Long agentId, Long depositId) {
        ClientDeposit deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new RuntimeException("الوديعة غير موجودة"));

        if (!deposit.getAgent().getId().equals(agentId)) {
            throw new RuntimeException("غير مصرح");
        }
        if (deposit.getStatus() == ClientDeposit.DepositStatus.WITHDRAWN) {
            throw new RuntimeException("الوديعة مسحوبة بالفعل");
        }

        deposit.setStatus(ClientDeposit.DepositStatus.WITHDRAWN);
        deposit.setWithdrawnAt(LocalDateTime.now());
        depositRepository.save(deposit);

        // ✅ Soustraire le montant du cashBalance + totalBalance du Dashboard
        jdbc.update(
                "UPDATE agents SET cash_balance = cash_balance - ?, total_balance = total_balance - ? WHERE id = ?",
                deposit.getAmount(), deposit.getAmount(), agentId
        );
        log.info("📉 cashBalance agent {} -{} (retrait dépôt client)", agentId, deposit.getAmount());

        log.info("✅ Dépôt retiré: {}", depositId);
        return toMap(deposit);
    }

    // ── Liste des dépôts de l'agent ───────────────────────────
    public Map<String, Object> getDeposits(Long agentId) {
        List<ClientDeposit> all = depositRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId);

        BigDecimal totalActive = depositRepository.sumActiveByAgentId(agentId);
        Long       countActive = depositRepository.countActiveByAgentId(agentId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (ClientDeposit d : all) { list.add(toMap(d)); }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deposits",    list);
        result.put("totalActive", totalActive);
        result.put("countActive", countActive);
        result.put("total",       all.size());
        return result;
    }

    // ── Mapper en Map ─────────────────────────────────────────
    private Map<String, Object> toMap(ClientDeposit d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          d.getId());
        m.put("clientName",  d.getClientName());
        m.put("clientPhone", d.getClientPhone());
        m.put("amount",      d.getAmount());
        m.put("type",        d.getType().name());
        m.put("bankName",    d.getBankName());
        m.put("bankAccount", d.getBankAccount());
        m.put("status",      d.getStatus().name());
        m.put("reference",   d.getReference());
        m.put("notes",       d.getNotes());
        m.put("createdAt",   d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        m.put("withdrawnAt", d.getWithdrawnAt() != null ? d.getWithdrawnAt().toString() : null);
        return m;
    }
}