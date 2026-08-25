package com.example.backendwekily.controller;

import com.example.backendwekily.dto.AuthRequestDTO;
import com.example.backendwekily.dto.AuthResponseDTO;
import com.example.backendwekily.dto.LoginPhoneRequestDTO;
import com.example.backendwekily.dto.RegisterRequestDTO;
import com.example.backendwekily.service.AuthService;
import com.example.backendwekily.service.DashboardService;
import com.example.backendwekily.service.LoginByPhoneService;
import com.example.backendwekily.service.RegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService         authService;
    private final RegisterService     registerService;
    private final LoginByPhoneService loginByPhoneService;
    private final DashboardService    dashboardService;

    // POST /api/auth/login — email + password
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // POST /api/auth/login-phone — téléphone + PIN ← écran Login Figma
    @PostMapping("/login-phone")
    public ResponseEntity<AuthResponseDTO> loginByPhone(
            @RequestBody LoginPhoneRequestDTO request) {
        log.info("📱 login-phone reçu : {}", request.getPhone());
        return ResponseEntity.ok(
                loginByPhoneService.loginByPhone(request.getPhone(), request.getPin())
        );
    }

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.ok(registerService.register(request));
    }

    // POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String token) {
        Long agentId = dashboardService.extractAgentId(token);
        authService.logout(agentId);
        return ResponseEntity.ok("تم تسجيل الخروج بنجاح");
    }
}