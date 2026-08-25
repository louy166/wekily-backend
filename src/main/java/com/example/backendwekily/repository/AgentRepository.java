package com.example.backendwekily.repository;

import com.example.backendwekily.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByEmail(String email);
    Optional<Agent> findByPhone(String phone);
    Optional<Agent> findByPhoneAndPin(String phone, String pin);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}