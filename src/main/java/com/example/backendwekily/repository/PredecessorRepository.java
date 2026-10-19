package com.example.backendwekily.repository;

import com.example.backendwekily.entity.Predecessor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PredecessorRepository extends JpaRepository<Predecessor, Long> {

    List<Predecessor> findByAgentIdOrderByUpdatedAtDesc(Long agentId);

    Optional<Predecessor> findByPhoneAndAgentId(String phone, Long agentId);

    Optional<Predecessor> findByIdAndAgentId(Long id, Long agentId);
}