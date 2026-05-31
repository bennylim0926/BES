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
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Judge;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.models.EventGenreParticipantMember;
import com.example.BES.respositories.EventGenreParticipantMemberRepo;
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

    @Autowired
    EventGenreRepo eventGenreRepo;

    @Autowired
    EventGenreParticipantMemberRepo egpMemberRepo;

    public EventGenreParticipant addWalkInToEventGenreParticipant(
            Participant p, String genre, EventParticipant ep,
            String judgeName, String entryMode, String teamName, List<String> teamMembers) {

        EventGenre eg = eventGenreRepo.findByEventAndName(ep.getEvent(), genre).orElse(null);
        Judge j = judgeRepo.findByName(judgeName).orElse(null);
        EventGenreParticipantId id = new EventGenreParticipantId(ep.getEvent().getEventId(), eg.getId(), p.getParticipantId());
        EventGenreParticipant egp = repo.findById(id).orElse(null);
        if (egp == null) {
            egp = new EventGenreParticipant();
            egp.setId(id);
            egp.setJudge(j);
            egp.setEvent(ep.getEvent());
            egp.setParticipant(p);
            egp.setEventGenre(eg);

            String genreFormat = eg != null ? eg.getFormat() : null;
            boolean isTeamFormat = isTeamFormat(genreFormat);
            boolean isTeamEntry = isTeamFormat && !"solo".equalsIgnoreCase(entryMode);

            if (isTeamEntry) {
                validateTeamEntry(genreFormat, teamName, teamMembers);
                egp.setFormat(genreFormat);
                egp.setTeamName(teamName);
                egp.setDisplayName(teamName);
            } else {
                egp.setFormat(isTeamFormat ? null : genreFormat);
                egp.setDisplayName(ep.getDisplayName() != null ? ep.getDisplayName() : p.getParticipantName());
            }
        }
        EventGenreParticipant saved = repo.save(egp);

        if (!"solo".equalsIgnoreCase(entryMode) && isTeamFormat(saved.getFormat())
                && teamMembers != null) {
            for (String memberName : teamMembers) {
                if (memberName != null && !memberName.isBlank()) {
                    egpMemberRepo.save(new EventGenreParticipantMember(saved, memberName));
                }
            }
        }
        return saved;
    }

    public void getAllAuditionNumsViaQR(Long participantId, Long eventId) {
        List<EventGenreParticipant> entries =
            repo.findByEventIdAndParticipantId(eventId, participantId);
        for (EventGenreParticipant entry : entries) {
            AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.eventGenreId = entry.getEventGenre().getId();
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
        EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId, dto.eventGenreId, dto.participantId);

        EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
        Judge j = participantInEventGenre.getJudge();
        if(participantInEventGenre.getParticipant() != null && participantInEventGenre.getAuditionNumber() == null){
            int totalInGenre;
            List<Integer> takenNumbers;

            String entryFormat = participantInEventGenre.getFormat();
            boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
            if(j != null){
                if (isSolo) {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSoloAndJudge(dto.eventId, dto.eventGenreId, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSoloAndJudge(dto.eventId, dto.eventGenreId, j.getName());
                } else {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormatAndJudge(dto.eventId, dto.eventGenreId, entryFormat, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormatAndJudge(dto.eventId, dto.eventGenreId, entryFormat, j.getName());
                }
            }else{
                if (isSolo) {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSolo(dto.eventId, dto.eventGenreId);
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSolo(dto.eventId, dto.eventGenreId);
                } else {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormat(dto.eventId, dto.eventGenreId, entryFormat);
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormat(dto.eventId, dto.eventGenreId, entryFormat);
                }
            }

            List<Integer> pool = IntStream.rangeClosed(1, totalInGenre)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            pool.removeAll(takenNumbers);
            Collections.shuffle(pool, SECURE_RANDOM);
            if (pool.isEmpty()) {
                throw new RuntimeException("No available audition numbers for this genre");
            }
            auditionNumber = pool.get(0);
            participantInEventGenre.setAuditionNumber(auditionNumber);
            repo.save(participantInEventGenre);
            EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(
                participantInEventGenre.getEvent(), participantInEventGenre.getParticipant()).orElse(null);
            String refCode = ep != null ? ep.getReferenceCode() : null;
            Map<String, Object> auditMsg = new java.util.HashMap<>();
            auditMsg.put("auditionNumber", auditionNumber);
            auditMsg.put("genre", participantInEventGenre.getEventGenre().getName());
            auditMsg.put("name", participantInEventGenre.getDisplayName());
            auditMsg.put("judge", j != null ? j.getName() : "");
            auditMsg.put("eventName", participantInEventGenre.getEvent().getEventName());
            auditMsg.put("participantId", participantInEventGenre.getParticipant().getParticipantId());
            auditMsg.put("eventId", participantInEventGenre.getEvent().getEventId());
            auditMsg.put("genreId", participantInEventGenre.getEventGenre().getId());
            auditMsg.put("walkin", false);
            auditMsg.put("refCode", refCode != null ? refCode : "");
            auditMsg.put("format", participantInEventGenre.getFormat() != null ? participantInEventGenre.getFormat() : "");
            messagingTemplate.convertAndSend("/topic/audition/", auditMsg);
        }else{
            messagingTemplate.convertAndSend("/topic/error/",
                Map.of(
                    "audition", participantInEventGenre.getAuditionNumber(),
                    "genre", participantInEventGenre.getEventGenre().getName(),
                    "name", participantInEventGenre.getDisplayName(),
                    "judge", j != null ? j.getName() : ""));
        }
    }

    public List<GetEventGenreParticipantDto> getAllEventGenreParticipantByEventService(String eventName){
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();
        LinkedHashMap<com.example.BES.models.EventGenreParticipantId, EventGenreParticipant> seen = new LinkedHashMap<>();
        for (EventGenreParticipant egp : repo.findByEvent(event)) {
            seen.putIfAbsent(egp.getId(), egp);
        }
        List<EventGenreParticipant> results = new ArrayList<>(seen.values());

        Map<Long, String> refCodeMap = new java.util.HashMap<>();
        Map<Long, java.util.List<String>> memberNamesMap = new java.util.HashMap<>();
        for (EventParticipant ep : eventParticipantRepo.findByEvent(event)) {
            long pid = ep.getParticipant().getParticipantId();
            refCodeMap.put(pid, ep.getReferenceCode());
            List<String> allMembers = new ArrayList<>();
            String leaderName = (ep.getStageName() != null && !ep.getStageName().isBlank())
                ? ep.getStageName()
                : ep.getParticipant().getParticipantName();
            allMembers.add(leaderName);
            if (ep.getTeamMembers() != null) {
                ep.getTeamMembers().stream()
                    .map(com.example.BES.models.EventParticipantTeamMember::getMemberName)
                    .forEach(allMembers::add);
            }
            memberNamesMap.put(pid, allMembers);
        }

        List<GetEventGenreParticipantDto> dtos = new ArrayList<>();
        for(EventGenreParticipant res : results){
            GetEventGenreParticipantDto dto = new GetEventGenreParticipantDto();
            dto.eventName = res.getEvent().getEventName();
            dto.participantName = res.getDisplayName();
            dto.genreName = res.getEventGenre().getName();
            dto.auditionNumber = res.getAuditionNumber();
            dto.walkin = false;
            long pid = res.getParticipant().getParticipantId();
            dto.participantId = pid;
            dto.eventId = res.getEvent().getEventId();
            dto.eventGenreId = res.getEventGenre().getId();
            dto.referenceCode = refCodeMap.get(pid);
            Judge j = res.getJudge();
            if(j != null){
                dto.judgeName = j.getName();
            }
            String fmt = res.getFormat();
            if (fmt != null && !fmt.equalsIgnoreCase("1v1")) {
                List<EventGenreParticipantMember> egpMembers = res.getMembers();
                if (egpMembers != null && !egpMembers.isEmpty()) {
                    List<String> memberList = new ArrayList<>();
                    String leaderName = res.getDisplayName() != null ? res.getDisplayName() : res.getParticipant().getParticipantName();
                    memberList.add(leaderName);
                    egpMembers.stream().map(EventGenreParticipantMember::getMemberName).forEach(memberList::add);
                    dto.memberNames = memberList;
                } else {
                    dto.memberNames = memberNamesMap.get(pid);
                }
            }
            dto.format = fmt;
            dtos.add(dto);
        }
        return dtos;
    }

    public void removeParticipantFromGenre(long participantId, long eventId, long eventGenreId) {
        EventGenreParticipantId id = new EventGenreParticipantId(eventId, eventGenreId, participantId);
        EventGenreParticipant egp = repo.findById(id).orElse(null);
        if (egp == null) return;
        String removedGenreName = egp.getEventGenre().getName();
        String removedParticipantName = egp.getDisplayName();
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
        EventGenre eg = eventGenreRepo.findByEventAndName(eventRepo.findById(eventId).orElse(null), genreName).orElse(null);
        if (eg == null) throw new RuntimeException("Event genre not found: " + genreName);
        EventGenreParticipantId id = new EventGenreParticipantId(eventId, eg.getId(), participantId);
        if (repo.existsById(id)) return;
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) throw new RuntimeException("Event or Participant not found");
        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
        EventGenreParticipant egp = new EventGenreParticipant();
        egp.setId(id);
        egp.setEvent(event);
        egp.setEventGenre(eg);
        egp.setParticipant(participant);
        egp.setDisplayName(ep != null ? ep.getDisplayName() : participant.getParticipantName());

        String genreFormat = eg != null ? eg.getFormat() : null;
        boolean isTeamEntry = isTeamFormat(genreFormat)
            && ep != null
            && ((ep.getTeamName() != null && !ep.getTeamName().isBlank())
                || (ep.getTeamMembers() != null && !ep.getTeamMembers().isEmpty()));
        egp.setFormat(isTeamEntry ? genreFormat : (isTeamFormat(genreFormat) ? null : genreFormat));
        if (isTeamEntry && ep != null) {
            egp.setDisplayName(ep.getTeamName() != null ? ep.getTeamName() : egp.getDisplayName());
            egp.setTeamName(ep.getTeamName());
        }

        repo.save(egp);
        if (ep != null) {
            String current = ep.getGenre();
            if (current == null || current.isBlank()) {
                ep.setGenre(genreName);
            } else if (Arrays.stream(current.split(",")).map(String::trim).noneMatch(g -> g.equalsIgnoreCase(genreName))) {
                ep.setGenre(current + ", " + genreName);
            }
            eventParticipantRepo.save(ep);
        }
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

    private boolean isTeamFormat(String fmt) {
        return fmt != null && !fmt.equalsIgnoreCase("1v1");
    }

    int parseFormatSize(String format) {
        if (format == null) return 0;
        String[] parts = format.split("v");
        try { return Integer.parseInt(parts[0]); } catch (NumberFormatException e) { return 0; }
    }

    void validateTeamEntry(String format, String teamName, List<String> memberNames) {
        int required = parseFormatSize(format) - 1;
        if (teamName == null || teamName.isBlank())
            throw new IllegalArgumentException("Team name is required for team entry");
        long nonBlank = memberNames == null ? 0L
            : memberNames.stream().filter(m -> m != null && !m.isBlank()).count();
        if (nonBlank != required)
            throw new IllegalArgumentException(
                "Expected " + required + " additional member(s) for " + format + ", got " + nonBlank);
    }

}
