package com.example.BES.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.GoogleDriveFileDto;
import com.example.BES.services.GoogleDriveFileService;

@RestController
@RequestMapping("/api/v1/files")
public class GoogleDriveFileController {
    
    @Autowired
    private GoogleDriveFileService service;

    @GetMapping("/{folderId}")
    public ResponseEntity<List<GoogleDriveFileDto>> findAllInFolder(@PathVariable String folderId) {
        return ResponseEntity.ok(service.findAllInFolder(folderId));
    }

}
