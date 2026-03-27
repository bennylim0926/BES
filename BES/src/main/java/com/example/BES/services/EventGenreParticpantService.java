package com.example.BES.services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class EventGenreParticpantService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    @Autowired
    EventParticipantRepo eventParticipantRepo;

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

    public void getAllAuditionNumsViaQR(Long participantId, Long eventId) {
        List<EventGenreParticipant> entries =
            repo.findByEventIdAndParticipantId(eventId, participantId);
        for (EventGenreParticipant entry : entries) {
            AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.genreId = entry.getGenre().getGenreId();
            int attempts = 0;
            while (true) {
                try {
                    getAuditionNumViaQR(dto);
                    break;
                } catch (Exception e) {
                    if (++attempts >= 3) throw e;
                }
            }
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void getAuditionNumViaQR(AddParticipantToEventGenreDto dto){
        Integer auditionNumber = 0;
        EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId,dto.genreId, dto.participantId);

        EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
        Judge j = participantInEventGenre.getJudge();
        if(participantInEventGenre.getParticipant() != null && participantInEventGenre.getAuditionNumber() == null){
            int totalInGenre;
            List<Integer> takenNumbers;

            if(j != null){
                totalInGenre = (int) repo.countByEventIdAndGenreIdAndJudge(dto.eventId, dto.genreId, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndGenreAndJudge(dto.eventId, dto.genreId, j.getName());
            }else{
                totalInGenre = (int) repo.countByEventIdAndGenreId(dto.eventId, dto.genreId);
                takenNumbers = repo.findAuditionNumberByEventAndGenre(dto.eventId, dto.genreId);
            }

            List<Integer> pool = IntStream.rangeClosed(1, totalInGenre)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            pool.removeAll(takenNumbers);
            Collections.shuffle(pool, SECURE_RANDOM);
            auditionNumber = pool.get(0);
            participantInEventGenre.setAuditionNumber(auditionNumber);
            repo.save(participantInEventGenre);
            messagingTemplate.convertAndSend("/topic/audition/",
                Map.of(
                    "auditionNumber", auditionNumber,
                    "genre", participantInEventGenre.getGenre().getGenreName(),
                    "name", participantInEventGenre.getParticipant().getParticipantName(),
                    "judge", j != null ? j.getName() : "",
                    "eventName", participantInEventGenre.getEvent().getEventName(),
                    "participantId", participantInEventGenre.getParticipant().getParticipantId(),
                    "eventId", participantInEventGenre.getEvent().getEventId(),
                    "genreId", participantInEventGenre.getGenre().getGenreId(),
                    "walkin", participantInEventGenre.getParticipant().getParticipantEmail() == null));
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
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();
        // Deduplicate by composite ID to avoid Hibernate row multiplication
        // caused by the @OneToMany(scores) join producing one row per score entry.
        LinkedHashMap<com.example.BES.models.EventGenreParticipantId, EventGenreParticipant> seen = new LinkedHashMap<>();
        for (EventGenreParticipant egp : repo.findByEvent(event)) {
            seen.putIfAbsent(egp.getId(), egp);
        }
        List<EventGenreParticipant> results = new ArrayList<>(seen.values());

        // Build emailSent and referenceCode maps from EventParticipant records
        Map<Long, Boolean> emailSentMap = new java.util.HashMap<>();
        Map<Long, String> refCodeMap = new java.util.HashMap<>();
        for (EventParticipant ep : eventParticipantRepo.findByEvent(event)) {
            emailSentMap.put(ep.getParticipant().getParticipantId(), ep.isEmailSent());
            refCodeMap.put(ep.getParticipant().getParticipantId(), ep.getReferenceCode());
        }

        List<GetEventGenreParticipantDto> dtos = new ArrayList<>();
        for(EventGenreParticipant res : results){
            GetEventGenreParticipantDto dto = new GetEventGenreParticipantDto();
            dto.eventName = res.getEvent().getEventName();
            dto.participantName = res.getParticipant().getParticipantName();
            dto.genreName = res.getGenre().getGenreName();
            dto.auditionNumber = res.getAuditionNumber();
            dto.walkin = (res.getParticipant().getParticipantEmail() == null)? true : false;
            dto.participantId = res.getParticipant().getParticipantId();
            dto.eventId = res.getEvent().getEventId();
            dto.genreId = res.getGenre().getGenreId();
            dto.emailSent = emailSentMap.getOrDefault(res.getParticipant().getParticipantId(), false);
            dto.referenceCode = refCodeMap.get(res.getParticipant().getParticipantId());
            Judge j = res.getJudge();
            if(j != null){
                dto.judgeName = j.getName();
            }
            dtos.add(dto);
        }
        return dtos;
    }

    public void removeParticipantFromGenre(long participantId, long eventId, long genreId) {
        EventGenreParticipantId id = new EventGenreParticipantId(eventId, genreId, participantId);
        EventGenreParticipant egp = repo.findById(id).orElse(null);
        if (egp == null) return;
        String removedGenreName = egp.getGenre().getGenreName();
        String removedParticipantName = egp.getParticipant().getParticipantName();
        String removedEventName = egp.getEvent().getEventName();
        Judge removedJudge = egp.getJudge();
        repo.delete(egp);
        messagingTemplate.convertAndSend("/topic/participant-removed/",
            Map.of(
                "name", removedParticipantName,
                "genre", removedGenreName,
                "judge", removedJudge != null ? removedJudge.getName() : "",
                "eventName", removedEventName));
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event != null && participant != null) {
            EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
            if (ep != null && ep.getGenre() != null) {
                String updated = Arrays.stream(ep.getGenre().split(","))
                    .map(String::trim)
                    .filter(g -> !g.equalsIgnoreCase(removedGenreName))
                    .collect(Collectors.joining(", "));
                ep.setGenre(updated);
                eventParticipantRepo.save(ep);
            }
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addGenreToExistingParticipant(long participantId, long eventId, String genreName) {
        Genre genre = genreRepo.findByGenreName(genreName).orElse(null);
        if (genre == null) throw new RuntimeException("Genre not found: " + genreName);
        EventGenreParticipantId id = new EventGenreParticipantId(eventId, genre.getGenreId(), participantId);
        if (repo.existsById(id)) return;
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) throw new RuntimeException("Event or Participant not found");
        EventGenreParticipant egp = new EventGenreParticipant();
        egp.setId(id);
        egp.setEvent(event);
        egp.setGenre(genre);
        egp.setParticipant(participant);
        repo.save(egp);
        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
        if (ep != null) {
            String current = ep.getGenre();
            if (current == null || current.isBlank()) {
                ep.setGenre(genreName);
            } else if (Arrays.stream(current.split(",")).map(String::trim).noneMatch(g -> g.equalsIgnoreCase(genreName))) {
                ep.setGenre(current + ", " + genreName);
            }
            eventParticipantRepo.save(ep);
        }
        AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
        dto.participantId = participantId;
        dto.eventId = eventId;
        dto.genreId = genre.getGenreId();
        getAuditionNumViaQR(dto);
    }

    public void updateParticipantsJudgeService(UpdateParticipantJudgeDto dto){
        for(ParticipantJudgeDto d : dto.updatedList){
            EventGenreParticipant egp = repo.findByEventGenreParticipant(d.eventName, d.genreName, d.participantName).orElse(null);
            if(egp != null){
                Judge j = judgeRepo.findByName(d.judgeName).orElse(null);
                egp.setJudge(j);
                repo.save(egp);
                messagingTemplate.convertAndSend("/topic/judge-update/",
                    Map.of(
                        "name", d.participantName,
                        "genre", d.genreName,
                        "judge", j != null ? j.getName() : "",
                        "eventName", d.eventName));
            }
        }
    }

}
