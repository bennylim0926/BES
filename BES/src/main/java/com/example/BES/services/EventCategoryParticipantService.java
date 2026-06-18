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

import com.example.BES.dtos.AddParticipantToEventCategoryDto;
import com.example.BES.dtos.GetEventCategoryParticipantDto;
import com.example.BES.dtos.ParticipantJudgeDto;
import com.example.BES.dtos.UpdateParticipantJudgeDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventCategoryParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Judge;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventCategoryParticipantMemberRepo;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.models.EventCategoryParticipantMember;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ParticipantRepo;
import com.example.BES.models.EventParticipantTeamMember;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.ScoreRepo;
import com.example.BES.dtos.UpdateParticipantDto;

@Service
public class EventCategoryParticipantService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    EventCategoryParticipantRepo repo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    ParticipantRepo participantRepo;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    JudgeRepo judgeRepo;

    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventCategoryRepo eventCategoryRepo;

    @Autowired
    EventCategoryParticipantMemberRepo ecpMemberRepo;

    @Autowired
    ScoreRepo scoreRepo;

    @Autowired
    AuditionFeedbackRepository auditionFeedbackRepository;

    @Autowired
    EventParticipantTeamMemberRepo eventParticipantTeamMemberRepo;

    public Map<String, String> addWalkInToEventCategoryParticipant(
            Participant p, String categoryName, EventParticipant ep,
            String judgeName, String entryMode, String teamName, List<String> teamMembers) {

        EventCategory ec = eventCategoryRepo.findByEventAndName(ep.getEvent(), categoryName).orElse(null);
        if (ec == null) throw new IllegalArgumentException("Division not found: " + categoryName);
        if ("solo".equalsIgnoreCase(entryMode) && !ec.isSoloAllowed())
            throw new IllegalArgumentException("Solo entries are not allowed for this division: " + categoryName);
        Judge j = judgeRepo.findFirstByName(judgeName).orElse(null);
        EventCategoryParticipantId id = new EventCategoryParticipantId(ep.getEvent().getEventId(), ec.getId(), p.getParticipantId());
        EventCategoryParticipant ecp = repo.findById(id).orElse(null);
        boolean isNew = ecp == null;
        if (isNew) {
            ecp = new EventCategoryParticipant();
            ecp.setId(id);
            ecp.setJudge(j);
            ecp.setEvent(ep.getEvent());
            ecp.setParticipant(p);
            ecp.setEventCategory(ec);

            String categoryFormat = ec.getFormat();
            boolean isTeamFormat = isTeamFormat(categoryFormat);
            boolean isTeamEntry = isTeamFormat && !"solo".equalsIgnoreCase(entryMode);

            if (isTeamEntry) {
                validateTeamEntry(categoryFormat, teamName, teamMembers);
                ecp.setFormat(categoryFormat);
                ecp.setTeamName(teamName);
                ecp.setDisplayName(teamName);
            } else {
                ecp.setFormat(isTeamFormat ? null : categoryFormat);
                ecp.setDisplayName(ep.getDisplayName() != null ? ep.getDisplayName() : p.getParticipantName());
            }
        }
        EventCategoryParticipant saved = repo.save(ecp);

        if (!"solo".equalsIgnoreCase(entryMode) && isTeamFormat(saved.getFormat())
                && teamMembers != null) {
            for (String memberName : teamMembers) {
                if (memberName != null && !memberName.isBlank()
                        && !ecpMemberRepo.existsByEventCategoryParticipantAndMemberName(saved, memberName)) {
                    ecpMemberRepo.save(new EventCategoryParticipantMember(saved, memberName));
                }
            }
        }
        Map<String, String> result = new java.util.HashMap<>();
        result.put("status", isNew ? "created" : "existing");
        result.put("category", categoryName);
        return result;
    }

    public void getAllAuditionNumsViaQR(Long participantId, Long eventId) {
        List<EventCategoryParticipant> entries =
            repo.findByEventIdAndParticipantId(eventId, participantId);
        if (!entries.isEmpty() && entries.stream().allMatch(e -> e.getAuditionNumber() != null)) {
            throw new IllegalStateException("already_checked_in");
        }
        for (EventCategoryParticipant entry : entries) {
            AddParticipantToEventCategoryDto dto = new AddParticipantToEventCategoryDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.eventCategoryId = entry.getEventCategory().getId();
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
    public void getAuditionNumViaQR(AddParticipantToEventCategoryDto dto) {
        Integer auditionNumber = 0;
        EventCategoryParticipantId id = new EventCategoryParticipantId(dto.eventId, dto.eventCategoryId, dto.participantId);

        EventCategoryParticipant participantInEventCategory = repo.findById(id).orElse(new EventCategoryParticipant());
        Judge j = participantInEventCategory.getJudge();
        if (participantInEventCategory.getParticipant() != null && participantInEventCategory.getAuditionNumber() == null) {
            int totalInCategory;
            List<Integer> takenNumbers;

            String entryFormat = participantInEventCategory.getFormat();
            boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
            if (j != null) {
                if (isSolo) {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndSoloAndJudge(dto.eventId, dto.eventCategoryId, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndSoloAndJudge(dto.eventId, dto.eventCategoryId, j.getName());
                } else {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndFormatAndJudge(dto.eventId, dto.eventCategoryId, entryFormat, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndFormatAndJudge(dto.eventId, dto.eventCategoryId, entryFormat, j.getName());
                }
            } else {
                if (isSolo) {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndSolo(dto.eventId, dto.eventCategoryId);
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndSolo(dto.eventId, dto.eventCategoryId);
                } else {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndFormat(dto.eventId, dto.eventCategoryId, entryFormat);
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndFormat(dto.eventId, dto.eventCategoryId, entryFormat);
                }
            }

            List<Integer> pool = IntStream.rangeClosed(1, totalInCategory)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            pool.removeAll(takenNumbers);
            List<Integer> participantOtherNumbers = repo.findAuditionNumbersForParticipantInOtherCategories(
                dto.eventId, dto.participantId, dto.eventCategoryId);
            if (pool.isEmpty()) {
                throw new RuntimeException("No available audition numbers for this category");
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
            participantInEventCategory.setAuditionNumber(auditionNumber);
            repo.save(participantInEventCategory);
            EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(
                participantInEventCategory.getEvent(), participantInEventCategory.getParticipant()).orElse(null);
            String refCode = ep != null ? ep.getReferenceCode() : null;
            Map<String, Object> auditMsg = new java.util.HashMap<>();
            auditMsg.put("auditionNumber", auditionNumber);
            auditMsg.put("category", participantInEventCategory.getEventCategory().getName());
            auditMsg.put("name", participantInEventCategory.getDisplayName());
            auditMsg.put("judge", j != null ? j.getName() : "");
            auditMsg.put("eventName", participantInEventCategory.getEvent().getEventName());
            auditMsg.put("participantId", participantInEventCategory.getParticipant().getParticipantId());
            auditMsg.put("eventId", participantInEventCategory.getEvent().getEventId());
            auditMsg.put("categoryId", participantInEventCategory.getEventCategory().getId());
            auditMsg.put("walkin", false);
            auditMsg.put("refCode", refCode != null ? refCode : "");
            auditMsg.put("format", participantInEventCategory.getFormat() != null ? participantInEventCategory.getFormat() : "");
            auditMsg.put("poolSize", pool.size());
            // Send the actual pool of remaining numbers so the slot-machine
            // animation cycles through what's truly available (e.g. {3, 15})
            // instead of the trivial 1..poolSize range.
            auditMsg.put("pool", new ArrayList<>(pool));
            messagingTemplate.convertAndSend("/topic/audition/", auditMsg);
        } else {
            messagingTemplate.convertAndSend("/topic/error/",
                Map.of(
                    "audition", participantInEventCategory.getAuditionNumber(),
                    "category", participantInEventCategory.getEventCategory().getName(),
                    "name", participantInEventCategory.getDisplayName(),
                    "judge", j != null ? j.getName() : "",
                    "eventName", participantInEventCategory.getEvent().getEventName()));
        }
    }

    @Transactional(readOnly = true)
    public List<GetEventCategoryParticipantDto> getAllEventCategoryParticipantByEventService(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();
        LinkedHashMap<com.example.BES.models.EventCategoryParticipantId, EventCategoryParticipant> seen = new LinkedHashMap<>();
        for (EventCategoryParticipant ecp : repo.findByEventWithJudge(event)) {
            seen.putIfAbsent(ecp.getId(), ecp);
        }
        List<EventCategoryParticipant> results = new ArrayList<>(seen.values());

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

        List<GetEventCategoryParticipantDto> dtos = new ArrayList<>();
        for (EventCategoryParticipant res : results) {
            GetEventCategoryParticipantDto dto = new GetEventCategoryParticipantDto();
            dto.eventName = res.getEvent().getEventName();
            dto.participantName = res.getDisplayName();
            dto.categoryName = res.getEventCategory().getName();
            dto.auditionNumber = res.getAuditionNumber();
            dto.walkin = false;
            long pid = res.getParticipant().getParticipantId();
            dto.participantId = pid;
            dto.eventId = res.getEvent().getEventId();
            dto.eventCategoryId = res.getEventCategory().getId();
            dto.referenceCode = refCodeMap.get(pid);
            Judge j = res.getJudge();
            if (j != null) {
                dto.judgeName = j.getName();
            }
            String fmt = res.getFormat();
            if (isTeamFormat(fmt)) {
                List<EventCategoryParticipantMember> ecpMembers = res.getMembers();
                if (ecpMembers != null && !ecpMembers.isEmpty()) {
                    List<String> memberList = new ArrayList<>();
                    // Use EP stageName for leader, same as memberNamesMap fallback
                    List<String> epMembers = memberNamesMap.get(pid);
                    if (epMembers != null && !epMembers.isEmpty()) {
                        memberList.add(epMembers.get(0));
                    } else {
                        memberList.add(res.getParticipant().getParticipantName());
                    }
                    ecpMembers.stream().map(EventCategoryParticipantMember::getMemberName).forEach(memberList::add);
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

    public void removeParticipantFromCategory(long participantId, long eventId, long eventCategoryId) {
        EventCategoryParticipantId id = new EventCategoryParticipantId(eventId, eventCategoryId, participantId);
        EventCategoryParticipant ecp = repo.findById(id).orElse(null);
        if (ecp == null) return;
        String removedCategoryName = ecp.getEventCategory().getName();
        String removedParticipantName = ecp.getDisplayName();
        String removedEventName = ecp.getEvent().getEventName();
        Judge removedJudge = ecp.getJudge();
        repo.delete(ecp);
        messagingTemplate.convertAndSend("/topic/participant-removed/",
            Map.of(
                "name", removedParticipantName,
                "category", removedCategoryName,
                "judge", removedJudge != null ? removedJudge.getName() : "",
                "eventName", removedEventName));
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event != null && participant != null) {
            EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
            if (ep != null && ep.getCategory() != null) {
                String updated = Arrays.stream(ep.getCategory().split(","))
                    .map(String::trim)
                    .filter(g -> !g.equalsIgnoreCase(removedCategoryName))
                    .collect(Collectors.joining(", "));
                ep.setCategory(updated);
                eventParticipantRepo.save(ep);
            }
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addCategoryToExistingParticipant(long participantId, long eventId, String categoryName,
            String entryMode, String teamName, List<String> teamMembers) {
        EventCategory ec = eventCategoryRepo.findByEventAndName(eventRepo.findById(eventId).orElse(null), categoryName).orElse(null);
        if (ec == null) throw new RuntimeException("Event category not found: " + categoryName);
        if ("solo".equalsIgnoreCase(entryMode) && !ec.isSoloAllowed())
            throw new IllegalArgumentException("Solo entries are not allowed for this division: " + categoryName);
        EventCategoryParticipantId id = new EventCategoryParticipantId(eventId, ec.getId(), participantId);
        if (repo.existsById(id)) return;
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) throw new RuntimeException("Event or Participant not found");
        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
        EventCategoryParticipant ecp = new EventCategoryParticipant();
        ecp.setId(id);
        ecp.setEvent(event);
        ecp.setEventCategory(ec);
        ecp.setParticipant(participant);
        ecp.setDisplayName(ep != null ? ep.getDisplayName() : participant.getParticipantName());

        String categoryFormat = ec.getFormat();
        boolean soloMode = "solo".equalsIgnoreCase(entryMode);
        boolean hasProvidedTeamName = teamName != null && !teamName.isBlank();

        boolean isTeamEntry;
        String effectiveTeamName;
        if (soloMode) {
            isTeamEntry = false;
            effectiveTeamName = null;
        } else if (hasProvidedTeamName) {
            isTeamEntry = isTeamFormat(categoryFormat);
            effectiveTeamName = teamName;
        } else {
            // Fall back to existing EP team info
            isTeamEntry = isTeamFormat(categoryFormat)
                && ep != null
                && ((ep.getTeamName() != null && !ep.getTeamName().isBlank())
                    || (ep.getTeamMembers() != null && !ep.getTeamMembers().isEmpty()));
            effectiveTeamName = (isTeamEntry && ep != null) ? ep.getTeamName() : null;
        }

        ecp.setFormat(isTeamEntry ? categoryFormat : (isTeamFormat(categoryFormat) ? null : categoryFormat));
        if (isTeamEntry && effectiveTeamName != null) {
            ecp.setDisplayName(effectiveTeamName);
            ecp.setTeamName(effectiveTeamName);
        }

        repo.save(ecp);

        // Save provided team members as ECP members
        if (isTeamEntry && teamMembers != null) {
            for (String member : teamMembers) {
                if (member != null && !member.isBlank()) {
                    ecpMemberRepo.save(new EventCategoryParticipantMember(ecp, member.trim()));
                }
            }
        }

        if (ep != null) {
            String current = ep.getCategory();
            if (current == null || current.isBlank()) {
                ep.setCategory(categoryName);
            } else if (Arrays.stream(current.split(",")).map(String::trim).noneMatch(g -> g.equalsIgnoreCase(categoryName))) {
                ep.setCategory(current + ", " + categoryName);
            }
            eventParticipantRepo.save(ep);
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void assignAuditionNumber(Long eventId, Long participantId, Long eventCategoryId, Integer auditionNumber) {
        EventCategoryParticipantId id = new EventCategoryParticipantId(eventId, eventCategoryId, participantId);
        EventCategoryParticipant ecp = repo.findById(id).orElseThrow(() ->
            new RuntimeException("Participant not enrolled in this division"));
        if (ecp.getAuditionNumber() != null) {
            throw new RuntimeException("Participant already has #" + ecp.getAuditionNumber() + " in this division — release first");
        }

        String entryFormat = ecp.getFormat();
        boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
        Judge j = ecp.getJudge();

        int totalInCategory;
        List<Integer> takenNumbers;
        if (j != null) {
            if (isSolo) {
                totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndSoloAndJudge(eventId, eventCategoryId, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndSoloAndJudge(eventId, eventCategoryId, j.getName());
            } else {
                totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndFormatAndJudge(eventId, eventCategoryId, entryFormat, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndFormatAndJudge(eventId, eventCategoryId, entryFormat, j.getName());
            }
        } else {
            if (isSolo) {
                totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndSolo(eventId, eventCategoryId);
                takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndSolo(eventId, eventCategoryId);
            } else {
                totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndFormat(eventId, eventCategoryId, entryFormat);
                takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndFormat(eventId, eventCategoryId, entryFormat);
            }
        }

        if (auditionNumber < 1 || auditionNumber > totalInCategory) {
            throw new RuntimeException("Number " + auditionNumber + " is out of range (1–" + totalInCategory + ")");
        }
        if (takenNumbers.contains(auditionNumber)) {
            throw new RuntimeException("Number " + auditionNumber + " is already taken");
        }

        ecp.setAuditionNumber(auditionNumber);
        repo.save(ecp);

        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(ecp.getEvent(), ecp.getParticipant()).orElse(null);
        String refCode = ep != null ? ep.getReferenceCode() : null;
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("auditionNumber", auditionNumber);
        msg.put("category", ecp.getEventCategory().getName());
        msg.put("name", ecp.getDisplayName());
        msg.put("judge", j != null ? j.getName() : "");
        msg.put("eventName", ecp.getEvent().getEventName());
        msg.put("participantId", ecp.getParticipant().getParticipantId());
        msg.put("eventId", ecp.getEvent().getEventId());
        msg.put("categoryId", ecp.getEventCategory().getId());
        msg.put("walkin", false);
        msg.put("refCode", refCode != null ? refCode : "");
        messagingTemplate.convertAndSend("/topic/audition/", msg);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void assignAuditionNumbersBatch(Long eventId, Long participantId, List<Map<String, Object>> assignments) {
        List<EventCategoryParticipant> toSave = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();

        for (Map<String, Object> a : assignments) {
            Long eventCategoryId = ((Number) a.get("eventCategoryId")).longValue();
            Integer auditionNumber = ((Number) a.get("auditionNumber")).intValue();

            EventCategoryParticipantId id = new EventCategoryParticipantId(eventId, eventCategoryId, participantId);
            EventCategoryParticipant ecp = repo.findById(id).orElseThrow(() ->
                new RuntimeException("Participant not enrolled in category " + eventCategoryId));
            if (ecp.getAuditionNumber() != null) {
                throw new RuntimeException("Participant already has #" + ecp.getAuditionNumber() + " in " + ecp.getEventCategory().getName() + " — release first");
            }

            String entryFormat = ecp.getFormat();
            boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
            Judge j = ecp.getJudge();

            int totalInCategory;
            List<Integer> takenNumbers;
            if (j != null) {
                if (isSolo) {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndSoloAndJudge(eventId, eventCategoryId, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndSoloAndJudge(eventId, eventCategoryId, j.getName());
                } else {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndFormatAndJudge(eventId, eventCategoryId, entryFormat, j.getName());
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndFormatAndJudge(eventId, eventCategoryId, entryFormat, j.getName());
                }
            } else {
                if (isSolo) {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndSolo(eventId, eventCategoryId);
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndSolo(eventId, eventCategoryId);
                } else {
                    totalInCategory = (int) repo.countByEventIdAndEventCategoryIdAndFormat(eventId, eventCategoryId, entryFormat);
                    takenNumbers = repo.findAuditionNumberByEventAndEventCategoryAndFormat(eventId, eventCategoryId, entryFormat);
                }
            }

            if (auditionNumber < 1 || auditionNumber > totalInCategory) {
                throw new RuntimeException("Number " + auditionNumber + " is out of range (1–" + totalInCategory + ") in " + ecp.getEventCategory().getName());
            }
            if (takenNumbers.contains(auditionNumber)) {
                throw new RuntimeException("Number " + auditionNumber + " is already taken in " + ecp.getEventCategory().getName());
            }

            ecp.setAuditionNumber(auditionNumber);
            toSave.add(ecp);

            EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(ecp.getEvent(), ecp.getParticipant()).orElse(null);
            String refCode = ep != null ? ep.getReferenceCode() : null;
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("auditionNumber", auditionNumber);
            msg.put("category", ecp.getEventCategory().getName());
            msg.put("name", ecp.getDisplayName());
            msg.put("judge", j != null ? j.getName() : "");
            msg.put("eventName", ecp.getEvent().getEventName());
            msg.put("participantId", ecp.getParticipant().getParticipantId());
            msg.put("eventId", ecp.getEvent().getEventId());
            msg.put("categoryId", ecp.getEventCategory().getId());
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
    public void swapAuditionNumbers(Long eventId, Long eventCategoryId, Long participantId1, Long participantId2) {
        EventCategoryParticipantId id1 = new EventCategoryParticipantId(eventId, eventCategoryId, participantId1);
        EventCategoryParticipantId id2 = new EventCategoryParticipantId(eventId, eventCategoryId, participantId2);
        EventCategoryParticipant ecp1 = repo.findById(id1).orElseThrow(() -> new RuntimeException("Participant 1 not found in this division"));
        EventCategoryParticipant ecp2 = repo.findById(id2).orElseThrow(() -> new RuntimeException("Participant 2 not found in this division"));
        if (ecp1.getAuditionNumber() == null || ecp2.getAuditionNumber() == null) {
            throw new RuntimeException("Both participants must have audition numbers to swap");
        }
        Integer tmp = ecp1.getAuditionNumber();
        ecp1.setAuditionNumber(ecp2.getAuditionNumber());
        ecp2.setAuditionNumber(tmp);
        repo.save(ecp1);
        repo.save(ecp2);
    }

    @Transactional
    public void releaseAuditionNumbers(Long eventId, Long participantId) {
        List<EventCategoryParticipant> entries = repo.findByEventIdAndParticipantId(eventId, participantId);
        if (entries.isEmpty()) throw new RuntimeException("Participant not found in this event");
        for (EventCategoryParticipant ecp : entries) {
            ecp.setAuditionNumber(null);
        }
        repo.saveAll(entries);
    }

    public void updateParticipantsJudgeService(UpdateParticipantJudgeDto dto) {
        for (ParticipantJudgeDto d : dto.updatedList) {
            EventCategoryParticipant ecp = repo.findByEventCategoryParticipant(d.eventName, d.categoryName, d.participantName).orElse(null);
            if (ecp != null) {
                Judge j = judgeRepo.findFirstByName(d.judgeName).orElse(null);
                ecp.setJudge(j);
                repo.save(ecp);
                messagingTemplate.convertAndSend("/topic/judge-update/",
                    Map.of(
                        "name", d.participantName,
                        "category", d.categoryName,
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

        List<EventCategoryParticipant> ecps = repo.findByEventIdAndParticipantId(eventId, participantId);
        for (EventCategoryParticipant ecp : ecps) {
            scoreRepo.deleteAll(scoreRepo.findByEventCategoryParticipant(ecp));
            auditionFeedbackRepository.deleteAll(auditionFeedbackRepository.findByEventCategoryParticipant(ecp));
        }

        repo.deleteAll(ecps);

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
        List<EventCategoryParticipant> ecps = repo.findByEventIdAndParticipantId(eventId, participantId);

        // ep.teamName is not set for sheet-imported teams — also check ECP format
        boolean isTeam = (ep != null && ep.getTeamName() != null && !ep.getTeamName().isBlank())
            || ecps.stream().anyMatch(ecp -> isTeamFormat(ecp.getFormat()));

        String trimmedName = dto.name.trim();
        if (isTeam) {
            // Check both teamName (walk-in) and displayName (sheet-imported teams have null teamName)
            boolean duplicate = eventParticipantRepo.findByEvent(event).stream()
                .filter(e -> !e.getParticipant().getParticipantId().equals(participantId))
                .anyMatch(e -> trimmedName.equalsIgnoreCase(e.getTeamName())
                    || trimmedName.equalsIgnoreCase(e.getDisplayName()));
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

            // Update leader name (index 0) — ep.stageName tracks the display name
            if (dto.memberNames != null && !dto.memberNames.isEmpty()) {
                String leaderName = dto.memberNames.get(0).trim();
                if (!leaderName.isEmpty()) {
                    ep.setStageName(leaderName);
                }
            }

            // EP-level: use collection management so orphanRemoval fires correctly
            ep.setTeamName(trimmedName);
            ep.setDisplayName(trimmedName);
            ep.getTeamMembers().clear();
            additionalMembers.forEach(m -> ep.getTeamMembers().add(new EventParticipantTeamMember(ep, m)));
            eventParticipantRepo.save(ep);

            // ECP-level: same pattern — clear + re-add via collection, not repo delete
            for (EventCategoryParticipant ecp : ecps) {
                ecp.setDisplayName(trimmedName);
                ecp.getMembers().clear();
                additionalMembers.forEach(m -> ecp.getMembers().add(new EventCategoryParticipantMember(ecp, m)));
            }
            repo.saveAll(ecps);
        } else {
            if (ep != null) {
                ep.setDisplayName(trimmedName);
                ep.setStageName(trimmedName);
                eventParticipantRepo.save(ep);
            }
            for (EventCategoryParticipant ecp : ecps) {
                ecp.setDisplayName(trimmedName);
            }
            repo.saveAll(ecps);
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
