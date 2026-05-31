package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.GetCheckinListDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.dtos.ImportResultDto;
import com.example.BES.dtos.VerifyParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventGenreParticipantMember;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.utils.ReferenceCodeUtil;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreParticipantMemberRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    @Autowired(required = false)
    GoogleSheetService sheetService;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    ParticipantService participantService;

    @Autowired
    ParticipantRepo participantRepo;

    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventGenreParticpantRepo eventGenreParticipantRepo;

    @Autowired
    EventGenreParticipantMemberRepo egpMemberRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

    public ImportResultDto addParticipantToEvent(AddParticipantToEventDto dto) throws IOException {
        Event event = eventRepo.findByEventName(dto.eventName).orElse(null);
        if (event == null) throw new NullPointerException("event is null");

        List<AddParticipantDto> importable = sheetService.getAllImportableParticipants(dto);
        List<EventGenre> allDivisions = eventGenreRepo.findByEvent(event);

        List<String> divisionsWithoutFormat = allDivisions.stream()
            .filter(eg -> eg.getFormat() == null || eg.getFormat().isBlank())
            .map(EventGenre::getName)
            .collect(Collectors.toList());
        if (!divisionsWithoutFormat.isEmpty()) {
            ImportResultDto blocked = new ImportResultDto();
            blocked.errors.add(new ImportResultDto.SkippedRow(0, "—",
                "Import blocked: set a format for these divisions first: " + String.join(", ", divisionsWithoutFormat)));
            return blocked;
        }

        ImportResultDto result = new ImportResultDto();
        int rowNumber = 2;

        for (AddParticipantDto participant : importable) {
            String participantName = participant.getParticipantName();
            try {
                boolean hasTeamFormatGenre = hasTeamFormatGenre(participant.getGenres(), allDivisions);

                if (hasTeamFormatGenre) {
                    String entryType = participant.getEntryType();
                    boolean soloBlocked = isSoloBlockedForAnyGenre(participant.getGenres(), allDivisions);
                    if ((entryType == null || entryType.isBlank()) && soloBlocked) {
                        result.errors.add(new ImportResultDto.SkippedRow(rowNumber, participantName,
                            "Solo entry not allowed for this division — ENTRY_TYPE required"));
                        result.skipped++;
                        rowNumber++;
                        continue;
                    }
                    if ("team".equals(entryType)) {
                        String format = getTeamFormat(participant.getGenres(), allDivisions);
                        validateTeamEntry(format, participant.getTeamName(), participant.getMemberNames());
                    }
                }

                Participant toAddParticipant = participantService.addParticpantService(participant);
                EventParticipant ep = eventParticipantRepo
                    .findByEventAndParticipant(event, toAddParticipant).orElse(null);

                boolean isNew = ep == null;
                if (isNew) {
                    ep = new EventParticipant();
                    ep.setParticipant(toAddParticipant);
                    ep.setEvent(event);
                    ep.setStageName(participant.getStageName());
                    ep.setDisplayName(resolveDisplayName(participant));
                    ep.setResidency(participant.getResidency());
                    ep.setGenre(participant.getGenres() != null ? String.join(", ", participant.getGenres()) : "");
                    ep.setPaymentVerified(!event.isPaymentRequired());
                    ep.setScreenshotUrl(participant.getScreenshotUrl());
                    ep.setReferenceCode(ReferenceCodeUtil.generate());
                    eventParticipantRepo.save(ep);
                }

                if (participant.getGenres() != null) {
                    for (String genreName : participant.getGenres()) {
                        EventGenre eg = findMatchingDivision(allDivisions, genreName);
                        if (eg == null) continue;
                        EventGenreParticipantId id = new EventGenreParticipantId(
                            event.getEventId(), eg.getId(), toAddParticipant.getParticipantId());
                        if (eventGenreParticipantRepo.existsById(id)) continue;
                        EventGenreParticipant egp = new EventGenreParticipant();
                        egp.setId(id);
                        egp.setEvent(event);
                        egp.setEventGenre(eg);
                        egp.setParticipant(toAddParticipant);

                        String effectiveFormat = eg != null ? eg.getFormat() : null;
                        boolean isTeamFormat = isTeamFormat(effectiveFormat);
                        boolean isTeamEntry = isTeamFormat && "team".equals(participant.getEntryType());

                        if (isTeamEntry) {
                            egp.setFormat(effectiveFormat);
                            egp.setTeamName(participant.getTeamName());
                            egp.setDisplayName(participant.getTeamName());
                        } else {
                            egp.setFormat(isTeamFormat ? null : effectiveFormat);
                            egp.setDisplayName(orElse(participant.getStageName(), participant.getParticipantName()));
                        }

                        EventGenreParticipant savedEgp = eventGenreParticipantRepo.save(egp);

                        if (isTeamEntry && participant.getMemberNames() != null) {
                            for (String memberName : participant.getMemberNames()) {
                                if (memberName != null && !memberName.isBlank()
                                        && !egpMemberRepo.existsByEventGenreParticipantAndMemberName(savedEgp, memberName)) {
                                    egpMemberRepo.save(new EventGenreParticipantMember(savedEgp, memberName));
                                }
                            }
                        }
                    }
                }
                if (isNew) result.imported++; else result.existing++;

            } catch (IllegalArgumentException e) {
                result.errors.add(new ImportResultDto.SkippedRow(rowNumber, participantName, e.getMessage()));
                result.skipped++;
            }
            rowNumber++;
        }
        return result;
    }

    public void verifyPayment(long participantId, long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) throw new RuntimeException("Event or participant not found");

        EventParticipant ep = eventParticipantRepo
            .findByEventAndParticipant(event, participant).orElse(null);
        if (ep == null) throw new RuntimeException("EventParticipant not found");

        ep.setPaymentVerified(true);
        eventParticipantRepo.save(ep);
    }

    public void verifyPaymentBatch(List<VerifyParticipantDto> list) {
        for (VerifyParticipantDto item : list) {
            verifyPayment(item.getParticipantId(), item.getEventId());
        }
    }

    public List<GetUnverifiedParticipantDto> getUnverifiedParticipantsFromDb(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();

        List<EventParticipant> eps = eventParticipantRepo.findByEventAndPaymentVerifiedFalse(event);
        List<GetUnverifiedParticipantDto> result = new ArrayList<>();
        for (EventParticipant ep : eps) {
            GetUnverifiedParticipantDto dto = new GetUnverifiedParticipantDto();
            dto.participantId = ep.getParticipant().getParticipantId();
            dto.eventId = ep.getEvent().getEventId();
            dto.name = ep.getDisplayName();
            List<EventGenreParticipant> egps = eventGenreParticipantRepo
                .findByEventIdAndParticipantId(ep.getEvent().getEventId(), ep.getParticipant().getParticipantId());
            dto.genres = egps.stream()
                .map(egp -> egp.getEventGenre().getName())
                .collect(Collectors.toList());
            dto.screenshotUrl = ep.getScreenshotUrl();
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<GetCheckinListDto> getCheckinList(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();

        List<EventParticipant> eps = eventParticipantRepo.findByEvent(event);
        List<GetCheckinListDto> result = new ArrayList<>();
        for (EventParticipant ep : eps) {
            GetCheckinListDto dto = new GetCheckinListDto();
            dto.participantId = ep.getParticipant().getParticipantId();
            dto.eventId = ep.getEvent().getEventId();
            String stage = ep.getStageName();
            String display = ep.getDisplayName();
            String name = ep.getParticipant().getParticipantName();
            dto.label = (stage != null && !stage.isBlank()) ? stage
                      : (display != null && !display.isBlank()) ? display
                      : name;
            List<EventGenreParticipant> egps = eventGenreParticipantRepo
                .findByEventIdAndParticipantId(ep.getEvent().getEventId(), ep.getParticipant().getParticipantId());
            // Collect unique member names from EGP members (primary) or EventParticipantTeamMember (fallback)
            java.util.LinkedHashSet<String> memberSet = new java.util.LinkedHashSet<>();
            for (EventGenreParticipant egp : egps) {
                if (egp.getMembers() != null) {
                    egp.getMembers().stream()
                        .map(com.example.BES.models.EventGenreParticipantMember::getMemberName)
                        .filter(n -> n != null && !n.isBlank())
                        .forEach(memberSet::add);
                }
            }
            if (memberSet.isEmpty()) {
                ep.getTeamMembers().stream()
                    .map(m -> m.getMemberName())
                    .filter(n -> n != null && !n.isBlank())
                    .forEach(memberSet::add);
            }
            dto.memberNames = new ArrayList<>(memberSet);
            dto.genres = egps.stream().map(egp -> {
                GetCheckinListDto.GenreStatus gs = new GetCheckinListDto.GenreStatus();
                gs.genreName = egp.getEventGenre().getName();
                gs.auditionNumber = egp.getAuditionNumber();
                return gs;
            }).collect(Collectors.toList());
            result.add(dto);
        }
        return result;
    }

    private boolean isTeamFormat(String format) {
        if (format == null) return false;
        // Only XvX formats where X > 1 are team formats (e.g. 2v2, 3v3)
        // "7 to smoke", "solo", "1v1" are individual formats
        return format.matches("(?i)\\d+v\\d+") && !format.equalsIgnoreCase("1v1");
    }

    private String orElse(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private String resolveDisplayName(AddParticipantDto dto) {
        if (dto.getStageName() != null && !dto.getStageName().isBlank()) {
            return dto.getStageName();
        }
        return dto.getParticipantName();
    }

    private boolean isSoloBlockedForAnyGenre(List<String> genres, List<EventGenre> divisions) {
        if (genres == null) return false;
        for (String genreName : genres) {
            EventGenre eg = findMatchingDivision(divisions, genreName);
            if (eg != null && isTeamFormat(eg.getFormat()) && !eg.isSoloAllowed()) return true;
        }
        return false;
    }

    private boolean hasTeamFormatGenre(List<String> genres, List<EventGenre> divisions) {
        if (genres == null) return false;
        for (String genreName : genres) {
            EventGenre eg = findMatchingDivision(divisions, genreName);
            if (eg != null && isTeamFormat(eg.getFormat())) return true;
        }
        return false;
    }

    private String getTeamFormat(List<String> genres, List<EventGenre> divisions) {
        if (genres == null) return null;
        for (String genreName : genres) {
            EventGenre eg = findMatchingDivision(divisions, genreName);
            if (eg != null && isTeamFormat(eg.getFormat())) return eg.getFormat();
        }
        return null;
    }

    private EventGenre findMatchingDivision(List<EventGenre> divisions, String sheetCategory) {
        if (sheetCategory == null) return null;
        String categoryLower = sheetCategory.toLowerCase().trim();
        EventGenre bestMatch = null;
        int bestMatchLength = -1;
        for (EventGenre eg : divisions) {
            List<String> names = new ArrayList<>();
            names.add(eg.getName().toLowerCase().trim());
            if (eg.getSheetAliases() != null && !eg.getSheetAliases().isBlank()) {
                for (String alias : eg.getSheetAliases().split(",")) {
                    String a = alias.trim().toLowerCase();
                    if (!a.isEmpty()) names.add(a);
                }
            }
            for (String name : names) {
                if (categoryLower.contains(name) || name.contains(categoryLower)) {
                    if (name.length() > bestMatchLength) {
                        bestMatch = eg;
                        bestMatchLength = name.length();
                    }
                }
            }
        }
        return bestMatch;
    }

    private void validateTeamEntry(String format, String teamName, List<String> memberNames) {
        int required = parseFormatSize(format) - 1;
        if (teamName == null || teamName.isBlank())
            throw new IllegalArgumentException("Team name is required for team entry");
        long nonBlank = memberNames == null ? 0L
            : memberNames.stream().filter(m -> m != null && !m.isBlank()).count();
        if (nonBlank != required)
            throw new IllegalArgumentException(
                "Member count mismatch: " + format + " requires " + required + " additional member(s), got " + nonBlank);
    }

    private int parseFormatSize(String format) {
        if (format == null) return 0;
        String[] parts = format.split("v");
        try { return Integer.parseInt(parts[0]); } catch (NumberFormatException e) { return 0; }
    }
}
