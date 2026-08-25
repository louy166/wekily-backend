package com.example.backendwekily.controller;

import com.example.backendwekily.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    // GET /api/notifications
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(
            @RequestHeader("Authorization") String token) {
        Long agentId = notificationService.extractAgentId(token);
        return ResponseEntity.ok(notificationService.getNotifications(agentId));
    }

    // PUT /api/notifications/{id}/read
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long agentId = notificationService.extractAgentId(token);
        notificationService.markRead(agentId, id);
        return ResponseEntity.ok().build();
    }

    // PUT /api/notifications/read-all
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader("Authorization") String token) {
        Long agentId = notificationService.extractAgentId(token);
        notificationService.markAllRead(agentId);
        return ResponseEntity.ok().build();
    }
}