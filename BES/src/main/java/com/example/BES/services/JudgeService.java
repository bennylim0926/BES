package com.example.BES.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.JudgeDivisionDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.BattleGenreState;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Judge;
import com.example.BES.respositories.BattleGenreStateRepository;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class JudgeService {
    @Autowired
    JudgeRepo judgeRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

    @Autowired
    BattleGenreStateRepository battleGenreStateRepository;

    @Autowired
    ObjectMapper objectMapper;

    public Judge addJudgeService(AddJudgeDto dto){
        return judgeRepo.findFirstByName(dto.judgeName).orElseGet(() -> {
            Judge j = new Judge();
            j.setName(dto.judgeName);
            return judgeRepo.save(j);
        });
    }

    public List<GetJudgeDto> getAllJudges(){
        List<Judge> judges = judgeRepo.findAll();
        List<GetJudgeDto> dtos = new ArrayList<>();
        for(Judge j : judges){
            GetJudgeDto dto = new GetJudgeDto();
            dto.judgeName = j.getName();
            dto.judgeId = j.getJudgeId();
            dtos.add(dto);
        }
        return dtos;
    }

    public Judge updateJudgeService(UpdateJudgeDto dto){
        Judge judge = judgeRepo.findById(dto.getId()).orElse(null);
        if(judge != null){
            judge.setName(dto.getNewName());
            judge = judgeRepo.save(judge);
        }
        return judge;
    }

    public String deleteJudgeService(DeleteJudgeDto dto){
        Judge judge = judgeRepo.findById(dto.getId()).orElse(null);
        String name = "";
        if(judge != null){
            name = judge.getName();
            judgeRepo.delete(judge);
        }
        return name;
    }

    public Judge getJudgeById(Long id){
        return judgeRepo.findById(id).orElse(null);
    }

    public List<GetJudgeDto> getJudgesByEvent(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();
        List<Judge> judges = judgeRepo.findJudgesByEventId(event.getEventId());
        List<GetJudgeDto> dtos = new ArrayList<>();
        for (Judge j : judges) {
            GetJudgeDto dto = new GetJudgeDto();
            dto.judgeName = j.getName();
            dto.judgeId = j.getJudgeId();
            dtos.add(dto);
        }
        return dtos;
    }

    @Transactional
    public Judge addJudgeToEvent(String eventName, String judgeName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return null;
        String trimmed = judgeName != null ? judgeName.trim() : "";
        if (trimmed.isEmpty()) return null;
        Judge j = judgeRepo.findFirstByName(trimmed).orElseGet(() -> {
            Judge newJ = new Judge();
            newJ.setName(trimmed);
            return judgeRepo.save(newJ);
        });
        // Add to event-level pool only — no division assignment
        judgeRepo.insertEventJudge(event.getEventId(), j.getJudgeId());
        return j;
    }

    @Transactional
    public void removeJudgeFromEvent(String eventName, Long judgeId) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return;
        // Remove from all divisions
        List<EventGenre> genres = eventGenreRepo.findByEvent(event);
        for (EventGenre eg : genres) {
            if (eg.getJudges() != null) {
                eg.getJudges().removeIf(j -> j.getJudgeId().equals(judgeId));
            }
        }
        eventGenreRepo.saveAll(genres);
        // Remove from event pool
        judgeRepo.deleteEventJudge(event.getEventId(), judgeId);
    }

    @Transactional
    public List<GetJudgeDto> getJudgesByDivision(Long divisionId) {
        EventGenre eg = eventGenreRepo.findById(divisionId).orElse(null);
        if (eg == null || eg.getJudges() == null) return new ArrayList<>();
        return eg.getJudges().stream().map(j -> {
            GetJudgeDto dto = new GetJudgeDto();
            dto.judgeName = j.getName();
            dto.judgeId = j.getJudgeId();
            return dto;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public List<GetJudgeDto> addJudgeToDivision(Long divisionId, String judgeName) {
        EventGenre eg = eventGenreRepo.findById(divisionId).orElseThrow();
        String trimmed = judgeName != null ? judgeName.trim() : "";
        if (trimmed.isEmpty()) return getJudgesByDivision(divisionId);

        Judge j = judgeRepo.findFirstByName(trimmed).orElseGet(() -> {
            Judge newJ = new Judge();
            newJ.setName(trimmed);
            return judgeRepo.save(newJ);
        });

        // Also ensure in event-level pool
        judgeRepo.insertEventJudge(eg.getEvent().getEventId(), j.getJudgeId());

        if (eg.getJudges() == null) eg.setJudges(new ArrayList<>());

        // Prevent duplicate in same division
        boolean alreadyInDivision = eg.getJudges().stream()
            .anyMatch(existing -> existing.getJudgeId().equals(j.getJudgeId()));
        if (!alreadyInDivision) {
            eg.getJudges().add(j);
            eventGenreRepo.save(eg);
        }

        return getJudgesByDivision(divisionId);
    }

    @Transactional
    public List<GetJudgeDto> assignJudgeToDivision(Long divisionId, Long judgeId) {
        EventGenre eg = eventGenreRepo.findById(divisionId).orElseThrow();
        Judge j = judgeRepo.findById(judgeId).orElse(null);
        if (j == null) return getJudgesByDivision(divisionId);

        // Also ensure in event-level pool
        judgeRepo.insertEventJudge(eg.getEvent().getEventId(), judgeId);

        if (eg.getJudges() == null) eg.setJudges(new ArrayList<>());
        boolean alreadyInDivision = eg.getJudges().stream()
            .anyMatch(existing -> existing.getJudgeId().equals(judgeId));
        if (!alreadyInDivision) {
            eg.getJudges().add(j);
            eventGenreRepo.save(eg);
        }
        return getJudgesByDivision(divisionId);
    }

    @Transactional
    public List<GetJudgeDto> removeJudgeFromDivision(Long divisionId, Long judgeId) {
        EventGenre eg = eventGenreRepo.findById(divisionId).orElseThrow();
        if (eg.getJudges() != null) eg.getJudges().removeIf(j -> j.getJudgeId().equals(judgeId));
        eventGenreRepo.save(eg);
        return getJudgesByDivision(divisionId);
    }

    public List<JudgeDivisionDto> getDivisionsByJudge(String eventName, Long judgeId) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();
        List<EventGenre> genres = eventGenreRepo.findByEvent(event);
        List<JudgeDivisionDto> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (EventGenre eg : genres) {
            // Check event-level judge assignments (set in EventDetails)
            if (eg.getJudges() != null) {
                for (Judge j : eg.getJudges()) {
                    if (j.getJudgeId().equals(judgeId)) {
                        result.add(new JudgeDivisionDto(eg.getName(), eg.getFormat()));
                        added.add(eg.getName());
                        break;
                    }
                }
            }
            // Also check battle-session judge assignments (set in BattleControl)
            if (!added.contains(eg.getName())) {
                battleGenreStateRepository.findByEventNameAndGenreName(eventName, eg.getName())
                    .ifPresent(state -> {
                        if (state.getJudgesJson() == null) return;
                        try {
                            List<Map<String, Object>> battleJudges = objectMapper.readValue(
                                state.getJudgesJson(), new TypeReference<>() {});
                            for (Map<String, Object> bj : battleJudges) {
                                Object bjId = bj.get("id");
                                if (bjId != null && Long.parseLong(bjId.toString()) == judgeId) {
                                    result.add(new JudgeDivisionDto(eg.getName(), eg.getFormat()));
                                    added.add(eg.getName());
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    });
            }
        }
        return result;
    }

}
