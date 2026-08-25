package com.example.backendwekily.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransfertRequest {

    @NotNull(message = "L'ID de l'agent émetteur est requis")
    private Long idAgentEmetteur;

    private Long idAgentRecepteur; // Peut être résolu par le backend via le téléphone

    @NotBlank(message = "Le numéro de téléphone du récepteur est requis")
    private String telephoneRecepteur; // Ajouté ici

    @NotNull(message = "Le montant envoyé est requis")
    @Min(value = 1, message = "Le montant doit être supérieur à 0")
    private Double montantEnvoye;

    @NotBlank(message = "La devise d'envoi est requise")
    private String devEnv;

    @NotBlank(message = "La devise de réception est requise")
    private String devRet;
}