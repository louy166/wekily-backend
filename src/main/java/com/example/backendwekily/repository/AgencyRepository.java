package com.example.backendwekily.repository;

import com.example.backendwekily.entity.Agency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AgencyRepository extends JpaRepository<Agency, Long> {

    // ✅ Lit depuis "active" (pas "is_active")
    @Query("SELECT a FROM Agency a WHERE a.agent.id = :agentId AND a.active = true")
    List<Agency> findByAgentIdAndActiveTrue(@Param("agentId") Long agentId);

    @Query("SELECT a FROM Agency a WHERE a.agent.id = :agentId")
    List<Agency> findByAgentId(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(a) FROM Agency a WHERE a.agent.id = :agentId AND a.active = true")
    Long countActiveByAgentId(@Param("agentId") Long agentId);
}