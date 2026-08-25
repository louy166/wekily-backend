package com.example.backendwekily.controller;

import com.example.backendwekily.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    // GET /api/dashboard
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestHeader("Authorization") String token) {
        Long agentId = dashboardService.extractAgentId(token);
        return ResponseEntity.ok(dashboardService.getDashboardData(agentId));
    }
}