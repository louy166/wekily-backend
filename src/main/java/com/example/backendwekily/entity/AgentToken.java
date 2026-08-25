package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_tokens")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AgentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    // ✅ EAGER — chargé directement avec le token, pas de session nécessaire
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (this.token == null) {
            this.token = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusDays(30);
    }
}