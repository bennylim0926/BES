package com.example.BES.controllers;

import com.example.BES.dtos.ClaimEmceeCategoryDto;
import com.example.BES.services.EmceeCategoryStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/emcee")
public class EmceeCategoryController {

    @Autowired
    private EmceeCategoryStore emceeCategoryStore;

    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    @PostMapping("/active-category")
    public ResponseEntity<?> claimCategory(
            @Valid @RequestBody ClaimEmceeCategoryDto dto,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return ResponseEntity.status(401).body("No session");
        emceeCategoryStore.claim(session.getId(), dto.getEventName(), dto.getCategoryName());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    @GetMapping("/active-categories")
    public ResponseEntity<Map<String, Set<String>>> getActiveCategories(@RequestParam String eventName) {
        return ResponseEntity.ok(Map.of("categories", emceeCategoryStore.getActiveCategories(eventName)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    @DeleteMapping("/active-category")
    public ResponseEntity<?> releaseCategory(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            emceeCategoryStore.release(session.getId());
        }
        return ResponseEntity.ok().build();
    }
}
