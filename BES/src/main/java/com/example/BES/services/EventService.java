package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import com.example.BES.models.Event;
import com.example.BES.dtos.AddEventDto;
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
}
