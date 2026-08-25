package com.example.backendwekily.repository;

import com.example.backendwekily.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<Bank, Integer> {
    Optional<Bank> findByName(String name);
    Optional<Bank> findByIcon(String icon);
}