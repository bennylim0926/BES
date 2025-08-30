package com.example.BES.services;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.enums.EmailTemplates;
import com.example.BES.models.Event;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;

import jakarta.mail.MessagingException;

@Service
public class EventParticpantService {
    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    ParticipantService participantService;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    MailSenderService mailService;

    @Autowired
    EmailTemplates emailTemplates;

    public EventParticipant AddPartipantToEventService(AddParticipantDto participant, String eventName) throws MessagingException{
        Event event = eventRepo.findByEventName(eventName).orElse(null);
        if(event == null){
            throw new NullPointerException("The event does not exist");
        }
        Participant joiningParticipant =  participantService.addParticpantService(participant);
        EventParticipant newParticipant = eventParticipantRepo.findByEventAndParticipant(event, joiningParticipant).orElse(new EventParticipant());
        if(newParticipant.getEvent() != null){
            return null;   
        }
        newParticipant.setParticipant(joiningParticipant);
        newParticipant.setEvent(event);
        
        mailService.sendEmailWithAttachment(eventName, joiningParticipant);

        return eventParticipantRepo.save(newParticipant);
    }
}
