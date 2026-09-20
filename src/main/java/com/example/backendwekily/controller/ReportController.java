package com.example.backendwekily.controller;

import com.example.backendwekily.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportService reportService;

    // GET /api/reports?period=الشهر
    @GetMapping
    public ResponseEntity<Map<String, Object>> getReport(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "الشهر") String period) {
        Long agentId = reportService.extractAgentId(token);
        return ResponseEntity.ok(reportService.getReport(agentId, period));
    }

    // ✅ GET /api/reports/balances — الرصيد الحالي لكل قسم
    @GetMapping("/balances")
    public ResponseEntity<?> getBalances(
            @RequestHeader("Authorization") String token) {
        Long agentId = reportService.extractAgentId(token);
        return ResponseEntity.ok(reportService.getCurrentBalances(agentId));
    }

    // ✅ POST /api/reports/reset-accounts — تصفير العمولات، المصاريف والأرباح
    @PostMapping("/reset-accounts")
    public ResponseEntity<?> resetAccounts(
            @RequestHeader("Authorization") String token) {
        try {
            Long agentId = reportService.extractAgentId(token);
            reportService.resetFinancialAccounts(agentId);
            return ResponseEntity.ok(Map.of("message", "Accounts reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}