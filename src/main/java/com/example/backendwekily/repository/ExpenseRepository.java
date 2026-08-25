package com.example.backendwekily.repository;

import com.example.backendwekily.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByAgentIdOrderByCreatedAtDesc(Long agentId);
}