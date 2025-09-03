package com.example.BES.services;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
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

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    public void addParticipantToEventGenreService(AddParticipantToEventGenreDto dto){
        Integer auditionNumber = 0;
        EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId,dto.genreId, dto.participantId);

        EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
        // System.out.println(participantInEventGenre.getAuditionNumber());
        if(participantInEventGenre.getParticipant() != null && participantInEventGenre.getAuditionNumber() == null){
            List<Integer> totalParticipantInGenre = repo.findAuditionNumberByEventAndGenre(dto.eventId, dto.genreId);
            List<Integer> randomPool = generateListFromOneToN(totalParticipantInGenre.size());
            randomPool.removeAll(totalParticipantInGenre);
            auditionNumber = randomPool.get(new Random().nextInt(randomPool.size()));
            participantInEventGenre.setAuditionNumber(auditionNumber);
            repo.save(participantInEventGenre);
            messagingTemplate.convertAndSend("/topic/audition/",
                Map.of(
                    "auditionNumber", auditionNumber,
                    "genre", participantInEventGenre.getGenre().getGenreName(),
                    "name", participantInEventGenre.getParticipant().getParticipantName()));
        }else{
            messagingTemplate.convertAndSend("/topic/error/",
                Map.of(
                    "audition", participantInEventGenre.getAuditionNumber(),
                    "genre", participantInEventGenre.getGenre().getGenreName(),
                    "name", participantInEventGenre.getParticipant().getParticipantName()));
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
