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

import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.AddParticipantToEventGenreDto;
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

    @PostMapping("/participants/")
    public ResponseEntity<String> confirmParticipantsInEvent(@RequestBody AddParticipantToEventDto dto) throws IOException, MessagingException{
        System.out.println("hi");
        service.addParticpantToEvent(dto);
        return new ResponseEntity<>("Paid participants should be in the system and received confirmation email", HttpStatus.CREATED);
    }
}