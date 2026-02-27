package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
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

    public void createEventService(AddEventDto dto){
        Event newEvent = new Event();
        newEvent.setEventName(dto.eventName);
        if(!repo.exists(Example.of(newEvent))){
            repo.save(newEvent);
        }
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
            dtos.add(dto);
        }
        return dtos;
    }
}
