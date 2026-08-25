package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_correspondance")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentCorrespondanceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agent_emetteur", nullable = false)
    private Long idAgentEmetteur;

    @Column(name = "id_agent_recepteur", nullable = false)
    private Long idAgentRecepteur;
}