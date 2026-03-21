package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.models.Event;
import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.respositories.EventRepo;

@Service
public class EventService {
    @Autowired
    EventRepo repo;

    @Autowired
    EmailTemplateService emailTemplateService;

    public void createEventService(AddEventDto dto){
        if (repo.findByEventName(dto.eventName).isPresent()) return;
        Event newEvent = new Event();
        newEvent.setEventName(dto.eventName);
        newEvent.setPaymentRequired(dto.paymentRequired);
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

    public List<GetEventDto> getAllEvents(){
        List<Event> events = repo.findAll();
        List<GetEventDto> dtos = new ArrayList<>();
        for(Event event: events){
            GetEventDto dto = new GetEventDto();
            dto.setId(event.getEventId());
            dto.setName(event.getEventName());
            dto.setPaymentRequired(event.isPaymentRequired());
            dtos.add(dto);
        }
        return dtos;
    }
}
