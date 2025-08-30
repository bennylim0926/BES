/*
 * 
 * CURRENTLY NOT IN USE
 * 
 */


// package com.example.BES.controllers;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.CrossOrigin;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;

// import com.example.BES.dtos.EmailRequestDto;
// import com.example.BES.services.MailSenderService;

// import jakarta.mail.MessagingException;

// @RestController
// @CrossOrigin
// @RequestMapping("/api/v1/mail")
// public class MailSenderController {
    
//     @Autowired
//     private MailSenderService mailService; 

//     @PostMapping("/comfirmation-qr")
//     public ResponseEntity<Void> sendComfirmationEmail(@RequestBody EmailRequestDto dto) throws MessagingException{
//         // mailService.sendEmailWithAttachment(dto);
//         return new ResponseEntity<>(HttpStatus.OK);
//     }   
// }
