package com.example.backendwekily.service;

import com.example.backendwekily.dto.AuthRequestDTO;
import com.example.backendwekily.dto.AuthResponseDTO;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.AgentToken;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.AgentTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AgentRepository      agentRepository;
    private final AgentTokenRepository agentTokenRepository;
    private final PasswordEncoder      passwordEncoder;

    // ── Login email + password ────────────────────────────────
    public AuthResponseDTO login(AuthRequestDTO req) {
        log.info("📧 Login email: {}", req.getEmail());

        Agent agent = agentRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("البريد الإلكتروني غير مسجل"));

        if (!passwordEncoder.matches(req.getPassword(), agent.getPasswordHash())) {
            throw new RuntimeException("كلمة المرور غير صحيحة");
        }

        return buildResponse(agent);
    }

    // ── Logout ────────────────────────────────────────────────
    public void logout(Long agentId) {
        agentTokenRepository.deleteByAgentId(agentId);
        log.info("✅ Logout agent {}", agentId);
    }

    // ── Générer token et réponse ──────────────────────────────
    private AuthResponseDTO buildResponse(Agent agent) {
        // Supprimer les anciens tokens
        agentTokenRepository.deleteByAgentId(agent.getId());

        // Créer un nouveau token
        AgentToken token = AgentToken.builder()
                .agent(agent)
                .token(UUID.randomUUID().toString())
                .build();
        agentTokenRepository.save(token);

        log.info("✅ Token généré pour agent {}", agent.getId());

        return AuthResponseDTO.builder()
                .token(token.getToken())
                .type("Bearer")
                .agentId(agent.getId())
                .name(agent.getFullName())
                .role(agent.getRole())
                .build();
    }
}