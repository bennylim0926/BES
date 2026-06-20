package com.example.BES.controllers;
import jakarta.validation.Valid;

import java.io.IOException;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.PaymentColumnRequestDto;
import com.example.BES.services.GoogleSheetService;


@RestController
@CrossOrigin
@RequestMapping("/api/v1/sheets")
public class GoogleSheetsController {

    @Autowired(required = false)
    GoogleSheetService service;

    // Get a breakdown by genre/categories of selected sheet
    @GetMapping("/participants/breakdown/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'HELPER')")
    public ResponseEntity<Map<String, Integer>> getSheetInformationById(@PathVariable String fileId) throws IOException{
        return ResponseEntity.ok(service.getParticipantsBreakDown(fileId));
    }

    @GetMapping("/participants/size/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'HELPER')")
    public ResponseEntity<Integer> getSheetSize(@PathVariable String fileId) throws IOException{
        return ResponseEntity.ok(service.getSheetSizeService(fileId));
    }

    @GetMapping("/categories/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'HELPER')")
    public ResponseEntity<Map<String, List<String>>> getCategories(@PathVariable String fileId) {
        try {
            List<String> values = service.getAllCategoryValues(fileId);
            return ResponseEntity.ok(Map.of("values", values));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("values", List.of()));
        }
    }

    // When user create database, insert this as well
    @PostMapping("/payment-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<Void> insertPaymentColumn(@Valid @RequestBody PaymentColumnRequestDto dto) throws IOException{
        service.insertPaymentColumn(dto.fileId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
