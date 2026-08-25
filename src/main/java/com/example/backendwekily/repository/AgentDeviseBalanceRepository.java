package com.example.backendwekily.repository;

import com.example.backendwekily.entity.AgentDeviseBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AgentDeviseBalanceRepository extends JpaRepository<AgentDeviseBalance, Long> {

    // Trouver le solde d'un agent pour une devise précise
    Optional<AgentDeviseBalance> findByAgentIdAndDevise(Long agentId, String devise);

    // Tous les soldes d'un agent (toutes devises)
    List<AgentDeviseBalance> findByAgentId(Long agentId);

    // Mettre à jour directement en base
    @Modifying
    @Query("UPDATE AgentDeviseBalance b SET b.balance = b.balance + :amount WHERE b.agent.id = :agentId AND b.devise = :devise")
    int addBalance(Long agentId, String devise, BigDecimal amount);
}