package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.CreatePickupCrewDto;
import com.example.BES.dtos.GetPickupCrewDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventCategoryParticipantId;
import com.example.BES.models.Participant;
import com.example.BES.models.PickupCrew;
import com.example.BES.models.PickupCrewMember;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
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
    EventCategoryRepo eventCategoryRepo;

    @Autowired
    ParticipantRepo participantRepo;

    @Autowired
    EventCategoryParticipantRepo egpRepo;

    @Autowired
    ScoreRepo scoreRepo;

    public List<GetPickupCrewDto> getCrewsForEventCategory(String eventName, String categoryName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, categoryName).orElse(null);
        if (event == null || eventCategory == null) return new ArrayList<>();

        List<PickupCrew> crews = crewRepo.findByEventAndEventCategory(event, eventCategory);
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

                // Display name from ECP (solo pickup entry for this category)
                EventCategory eventCategoryForMembers = eventCategoryRepo.findByEventAndName(event, categoryName).orElse(null);
                Long ecId = eventCategoryForMembers != null ? eventCategoryForMembers.getId() : null;
                if (ecId == null) continue;
                EventCategoryParticipantId egpId = new EventCategoryParticipantId(
                    event.getEventId(), ecId, m.getParticipant().getParticipantId());
                EventCategoryParticipant egp = egpRepo.findById(egpId).orElse(null);
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
        EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, dto.categoryName).orElse(null);
        if (event == null || eventCategory == null) throw new RuntimeException("Event or category not found");

        // Validate crew size matches category format
        if (eventCategory.getFormat() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)v\\d+$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(eventCategory.getFormat());
            if (m.matches()) {
                int required = Integer.parseInt(m.group(1));
                if (dto.memberParticipantIds.size() != required) {
                    throw new RuntimeException("Crew size must be " + required + " for " + eventCategory.getFormat() + " format");
                }
            }
        }

        // Validate all members are solo pickup entries (format=null) and not already in a crew
        for (Long participantId : dto.memberParticipantIds) {
            EventCategory eventCategoryForMember = eventCategoryRepo.findByEventAndName(event, dto.categoryName).orElse(null);
            Long ecMemberId = eventCategoryForMember != null ? eventCategoryForMember.getId() : null;
            if (ecMemberId == null) throw new RuntimeException("Event category not found");
            EventCategoryParticipantId egpId = new EventCategoryParticipantId(
                event.getEventId(), ecMemberId, participantId);
            EventCategoryParticipant egp = egpRepo.findById(egpId).orElse(null);
            if (egp == null) throw new RuntimeException("Participant " + participantId + " is not registered in this category");
            if (egp.getFormat() != null) throw new RuntimeException("Participant " + participantId + " is a pre-formed team entry, not a solo pickup");
            if (crewRepo.countMemberInEventCategory(event, eventCategory, participantId) > 0) {
                throw new RuntimeException("Participant " + participantId + " is already in a crew for this category");
            }
        }

        PickupCrew crew = new PickupCrew();
        crew.setEvent(event);
        crew.setEventCategory(eventCategory);
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
            EventCategory eventCategoryForDto = eventCategoryRepo.findByEventAndName(event, dto.categoryName).orElse(null);
            Long ecDtoId = eventCategoryForDto != null ? eventCategoryForDto.getId() : null;
            EventCategoryParticipantId egpId = new EventCategoryParticipantId(
                event.getEventId(), ecDtoId, m.getParticipant().getParticipantId());
            EventCategoryParticipant egp = egpRepo.findById(egpId).orElse(null);
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
