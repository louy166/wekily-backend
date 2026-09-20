package com.example.backendwekily.service;

import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final AgentRepository       agentRepository;
    private final AgentTokenRepository  agentTokenRepository;
    private final AgencyRepository      agencyRepository;
    private final TransactionRepository transactionRepository;

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    public Map<String, Object> getProfile(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        // Compter les agences actives
        long agencyCount = agencyRepository.countActiveByAgentId(agentId);

        // Compter les transactions totales
        LocalDateTime beginOfTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        int txCount = safeInt(transactionRepository.countDailyTransactions(agentId, beginOfTime));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fullName",         agent.getFullName());
        result.put("phone",            agent.getPhone()      != null ? agent.getPhone()      : "");
        result.put("email",            agent.getEmail()      != null ? agent.getEmail()      : "");
        result.put("nationalId",       agent.getNationalId() != null ? agent.getNationalId() : "");
        result.put("city",             agent.getCity()       != null ? agent.getCity()       : "");
        result.put("region",           agent.getRegion()     != null ? agent.getRegion()     : "");
        result.put("role",             agent.getRole()       != null ? agent.getRole()       : "وكيل");
        result.put("agentCode",        String.format("AGT-MR-%06d", agentId));
        result.put("totalBalance",     agent.getTotalBalance() != null ? agent.getTotalBalance() : 0);
        result.put("transactionCount", txCount);
        result.put("agencyCount",      agencyCount);

        return result;
    }

    @Transactional
    public Map<String, Object> updateProfile(Long agentId, Map<String, String> req) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        if (req.containsKey("fullName") && req.get("fullName") != null) {
            agent.setFullName(req.get("fullName"));
        }
        if (req.containsKey("phone") && req.get("phone") != null) {
            agent.setPhone(req.get("phone"));
        }
        if (req.containsKey("city") && req.get("city") != null) {
            agent.setCity(req.get("city"));
        }
        if (req.containsKey("region") && req.get("region") != null) {
            agent.setRegion(req.get("region"));
        }

        agentRepository.save(agent);
        log.info("✅ Profil mis à jour pour agent {}", agentId);

        return getProfile(agentId);
    }

    // Surcharges pour gérer proprement Long et Integer
    private int safeInt(Long v)    { return v != null ? v.intValue() : 0; }
    private int safeInt(Integer v) { return v != null ? v : 0; }
}