package com.example.BES.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AuditionDisplayStateDto;
import com.example.BES.services.EventAuditionDisplayService;

import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/event/audition-display")
public class EventAuditionDisplayController {

    @Autowired
    private EventAuditionDisplayService displayService;

    @PostMapping
    public ResponseEntity<?> updateDisplayState(@RequestBody AuditionDisplayStateDto dto) {
        if (dto == null || dto.eventName == null || dto.eventName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventName is required"));
        }
        if (dto.categoryName == null || dto.categoryName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "categoryName is required"));
        }
        displayService.updateState(dto.eventName, dto);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @GetMapping
    public ResponseEntity<?> getDisplayState(
            @RequestParam String event,
            @RequestParam String category) {
        if (category == null || category.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "category is required"));
        }
        AuditionDisplayStateDto state = displayService.getState(event, category);
        if (state == null) {
            return ResponseEntity.ok(Map.of(
                "standby", true,
                "eventName", event,
                "categoryName", category
            ));
        }
        return ResponseEntity.ok(state);
    }
}
