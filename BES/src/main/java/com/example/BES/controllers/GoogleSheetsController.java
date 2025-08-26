package com.example.BES.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.services.GoogleSheetService;
import com.google.api.services.sheets.v4.model.ValueRange;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/sheets")
public class GoogleSheetsController {

    @Autowired
    GoogleSheetService service;

    @GetMapping("/{sheetId}")
    public ResponseEntity<GoogleSheetFileDto> getSheetInformationById(@PathVariable String sheetId) throws IOException{
        return ResponseEntity.ok(service.getSheetInformationById(sheetId));
    }

    // Refresh Payment status
    @GetMapping("/payment/{sheetId}")
    public ResponseEntity<ValueRange> getPaymentInformationById(@PathVariable String sheetId) throws IOException{
        return ResponseEntity.ok(service.updatePaymentStatus(sheetId));
    }

}