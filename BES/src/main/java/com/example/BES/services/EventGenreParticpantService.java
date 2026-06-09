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
import com.example.BES.models.EventParticipantTeamMember;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.ScoreRepo;
import com.example.BES.dtos.UpdateParticipantDto;

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

    @Autowired
    ScoreRepo scoreRepo;

    @Autowired
    AuditionFeedbackRepository auditionFeedbackRepository;

    @Autowired
    EventParticipantTeamMemberRepo eventParticipantTeamMemberRepo;

    public EventGenreParticipant addWalkInToEventGenreParticipant(
            Participant p, String genre, EventParticipant ep,
            String judgeName, String entryMode, String teamName, List<String> teamMembers) {

        EventGenre eg = eventGenreRepo.findByEventAndName(ep.getEvent(), genre).orElse(null);
        if (eg == null) throw new IllegalArgumentException("Division not found: " + genre);
        if ("solo".equalsIgnoreCase(entryMode) && !eg.isSoloAllowed())
            throw new IllegalArgumentException("Solo entries are not allowed for this division: " + genre);
        Judge j = judgeRepo.findFirstByName(judgeName).orElse(null);
        EventGenreParticipantId id = new EventGenreParticipantId(ep.getEvent().getEventId(), eg.getId(), p.getParticipantId());
        EventGenreParticipant egp = repo.findById(id).orElse(null);
        if (egp == null) {
            egp = new EventGenreParticipant();
            egp.setId(id);
            egp.setJudge(j);
            egp.setEvent(ep.getEvent());
            egp.setParticipant(p);
            egp.setEventGenre(eg);

            String genreFormat = eg.getFormat();
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
        if (!entries.isEmpty() && entries.stream().allMatch(e -> e.getAuditionNumber() != null)) {
            throw new IllegalStateException("already_checked_in");
        }
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
            List<Integer> participantOtherNumbers = repo.findAuditionNumbersForParticipantInOtherGenres(
                dto.eventId, dto.participantId, dto.eventGenreId);
            if (pool.isEmpty()) {
                throw new RuntimeException("No available audition numbers for this genre");
            }
            // Prefer a number not already used by this participant in another division.
            // Falls back to the full available pool if all remaining numbers are taken elsewhere.
            List<Integer> workingPool = new ArrayList<>(pool);
            if (!participantOtherNumbers.isEmpty()) {
                List<Integer> crossExcluded = new ArrayList<>(pool);
                crossExcluded.removeAll(participantOtherNumbers);
                if (!crossExcluded.isEmpty()) workingPool = crossExcluded;
            }
            Collections.shuffle(workingPool, SECURE_RANDOM);
            // Best-effort: prefer a number at least 2 away from participant's other-division numbers.
            if (!participantOtherNumbers.isEmpty()) {
                List<Integer> preferred = workingPool.stream()
                    .filter(n -> participantOtherNumbers.stream().allMatch(other -> Math.abs(n - other) > 1))
                    .collect(Collectors.toList());
                auditionNumber = preferred.isEmpty() ? workingPool.get(0) : preferred.get(0);
            } else {
                auditionNumber = workingPool.get(0);
            }
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
                    "judge", j != null ? j.getName() : "",
                    "eventName", participantInEventGenre.getEvent().getEventName()));
        }
    }

    @Transactional(readOnly = true)
    public List<GetEventGenreParticipantDto> getAllEventGenreParticipantByEventService(String eventName){
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();
        LinkedHashMap<com.example.BES.models.EventGenreParticipantId, EventGenreParticipant> seen = new LinkedHashMap<>();
        for (EventGenreParticipant egp : repo.findByEventWithJudge(event)) {
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
            if (isTeamFormat(fmt)) {
                List<EventGenreParticipantMember> egpMembers = res.getMembers();
                if (egpMembers != null && !egpMembers.isEmpty()) {
                    List<String> memberList = new ArrayList<>();
                    memberList.add(res.getParticipant().getParticipantName());
                    egpMembers.stream().map(EventGenreParticipantMember::getMemberName).forEach(memberList::add);
                    dto.memberNames = memberList;
                } else {
                    List<String> fallback = memberNamesMap.get(pid);
                    if (fallback != null && fallback.size() > 1) dto.memberNames = fallback;
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
    public void addGenreToExistingParticipant(long participantId, long eventId, String genreName,
            String entryMode, String teamName, List<String> teamMembers) {
        EventGenre eg = eventGenreRepo.findByEventAndName(eventRepo.findById(eventId).orElse(null), genreName).orElse(null);
        if (eg == null) throw new RuntimeException("Event genre not found: " + genreName);
        if ("solo".equalsIgnoreCase(entryMode) && !eg.isSoloAllowed())
            throw new IllegalArgumentException("Solo entries are not allowed for this division: " + genreName);
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

        String genreFormat = eg.getFormat();
        boolean soloMode = "solo".equalsIgnoreCase(entryMode);
        boolean hasProvidedTeamName = teamName != null && !teamName.isBlank();

        boolean isTeamEntry;
        String effectiveTeamName;
        if (soloMode) {
            isTeamEntry = false;
            effectiveTeamName = null;
        } else if (hasProvidedTeamName) {
            isTeamEntry = isTeamFormat(genreFormat);
            effectiveTeamName = teamName;
        } else {
            // Fall back to existing EP team info
            isTeamEntry = isTeamFormat(genreFormat)
                && ep != null
                && ((ep.getTeamName() != null && !ep.getTeamName().isBlank())
                    || (ep.getTeamMembers() != null && !ep.getTeamMembers().isEmpty()));
            effectiveTeamName = (isTeamEntry && ep != null) ? ep.getTeamName() : null;
        }

        egp.setFormat(isTeamEntry ? genreFormat : (isTeamFormat(genreFormat) ? null : genreFormat));
        if (isTeamEntry && effectiveTeamName != null) {
            egp.setDisplayName(effectiveTeamName);
            egp.setTeamName(effectiveTeamName);
        }

        repo.save(egp);

        // Save provided team members as EGP members
        if (isTeamEntry && teamMembers != null) {
            for (String member : teamMembers) {
                if (member != null && !member.isBlank()) {
                    egpMemberRepo.save(new EventGenreParticipantMember(egp, member.trim()));
                }
            }
        }

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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void assignAuditionNumber(Long eventId, Long participantId, Long eventGenreId, Integer auditionNumber) {
        EventGenreParticipantId id = new EventGenreParticipantId(eventId, eventGenreId, participantId);
        EventGenreParticipant egp = repo.findById(id).orElseThrow(() ->
            new RuntimeException("Participant not enrolled in this division"));
        if (egp.getAuditionNumber() != null) {
            throw new RuntimeException("Participant already has #" + egp.getAuditionNumber() + " in this division — release first");
        }

        String entryFormat = egp.getFormat();
        boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
        Judge j = egp.getJudge();

        int totalInGenre;
        List<Integer> takenNumbers;
        if (j != null) {
            if (isSolo) {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSoloAndJudge(eventId, eventGenreId, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSoloAndJudge(eventId, eventGenreId, j.getName());
            } else {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormatAndJudge(eventId, eventGenreId, entryFormat, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormatAndJudge(eventId, eventGenreId, entryFormat, j.getName());
            }
        } else {
            if (isSolo) {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSolo(eventId, eventGenreId);
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSolo(eventId, eventGenreId);
            } else {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormat(eventId, eventGenreId, entryFormat);
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormat(eventId, eventGenreId, entryFormat);
            }
        }

        if (auditionNumber < 1 || auditionNumber > totalInGenre) {
            throw new RuntimeException("Number " + auditionNumber + " is out of range (1–" + totalInGenre + ")");
        }
        if (takenNumbers.contains(auditionNumber)) {
            throw new RuntimeException("Number " + auditionNumber + " is already taken");
        }

        egp.setAuditionNumber(auditionNumber);
        repo.save(egp);

        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(egp.getEvent(), egp.getParticipant()).orElse(null);
        String refCode = ep != null ? ep.getReferenceCode() : null;
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("auditionNumber", auditionNumber);
        msg.put("genre", egp.getEventGenre().getName());
        msg.put("name", egp.getDisplayName());
        msg.put("judge", j != null ? j.getName() : "");
        msg.put("eventName", egp.getEvent().getEventName());
        msg.put("participantId", egp.getParticipant().getParticipantId());
        msg.put("eventId", egp.getEvent().getEventId());
        msg.put("genreId", egp.getEventGenre().getId());
        msg.put("walkin", false);
        msg.put("refCode", refCode != null ? refCode : "");
        messagingTemplate.convertAndSend("/topic/audition/", msg);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void assignAuditionNumbersBatch(Long eventId, Long participantId, List<Map<String, Object>> assignments) {
        List<EventGenreParticipant> toSave = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();

        for (Map<String, Object> a : assignments) {
            Long eventGenreId = ((Number) a.get("eventGenreId")).longValue();
            Integer auditionNumber = ((Number) a.get("auditionNumber")).intValue();

            EventGenreParticipantId id = new EventGenreParticipantId(eventId, eventGenreId, participantId);
            EventGenreParticipant egp = repo.findById(id).orElseThrow(() ->
                new RuntimeException("Participant not enrolled in division " + eventGenreId));
            if (egp.getAuditionNumber() != null) {
                throw new RuntimeException("Participant already has #" + egp.getAuditionNumber() + " in " + egp.getEventGenre().getName() + " — release first");
            }

            String entryFormat = egp.getFormat();
            boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
            Judge j = egp.getJudge();

            int totalInGenre;
            List<Integer> takenNumbers;
            if (j != null) {
                if (isSolo) {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSoloAndJudge(eventId, eventGenreId, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSoloAndJudge(eventId, eventGenreId, j.getName());
                } else {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormatAndJudge(eventId, eventGenreId, entryFormat, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormatAndJudge(eventId, eventGenreId, entryFormat, j.getName());
                }
            } else {
                if (isSolo) {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSolo(eventId, eventGenreId);
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSolo(eventId, eventGenreId);
                } else {
                    totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormat(eventId, eventGenreId, entryFormat);
                    takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormat(eventId, eventGenreId, entryFormat);
                }
            }

            if (auditionNumber < 1 || auditionNumber > totalInGenre) {
                throw new RuntimeException("Number " + auditionNumber + " is out of range (1–" + totalInGenre + ") in " + egp.getEventGenre().getName());
            }
            if (takenNumbers.contains(auditionNumber)) {
                throw new RuntimeException("Number " + auditionNumber + " is already taken in " + egp.getEventGenre().getName());
            }

            egp.setAuditionNumber(auditionNumber);
            toSave.add(egp);

            EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(egp.getEvent(), egp.getParticipant()).orElse(null);
            String refCode = ep != null ? ep.getReferenceCode() : null;
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("auditionNumber", auditionNumber);
            msg.put("genre", egp.getEventGenre().getName());
            msg.put("name", egp.getDisplayName());
            msg.put("judge", j != null ? j.getName() : "");
            msg.put("eventName", egp.getEvent().getEventName());
            msg.put("participantId", egp.getParticipant().getParticipantId());
            msg.put("eventId", egp.getEvent().getEventId());
            msg.put("genreId", egp.getEventGenre().getId());
            msg.put("walkin", false);
            msg.put("refCode", refCode != null ? refCode : "");
            messages.add(msg);
        }

        // All validations passed — save atomically then notify
        repo.saveAll(toSave);
        for (Map<String, Object> msg : messages) {
            messagingTemplate.convertAndSend("/topic/audition/", msg);
        }
    }

    @Transactional
    public void swapAuditionNumbers(Long eventId, Long eventGenreId, Long participantId1, Long participantId2) {
        EventGenreParticipantId id1 = new EventGenreParticipantId(eventId, eventGenreId, participantId1);
        EventGenreParticipantId id2 = new EventGenreParticipantId(eventId, eventGenreId, participantId2);
        EventGenreParticipant egp1 = repo.findById(id1).orElseThrow(() -> new RuntimeException("Participant 1 not found in this division"));
        EventGenreParticipant egp2 = repo.findById(id2).orElseThrow(() -> new RuntimeException("Participant 2 not found in this division"));
        if (egp1.getAuditionNumber() == null || egp2.getAuditionNumber() == null) {
            throw new RuntimeException("Both participants must have audition numbers to swap");
        }
        Integer tmp = egp1.getAuditionNumber();
        egp1.setAuditionNumber(egp2.getAuditionNumber());
        egp2.setAuditionNumber(tmp);
        repo.save(egp1);
        repo.save(egp2);
    }

    @Transactional
    public void releaseAuditionNumbers(Long eventId, Long participantId) {
        List<EventGenreParticipant> entries = repo.findByEventIdAndParticipantId(eventId, participantId);
        if (entries.isEmpty()) throw new RuntimeException("Participant not found in this event");
        for (EventGenreParticipant egp : entries) {
            egp.setAuditionNumber(null);
        }
        repo.saveAll(entries);
    }

    public void updateParticipantsJudgeService(UpdateParticipantJudgeDto dto){
        for(ParticipantJudgeDto d : dto.updatedList){
            EventGenreParticipant egp = repo.findByEventGenreParticipant(d.eventName, d.genreName, d.participantName).orElse(null);
            if(egp != null){
                Judge j = judgeRepo.findFirstByName(d.judgeName).orElse(null);
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

    @Transactional
    public void deleteParticipantFromEvent(long participantId, long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) return;

        List<EventGenreParticipant> egps = repo.findByEventIdAndParticipantId(eventId, participantId);
        for (EventGenreParticipant egp : egps) {
            scoreRepo.deleteAll(scoreRepo.findByEventGenreParticipant(egp));
            auditionFeedbackRepository.deleteAll(auditionFeedbackRepository.findByEventGenreParticipant(egp));
        }

        repo.deleteAll(egps);

        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
        if (ep != null) {
            eventParticipantTeamMemberRepo.deleteByEventParticipant(ep);
            eventParticipantRepo.delete(ep);
        }

        List<EventParticipant> remaining = eventParticipantRepo.findByParticipant(participant);
        if (remaining.isEmpty()) {
            participantRepo.delete(participant);
        }
    }

    @Transactional
    public void updateParticipant(long participantId, long eventId, UpdateParticipantDto dto) {
        if (dto.name == null || dto.name.isBlank())
            throw new IllegalArgumentException("Name must not be empty");

        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null)
            throw new RuntimeException("Event or Participant not found");

        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
        List<EventGenreParticipant> egps = repo.findByEventIdAndParticipantId(eventId, participantId);

        // ep.teamName is not set for sheet-imported teams — also check EGP format
        boolean isTeam = (ep != null && ep.getTeamName() != null && !ep.getTeamName().isBlank())
            || egps.stream().anyMatch(egp -> isTeamFormat(egp.getFormat()));

        String trimmedName = dto.name.trim();
        if (isTeam) {
            boolean duplicate = eventParticipantRepo.findByEvent(event).stream()
                .filter(e -> !e.getParticipant().getParticipantId().equals(participantId))
                .anyMatch(e -> trimmedName.equalsIgnoreCase(e.getTeamName()));
            if (duplicate)
                throw new IllegalStateException("A team with this name already exists in this event");
        } else {
            boolean duplicate = eventParticipantRepo.findByEvent(event).stream()
                .filter(e -> !e.getParticipant().getParticipantId().equals(participantId))
                .anyMatch(e -> trimmedName.equalsIgnoreCase(e.getParticipant().getParticipantName()));
            if (duplicate)
                throw new IllegalStateException("A participant with this name already exists in this event");
        }

        if (isTeam) {
            List<String> additionalMembers = dto.memberNames == null
                ? new ArrayList<>()
                : dto.memberNames.stream()
                    .skip(1) // index 0 = participant.name, always prepended on read — only store the rest
                    .filter(m -> m != null && !m.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toList());

            // Update leader name (index 0) — keeps participant.participantName and ep.stageName in sync
            // so both the edit modal and check-in card show the same value
            if (dto.memberNames != null && !dto.memberNames.isEmpty()) {
                String leaderName = dto.memberNames.get(0).trim();
                if (!leaderName.isEmpty()) {
                    participant.setParticipantName(leaderName);
                    participantRepo.save(participant);
                    ep.setStageName(leaderName);
                }
            }

            // EP-level: use collection management so orphanRemoval fires correctly
            ep.setTeamName(trimmedName);
            ep.setDisplayName(trimmedName);
            ep.getTeamMembers().clear();
            additionalMembers.forEach(m -> ep.getTeamMembers().add(new EventParticipantTeamMember(ep, m)));
            eventParticipantRepo.save(ep);

            // EGP-level: same pattern — clear + re-add via collection, not repo delete
            for (EventGenreParticipant egp : egps) {
                egp.setDisplayName(trimmedName);
                egp.getMembers().clear();
                additionalMembers.forEach(m -> egp.getMembers().add(new EventGenreParticipantMember(egp, m)));
            }
            repo.saveAll(egps);
        } else {
            participant.setParticipantName(trimmedName);
            participantRepo.save(participant);
            if (ep != null) {
                ep.setDisplayName(trimmedName);
                eventParticipantRepo.save(ep);
            }
            for (EventGenreParticipant egp : egps) {
                egp.setDisplayName(trimmedName);
            }
            repo.saveAll(egps);
        }
    }

    private boolean isTeamFormat(String fmt) {
        return fmt != null && fmt.matches("(?i)\\d+v\\d+") && !fmt.equalsIgnoreCase("1v1");
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
