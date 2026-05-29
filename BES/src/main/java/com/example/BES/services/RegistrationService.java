package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.dtos.VerifyParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.EventParticipantTeamMember;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.utils.ReferenceCodeUtil;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class RegistrationService {

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
    EventParticipantTeamMemberRepo teamMemberRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

    public void addParticipantToEvent(AddParticipantToEventDto dto)
    throws IOException {
        Event event = eventRepo.findByEventName(dto.eventName).orElse(null);
        if (event == null) throw new NullPointerException("event is null");

        List<AddParticipantDto> importable = sheetService.getAllImportableParticipants(dto);

        for (AddParticipantDto participant : importable) {
            Participant toAddParticipant = participantService.addParticpantService(participant);
            EventParticipant ep = eventParticipantRepo
                .findByEventAndParticipant(event, toAddParticipant).orElse(null);

            if (ep != null) {
                if (!ep.isPaymentVerified()) continue;
            } else {
                // Case D: new participant — create EP and EGP entries
                ep = new EventParticipant();
                ep.setParticipant(toAddParticipant);
                ep.setEvent(event);
                ep.setStageName(participant.getStageName());
                ep.setTeamName(participant.getTeamName());
                ep.setDisplayName(resolveDisplayName(participant));
                ep.setResidency(participant.getResidency());
                ep.setGenre(participant.getGenres() != null ? String.join(", ", participant.getGenres()) : "");
                ep.setPaymentVerified(!event.isPaymentRequired());
                ep.setScreenshotUrl(participant.getScreenshotUrl());
                ep.setReferenceCode(ReferenceCodeUtil.generate());

                // Save EP before saving team members (need EP id)
                eventParticipantRepo.save(ep);

                // Save team member records
                if (participant.getMemberNames() != null && !participant.getMemberNames().isEmpty()) {
                    for (String memberName : participant.getMemberNames()) {
                        teamMemberRepo.save(new EventParticipantTeamMember(ep, memberName));
                    }
                }

                // Save EGP entries
                List<EventGenreParticipant> egps = new ArrayList<>();
                if (participant.getGenres() != null) {
                    for (String genreName : participant.getGenres()) {
                        Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
                        if (genre == null) continue;
                        EventGenreParticipantId id = new EventGenreParticipantId(
                            event.getEventId(), genre.getGenreId(), toAddParticipant.getParticipantId());
                        EventGenreParticipant egp = new EventGenreParticipant();
                        egp.setId(id);
                        egp.setEvent(event);
                        egp.setGenre(genre);
                        egp.setParticipant(toAddParticipant);

                        // Always use the format configured in the web portal (EventGenre.format)
                        // — never rely on sheet cell format strings (e.g. "Hip Hop 1v1")
                        // so all registration paths (sheet, walk-in, QR) share the same pool.
                        EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
                        String effectiveFormat = eg != null ? eg.getFormat() : null;

                        boolean isTeamEntry = isTeamFormat(effectiveFormat)
                            && ((participant.getTeamName() != null && !participant.getTeamName().isBlank())
                                || (participant.getMemberNames() != null && !participant.getMemberNames().isEmpty()));
                        String format = isTeamEntry ? effectiveFormat : (isTeamFormat(effectiveFormat) ? null : effectiveFormat);
                        egp.setFormat(format);
                        egp.setDisplayName(isTeamFormat(format)
                            ? orElse(participant.getTeamName(), participant.getParticipantName())
                            : orElse(participant.getStageName(), participant.getParticipantName()));

                        eventGenreParticipantRepo.save(egp);
                        egps.add(egp);
                    }
                }

            }
        }
    }

    public void verifyAndEmail(long participantId, long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) throw new RuntimeException("Event or participant not found");

        EventParticipant ep = eventParticipantRepo
            .findByEventAndParticipant(event, participant).orElse(null);
        if (ep == null) throw new RuntimeException("EventParticipant not found");

        ep.setPaymentVerified(true);
        eventParticipantRepo.save(ep);
    }

    public void verifyAndEmailBatch(List<VerifyParticipantDto> list) {
        for (VerifyParticipantDto item : list) {
            verifyAndEmail(item.getParticipantId(), item.getEventId());
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
                .map(egp -> egp.getGenre().getGenreName())
                .collect(Collectors.toList());
            dto.screenshotUrl = ep.getScreenshotUrl();
            result.add(dto);
        }
        return result;
    }

    /** A format is "team" if it is non-null and not "1v1". */
    private boolean isTeamFormat(String format) {
        return format != null && !format.equalsIgnoreCase("1v1");
    }

    /** Returns preferred if non-blank, otherwise fallback. */
    private String orElse(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private String resolveDisplayName(AddParticipantDto dto) {
        if (dto.getStageName() != null && !dto.getStageName().isBlank()) {
            return dto.getStageName();
        }
        return dto.getParticipantName();
    }
}
