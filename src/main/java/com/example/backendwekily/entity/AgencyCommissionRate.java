package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agency_commission_rate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "agency_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AgencyCommissionRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(name = "withdrawal_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal withdrawalRate = new BigDecimal("50.00");

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}