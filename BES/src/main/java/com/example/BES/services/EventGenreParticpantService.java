package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class EventGenreParticpantService {
    @Autowired
    EventGenreParticpantRepo repo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    ParticipantRepo participantRepo;

    public void addParticipantToEventGenreService(AddParticipantToEventGenreDto dto){
        EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId,dto.genreId, dto.participantId);
        EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
        if(participantInEventGenre.getParticipant() == null){
            Event event = eventRepo.findById(dto.eventId).orElse(null);
            Genre genre = genreRepo.findById(dto.genreId).orElse(null);
            Participant participant = participantRepo.findById(dto.participantId).orElse(null);
            if(event != null && genre != null && participant != null){
                participantInEventGenre.setId(id);
                participantInEventGenre.setEvent(event);
                participantInEventGenre.setGenre(genre);
                participantInEventGenre.setParticipant(participant);    
                repo.save(participantInEventGenre);
            }else{
                throw new NullPointerException();
            }
        }
    }
}
