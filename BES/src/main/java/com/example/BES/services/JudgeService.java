package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddJudgesDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Judge;
import com.example.BES.respositories.JudgeRepo;

@Service
public class JudgeService {
    @Autowired
    JudgeRepo judgeRepo;

    public void addJudgesService(AddJudgesDto dto){
        for(String judge: dto.judges){
            Judge j = new Judge();
            j.setName(judge);
            judgeRepo.save(j);
        }
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
}
