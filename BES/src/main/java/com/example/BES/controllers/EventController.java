package com.example.BES.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.services.EventGenreService;
import com.example.BES.services.EventParticpantService;
import com.example.BES.services.EventService;
import com.example.BES.services.ParticipantService;

/*
 * Event Controller should handle
 * 1. Create new event
 * 2. Assign genres to event
 * 3. Create participant
 * 4. Sign particpant in an event
 * 5. Assign participant to genre of his choice in event
 * 
 */
@RestController
@CrossOrigin
@RequestMapping("/api/v1/event")
public class EventController {
    @Autowired
    EventService eventService;

    @Autowired
    ParticipantService participantService;

    @Autowired 
    EventParticpantService eventParticipantService;

    @Autowired
    EventGenreService eventGenreService;

    @PostMapping
    public ResponseEntity<Void> createNewEvent(@RequestBody AddEventDto dto){
        eventService.createEventService(dto);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/genre")
    public ResponseEntity<String> assignGenreToEvent(@RequestBody AddGenreToEventDto dto){
        try{
            eventGenreService.addGenreToEventService(dto);
            return new ResponseEntity<>(String.format("Add %s to %s", dto.genreName, dto.eventName), HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // if paid then register in database
    // send email to them as well (logic in another controller)
    @PostMapping("/participants")
    public ResponseEntity<String> addParticipantToEvent(@RequestBody AddParticipantDto dto){
        // Add particpant to database if not exist
        // Add participant to event if not exist
        try{
            eventParticipantService.AddPartipantToEventService(dto, dto.eventName);
            return new ResponseEntity<>("Register user to the event", HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
