package com.example.backendwekily.repository;

import com.example.backendwekily.entity.TransfertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransfertRepository extends JpaRepository<TransfertEntity, Long> {

    // Correction ici : IdAgentEmetteur au lieu de AgentEmetteurId
    List<TransfertEntity> findByIdAgentEmetteurOrderByCreatedAtDesc(Long idAgentEmetteur);
}