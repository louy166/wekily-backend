package com.example.backendwekily.controller;

import com.example.backendwekily.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileService profileService;

    // GET /api/profile
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader("Authorization") String token) {
        Long agentId = profileService.extractAgentId(token);
        return ResponseEntity.ok(profileService.getProfile(agentId));
    }

    // PUT /api/profile — mettre à jour les infos
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> req) {
        Long agentId = profileService.extractAgentId(token);
        return ResponseEntity.ok(profileService.updateProfile(agentId, req));
    }
}