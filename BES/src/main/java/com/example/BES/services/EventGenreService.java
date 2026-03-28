package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreId;
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

    public List<GetGenreDto> getGenresByEventService(String eventName) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (e == null) return new ArrayList<>();
        List<GetGenreDto> dtos = new ArrayList<>();
        for (EventGenre eg : eventGenreRepo.findByEvent(e)) {
            GetGenreDto dto = new GetGenreDto();
            dto.id = eg.getGenre().getGenreId();
            dto.genreName = eg.getGenre().getGenreName();
            dto.format = eg.getFormat();
            dtos.add(dto);
        }
        return dtos;
    }

    public void addGenreToEventService(AddGenreToEventDto dto){
        Event e = eventRepo.findByEventName(dto.eventName).orElse(null);
        // Genre g = genreRepo.findByGenreName(dto.genreName.toLowerCase()).orElse(null);
        for(String genre :dto.genreName){
            Genre g = genreRepo.findByGenreName(genre.toLowerCase()).orElse(null);
            if(g == null){
                throw new NullPointerException("genre does not exist");
            }
            EventGenre eventGenre = eventGenreRepo.findByEventAndGenre(e, g).orElse(new EventGenre());
            if(eventGenre.getGenre() != null){
                throw new DataIntegrityViolationException("This event alrd has this genre");
            }
            eventGenre.setEvent(e);
            eventGenre.setGenre(g);
            eventGenre.setId(new EventGenreId(e.getEventId(), g.getGenreId()));
            eventGenreRepo.save(eventGenre);
        }
    }
}
