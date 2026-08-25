package com.example.backendwekily.controller;

import com.example.backendwekily.dto.ExpenseRequestDTO;
import com.example.backendwekily.dto.ExpenseResponseDTO;
import com.example.backendwekily.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExpenseController {

    private final ExpenseService expenseService;

    // POST /api/expenses — تسجيل مصروف
    @PostMapping
    public ResponseEntity<ExpenseResponseDTO> create(
            @RequestHeader("Authorization") String token,
            @RequestBody ExpenseRequestDTO req) {
        log.info("💸 Nouveau mصروف : cat={} montant={}", req.getCategory(), req.getAmount());
        Long agentId = expenseService.extractAgentId(token);
        return ResponseEntity.ok(expenseService.createExpense(agentId, req));
    }

    // GET /api/expenses — Liste des dépenses
    @GetMapping
    public ResponseEntity<List<ExpenseResponseDTO>> getAll(
            @RequestHeader("Authorization") String token) {
        Long agentId = expenseService.extractAgentId(token);
        return ResponseEntity.ok(expenseService.getExpenses(agentId));
    }
}