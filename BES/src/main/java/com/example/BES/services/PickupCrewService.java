package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.CreatePickupCrewDto;
import com.example.BES.dtos.GetPickupCrewDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.models.PickupCrew;
import com.example.BES.models.PickupCrewMember;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;
import com.example.BES.respositories.PickupCrewRepo;
import com.example.BES.respositories.ScoreRepo;

@Service
public class PickupCrewService {

    @Autowired
    PickupCrewRepo crewRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    ParticipantRepo participantRepo;

    @Autowired
    EventGenreParticpantRepo egpRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

    @Autowired
    ScoreRepo scoreRepo;

    public List<GetPickupCrewDto> getCrewsForEventGenre(String eventName, String genreName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
        if (event == null || genre == null) return new ArrayList<>();

        List<PickupCrew> crews = crewRepo.findByEventAndGenre(event, genre);
        List<GetPickupCrewDto> result = new ArrayList<>();

        for (PickupCrew crew : crews) {
            GetPickupCrewDto dto = new GetPickupCrewDto();
            dto.id = crew.getId();
            dto.crewName = crew.getCrewName();
            dto.members = new ArrayList<>();

            double totalScore = 0;
            int scoredMembers = 0;
            boolean leaderScoreSet = false;

            for (PickupCrewMember m : crew.getMembers()) {
                GetPickupCrewDto.MemberDto memberDto = new GetPickupCrewDto.MemberDto();
                memberDto.participantId = m.getParticipant().getParticipantId();

                // Display name from EGP (solo pickup entry for this genre)
                EventGenreParticipantId egpId = new EventGenreParticipantId(
                    event.getEventId(), genre.getGenreId(), m.getParticipant().getParticipantId());
                EventGenreParticipant egp = egpRepo.findById(egpId).orElse(null);
                memberDto.displayName = egp != null ? egp.getDisplayName()
                    : m.getParticipant().getParticipantName();
                dto.members.add(memberDto);

                // Aggregate score: sum all score rows for this participant+event+genre
                if (egp != null) {
                    List<Score> scores = scoreRepo.findByEventGenreParticipant(egp);
                    if (!scores.isEmpty()) {
                        double participantTotal = scores.stream()
                            .mapToDouble(s -> s.getValue() != null ? s.getValue() : 0)
                            .sum();
                        totalScore += participantTotal;
                        scoredMembers++;
                        // First member in list is the leader
                        if (!leaderScoreSet) {
                            dto.leaderScore = Math.round((participantTotal / scores.size()) * 100.0) / 100.0;
                            leaderScoreSet = true;
                        }
                    }
                }
            }

            dto.avgScore = scoredMembers > 0 ? Math.round((totalScore / scoredMembers) * 100.0) / 100.0 : null;
            result.add(dto);
        }

        return result;
    }

    @Transactional
    public GetPickupCrewDto createCrew(CreatePickupCrewDto dto) {
        Event event = eventRepo.findByEventNameIgnoreCase(dto.eventName).orElse(null);
        Genre genre = genreRepo.findByGenreName(dto.genreName.toLowerCase()).orElse(null);
        if (event == null || genre == null) throw new RuntimeException("Event or genre not found");

        // Validate crew size matches genre format
        EventGenre eventGenre = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
        if (eventGenre != null && eventGenre.getFormat() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)v\\d+$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(eventGenre.getFormat());
            if (m.matches()) {
                int required = Integer.parseInt(m.group(1));
                if (dto.memberParticipantIds.size() != required) {
                    throw new RuntimeException("Crew size must be " + required + " for " + eventGenre.getFormat() + " format");
                }
            }
        }

        // Validate all members are solo pickup entries (format=null) and not already in a crew
        for (Long participantId : dto.memberParticipantIds) {
            EventGenreParticipantId egpId = new EventGenreParticipantId(
                event.getEventId(), genre.getGenreId(), participantId);
            EventGenreParticipant egp = egpRepo.findById(egpId).orElse(null);
            if (egp == null) throw new RuntimeException("Participant " + participantId + " is not registered in this genre");
            if (egp.getFormat() != null) throw new RuntimeException("Participant " + participantId + " is a pre-formed team entry, not a solo pickup");
            if (crewRepo.countMemberInEventGenre(event, genre, participantId) > 0) {
                throw new RuntimeException("Participant " + participantId + " is already in a crew for this genre");
            }
        }

        PickupCrew crew = new PickupCrew();
        crew.setEvent(event);
        crew.setGenre(genre);
        crew.setCrewName(dto.crewName);
        crew = crewRepo.save(crew);

        List<PickupCrewMember> members = new ArrayList<>();
        for (Long participantId : dto.memberParticipantIds) {
            Participant participant = participantRepo.findById(participantId).orElseThrow();
            PickupCrewMember member = new PickupCrewMember();
            member.setCrew(crew);
            member.setParticipant(participant);
            members.add(member);
        }
        crew.setMembers(members);
        crew = crewRepo.save(crew);

        // Return as DTO
        GetPickupCrewDto result = new GetPickupCrewDto();
        result.id = crew.getId();
        result.crewName = crew.getCrewName();
        result.members = new ArrayList<>();
        for (PickupCrewMember m : crew.getMembers()) {
            GetPickupCrewDto.MemberDto memberDto = new GetPickupCrewDto.MemberDto();
            memberDto.participantId = m.getParticipant().getParticipantId();
            EventGenreParticipantId egpId = new EventGenreParticipantId(
                event.getEventId(), genre.getGenreId(), m.getParticipant().getParticipantId());
            EventGenreParticipant egp = egpRepo.findById(egpId).orElse(null);
            memberDto.displayName = egp != null ? egp.getDisplayName() : m.getParticipant().getParticipantName();
            result.members.add(memberDto);
        }
        result.avgScore = null;
        return result;
    }

    @Transactional
    public void deleteCrew(Long crewId) {
        crewRepo.deleteById(crewId);
    }
}
