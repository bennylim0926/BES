package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.dtos.GetEventGenreParticipantDto;
import com.example.BES.dtos.ParticipantJudgeDto;
import com.example.BES.dtos.UpdateParticipantJudgeDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Judge;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.JudgeRepo;
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

    @Autowired
    JudgeRepo judgeRepo;

    public EventGenreParticipant addWalkInToEventGenreParticipant(Participant p, String genre, EventParticipant ep, String judge){
        
        Genre g = genreRepo.findByGenreName(genre).orElse(null);
        Judge j = judgeRepo.findByName(judge).orElse(null);
        EventGenreParticipantId id = new EventGenreParticipantId(ep.getEvent().getEventId(), g.getGenreId(), p.getParticipantId());
        EventGenreParticipant egp = repo.findById(id).orElse(null);
        if(egp == null){
            egp = new EventGenreParticipant();
            egp.setId(id);
            egp.setJudge(j);
            egp.setEvent(ep.getEvent());
            egp.setParticipant(p);
            egp.setGenre(g);
        }
        return repo.save(egp);
    }

    public void getAuditionNumViaQR(AddParticipantToEventGenreDto dto){
        Integer auditionNumber = 0;
        EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId,dto.genreId, dto.participantId);

        EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
        Judge j = participantInEventGenre.getJudge();
        if(participantInEventGenre.getParticipant() != null && participantInEventGenre.getAuditionNumber() == null){
            // if judge is null, give audition like normal
            List<Integer> totalParticipantInGenre = new ArrayList<>();
            
            if(j != null){
                totalParticipantInGenre = 
                    repo.findAuditionNumberByEventAndGenreAndJudge(dto.eventId, dto.genreId, j.getName());
            }else{
                totalParticipantInGenre = 
                    repo.findAuditionNumberByEventAndGenre(dto.eventId, dto.genreId);
            }
           
            List<Integer> randomPool = generateListFromOneToN(totalParticipantInGenre.size());
            randomPool.removeAll(totalParticipantInGenre);
            auditionNumber = randomPool.get(ThreadLocalRandom.current().nextInt(randomPool.size()));
            participantInEventGenre.setAuditionNumber(auditionNumber);
            repo.save(participantInEventGenre);
            messagingTemplate.convertAndSend("/topic/audition/",
                Map.of(
                    "auditionNumber", auditionNumber,
                    "genre", participantInEventGenre.getGenre().getGenreName(),
                    "name", participantInEventGenre.getParticipant().getParticipantName(),
                    "judge", j != null ? j.getName() : ""));
        }else{
            messagingTemplate.convertAndSend("/topic/error/",
                Map.of(
                    "audition", participantInEventGenre.getAuditionNumber(),
                    "genre", participantInEventGenre.getGenre().getGenreName(),
                    "name", participantInEventGenre.getParticipant().getParticipantName(),
                    "judge", j != null ? j.getName() : ""));
        }
    }

    public List<GetEventGenreParticipantDto> getAllEventGenreParticipantByEventService(String eventName){
        Event event = eventRepo.findByEventName(eventName).orElse(null);
        List<EventGenreParticipant> results =  repo.findByEvent(event);
        List<GetEventGenreParticipantDto> dtos = new ArrayList<>();
        for(EventGenreParticipant res : results){
            GetEventGenreParticipantDto dto = new GetEventGenreParticipantDto();
            dto.eventName = res.getEvent().getEventName();
            dto.participantName = res.getParticipant().getParticipantName();
            dto.genreName = res.getGenre().getGenreName();
            dto.auditionNumber = res.getAuditionNumber();
            dto.walkin = (res.getParticipant().getParticipantEmail() == null)? true : false; 
            Judge j = res.getJudge();
            if(j != null){
                dto.judgeName = j.getName();
            }
            dtos.add(dto);
        }
        return dtos;
    }

    public void updateParticipantsJudgeService(UpdateParticipantJudgeDto dto){
        for(ParticipantJudgeDto d : dto.updatedList){
            EventGenreParticipant egp = repo.findByEventGenreParticipant(d.eventName, d.genreName, d.participantName).orElse(null);
            if(egp != null){
                Judge j = judgeRepo.findByName(d.judgeName).orElse(null);
                egp.setJudge(j);
                repo.save(egp);
            }
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
