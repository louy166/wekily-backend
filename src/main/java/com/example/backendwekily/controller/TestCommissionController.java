package com.example.backendwekily.controller;

import com.example.backendwekily.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestCommissionController {

    private final CommissionService commissionService;

    // GET /api/test/commission?amount=1500&bankId=1&type=WITHDRAWAL
    @GetMapping("/commission")
    public ResponseEntity<Map<String, Object>> testCommission(
            @RequestParam(defaultValue = "1500") BigDecimal amount,
            @RequestParam(defaultValue = "1") Integer bankId,
            @RequestParam(defaultValue = "WITHDRAWAL") String type) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", amount);
        result.put("bankId", bankId);
        result.put("type", type);

        if ("WITHDRAWAL".equals(type)) {
            BigDecimal fee = commissionService.calcWithdrawalFee(amount, bankId);
            BigDecimal agentPart = fee.multiply(BigDecimal.valueOf(50))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            result.put("fee", fee);
            result.put("agentPart_50pct", agentPart);
        } else {
            BigDecimal commission = commissionService.calcDepositCommission(amount, bankId);
            result.put("commission", commission);
        }

        return ResponseEntity.ok(result);
    }

    // POST /api/test/record-commission — Test enregistrement direct
    @PostMapping("/record-commission")
    public ResponseEntity<Map<String, String>> recordTest(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1500") BigDecimal amount,
            @RequestParam(defaultValue = "1") Long agencyId,
            @RequestParam(defaultValue = "WITHDRAWAL") String type) {

        Long agentId = commissionService.extractAgentId(token);
        BigDecimal fee = "WITHDRAWAL".equals(type)
                ? commissionService.calcWithdrawalFee(amount, 1)
                : commissionService.calcDepositCommission(amount, 1);

        commissionService.recordCommission(agentId, agencyId, null, type,
                amount, fee, BigDecimal.valueOf(50));

        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "fee", fee.toString(),
                "agentId", agentId.toString()
        ));
    }
}