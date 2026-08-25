package com.example.backendwekily.controller;

import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.repository.AgencyRepository;
import com.example.backendwekily.service.AgencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgencyController {

    private final AgencyRepository agencyRepository;
    private final AgencyService    agencyService;

    // GET /api/agencies — agences avec stats + auto-création si vides
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAgencies(
            @RequestHeader("Authorization") String token) {
        Long agentId = agencyService.extractAgentId(token);
        return ResponseEntity.ok(agencyService.getAgenciesWithStats(agentId));
    }

    // GET /api/agencies/all — toutes les agences (sans token)
    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllAgencies() {
        List<Map<String, Object>> result = new ArrayList<>();
        agencyRepository.findAll().forEach(ag -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",   ag.getId());
            m.put("name", ag.getName());
            result.add(m);
        });
        return ResponseEntity.ok(result);
    }

    // ✅ POST /api/agencies — ajouter une nouvelle agence pour l'agent connecté
    @PostMapping
    public ResponseEntity<?> addAgency(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String token) {
        try {
            Long agentId = agencyService.extractAgentId(token);

            String name = (String) body.get("name");
            String icon = (String) body.get("icon");
            Object balanceObj = body.get("balance");

            if (name == null || name.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "اسم البنك مطلوب"));
            }
            if (icon == null || icon.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "معرف البنك مطلوب"));
            }

            // ✅ Conversion sûre du solde (Integer, Double ou String selon JSON)
            java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
            if (balanceObj != null) {
                balance = new java.math.BigDecimal(balanceObj.toString());
            }

            Agency created = agencyService.addAgencyForAgent(agentId, name, icon, balance);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id",      created.getId());
            resp.put("name",    created.getName());
            resp.put("icon",    created.getIcon());
            resp.put("balance", created.getCurrentBalance());
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("❌ Erreur addAgency: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ✅ DELETE /api/agencies/{id} — supprimer (soft delete) une agence de l'agent
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAgency(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        try {
            Long agentId = agencyService.extractAgentId(token);
            agencyService.deleteAgencyForAgent(agentId, id);
            return ResponseEntity.ok(Map.of("message", "تم حذف الوكالة بنجاح"));
        } catch (Exception e) {
            log.error("❌ Erreur deleteAgency: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}