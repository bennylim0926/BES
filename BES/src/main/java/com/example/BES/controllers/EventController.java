package com.example.BES.controllers;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.AddJudgesDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.dtos.GetEventGenreParticipantDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.UpdateParticipantJudgeDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.services.EventGenreParticpantService;
import com.example.BES.services.EventGenreService;
import com.example.BES.services.EventParticpantService;
import com.example.BES.services.EventService;
import com.example.BES.services.GenreService;
import com.example.BES.services.JudgeService;
import com.example.BES.services.ParticipantService;
import com.example.BES.services.RegistrationService;
import com.example.BES.services.ScoreService;
import com.google.gson.Gson;
import com.google.zxing.WriterException;

import jakarta.mail.MessagingException;

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

    @Autowired
    RegistrationService registerService;

    @Autowired
    JudgeService judgeService;

    @Autowired
    ScoreService scoreService;

    private static final Gson gson = new Gson();

    // Check is this event exist in table
    @GetMapping("/{eventName}")
    public ResponseEntity<Boolean> eventExistByName(@PathVariable String eventName){
        AddEventDto event = eventService.findEventbyNameSerivce(eventName);
        return new ResponseEntity<>(event != null, HttpStatus.OK);
    }

    // Get all possible genres
    @GetMapping("/genre")
    public ResponseEntity<List<GetGenreDto>> getAllGenres(){
        return new ResponseEntity<>(genreService.getAllGenres(), HttpStatus.OK);
    }

    // Create a new entry in the event table
    @PostMapping
    public ResponseEntity<String> createNewEvent(@RequestBody AddEventDto dto){
        eventService.createEventService(dto);
        return new ResponseEntity<>(gson.toJson("Table created"), HttpStatus.CREATED);
    }

    // Assign a genre to a existing event
    @PostMapping("/genre")
    public ResponseEntity<String> assignGenreToEvent(@RequestBody AddGenreToEventDto dto){
        try{
            eventGenreService.addGenreToEventService(dto);
            return new ResponseEntity<>(gson.toJson(String.format("Created event with genres")), HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/judges")
    public ResponseEntity<String> addJudge(@RequestBody AddJudgesDto dto){
        try{
            judgeService.addJudgesService(dto);
            return new ResponseEntity<>(gson.toJson("Judges are added"), HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/judges")
    public ResponseEntity<List<GetJudgeDto>> getAllJudges(){
        try{
            return new ResponseEntity<>(judgeService.getAllJudges(), HttpStatus.OK);
        }catch(Exception e){
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    // add participants from form (walk ins)
    @PostMapping("/walkins/")
    public ResponseEntity<String> addWalkInToSystem(@RequestBody AddWalkInDto dto){
        try{
            Participant p = participantService.addWalkInService(dto);
            EventParticipant ep =  eventParticipantService.addNewWalkInInEventService(p, dto.eventName, dto.genre);
            EventGenreParticipant egp = eventGenreParticipantService.addWalkInToEventGenreParticipant(p, dto.genre, ep, dto.judgeName);
            AddParticipantToEventGenreDto auditionDto = new AddParticipantToEventGenreDto();
            auditionDto.eventId = egp.getEvent().getEventId();
            auditionDto.genreId = egp.getGenre().getGenreId();
            auditionDto.participantId = egp.getParticipant().getParticipantId();
            eventGenreParticipantService.getAuditionNumViaQR(auditionDto);
            return new ResponseEntity<>(gson.toJson("Added walkin"), HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(gson.toJson("error"), HttpStatus.BAD_REQUEST);
        }
    }

    // Add participants by reading the sheet
    @PostMapping("/participants/")
    public ResponseEntity<String> addParticipantsToSystem(@RequestBody AddParticipantToEventDto dto)throws IOException, MessagingException, WriterException{
        try{
            registerService.addParticipantToEvent(dto);
            return new ResponseEntity<>(gson.toJson( "Participants list updated!"), HttpStatus.CREATED);
        }catch(NullPointerException e){
            return new ResponseEntity<>(gson.toJson( "The record is empty, please verify the payment in the google sheet"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/participants/{eventName}")
    public ResponseEntity<List<GetEventGenreParticipantDto>> getParticipantsFromEventGenre(@PathVariable String eventName)throws IOException, MessagingException, WriterException{
        try{
            List<GetEventGenreParticipantDto> results = eventGenreParticipantService.getAllEventGenreParticipantByEventService(eventName);
            if(results.size() > 0){
                return new ResponseEntity<>(results, HttpStatus.OK);
            }
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
            
        }catch(NullPointerException e){
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }

    // Get all the paid participants in an event regardless of their genres
    @GetMapping("/verified-participant/{eventName}")
    public ResponseEntity<List<GetParticipantByEventDto>> getAllVerifiedParticipant(@PathVariable String eventName){
        List<GetParticipantByEventDto> res = eventParticipantService.getAllParticipantsByEvent(eventName);
        if(res == null){
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    /*
     * This is actually a POST method, and this link is send to the participants
     * When organiser scan the QR, it will give the participant audition number based on genre
     */
    @GetMapping("/register-participant/{participantId}/{eventId}/{genreId}")
    public ResponseEntity<String> registerParticipantWithGenre(@PathVariable Long participantId, @PathVariable Long eventId, @PathVariable Long genreId) throws IOException{ 
        try{
            AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.genreId = genreId;
            eventGenreParticipantService.getAuditionNumViaQR(dto);
            return new ResponseEntity<>("registered", HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/participants-judge/")
    public ResponseEntity<String> updateParticipantJudge(@RequestBody UpdateParticipantJudgeDto dto){
        try{
            // eventGenreParticipantService.addParticipantToEventGenreService(dto);
            eventGenreParticipantService.updateParticipantsJudgeService(dto);
            return new ResponseEntity<>(gson.toJson("All updated"), HttpStatus.CREATED);
        }catch(Exception e){
            return new ResponseEntity<>(gson.toJson("Failed to update"), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/scores")
    public ResponseEntity<String> updateParticipantScore(@RequestBody UpdateParticipantsScoreDto dto){
        try{
            // score service here
            scoreService.updateParticipantScoreService(dto);
            return new ResponseEntity<>(gson.toJson("Score updated!"), HttpStatus.ACCEPTED);
        }catch(Exception e){
            return new ResponseEntity<>(gson.toJson("Failed to update score"), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/scores/{eventName}")
    public ResponseEntity<List<GetParticipatnScoreDto>> getParticipantScore(@PathVariable String eventName){
        try{
            return new ResponseEntity<>(scoreService.getAllScore(eventName), HttpStatus.OK);
        }catch(Exception e){
            System.out.println(e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }
}