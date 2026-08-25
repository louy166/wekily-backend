package com.example.backendwekily.controller;

import com.example.backendwekily.dto.TransactionRequestDTO;
import com.example.backendwekily.dto.TransactionResponseDTO;
import com.example.backendwekily.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionService transactionService;

    // POST /api/transactions — سحب | إيداع | إضافة
    @PostMapping
    public ResponseEntity<TransactionResponseDTO> create(
            @RequestHeader("Authorization") String token,
            @RequestBody TransactionRequestDTO req) {
        log.info("📥 Nouvelle transaction : type={} montant={}", req.getType(), req.getAmount());
        Long agentId = transactionService.extractAgentId(token);
        return ResponseEntity.ok(transactionService.createTransaction(agentId, req));
    }

    // GET /api/transactions — Historique
    @GetMapping
    public ResponseEntity<List<TransactionResponseDTO>> getAll(
            @RequestHeader("Authorization") String token) {
        Long agentId = transactionService.extractAgentId(token);
        return ResponseEntity.ok(transactionService.getTransactions(agentId));
    }

    // ✅ PUT /api/transactions/{id}/commission
    // Ajouter ou corriger la commission manuelle d'une opération passée
    @PutMapping("/{id}/commission")
    public ResponseEntity<?> updateCommission(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String token) {
        Long agentId = transactionService.extractAgentId(token);
        Object val = body.get("manualCommission");
        if (val == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "يرجى إرسال قيمة العمولة"));
        }
        double commission = Double.parseDouble(val.toString());
        transactionService.updateManualCommission(agentId, id, commission);
        log.info("✅ Commission mise à jour: tx={} comm={}", id, commission);
        return ResponseEntity.ok(Map.of(
                "message", "تم تحديث العمولة بنجاح",
                "manualCommission", commission
        ));
    }
}