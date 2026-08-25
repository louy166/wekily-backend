package com.example.backendwekily.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class TransactionResponseDTO {
    private Long       id;
    private String     reference;
    private BigDecimal amount;
    private String     type;
    private String     status;

    // ✅ "name" pour le frontend (clientName en backend)
    @JsonProperty("name")
    private String     clientName;

    // 📲 AJOUT : Numéro de téléphone pour le frontend
    private String     clientPhone;

    // ✅ "agency" pour le frontend (agencyName en backend)
    @JsonProperty("agency")
    private String     agencyName;

    private String     date;
    private Double     manualCommission; // ✅ commission manuelle
}