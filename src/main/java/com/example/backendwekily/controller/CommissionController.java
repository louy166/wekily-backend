package com.example.backendwekily.controller;

import com.example.backendwekily.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CommissionController {

    private final CommissionService commissionService;

    // GET /api/commissions — dashboard complet
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestHeader("Authorization") String token) {
        Long agentId = commissionService.extractAgentId(token);
        return ResponseEntity.ok(commissionService.getCommissionDashboard(agentId));
    }

    // PUT /api/commissions/rate/{agencyId} — modifier le taux
    @PutMapping("/rate/{agencyId}")
    public ResponseEntity<Map<String, String>> updateRate(
            @RequestHeader("Authorization") String token,
            @PathVariable Long agencyId,
            @RequestBody Map<String, Double> body) {
        Long agentId = commissionService.extractAgentId(token);
        BigDecimal rate = BigDecimal.valueOf(body.getOrDefault("rate", 50.0));
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "النسبة يجب أن تكون بين 0 و 100"));
        }
        commissionService.updateAgencyRate(agentId, agencyId, rate);
        return ResponseEntity.ok(Map.of("message", "تم تحديث النسبة بنجاح"));
    }

    // GET /api/commissions/calculate?type=WITHDRAWAL&amount=1500&agencyId=3
    @GetMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(
            @RequestHeader("Authorization") String token,
            @RequestParam String type,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) Long agencyId) {

        Long agentId = commissionService.extractAgentId(token);

        // bank_id depuis l'agence (défaut 1)
        Integer bankId = getBankIdFromCommission(agencyId);

        BigDecimal fee;
        BigDecimal agentCommission;
        BigDecimal rate;

        if ("WITHDRAWAL".equals(type)) {
            fee  = commissionService.calcWithdrawalFee(amount, bankId);
            rate = agencyId != null
                    ? commissionService.getAgencyRate(agentId, agencyId)
                    : new BigDecimal("50");
            agentCommission = fee.multiply(rate)
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else {
            // DEPOSIT — agent reçoit 100% de la commission
            fee             = commissionService.calcDepositCommission(amount, bankId);
            rate            = new BigDecimal("100");
            agentCommission = fee;
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("type",            type);
        result.put("amount",          amount);
        result.put("bankId",          bankId);
        result.put("fee",             fee);
        result.put("rate",            rate);
        result.put("agentCommission", agentCommission);
        return ResponseEntity.ok(result);
    }
    private Integer getBankIdFromCommission(Long agencyId) {
        if (agencyId == null) { return 1; }
        try {
            return commissionService.getBankIdPublic(agencyId);
        } catch (Exception e) {
            return 1;
        }
    }
}