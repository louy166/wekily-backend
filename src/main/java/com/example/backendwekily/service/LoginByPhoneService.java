package com.example.backendwekily.service;

import com.example.backendwekily.dto.AuthResponseDTO;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.AgentToken;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.AgentTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginByPhoneService {

    private final AgentRepository      agentRepository;
    private final AgentTokenRepository agentTokenRepository;

    @Transactional
    public AuthResponseDTO loginByPhone(String phone, String pin) {
        log.info("📱 Login phone: {}", phone);

        // Chercher l'agent par téléphone
        Agent agent = agentRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("رقم الهاتف غير مسجل"));

        // ✅ 1. Vérifier si l'agent est bloqué par l'admin (AVANT tout le reste)
        if (Boolean.TRUE.equals(agent.getBlocked())) {
            log.warn("🚫 Agent {} tenté de se connecter — compte bloqué", phone);
            throw new RuntimeException("تم تعليق حسابك من قبل الإدارة. يرجى التواصل مع الدعم.");
        }

        // 2. Vérifier le PIN
        if (agent.getPin() == null || !agent.getPin().equals(pin)) {
            throw new RuntimeException("رمز PIN غير صحيح");
        }

        // 3. Vérifier que le compte est actif
        if (Boolean.FALSE.equals(agent.getActive())) {
            throw new RuntimeException("الحساب غير مفعّل");
        }

        // 4. Supprimer les anciens tokens
        try {
            agentTokenRepository.deleteByAgentId(agent.getId());
        } catch (Exception e) {
            log.warn("Impossible de supprimer les anciens tokens: {}", e.getMessage());
        }

        // 5. Créer un nouveau token
        AgentToken token = AgentToken.builder()
                .agent(agent)
                .token(UUID.randomUUID().toString())
                .build();
        agentTokenRepository.save(token);

        log.info("✅ Login réussi pour agent {} (ID: {})", agent.getFullName(), agent.getId());

        return AuthResponseDTO.builder()
                .token(token.getToken())
                .type("Bearer")
                .agentId(agent.getId())
                .name(agent.getFullName())
                .role(agent.getRole())
                .build();
    }
}