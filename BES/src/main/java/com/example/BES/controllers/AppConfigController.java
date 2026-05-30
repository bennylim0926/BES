package com.example.BES.controllers;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.BES.services.AppConfigService;

@RestController
@RequestMapping("/api/v1/config")
public class AppConfigController {

    @Autowired
    private AppConfigService service;

    @Autowired
    private SimpMessagingTemplate messaging;

    @GetMapping("/app")
    public ResponseEntity<Map<String, String>> getAppConfig() {
        String color = service.getAccentColor();
        return ResponseEntity.ok(Map.of("accentColor", color));
    }

    @PostMapping("/app")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> postAppConfig(@RequestBody Map<String, String> body) {
        String color = body.get("accentColor");
        if (color == null || color.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accentColor is required"));
        }
        String saved = service.saveAccentColor(color);
        messaging.convertAndSend("/topic/app-config", Map.of("accentColor", saved));
        return ResponseEntity.ok(Map.of("accentColor", saved));
    }
}
