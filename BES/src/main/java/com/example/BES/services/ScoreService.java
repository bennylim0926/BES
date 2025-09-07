package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetParticipatnScoreDto;
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

    public List<GetParticipatnScoreDto> getAllScore(String eventName){
        List<Score> scoreList = repo.findbyEvent(eventName);
        List<GetParticipatnScoreDto> scoreListDto = new ArrayList<>();
        for(Score s : scoreList){
            GetParticipatnScoreDto dto = new GetParticipatnScoreDto();
            dto.eventName = s.getEventGenreParticipant().getEvent().getEventName();
            dto.genreName = s.getEventGenreParticipant().getGenre().getGenreName();
            dto.judgeName = s.getJudge().getName();
            dto.participantName = s.getEventGenreParticipant().getParticipant().getParticipantName();
            dto.score = s.getValue();
            scoreListDto.add(dto);
        }
        System.out.println("hiii");
        return scoreListDto;
    }

    public void updateParticipantScoreService(UpdateParticipantScoreDto dto){
        for(ParticipantScoreDto d : dto.participantScore){
            EventGenreParticipant record = eventGenreParticpantRepo.findByEventGenreParticipant(dto.eventName, dto.genreName, d.participantName).orElse(null);
            Judge judge = judgeRepo.findByName(dto.judgeName).orElse(null);
            Score score = repo.findByEventGenreParticipantAndJudge(record,judge).orElse(null);
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
    }
}
