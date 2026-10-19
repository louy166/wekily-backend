package com.example.backendwekily.service;

import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.Predecessor;
import com.example.backendwekily.entity.PredecessorOperation;
import com.example.backendwekily.entity.PredecessorOperation.OperationType;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.PredecessorOperationRepository;
import com.example.backendwekily.repository.PredecessorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredecessorService {

    private final PredecessorRepository predecessorRepo;
    private final PredecessorOperationRepository operationRepo;
    private final AgentRepository agentRepo;

    // ── Liste des prédécesseurs d'un agent ──────────────────────
    public Map<String, Object> listByAgent(Long agentId) {
        List<Predecessor> preds = predecessorRepo.findByAgentIdOrderByUpdatedAtDesc(agentId);

        BigDecimal totalBalance = BigDecimal.ZERO;
        List<Map<String, Object>> list = new ArrayList<>();

        for (Predecessor p : preds) {
            List<PredecessorOperation> ops = operationRepo.findByPredecessorIdOrderByCreatedAtDesc(p.getId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("phone", p.getPhone());
            item.put("name", p.getName());
            item.put("balance", p.getBalance());
            item.put("totalDeposited", p.getTotalDeposited());
            item.put("totalWithdrawn", p.getTotalWithdrawn());
            item.put("operationCount", ops.size());
            item.put("createdAt", p.getCreatedAt());

            List<Map<String, Object>> opsData = ops.stream().map(op -> {
                Map<String, Object> opMap = new LinkedHashMap<>();
                opMap.put("id", op.getId());
                opMap.put("amount", op.getAmount());
                opMap.put("type", op.getType().name());
                opMap.put("notes", op.getNotes());
                opMap.put("createdAt", op.getCreatedAt());
                return opMap;
            }).collect(Collectors.toList());

            item.put("operations", opsData);
            list.add(item);

            totalBalance = totalBalance.add(p.getBalance());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("predecessors", list);
        result.put("totalBalance", totalBalance);
        result.put("predecessorCount", preds.size());
        return result;
    }

    // ── Dépôt chez un prédécesseur ─────────────────────────────
    @Transactional
    public Map<String, Object> deposit(Long agentId, String phone, String name, BigDecimal amount, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("المبلغ يجب أن يكون أكبر من صفر");
        }

        Agent agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("الوكيل غير موجود"));

        // Trouver ou créer le prédécesseur
        Predecessor pred = predecessorRepo.findByPhoneAndAgentId(phone, agentId)
                .orElseGet(() -> {
                    log.info("✅ Nouveau prédécesseur: {} ({})", name, phone);
                    return predecessorRepo.save(Predecessor.builder()
                            .phone(phone)
                            .name(name)
                            .agent(agent)
                            .build());
                });

        // Mettre à jour le nom si changé
        if (name != null && !name.isEmpty() && !name.equals(pred.getName())) {
            pred.setName(name);
        }

        // Mettre à jour les soldes
        pred.setBalance(pred.getBalance().add(amount));
        pred.setTotalDeposited(pred.getTotalDeposited().add(amount));
        predecessorRepo.save(pred);

        // Créer l'opération
        operationRepo.save(PredecessorOperation.builder()
                .predecessor(pred)
                .amount(amount)
                .type(OperationType.DEPOSIT)
                .notes(notes)
                .build());

        log.info("✅ Dépôt {} chez prédécesseur {} (ID: {})", amount, pred.getName(), pred.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("predecessorId", pred.getId());
        result.put("newBalance", pred.getBalance());
        return result;
    }

    // ── Retrait (partiel ou total) ──────────────────────────────
    @Transactional
    public Map<String, Object> withdraw(Long agentId, Long predecessorId, BigDecimal amount, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("المبلغ يجب أن يكون أكبر من صفر");
        }

        Predecessor pred = predecessorRepo.findByIdAndAgentId(predecessorId, agentId)
                .orElseThrow(() -> new RuntimeException("السَّلَف غير موجود"));

        if (amount.compareTo(pred.getBalance()) > 0) {
            throw new RuntimeException("المبلغ أكبر من الرصيد المتاح (" + pred.getBalance() + ")");
        }

        // Mettre à jour les soldes
        pred.setBalance(pred.getBalance().subtract(amount));
        pred.setTotalWithdrawn(pred.getTotalWithdrawn().add(amount));
        predecessorRepo.save(pred);

        // Créer l'opération
        operationRepo.save(PredecessorOperation.builder()
                .predecessor(pred)
                .amount(amount)
                .type(OperationType.WITHDRAW)
                .notes(notes)
                .build());

        log.info("✅ Retrait {} du prédécesseur {} (ID: {}) — nouveau solde: {}",
                amount, pred.getName(), pred.getId(), pred.getBalance());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("predecessorId", pred.getId());
        result.put("newBalance", pred.getBalance());
        return result;
    }
}