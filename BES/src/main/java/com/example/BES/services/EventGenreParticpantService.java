package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
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
        Integer auditionNumber = 0;
        // get total number of eventGenre 
        // get audition number as list
        // generate [1,..n] and minus ^
        EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId,dto.genreId, dto.participantId);
        EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
        if(participantInEventGenre.getParticipant() != null){
            // Event event = eventRepo.findById(dto.eventId).orElse(null);
            // Genre genre = genreRepo.findById(dto.genreId).orElse(null);
            // should not insert eventGenreParticipant here
            List<Integer> totalParticipantInGenre = repo.findAuditionNumberByEventAndGenre(dto.eventId, dto.genreId);
            List<Integer> randomPool = generateListFromOneToN(totalParticipantInGenre.size());
            randomPool.removeAll(totalParticipantInGenre);
            auditionNumber = randomPool.get(new Random().nextInt(randomPool.size()));
            participantInEventGenre.setAuditionNumber(auditionNumber);
            // Integer total = repo.findByEventAndGenre(event, genre).size();
            // System.out.println(String.format("eventId: %d, genreId: %d",event.getEventId(), genre.getGenreId()));
            // System.out.println(total);
            System.out.println(totalParticipantInGenre);
            
            // Participant participant = participantRepo.findById(dto.participantId).orElse(null);
            // if(event != null && genre != null && participant != null){
            //     participantInEventGenre.setId(id);
            //     participantInEventGenre.setEvent(event);
            //     participantInEventGenre.setGenre(genre);
            //     participantInEventGenre.setParticipant(participant);    
            //     participantInEventGenre.setAuditionNumber(auditionNumber);
            repo.save(participantInEventGenre);
        }
    }
    public static List<Integer> generateListFromOneToN(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be a positive integer.");
        }
        return IntStream.rangeClosed(1, n)
                        .boxed() // Convert int to Integer
                        .collect(Collectors.toList());
    }
}
