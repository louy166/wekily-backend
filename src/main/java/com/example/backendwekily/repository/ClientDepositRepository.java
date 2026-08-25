package com.example.backendwekily.repository;

import com.example.backendwekily.entity.ClientDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientDepositRepository extends JpaRepository<ClientDeposit, Long> {

    List<ClientDeposit> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    List<ClientDeposit> findByAgentIdAndStatusOrderByCreatedAtDesc(
            Long agentId, ClientDeposit.DepositStatus status);

    @Query("SELECT COALESCE(SUM(d.amount),0) FROM ClientDeposit d " +
            "WHERE d.agent.id=:agentId AND d.status='ACTIVE'")
    BigDecimal sumActiveByAgentId(Long agentId);

    @Query("SELECT COUNT(d) FROM ClientDeposit d " +
            "WHERE d.agent.id=:agentId AND d.status='ACTIVE'")
    Long countActiveByAgentId(Long agentId);

    // ✅ Ajouté pour identifier automatiquement l'identité du client par son téléphone
    Optional<ClientDeposit> findFirstByClientPhoneOrderByCreatedAtDesc(String clientPhone);
}