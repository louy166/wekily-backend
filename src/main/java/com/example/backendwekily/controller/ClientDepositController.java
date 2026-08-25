package com.example.backendwekily.controller;

import com.example.backendwekily.service.ClientDepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ClientDepositController {

    private final ClientDepositService depositService;

    // GET /api/deposits — liste de tous les dépôts
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDeposits(
            @RequestHeader("Authorization") String token) {
        Long agentId = depositService.extractAgentId(token);
        return ResponseEntity.ok(depositService.getDeposits(agentId));
    }

    // POST /api/deposits — créer un dépôt
    @PostMapping
    public ResponseEntity<Map<String, Object>> createDeposit(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> body) {
        Long agentId = depositService.extractAgentId(token);
        return ResponseEntity.ok(depositService.createDeposit(agentId, body));
    }

    // PUT /api/deposits/{id}/withdraw — retirer un dépôt
    @PutMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawDeposit(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long agentId = depositService.extractAgentId(token);
        return ResponseEntity.ok(depositService.withdrawDeposit(agentId, id));
    }
}