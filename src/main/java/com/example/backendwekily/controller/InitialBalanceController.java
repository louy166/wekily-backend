package com.example.backendwekily.controller;

import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.repository.*;
import com.example.backendwekily.service.AgencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InitialBalanceController {

    private final AgentTokenRepository agentTokenRepository;
    private final AgentRepository      agentRepository;
    private final AgencyRepository     agencyRepository;

    private Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // POST /api/agencies/initial-balance
    @PostMapping("/initial-balance")
    public ResponseEntity<Map<String, Object>> setInitialBalance(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        Long agentId = extractAgentId(authHeader);
        Agent agent  = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        // ── Rصيد نقدي ─────────────────────────────────────
        BigDecimal cashBalance = new BigDecimal(
                body.getOrDefault("cashBalance", "0").toString());

        // ── Rصيد كل وكالة ─────────────────────────────────
        BigDecimal totalAgencies = BigDecimal.ZERO;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agencyBalances =
                (List<Map<String, Object>>) body.getOrDefault("agencyBalances", new ArrayList<>());

        for (Map<String, Object> ab : agencyBalances) {
            Long       agencyId = Long.valueOf(ab.get("agencyId").toString());
            BigDecimal balance  = new BigDecimal(ab.getOrDefault("balance", "0").toString());

            agencyRepository.findById(agencyId).ifPresent(agency -> {
                agency.setCurrentBalance(balance);
                agencyRepository.save(agency);
                log.info("✅ Solde agence {} → {}", agency.getName(), balance);
            });

            totalAgencies = totalAgencies.add(balance);
        }

        // ✅ Stocker cash séparément + total pour stats globales
        agent.setCashBalance(cashBalance);               // affiché dans la carte Dashboard
        agent.setTotalBalance(cashBalance.add(totalAgencies)); // total pour les stats
        agentRepository.save(agent);
        log.info("✅ Rصيد agent {} → cash={}, agencies={}, total={}",
                agentId, cashBalance, totalAgencies, cashBalance.add(totalAgencies));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success",      true);
        response.put("cashBalance",  cashBalance);
        response.put("totalBalance", cashBalance.add(totalAgencies));
        response.put("message",      "تم إعداد الرصيد الأولي بنجاح");
        return ResponseEntity.ok(response);
    }
}