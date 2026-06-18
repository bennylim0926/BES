package com.example.BES.controllers;

import java.util.HashMap;
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
    public ResponseEntity<Map<String, Object>> getAppConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("accentColor", service.getAccentColor());
        config.put("demoEnabled", service.isDemoEnabled());
        config.put("sheetConfig", getSheetConfig());
        return ResponseEntity.ok(config);
    }

    private Map<String, String> getSheetConfig() {
        Map<String, String> sc = new HashMap<>();
        sc.put("nameKeyword",          service.get("sheet.nameKeyword", "name"));
        sc.put("stageNameKeyword",     service.get("sheet.stageNameKeyword", "stage name"));
        sc.put("teamNameKeywords",     service.get("sheet.teamNameKeywords", "team,duo,battler,crew,group"));
        sc.put("memberNameKeywords",   service.get("sheet.memberNameKeywords", "member,dancer"));
        sc.put("categoryKeywords",     service.get("sheet.categoryKeywords", "categor"));
        sc.put("entryTypeKeyword",     service.get("sheet.entryTypeKeyword", "entry type"));
        sc.put("emailKeyword",         service.get("sheet.emailKeyword", "email"));
        sc.put("paymentKeyword",       service.get("sheet.paymentKeyword", "payment status"));
        sc.put("screenshotKeywords",   service.get("sheet.screenshotKeywords", "screenshot,receipt,proof,prove,payment"));
        return sc;
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

    @PostMapping("/sheet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> postSheetConfig(@RequestBody Map<String, String> body) {
        service.saveSheetConfig(body);

        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("sheetConfig", getSheetConfig());
        messaging.convertAndSend("/topic/app-config", broadcast);

        return ResponseEntity.ok(broadcast);
    }
}
