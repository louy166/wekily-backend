package com.example.backendwekily.repository;

import com.example.backendwekily.entity.AgentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AgentTokenRepository extends JpaRepository<AgentToken, Long> {
    Optional<AgentToken> findByToken(String token);
    void deleteByAgentId(Long agentId);
}