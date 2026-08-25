package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agencies")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // ✅ name_ar supprimé — colonne inexistante en DB

    @Column(nullable = true)
    @Builder.Default
    private String color = "#4147D5";

    @Column(nullable = true)
    @Builder.Default
    private String icon = "default";

    @Column(name = "current_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public boolean isActive() { return active != null && active; }
}