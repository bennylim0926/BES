package com.example.BES.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.GetResultsDto;
import com.example.BES.services.QrCodeService;
import com.example.BES.services.ResultsService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/results")
@Tag(name = "Results Portal", description = "Public participant results lookup and QR generation")
public class ResultsController {

    @Autowired
    ResultsService resultsService;

    @Autowired
    QrCodeService qrCodeService;

    @Autowired
    Environment env;

    @GetMapping
    public ResponseEntity<?> getResults(@RequestParam String ref) {
        GetResultsDto results = resultsService.getResultsByRefCode(ref);
        if (results == null) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Results not found or not yet released"));
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/qr")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<byte[]> getResultsQr(@RequestParam String ref) {
        try {
            String domain = env.getProperty("DOMAIN", "");
            String url = domain + "/results?ref=" + ref;
            byte[] qrBytes = qrCodeService.generateQrCode(url, 300, 300);
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrBytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
