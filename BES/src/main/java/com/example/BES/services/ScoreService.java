package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.ParticipantScoreDto;
import com.example.BES.dtos.UpdateParticipantScoreDto;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ScoreRepo;

@Service
public class ScoreService {
    @Autowired
    ScoreRepo repo;

    @Autowired
    EventGenreParticpantRepo eventGenreParticpantRepo;

    @Autowired
    JudgeRepo judgeRepo;

    public void updateParticipantScoreService(UpdateParticipantScoreDto dto){
        for(ParticipantScoreDto d : dto.participantScore){
            EventGenreParticipant record = eventGenreParticpantRepo.findByEventGenreParticipant(dto.eventName, dto.genreName, d.participantName).orElse(null);
            Judge judge = judgeRepo.findByName(dto.judgeName).orElse(null);
            Score score = repo.findByEventGenreParticipant(record).orElse(null);
            if(score == null){
                Score newScore = new Score();
                newScore.setJudge(judge);
                newScore.setEventGenreParticipant(record);
                newScore.setAspect("");
                newScore.setValue(d.score);
                repo.save(newScore);
            }else{
                score.setValue(d.score);
                repo.save(score);
            }
        }
        repo.findByEventGenreParticipant(null);
    }
}
