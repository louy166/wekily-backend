package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_deposits")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClientDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "client_phone", length = 20)
    private String clientPhone;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DepositType type; // CASH, BANK

    @Column(name = "bank_name", length = 50)
    private String bankName; // بنكيلي, مصرفي...

    @Column(name = "bank_account", length = 50)
    private String bankAccount; // رقم الحساب

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private DepositStatus status = DepositStatus.ACTIVE;

    @Column(length = 255)
    private String notes;

    @Column(name = "reference", unique = true, length = 50)
    private String reference;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (reference == null) {
            reference = "DEP-" + System.currentTimeMillis() % 1000000;
        }
    }

    public enum DepositType   { CASH, BANK }
    public enum DepositStatus { ACTIVE, WITHDRAWN }
}