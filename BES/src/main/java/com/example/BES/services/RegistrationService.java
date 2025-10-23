package com.example.BES.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.enums.EmailTemplates;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.google.zxing.WriterException;

import jakarta.mail.MessagingException;

@Service
public class RegistrationService {

    @Autowired
    GoogleSheetService sheetService;

    @Autowired 
    EventParticpantService eventParticipantService;

    @Autowired
    EmailTemplates emailTemplates;

    @Autowired
    MailSenderService mailService;

    @Autowired 
    EventRepo eventRepo;

    @Autowired
    ParticipantService participantService;

    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventGenreParticpantRepo eventGenreParticipantRepo;

    @Autowired
    GenreRepo genreRepo;

    public void addParticipantToEvent(AddParticipantToEventDto dto)
    throws IOException, MessagingException, WriterException{
        List<AddParticipantDto> paidParticipants = sheetService.getAllPaidParticipants(dto);
        for(AddParticipantDto participant: paidParticipants){
            Event event = eventRepo.findByEventName(dto.eventName).orElse(null);
            if(event == null){
                throw new NullPointerException("event is null");
            }
            Participant toAddpariticipant = participantService.addParticpantService(participant);
            Map<EventParticipant,List<EventGenreParticipantId>> mapping = eventParticipantService.getAlleventGenreParticipantIds(participant, event, toAddpariticipant);
            if(mapping == null){
                continue;
            }
            EventParticipant eventParticipant = mapping.keySet().iterator().next();
            List<EventGenreParticipantId> ids =  mapping.get(eventParticipant);
            mailService.sendEmailWithAttachment(dto.eventName, toAddpariticipant, mapping.get(eventParticipant));
            eventParticipantRepo.save(eventParticipant);
            for(EventGenreParticipantId id: ids){
                EventGenreParticipant p = new EventGenreParticipant();
                p.setId(id);
                p.setEvent(event);
                p.setGenre(genreRepo.findById(id.getGenreId()).orElse(null));
                p.setParticipant(toAddpariticipant);
                eventGenreParticipantRepo.save(p);
            }
        }
    }
}