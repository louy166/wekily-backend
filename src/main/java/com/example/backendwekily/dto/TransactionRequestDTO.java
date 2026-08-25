package com.example.backendwekily.dto;

import lombok.Data;

@Data
public class TransactionRequestDTO {
    private String clientName;
    private String clientPhone;
    private Double amount;
    private String type;       // DEPOSIT | WITHDRAWAL | TRANSFER
    private Long   agencyId;
    private String notes;
    private Double manualCommission; // ✅ commission saisie manuellement
    private String reference;
}