package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.enums.EmailTemplates;
import com.example.BES.mapper.EventParticipantDtoMapper;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.google.zxing.WriterException;

import jakarta.mail.MessagingException;

@Service
public class EventParticpantService {
    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventGenreParticpantRepo eventGenreParticipantRepo;

    @Autowired
    ParticipantService participantService;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    MailSenderService mailService;

    @Autowired
    EmailTemplates emailTemplates;

    @Autowired
    EventParticipantDtoMapper eventParticipantDtoMapper;

    public void AddPartipantToEventService(AddParticipantDto participant, String eventName) throws MessagingException, IOException, WriterException{
        Event event = eventRepo.findByEventName(eventName).orElse(null);
        if(event == null){
            throw new NullPointerException("The event does not exist");
        }
        Participant joiningParticipant =  participantService.addParticpantService(participant);
        EventParticipant newParticipant = eventParticipantRepo.findByEventAndParticipant(event, joiningParticipant).orElse(new EventParticipant());
        if(newParticipant.getEvent() != null){
            return ;   
        }
        newParticipant.setParticipant(joiningParticipant);
        newParticipant.setEvent(event);
        newParticipant.setResidency(participant.getResidency());
        newParticipant.setGenre(String.join(", ", participant.getGenres()));
        List<EventGenreParticipantId> ids = new ArrayList<>();
        for(String g: participant.getGenres()){
            Genre genre = genreRepo.findByGenreName(g.toLowerCase()).orElse(null);
            EventGenreParticipantId id = new EventGenreParticipantId(event.getEventId(), genre.getGenreId(), joiningParticipant.getParticipantId());
            ids.add(id);
        }
        
        mailService.sendEmailWithAttachment(eventName, joiningParticipant, ids);
        
        eventParticipantRepo.save(newParticipant);
        for(EventGenreParticipantId id : ids){
            EventGenreParticipant p = new EventGenreParticipant();
            p.setId(id);
            p.setEvent(event);
            p.setGenre(genreRepo.findById(id.getGenreId()).orElse(null));
            p.setParticipant(joiningParticipant);
            eventGenreParticipantRepo.save(p);
        }
    }

    // get by event
    public List<GetParticipantByEventDto> getAllParticipantsByEvent(String eventName){
        Event event = eventRepo.findByEventName(eventName).orElse(null);
        if(event == null){
            throw new NullPointerException("The event does not exist");
        }
        List<EventParticipant> results = eventParticipantRepo.findByEvent(event);
        List<GetParticipantByEventDto> res =  eventParticipantDtoMapper.mapRow(results);
        return res;
    }
}