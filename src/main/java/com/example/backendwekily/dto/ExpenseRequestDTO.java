package com.example.backendwekily.dto;

import lombok.Data;

@Data
public class ExpenseRequestDTO {
    private String category;
    private Double amount;
    private String description;

    // ✅ Nouveaux champs pour la gestion du solde Caisse / Banque
    private String fundingType; // Reçoit "CASH" ou "BANK" depuis l'application
    private String bankName;    // Reçoit le nom de la banque (ex: "bankily", "masrvi", etc.)
}