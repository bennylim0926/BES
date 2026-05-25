package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;

@Service
public class JudgeService {
    @Autowired
    JudgeRepo judgeRepo;

    @Autowired
    EventRepo eventRepo;

    // public void addJudgesService(AddJudgesDto dto){
    //     for(String judge: dto.judges){
    //         Judge j = new Judge();
    //         j.setName(judge);
    //         judgeRepo.save(j);
    //     }
    // }

    public Judge addJudgeService(AddJudgeDto dto){
        Judge j = new Judge();
        j.setName(dto.judgeName);
        Judge judge = judgeRepo.save(j);
        return judge;
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

    @Transactional
    public List<GetJudgeDto> getJudgesByEvent(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null || event.getJudges() == null) return new ArrayList<>();
        List<GetJudgeDto> dtos = new ArrayList<>();
        for (Judge j : event.getJudges()) {
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
        Judge j = new Judge();
        j.setName(judgeName);
        j = judgeRepo.save(j);
        if (event.getJudges() == null) {
            event.setJudges(new ArrayList<>());
        }
        event.getJudges().add(j);
        eventRepo.save(event);
        return j;
    }

    @Transactional
    public void removeJudgeFromEvent(String eventName, Long judgeId) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null || event.getJudges() == null) return;
        event.getJudges().removeIf(j -> j.getJudgeId().equals(judgeId));
        eventRepo.save(event);
    }
}
