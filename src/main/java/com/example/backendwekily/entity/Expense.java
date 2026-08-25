package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false)
    private String category;    // إيجار | فواتير | مواصلات | مكتبية | صيانة | أخرى

    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // ✅ Nouveaux champs ajoutés pour la gestion du canal de financement (Caisse / Banque)
    @Column(name = "funding_type", length = 10)
    private String fundingType; // CASH, BANK

    @Column(name = "bank_name", length = 50)
    private String bankName; // بنكيلي, مصرفي...

    @Column(unique = true)
    private String reference;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.reference == null) {
            this.reference = "EXP-" + LocalDateTime.now().getYear() + "-" +
                    String.format("%06d", (long)(Math.random() * 999999));
        }
    }
}