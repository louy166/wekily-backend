package com.example.backendwekily.controller;

import com.example.backendwekily.entity.AgentToken;
import com.example.backendwekily.repository.AgentTokenRepository;
import com.example.backendwekily.service.PredecessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/predecessors")
@RequiredArgsConstructor
public class PredecessorController {

    private final PredecessorService service;
    private final AgentTokenRepository tokenRepo;

    // ── Extraire l'agentId depuis le token Bearer ───────────────
    private Long extractAgentId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new RuntimeException("غير مصرّح");
        }
        String token = auth.substring(7);
        AgentToken at = tokenRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException("جلسة غير صالحة"));
        return at.getAgent().getId();
    }

    // ── GET /api/predecessors ───────────────────────────────────
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth) {
        try {
            Long agentId = extractAgentId(auth);
            return ResponseEntity.ok(service.listByAgent(agentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── POST /api/predecessors/deposit ──────────────────────────
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(
            @RequestHeader("Authorization") String auth,
            @RequestBody Map<String, Object> body) {
        try {
            Long agentId = extractAgentId(auth);
            String phone  = (String) body.get("phone");
            String name   = (String) body.get("name");
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String notes  = body.get("notes") != null ? body.get("notes").toString() : "";

            return ResponseEntity.ok(service.deposit(agentId, phone, name, amount, notes));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── POST /api/predecessors/{id}/withdraw ────────────────────
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdraw(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Long agentId = extractAgentId(auth);
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String notes = body.get("notes") != null ? body.get("notes").toString() : "";

            return ResponseEntity.ok(service.withdraw(agentId, id, amount, notes));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}