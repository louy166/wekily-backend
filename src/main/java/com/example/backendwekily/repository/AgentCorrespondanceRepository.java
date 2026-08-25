package com.example.backendwekily.repository;

import com.example.backendwekily.entity.AgentCorrespondanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentCorrespondanceRepository extends JpaRepository<AgentCorrespondanceEntity, Long> {

    // ✅ Vérifier si l'agent existe dans la table (comme émetteur OU receveur)
    @Query("SELECT COUNT(a) > 0 FROM AgentCorrespondanceEntity a " +
            "WHERE a.idAgentEmetteur = :agentId OR a.idAgentRecepteur = :agentId")
    boolean existsByAgentId(Long agentId);
}