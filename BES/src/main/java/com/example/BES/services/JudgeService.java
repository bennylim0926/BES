package com.example.BES.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;

@Service
public class JudgeService {
    @Autowired
    JudgeRepo judgeRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

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
        List<EventGenre> genres = eventGenreRepo.findByEvent(event);
        List<GetJudgeDto> dtos = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (EventGenre eg : genres) {
            if (eg.getJudges() != null) {
                for (Judge j : eg.getJudges()) {
                    if (seen.add(j.getJudgeId())) {
                        GetJudgeDto dto = new GetJudgeDto();
                        dto.judgeName = j.getName();
                        dto.judgeId = j.getJudgeId();
                        dtos.add(dto);
                    }
                }
            }
        }
        return dtos;
    }

    @Transactional
    public Judge addJudgeToEvent(String eventName, String judgeName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return null;
        List<EventGenre> genres = eventGenreRepo.findByEvent(event);
        if (genres.isEmpty()) return null;
        Judge j = judgeRepo.findFirstByName(judgeName).orElseGet(() -> {
            Judge newJ = new Judge();
            newJ.setName(judgeName);
            return judgeRepo.save(newJ);
        });
        EventGenre eg = genres.get(0);
        if (eg.getJudges() == null) eg.setJudges(new ArrayList<>());
        eg.getJudges().add(j);
        eventGenreRepo.save(eg);
        return j;
    }

    @Transactional
    public void removeJudgeFromEvent(String eventName, Long judgeId) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return;
        List<EventGenre> genres = eventGenreRepo.findByEvent(event);
        for (EventGenre eg : genres) {
            if (eg.getJudges() != null) {
                eg.getJudges().removeIf(j -> j.getJudgeId().equals(judgeId));
            }
        }
        eventGenreRepo.saveAll(genres);
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
        Judge j = judgeRepo.findFirstByName(judgeName).orElseGet(() -> {
            Judge newJ = new Judge();
            newJ.setName(judgeName);
            return judgeRepo.save(newJ);
        });
        if (eg.getJudges() == null) eg.setJudges(new ArrayList<>());
        eg.getJudges().add(j);
        eventGenreRepo.save(eg);
        return getJudgesByDivision(divisionId);
    }

    @Transactional
    public List<GetJudgeDto> removeJudgeFromDivision(Long divisionId, Long judgeId) {
        EventGenre eg = eventGenreRepo.findById(divisionId).orElseThrow();
        if (eg.getJudges() != null) eg.getJudges().removeIf(j -> j.getJudgeId().equals(judgeId));
        eventGenreRepo.save(eg);
        return getJudgesByDivision(divisionId);
    }
}
