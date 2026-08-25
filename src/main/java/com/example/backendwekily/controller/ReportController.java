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

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReport(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "الشهر") String period,
            @RequestParam(required = false) String from,  // ✅ تاريخ البداية
            @RequestParam(required = false) String to) {  // ✅ تاريخ النهاية
        Long agentId = reportService.extractAgentId(token);
        // ✅ نمرر from و to للـ service
        return ResponseEntity.ok(reportService.getReport(agentId, period, from, to));
    }
}