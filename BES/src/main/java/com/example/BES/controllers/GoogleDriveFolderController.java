package com.example.BES.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.BES.dtos.GoogleDriveFolderDto;
import com.example.BES.services.GoogleDriveFolderService;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/folders")
public class GoogleDriveFolderController {

    @Autowired(required = false)
    GoogleDriveFolderService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'HELPER')")
    public ResponseEntity<List<GoogleDriveFolderDto>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }
}