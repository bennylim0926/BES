package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetEventDivisionDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;

@Service
public class EventGenreService {
    @Autowired
    EventGenreRepo eventGenreRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    public List<GetEventDivisionDto> getGenresByEventService(String eventName) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (e == null) return new ArrayList<>();
        List<GetEventDivisionDto> dtos = new ArrayList<>();
        for (EventGenre eg : eventGenreRepo.findByEvent(e)) {
            GetEventDivisionDto dto = new GetEventDivisionDto();
            dto.eventGenreId = eg.getId();
            dto.name = eg.getName();
            dto.format = eg.getFormat();
            dto.sheetAliases = eg.getSheetAliases();
            dto.genreId = eg.getGenre() != null ? eg.getGenre().getGenreId() : null;
            dtos.add(dto);
        }
        return dtos;
    }

    public void updateEventGenreFormat(Long eventGenreId, String format) {
        EventGenre eg = eventGenreRepo.findById(eventGenreId).orElse(null);
        if (eg == null) throw new RuntimeException("Event genre not found");
        eg.setFormat(format == null || format.isBlank() ? null : format.trim());
        eventGenreRepo.save(eg);
    }

    public void addGenreToEventService(AddGenreToEventDto dto){
        Event e = eventRepo.findByEventName(dto.eventName).orElse(null);
        for(String genre : dto.genreName){
            Genre g = genreRepo.findByGenreName(genre.toLowerCase()).orElse(null);
            if(g == null){
                throw new NullPointerException("genre does not exist");
            }
            EventGenre eventGenre = eventGenreRepo.findByEventAndName(e, genre).orElse(new EventGenre());
            if(eventGenre.getEvent() != null){
                throw new DataIntegrityViolationException("This event already has this genre");
            }
            eventGenre.setEvent(e);
            eventGenre.setGenre(g);
            eventGenre.setName(genre);
            if (dto.genreFormats != null) {
                String fmt = dto.genreFormats.get(genre);
                eventGenre.setFormat(fmt == null || fmt.isBlank() ? null : fmt.trim());
            }
            if (dto.genreAliases != null) {
                String aliases = dto.genreAliases.get(genre);
                eventGenre.setSheetAliases(aliases == null || aliases.isBlank() ? null : aliases.trim());
            }
            eventGenreRepo.save(eventGenre);
        }
    }
}
