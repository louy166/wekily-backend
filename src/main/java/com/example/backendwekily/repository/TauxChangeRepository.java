package com.example.backendwekily.repository;

import com.example.backendwekily.entity.TauxChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TauxChangeRepository extends JpaRepository<TauxChangeEntity, Long> {
    Optional<TauxChangeEntity> findByDevEnvAndDevRet(String devEnv, String devRet);
}