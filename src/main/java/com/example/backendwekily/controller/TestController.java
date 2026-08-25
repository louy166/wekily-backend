package com.example.backendwekily.controller;

import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.Transaction;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.AgentTokenRepository;
import com.example.backendwekily.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestController {

    private final AgentTokenRepository  agentTokenRepository;
    private final AgentRepository       agentRepository;
    private final TransactionRepository transactionRepository;

    // GET /api/test/token?token=xxx  → vérifie si le token est valide
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> checkToken(
            @RequestParam String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        var opt = agentTokenRepository.findByToken(token);
        if (opt.isPresent()) {
            Agent agent = opt.get().getAgent();
            result.put("valid",   true);
            result.put("agentId", agent.getId());
            result.put("name",    agent.getFullName());
            result.put("balance", agent.getTotalBalance());
        } else {
            result.put("valid",   false);
            result.put("message", "Token non trouvé");
        }
        return ResponseEntity.ok(result);
    }

    // POST /api/test/transaction — crée une transaction de test
    @PostMapping("/transaction")
    public ResponseEntity<Map<String, Object>> testTransaction(
            @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String token = authHeader.replace("Bearer ", "").trim();
            var opt = agentTokenRepository.findByToken(token);
            if (opt.isEmpty()) {
                result.put("error", "Token invalide");
                return ResponseEntity.status(401).body(result);
            }

            Agent agent = opt.get().getAgent();

            Transaction tx = Transaction.builder()
                    .agent(agent)
                    .clientName("TEST")
                    .amount(BigDecimal.valueOf(100))
                    .type(Transaction.TransactionType.DEPOSIT)
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .build();

            transactionRepository.save(tx);

            result.put("success",   true);
            result.put("reference", tx.getReference());
            result.put("agentId",   agent.getId());
            result.put("message",   "Transaction de test sauvegardée ✅");

        } catch (Exception e) {
            result.put("error",   e.getMessage());
            result.put("type",    e.getClass().getSimpleName());
        }
        return ResponseEntity.ok(result);
    }
}