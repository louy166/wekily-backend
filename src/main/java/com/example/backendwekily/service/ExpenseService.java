package com.example.backendwekily.service;

import com.example.backendwekily.dto.ExpenseRequestDTO;
import com.example.backendwekily.dto.ExpenseResponseDTO;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.entity.Expense;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.AgentTokenRepository;
import com.example.backendwekily.repository.ExpenseRepository;
import com.example.backendwekily.repository.AgencyRepository;
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
public class ExpenseService {

    private final ExpenseRepository    expenseRepository;
    private final AgentRepository      agentRepository;
    private final AgentTokenRepository agentTokenRepository;
    private final AgencyRepository     agencyRepository;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    @Transactional
    public ExpenseResponseDTO createExpense(Long agentId, ExpenseRequestDTO req) {
        // Récupération de l'agent
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        BigDecimal amount = BigDecimal.valueOf(Math.abs(req.getAmount()));
        String fundingType = req.getFundingType() != null ? req.getFundingType().toUpperCase() : "CASH";
        String bankName = req.getBankName() != null ? req.getBankName().trim() : "";

        // Log de débogage pour vérifier le solde lu
        log.info("DEBUG: Vérification solde - Agent ID: {}, Solde Cash trouvé: {}, Montant demande: {}",
                agentId, agent.getCashBalance(), amount);

        // ── 1. GESTION DU DÉBIT (CASH OU BANQUE) ──
        if ("CASH".equals(fundingType)) {
            BigDecimal currentCash = agent.getCashBalance() != null ? agent.getCashBalance() : BigDecimal.ZERO;

            if (currentCash.compareTo(amount) < 0) {
                log.error("Erreur de solde: Disponible={}, Demandé={}", currentCash, amount);
                throw new RuntimeException("رصيد الصندوق النقدي غير كافٍ. المتوفر: " + currentCash + " المطلوب: " + amount);
            }

            agent.setCashBalance(currentCash.subtract(amount));
            log.info("💰 Dépense CASH: solde mis à jour → {}", agent.getCashBalance());

        } else if ("BANK".equals(fundingType)) {
            if (bankName.isEmpty()) {
                throw new RuntimeException("اسم البنك مطلوب عند اختيار الدفع البنكي");
            }

            List<Agency> agencies = agencyRepository.findByAgentIdAndActiveTrue(agentId);
            Agency targetBank = agencies.stream()
                    .filter(a -> bankName.equalsIgnoreCase(a.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("حساب البنك (" + bankName + ") غير موجود"));

            BigDecimal currentBankBalance = targetBank.getCurrentBalance() != null ? targetBank.getCurrentBalance() : BigDecimal.ZERO;

            if (currentBankBalance.compareTo(amount) < 0) {
                throw new RuntimeException("رصيد حساب " + bankName + " غير كافٍ");
            }

            targetBank.setCurrentBalance(currentBankBalance.subtract(amount));
            agencyRepository.save(targetBank);
        }

        // ── 2. MISE À JOUR DU TOTAL GLOBAL ET PERSISTANCE ──
        agent.setTotalBalance(
                (agent.getTotalBalance() != null ? agent.getTotalBalance() : BigDecimal.ZERO).subtract(amount)
        );

        // Utilisation de saveAndFlush pour garantir l'écriture immédiate en DB
        agentRepository.saveAndFlush(agent);

        // ── 3. SAUVEGARDE DE LA DÉPENSE ──
        Expense expense = Expense.builder()
                .agent(agent)
                .category(req.getCategory())
                .description(req.getDescription())
                .amount(amount.negate())
                .fundingType(fundingType)
                .bankName("BANK".equals(fundingType) ? bankName : null)
                .build();

        expenseRepository.save(expense);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM");
        return ExpenseResponseDTO.builder()
                .id(expense.getId())
                .reference(expense.getReference())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .amount(expense.getAmount().doubleValue())
                .fundingType(expense.getFundingType())
                .bankName(expense.getBankName())
                .date(expense.getCreatedAt() != null ? expense.getCreatedAt().format(fmt) : "الآن")
                .build();
    }

    public List<ExpenseResponseDTO> getExpenses(Long agentId) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM");
        return expenseRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                .stream()
                .map(e -> ExpenseResponseDTO.builder()
                        .id(e.getId())
                        .reference(e.getReference())
                        .category(e.getCategory())
                        .description(e.getDescription())
                        .amount(e.getAmount().doubleValue())
                        .fundingType(e.getFundingType())
                        .bankName(e.getBankName())
                        .date(e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "—")
                        .build())
                .collect(Collectors.toList());
    }
}