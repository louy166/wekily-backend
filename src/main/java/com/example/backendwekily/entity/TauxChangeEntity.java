package com.example.backendwekily.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "taux_change")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TauxChangeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dev_env", nullable = false, length = 10)
    private String devEnv; // Ex: MRU

    @Column(name = "dev_ret", nullable = false, length = 10)
    private String devRet; // Ex: CFA

    @Column(name = "tau", nullable = false, length = 50)
    private String tau; // Ex: 3500/5000 ou 1.5
}