package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "commissions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "agency_id")
    private Long agencyId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(nullable = false, length = 20)
    private String type; // WITHDRAWAL ou DEPOSIT

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(name = "agent_commission", nullable = false, precision = 10, scale = 2)
    private BigDecimal agentCommission;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal rate = new BigDecimal("50.00");

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) { createdAt = LocalDateTime.now(); }
    }
}