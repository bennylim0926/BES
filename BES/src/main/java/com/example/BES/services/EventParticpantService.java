package com.example.BES.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.mapper.EventParticipantDtoMapper;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;

@Service
public class EventParticpantService {
    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    EventParticipantDtoMapper eventParticipantDtoMapper;

    public Map<EventParticipant,List<EventGenreParticipantId>>  getAlleventGenreParticipantIds(AddParticipantDto dto,Event event, Participant participant){
        EventParticipant newParticipant = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(new EventParticipant());
        if(newParticipant.getEvent() != null){
            return null;   
        }
        newParticipant.setParticipant(participant);
        newParticipant.setEvent(event);
        newParticipant.setResidency(dto.getResidency());
        newParticipant.setGenre(String.join(", ", dto.getGenres()));
        List<EventGenreParticipantId> ids = new ArrayList<>();
        for(String g: dto.getGenres()){
            Genre genre = genreRepo.findByGenreName(g.toLowerCase()).orElse(null);
            EventGenreParticipantId id = new EventGenreParticipantId(event.getEventId(), genre.getGenreId(), participant.getParticipantId());
            ids.add(id);
        }
        Map<EventParticipant,List<EventGenreParticipantId>> res = new HashMap<>();
        res.put(newParticipant, ids);
        return res;
    }

    // get by event
    public List<GetParticipantByEventDto> getAllParticipantsByEvent(String eventName){
        Event event = eventRepo.findByEventName(eventName).orElse(null);
        if(event == null){
            return null;
        }
        List<EventParticipant> results = eventParticipantRepo.findByEvent(event);
        List<GetParticipantByEventDto> res =  eventParticipantDtoMapper.mapRow(results);
        return res;
    }

    public EventParticipant addNewWalkInInEventService(Participant p, String eventName, String genre){
        Event event = eventRepo.findByEventName(eventName).orElse(null);
        if(event == null){
            return null;
        }
        EventParticipant e = eventParticipantRepo.findByEventAndParticipant(event, p).orElse(null);
        if(e == null){
            e = new EventParticipant();
            e.setEvent(event);
            e.setParticipant(p);
        }
        return eventParticipantRepo.save(e);
    }
}