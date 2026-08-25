package com.example.backendwekily.service;

import com.example.backendwekily.dto.AuthResponseDTO;
import com.example.backendwekily.dto.RegisterRequestDTO;
import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.AgentToken;
import com.example.backendwekily.repository.AgencyRepository;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.AgentTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private final AgentRepository      agentRepository;
    private final AgentTokenRepository agentTokenRepository;
    private final AgencyRepository     agencyRepository;

    // ✅ 7 agences disponibles — id correspond à RegisterScreen (1-7)
    private static final List<String[]> ALL_AGENCIES = List.of(
            new String[]{"مصرفي",    "masrvi",  "1"},
            new String[]{"بنكيلي",   "bankily", "2"},
            new String[]{"موف موني", "moov",    "3"},
            new String[]{"السداد",   "sedad",   "4"},
            new String[]{"بيم بنك",  "bimbank", "5"},
            new String[]{"أمانتي",   "amanty",  "6"},
            new String[]{"كليك",     "click",   "7"}
    );

    // ✅ Retourne AuthResponseDTO — compatible avec AuthController
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO req) {
        log.info("📝 Inscription: {}", req.getPhone());

        // Vérifier doublon téléphone
        if (agentRepository.existsByPhone(req.getPhone())) {
            throw new RuntimeException("رقم الهاتف مسجل مسبقاً");
        }

        // Créer l'agent
        Agent agent = Agent.builder()
                .fullName(req.getFullName())
                .phone(req.getPhone())
                .email(req.getEmail() != null ? req.getEmail()
                        : req.getPhone() + "@wekily.com")
                .passwordHash("no-password")
                .pin(req.getPin())
                .nationalId(req.getNationalId())
                .city(req.getCity())
                .region(req.getRegion())
                .role("وكيل")
                .totalBalance(BigDecimal.ZERO)
                .active(true)
                .build();

        agentRepository.save(agent);
        log.info("✅ Agent créé: {} (ID: {})", agent.getFullName(), agent.getId());

        // ✅ Créer UNIQUEMENT les agences sélectionnées par l'agent
        List<Integer> selectedIds = req.getAgencyIds() != null
                ? req.getAgencyIds()
                : List.of(1, 2, 3, 4); // défaut si non fourni

        int count = 0;
        for (String[] ag : ALL_AGENCIES) {
            int idx = Integer.parseInt(ag[2]); // id dans la liste
            if (selectedIds.contains(idx)) {
                agencyRepository.save(Agency.builder()
                        .name(ag[0]).icon(ag[1])
                        .currentBalance(BigDecimal.ZERO)
                        .active(true).agent(agent)
                        .build());
                count++;
                log.info("✅ Agence créée: {}", ag[0]);
            }
        }
        log.info("✅ {} agence(s) créée(s) pour agent {}", count, agent.getId());

        // Générer le token
        AgentToken token = AgentToken.builder().agent(agent).build();
        agentTokenRepository.save(token);

        // ✅ Retourner AuthResponseDTO
        return AuthResponseDTO.builder()
                .token(token.getToken())
                .type("Bearer")
                .agentId(agent.getId())
                .name(agent.getFullName())
                .role(agent.getRole())
                .build();
    }
}