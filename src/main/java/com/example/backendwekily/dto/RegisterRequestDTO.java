package com.example.backendwekily.dto;

import lombok.Data;
import java.util.List;

@Data
public class RegisterRequestDTO {
    private String      fullName;
    private String      phone;
    private String      email;
    private String      nationalId;
    private String      city;
    private String      region;
    private String      pin;
    private List<Integer> agencyIds; // IDs des agences sélectionnées (1-7)
    // password optionnel (on utilise PIN)
    private String      password;
}