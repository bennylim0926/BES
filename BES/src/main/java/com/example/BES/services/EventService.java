package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.models.Event;
import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetJudgingModeDto;
import com.example.BES.respositories.EventRepo;

@Service
public class EventService {
    @Autowired
    EventRepo repo;

    @Autowired
    EmailTemplateService emailTemplateService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    public void createEventService(AddEventDto dto){
        if (repo.findByEventName(dto.eventName).isPresent()) return;
        Event newEvent = new Event();
        newEvent.setEventName(dto.eventName);
        newEvent.setPaymentRequired(dto.paymentRequired);
        if (dto.accessCode != null && dto.accessCode.matches("\\d{4}")) {
            newEvent.setAccessCode(dto.accessCode);
        } else {
            newEvent.setAccessCode(String.format("%04d", new Random().nextInt(10000)));
        }
        Event saved = repo.save(newEvent);
        emailTemplateService.createDefaultTemplate(saved);
    }

    public AddEventDto findEventbyNameSerivce(String eventName){
        Event event = repo.findByEventName(eventName).orElse(null);
        if(event != null){
            AddEventDto dto = new AddEventDto();
            dto.eventName = eventName;
            return dto;
        }
        return null;
    }

    public List<GetEventDto> getAllEvents(boolean includeAccessCode){
        List<Event> events = repo.findAll();
        List<GetEventDto> dtos = new ArrayList<>();
        for(Event event: events){
            GetEventDto dto = new GetEventDto();
            dto.setId(event.getEventId());
            dto.setName(event.getEventName());
            dto.setPaymentRequired(event.isPaymentRequired());
            if (includeAccessCode) {
                dto.setAccessCode(event.getAccessCode());
            }
            dtos.add(dto);
        }
        return dtos;
    }

    public boolean verifyAccessCode(Long eventId, String code){
        Event event = repo.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return event.getAccessCode().equals(code);
    }

    public GetJudgingModeDto getJudgingMode(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return new GetJudgingModeDto(event.getEventName(), event.getJudgingMode());
    }

    public void setJudgingMode(String eventName, String mode) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setJudgingMode(mode);
        repo.save(event);
        messagingTemplate.convertAndSend("/topic/judging-mode/",
            java.util.Map.of("eventName", eventName, "judgingMode", mode));
    }

    public void updateAccessCode(Long eventId, String newCode){
        if (newCode == null || !newCode.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access code must be exactly 4 digits");
        }
        Event event = repo.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setAccessCode(newCode);
        repo.save(event);
    }
}
