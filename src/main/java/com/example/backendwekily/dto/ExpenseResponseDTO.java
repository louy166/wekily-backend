package com.example.backendwekily.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ExpenseResponseDTO {
    private Long   id;
    private String reference;
    private String category;
    private String description;
    private Double amount;
    private String date;

    // ✅ Nouveaux champs pour renvoyer le canal et le nom de la banque à l'application mobile
    private String fundingType; // Renvoie "CASH" ou "BANK"
    private String bankName;    // Renvoie "بنكيلي", "مصرفي", etc.
}