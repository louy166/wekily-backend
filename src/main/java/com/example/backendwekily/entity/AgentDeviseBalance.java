package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

// ✅ Solde par devise pour chaque agent
// Ex: agent 1 → MRU: 50000 | CFA: 175000000
@Entity
@Table(name = "agent_devise_balance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "devise"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentDeviseBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "devise", nullable = false, length = 10)
    private String devise; // ex: "MRU", "CFA", "EUR"

    @Column(name = "balance", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
}