package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agents")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Champs de base ────────────────────────────────────────
    @Column(nullable = false)
    private String fullName;

    @Column(nullable = true, unique = true)
    private String email; // ✅ nullable → null autorisé (évite Duplicate empty string)

    @Column(nullable = false)
    private String passwordHash;

    private String role;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalBalance = BigDecimal.ZERO;

    // ✅ Solde cash initial (affiché dans la carte Dashboard)
    @Column(name = "cash_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Builder.Default
    private Boolean active = true;

    // ✅ Champ blocked — ajouté pour le panel admin
    @Column(name = "blocked", columnDefinition = "TINYINT(1) DEFAULT 0")
    @Builder.Default
    private Boolean blocked = false;

    // ── Champs register ───────────────────────────────────────
    private String phone;

    @Column(name = "national_id")
    private String nationalId;

    private String city;
    private String region;
    private String pin;

    // ── Timestamps ────────────────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}