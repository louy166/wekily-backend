package com.example.backendwekily.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransfertResponse {
    private Long id;
    private String reference;
    private Long idAgentEmetteur;
    private Long idAgentRecepteur;
    private Double montantEnvoye;
    private String devEnv;
    private Double montantRetire;
    private String devRet;
    private String tauApplique;
    private String statut;
    private String date;
    private String message;
}