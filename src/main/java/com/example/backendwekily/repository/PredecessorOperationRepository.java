package com.example.backendwekily.repository;

import com.example.backendwekily.entity.PredecessorOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PredecessorOperationRepository extends JpaRepository<PredecessorOperation, Long> {

    List<PredecessorOperation> findByPredecessorIdOrderByCreatedAtDesc(Long predecessorId);
}