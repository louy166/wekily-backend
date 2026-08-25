package com.example.backendwekily.service;

import com.example.backendwekily.entity.Agency;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyService {

    private final AgencyRepository      agencyRepository;
    private final JdbcTemplate            jdbc;
    private final AgentRepository       agentRepository;
    private final AgentTokenRepository  agentTokenRepository;
    private final TransactionRepository transactionRepository;

    // ── Nom d'agences par défaut ─────────────────────────────
    private static final List<String[]> DEFAULT_AGENCIES = List.of(
            new String[]{"بنكيلي",   "bankily"},
            new String[]{"مصرفي",    "masrvi" },
            new String[]{"السداد",   "sedad"  },
            new String[]{"موف موني", "moov"   }
    );

    public Long extractAgentId(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return agentTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"))
                .getAgent().getId();
    }

    // ✅ Auto-crée les agences si l'agent n'en a pas encore
    @Transactional
    public List<Map<String, Object>> getAgenciesWithStats(Long agentId) {

        List<Agency> agencies = agencyRepository.findByAgentIdAndActiveTrue(agentId);
        log.info("📊 Agences trouvées pour agent {}: {}", agentId, agencies.size());

        // ✅ Auto-seed : créer les 4 agences si aucune n'existe
        if (agencies.isEmpty()) {
            log.warn("⚠️ Aucune agence pour agent {} — vérifier l'inscription", agentId);
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Agency ag : agencies) {
            // ✅ JDBC direct — le plus fiable pour les stats du jour
            BigDecimal todayDep = safeJdbc(
                    "SELECT COALESCE(SUM(amount),0) FROM transactions " +
                            "WHERE agent_id=? AND (agency_id=? OR agency_name=?) " +
                            "AND type='DEPOSIT' AND DATE(created_at)=CURDATE()",
                    agentId, ag.getId(), ag.getName() != null ? ag.getName() : "");
            BigDecimal todayWit = safeJdbc(
                    "SELECT COALESCE(SUM(ABS(amount)),0) FROM transactions " +
                            "WHERE agent_id=? AND (agency_id=? OR agency_name=?) " +
                            "AND type='WITHDRAWAL' AND DATE(created_at)=CURDATE()",
                    agentId, ag.getId(), ag.getName() != null ? ag.getName() : "");

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",               ag.getId());
            m.put("name",             ag.getName());
            m.put("icon",             ag.getIcon());
            m.put("balance",          ag.getCurrentBalance() != null ? ag.getCurrentBalance() : BigDecimal.ZERO);
            m.put("currentBalance",   ag.getCurrentBalance() != null ? ag.getCurrentBalance() : BigDecimal.ZERO);
            m.put("todayDeposits",    todayDep);
            m.put("todayWithdrawals", todayWit);
            m.put("active",           ag.getActive());
            result.add(m);
        }
        log.info("✅ {} agences retournées pour agent {}", result.size(), agentId);
        return result;
    }

    // ── Créer les 4 agences par défaut ───────────────────────
    private List<Agency> createDefaultAgencies(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        List<Agency> created = new ArrayList<>();
        for (String[] ag : DEFAULT_AGENCIES) {
            Agency agency = Agency.builder()
                    .name(ag[0])
                    .icon(ag[1])
                    .currentBalance(BigDecimal.ZERO)
                    .active(true)
                    .agent(agent)
                    .build();
            created.add(agencyRepository.save(agency));
            log.info("✅ Agence créée: {}", ag[0]);
        }
        return created;
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private BigDecimal safeJdbc(String sql, Object... args) {
        try {
            BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class, args);
            return result != null ? result : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("⚠️ safeJdbc: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ── getAgencyById ─────────────────────────────────────────
    public Agency getAgencyById(Long id) {
        return agencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("الوكالة غير موجودة"));
    }

    // ── createAgency ──────────────────────────────────────────
    public Agency createAgency(Agency agency) {
        return agencyRepository.save(agency);
    }

    // ── updateAgency ──────────────────────────────────────────
    public Agency updateAgency(Long id, Agency updated) {
        Agency existing = getAgencyById(id);
        if (updated.getName()           != null) existing.setName(updated.getName());
        if (updated.getIcon()           != null) existing.setIcon(updated.getIcon());
        if (updated.getCurrentBalance() != null) existing.setCurrentBalance(updated.getCurrentBalance());
        if (updated.getActive()         != null) existing.setActive(updated.getActive());
        return agencyRepository.save(existing);
    }

    // ── deleteAgency ──────────────────────────────────────────
    public void deleteAgency(Long id) {
        Agency agency = getAgencyById(id);
        agency.setActive(false); // soft delete
        agencyRepository.save(agency);
        log.info("✅ Agence {} désactivée", id);
    }

    // ✅ Ajouter une nouvelle agence pour un agent (depuis AgenciesScreen mobile)
    @Transactional
    public Agency addAgencyForAgent(Long agentId, String name, String icon, BigDecimal balance) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("الوكيل غير موجود"));

        // Empêcher le doublon (même icon déjà ajouté et actif)
        boolean exists = agencyRepository.findByAgentIdAndActiveTrue(agentId)
                .stream()
                .anyMatch(a -> icon.equalsIgnoreCase(a.getIcon()));
        if (exists) {
            throw new RuntimeException(name + " مضافة بالفعل");
        }

        Agency agency = Agency.builder()
                .name(name)
                .icon(icon)
                .currentBalance(balance != null ? balance : BigDecimal.ZERO)
                .active(true)
                .agent(agent)
                .build();

        Agency saved = agencyRepository.save(agency);
        log.info("✅ Agence {} ajoutée pour agent {} avec solde initial {}", name, agentId, balance);
        return saved;
    }

    // ✅ Supprimer (soft delete) une agence appartenant à un agent précis
    @Transactional
    public void deleteAgencyForAgent(Long agentId, Long agencyId) {
        Agency agency = getAgencyById(agencyId);
        if (!agency.getAgent().getId().equals(agentId)) {
            throw new RuntimeException("غير مصرح بحذف هذه الوكالة");
        }
        agency.setActive(false);
        agencyRepository.save(agency);
        log.info("✅ Agence {} ({}) supprimée pour agent {}", agencyId, agency.getName(), agentId);
    }

}