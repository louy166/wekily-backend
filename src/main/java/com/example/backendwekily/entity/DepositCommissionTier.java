package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "deposit_commission_tiers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bank_id","amount_from","amount_to"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DepositCommissionTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "amount_from", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountFrom;

    @Column(name = "amount_to", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountTo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal commission;
}