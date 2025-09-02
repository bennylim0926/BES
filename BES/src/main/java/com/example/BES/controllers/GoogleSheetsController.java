package com.example.BES.controllers;

import java.io.IOException;
import com.google.gson.Gson;
import com.google.zxing.WriterException;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.dtos.PaymentColumnRequestDto;
import com.example.BES.services.GoogleSheetService;

import jakarta.mail.MessagingException;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/sheets")
public class GoogleSheetsController {

    @Autowired
    GoogleSheetService service;

    private static final Gson gson = new Gson();

    // Get a breakdown by genre/categories of selected sheet
    @GetMapping("/participants/breakdown/{fileId}")
    public ResponseEntity<GoogleSheetFileDto> getSheetInformationById(@PathVariable String fileId) throws IOException{
        return ResponseEntity.ok(service.getParticipantsBreakDown(fileId));
    }

    // When user create database, insert this as well
    @PostMapping("/payment-status")
    public ResponseEntity<Void> insertPaymentColumn(@RequestBody PaymentColumnRequestDto dto) throws IOException{
        service.insertPaymentColumn(dto.fileId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /*
     * Big function as this does quite a few things:
     * Go through the sheet to take all verified participants but not added into database
     * Add participant to event database
     * Add participant to event's genre database
     * Send email with qr to those verified participants 
     */
    @PostMapping("/participants/")
    public ResponseEntity<String> confirmParticipantsInEvent(@RequestBody AddParticipantToEventDto dto) throws IOException, MessagingException, WriterException{
        service.addParticpantToEvent(dto);
        return new ResponseEntity<>(gson.toJson( "Paid participants should be in the system and received confirmation email"), HttpStatus.CREATED);
    }
}