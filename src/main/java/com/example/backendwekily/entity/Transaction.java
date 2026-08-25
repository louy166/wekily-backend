package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Entity
@Table(name = "transactions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false)
    @Builder.Default
    private String clientName = "—";

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionType type = TransactionType.DEPOSIT;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;

    private String agencyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    // ✅ Pas de @Column(unique=true) pour éviter les conflits au démarrage
    private String reference;

    private String notes;

    // ✅ Commission saisie manuellement par l'agent (Commission2Screen)
    @Column(name = "manual_commission", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal manualCommission = BigDecimal.ZERO;

    // ✅ Statut de retrait pour les transferts reçus
    // null = non retiré | DONE = retiré
    @Column(name = "retrait_status", length = 20)
    private String retraitStatus;

    // Méthode de retrait : CASH | BANKILY | MASRVI | SEDAD | MOOV | BIMBANK | AMANTY | CLICK | DEVISE
    @Column(name = "retrait_method", length = 20)
    private String retraitMethod;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.reference == null) {
            String prefix = switch (this.type) {
                case WITHDRAWAL -> "RET";
                case DEPOSIT    -> "DEP";
                case TRANSFER   -> "ADD";
            };
            long rand = ThreadLocalRandom.current().nextLong(100000, 999999);
            this.reference = prefix + "-" + LocalDateTime.now().getYear() + "-" + rand;
        }
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public enum TransactionType   { DEPOSIT, WITHDRAWAL, TRANSFER }
    public enum TransactionStatus { COMPLETED, PENDING, CANCELLED }
}