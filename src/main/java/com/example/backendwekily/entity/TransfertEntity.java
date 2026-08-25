package com.example.backendwekily.entity; // Ou model selon votre package

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID; // <-- Importez UUID

@Entity
@Table(name = "transfert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransfertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reference;

    @Column(name = "id_agent_emetteur", nullable = false)
    private Long idAgentEmetteur;

    @Column(name = "id_agent_recepteur", nullable = false)
    private Long idAgentRecepteur;

    @Column(name = "montant_envoye", nullable = false)
    private Double montantEnvoye;

    @Column(name = "dev_env", nullable = false)
    private String devEnv;

    @Column(name = "montant_retire", nullable = false)
    private Double montantRetire;

    @Column(name = "dev_ret", nullable = false)
    private String devRet;

    @Column(name = "tau_applique")
    private String tauApplique;

    private String statut;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 👇 Cette méthode génère automatiquement la référence avant l'enregistrement si elle est vide
    @PrePersist
    public void prePersist() {
        if (this.reference == null || this.reference.isEmpty()) {
            this.reference = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}