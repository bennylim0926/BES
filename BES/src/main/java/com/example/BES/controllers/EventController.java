package com.example.BES.controllers;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.services.EventGenreParticpantService;
import com.example.BES.services.EventGenreService;
import com.example.BES.services.EventParticpantService;
import com.example.BES.services.EventService;
import com.example.BES.services.GenreService;
import com.example.BES.services.ParticipantService;

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

    @Autowired
    EventGenreParticpantService eventGenreParticipantService;

    @Autowired
    GenreService genreService;

    @GetMapping("/{eventName}")
    public ResponseEntity<Boolean> eventExistByName(@PathVariable String eventName){
        AddEventDto event = eventService.findEventbyNameSerivce(eventName);
        return new ResponseEntity<>(event != null, HttpStatus.OK);
    }

    @GetMapping("/genre")
    public ResponseEntity<List<GetGenreDto>> getAllGenres(){
        return new ResponseEntity<>(genreService.getAllGenres(), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Void> createNewEvent(@RequestBody AddEventDto dto){
        eventService.createEventService(dto);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/genre")
    public ResponseEntity<String> assignGenreToEvent(@RequestBody AddGenreToEventDto dto){
        try{
            eventGenreService.addGenreToEventService(dto);
            return new ResponseEntity<>(String.format("create database"), HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/verified-participant/{eventName}")
    public ResponseEntity<List<GetParticipantByEventDto>> getAllVerifiedParticipant(@PathVariable String eventName){
        // System.out.println(eventName);
        List<GetParticipantByEventDto> res = eventParticipantService.getAllParticipantsByEvent(eventName);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    // @PostMapping("/register-participant")
    // public ResponseEntity<String> registerParticipantWithGenre(@RequestBody AddParticipantToEventGenreDto dto) throws IOException{ 
    //     try{
    //         eventGenreParticipantService.addParticipantToEventGenreService(dto);
    //         return new ResponseEntity<>("", HttpStatus.CREATED);
    //     }catch(Exception e){
    //         return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
    //     }
    // }
    @GetMapping("/register-participant/{participantId}/{eventId}/{genreId}")
    public ResponseEntity<String> registerParticipantWithGenre(@PathVariable Long participantId, @PathVariable Long eventId, @PathVariable Long genreId) throws IOException{ 
        try{
            AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.genreId = genreId;
            eventGenreParticipantService.addParticipantToEventGenreService(dto);
            return new ResponseEntity<>("registered", HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
        }
    }
    
}