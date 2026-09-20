package com.example.backendwekily.repository;

import com.example.backendwekily.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findTop10ByAgentIdOrderByCreatedAtDesc(Long agentId);
    List<Transaction> findByAgentIdOrderByCreatedAtDesc(Long agentId);

    // ── Stats globales ────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
            "WHERE t.agent.id=:id AND t.type='DEPOSIT' AND t.status='COMPLETED' AND t.createdAt>=:start")
    BigDecimal sumDailyDeposits(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(SUM(ABS(t.amount)),0) FROM Transaction t " +
            "WHERE t.agent.id=:id AND t.type='WITHDRAWAL' AND t.status='COMPLETED' AND t.createdAt>=:start")
    BigDecimal sumDailyWithdrawals(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
            "WHERE t.agent.id=:id AND t.type='TRANSFER' AND t.status='COMPLETED' AND t.createdAt>=:start")
    BigDecimal sumDailyTransfers(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.agent.id=:id AND t.createdAt>=:start")
    Long countDailyTransactions(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.agent.id=:id AND t.type='DEPOSIT' AND t.createdAt>=:start")
    Long countDailyDeposits(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.agent.id=:id AND t.type='WITHDRAWAL' AND t.createdAt>=:start")
    Long countDailyWithdrawals(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.agent.id=:id AND t.type='TRANSFER' AND t.createdAt>=:start")
    Long countDailyTransfers(@Param("id") Long id, @Param("start") LocalDateTime start);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.agent.id=:id AND t.status='PENDING'")
    Long countPendingTransactions(@Param("id") Long agentId);

    // ── Stats par agence (native SQL + CURDATE) ──────────────
    @Query(value =
            "SELECT COALESCE(SUM(t.amount), 0) FROM transactions t " +
                    "WHERE t.agent_id = :agentId " +
                    "AND (t.agency_id = :agencyId OR t.agency_name = :agencyName) " +
                    "AND t.type = 'DEPOSIT' AND DATE(t.created_at) = CURDATE()",
            nativeQuery = true)
    BigDecimal sumDailyDepositsByAgency(@Param("agentId") Long agentId,
                                        @Param("agencyId") Long agencyId,
                                        @Param("agencyName") String agencyName);

    @Query(value =
            "SELECT COALESCE(SUM(ABS(t.amount), 0) FROM transactions t " +
                    "WHERE t.agent_id = :agentId " +
                    "AND (t.agency_id = :agencyId OR t.agency_name = :agencyName) " +
                    "AND t.type = 'WITHDRAWAL' AND DATE(t.created_at) = CURDATE()",
            nativeQuery = true)
    BigDecimal sumDailyWithdrawalsByAgency(@Param("agentId") Long agentId,
                                           @Param("agencyId") Long agencyId,
                                           @Param("agencyName") String agencyName);

    // ── Données pour graphique ────────────────────────────────
    @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
            "WHERE t.agent.id=:id AND t.type='DEPOSIT' AND t.status='COMPLETED' " +
            "AND t.createdAt>=:from AND t.createdAt<:to")
    BigDecimal sumDepositsInRange(@Param("id") Long id,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(ABS(t.amount)),0) FROM Transaction t " +
            "WHERE t.agent.id=:id AND t.type='WITHDRAWAL' AND t.status='COMPLETED' " +
            "AND t.createdAt>=:from AND t.createdAt<:to")
    BigDecimal sumWithdrawalsInRange(@Param("id") Long id,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}