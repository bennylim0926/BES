package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddJudgesDto;
import com.example.BES.dtos.GetJudgeDto;
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
            dtos.add(dto);
        }
        return dtos;
    }

}
