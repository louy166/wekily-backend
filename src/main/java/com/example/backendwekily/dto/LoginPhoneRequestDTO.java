package com.example.backendwekily.dto;

import lombok.Data;

@Data
public class LoginPhoneRequestDTO {
    private String phone;
    private String pin;
}