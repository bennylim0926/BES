package com.example.BES.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties.Io;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.EmailRequestDto;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.dtos.PaymentColumnRequestDto;
import com.example.BES.dtos.ParticpantsDto;
import com.example.BES.services.GoogleSheetService;

import jakarta.mail.MessagingException;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/sheets")
public class GoogleSheetsController {

    @Autowired
    GoogleSheetService service;

    @GetMapping("/participants/breakdown/{sheetId}")
    public ResponseEntity<GoogleSheetFileDto> getSheetInformationById(@PathVariable String sheetId) throws IOException{
        return ResponseEntity.ok(service.getParticipantsBreakDown(sheetId));
    }

    // Refresh Payment status
    // Need to pass event name
    // and this might be a POST since internally it changes stuffs
    @PostMapping("/payment")
    public ResponseEntity<Void> getPaymentInformationById(@RequestBody EmailRequestDto dto) throws IOException, MessagingException{
        service.updatePaymentStatus(dto.sheetId, dto.eventName);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    // Whenever the event change or start app, insert payment column if not exist
    @PostMapping("/payment-status")
    public ResponseEntity<Void> insertPaymentColumn(@RequestBody PaymentColumnRequestDto dto) throws IOException{
        service.insertPaymentColumn(dto.sheetId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/paid-participants/{fileId}")
    public ResponseEntity<List<ParticpantsDto>> getPaidParticipants(@PathVariable String fileId) throws IOException{
        return ResponseEntity.ok(service.getAllNewPaidParticipants(fileId));
    }
}