package com.example.backendwekily.controller;

import com.example.backendwekily.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/admin/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;

    // ── Authentification Admin ────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String username = req.getOrDefault("username", "");
        String password = req.getOrDefault("password", "");
        log.info("🔐 [Admin] Tentative login: {}", username);
        return adminService.login(username, password);
    }

    // ── Statistiques Dashboard ────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<?> stats(@RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.getStats());
    }

    // ── Agents ────────────────────────────────────────────────
    @GetMapping("/agents")
    public ResponseEntity<?> getAgents(@RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.getAllAgents());
    }

    @PostMapping("/agents/{id}/block")
    public ResponseEntity<?> blockAgent(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        adminService.setAgentBlocked(id, true);
        return ResponseEntity.ok(Map.of("message", "تم حظر الوكيل بنجاح", "blocked", true));
    }

    @PostMapping("/agents/{id}/unblock")
    public ResponseEntity<?> unblockAgent(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        adminService.setAgentBlocked(id, false);
        return ResponseEntity.ok(Map.of("message", "تم رفع الحظر بنجاح", "blocked", false));
    }

    // ── Banks ─────────────────────────────────────────────────
    @GetMapping("/banks")
    public ResponseEntity<?> getBanks(@RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.getAllBanks());
    }

    @PostMapping("/banks")
    public ResponseEntity<?> addBank(
            @RequestBody Map<String, String> req,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.addBank(req.get("name"), req.get("icon")));
    }

    @PutMapping("/banks/{id}")
    public ResponseEntity<?> updateBank(
            @PathVariable Long id,
            @RequestBody Map<String, String> req,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.updateBank(id, req.get("name"), req.get("icon")));
    }

    @DeleteMapping("/banks/{id}")
    public ResponseEntity<?> deleteBank(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        adminService.deleteBank(id);
        return ResponseEntity.ok(Map.of("message", "تم حذف البنك"));
    }

    // ── Tiers ─────────────────────────────────────────────────
    @GetMapping("/tiers")
    public ResponseEntity<?> getTiers(@RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.getAllTiers());
    }

    @PostMapping("/tiers")
    public ResponseEntity<?> addTier(
            @RequestBody Map<String, Object> req,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.addTier(req));
    }

    @PutMapping("/tiers/{id}")
    public ResponseEntity<?> updateTier(
            @PathVariable Long id,
            @RequestBody Map<String, Object> req,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        return ResponseEntity.ok(adminService.updateTier(id, req));
    }

    @DeleteMapping("/tiers/{id}")
    public ResponseEntity<?> deleteTier(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        if (!adminService.isValidToken(auth)) { return ResponseEntity.status(403).body("غير مصرح"); }
        adminService.deleteTier(id);
        return ResponseEntity.ok(Map.of("message", "تم حذف الشريحة"));
    }
}