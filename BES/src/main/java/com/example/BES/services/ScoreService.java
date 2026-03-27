package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AspectScoreDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.ParticipantScoreDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
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

    public List<GetParticipatnScoreDto> getAllScore(String eventName) {
        List<Score> scoreList = repo.findbyEvent(eventName);
        List<GetParticipatnScoreDto> scoreListDto = new ArrayList<>();
        for (Score s : scoreList) {
            if (s.getJudge() == null || s.getEventGenreParticipant() == null) continue;
            GetParticipatnScoreDto dto = new GetParticipatnScoreDto();
            dto.eventName = s.getEventGenreParticipant().getEvent().getEventName();
            dto.genreName = s.getEventGenreParticipant().getGenre().getGenreName();
            dto.judgeName = s.getJudge().getName();
            dto.participantName = s.getEventGenreParticipant().getDisplayName();
            dto.score = s.getValue();
            dto.aspect = s.getAspect() != null ? s.getAspect() : "";
            scoreListDto.add(dto);
        }
        return scoreListDto;
    }

    public void updateParticipantScoreService(UpdateParticipantsScoreDto dto) {
        for (ParticipantScoreDto d : dto.participantScore) {
            EventGenreParticipant record = eventGenreParticpantRepo
                    .findByEventGenreParticipant(dto.eventName, dto.genreName, d.participantName).orElse(null);
            Judge judge = judgeRepo.findByName(dto.judgeName).orElse(null);
            if (record == null || judge == null) continue;

            if (d.aspects != null && !d.aspects.isEmpty()) {
                // Multi-criteria mode: one Score row per aspect
                for (AspectScoreDto aspectScore : d.aspects) {
                    Score score = repo.findByEventGenreParticipantAndJudgeAndAspect(record, judge, aspectScore.aspect)
                            .orElse(null);
                    if (score == null) {
                        Score newScore = new Score();
                        newScore.setJudge(judge);
                        newScore.setEventGenreParticipant(record);
                        newScore.setAspect(aspectScore.aspect);
                        newScore.setValue(aspectScore.score);
                        repo.save(newScore);
                    } else {
                        score.setValue(aspectScore.score);
                        repo.save(score);
                    }
                }
            } else {
                // Legacy single-score mode
                Score score = repo.findByEventGenreParticipantAndJudge(record, judge).orElse(null);
                if (score == null) {
                    Score newScore = new Score();
                    newScore.setJudge(judge);
                    newScore.setEventGenreParticipant(record);
                    newScore.setAspect("");
                    newScore.setValue(d.score);
                    repo.save(newScore);
                } else {
                    score.setValue(d.score);
                    repo.save(score);
                }
            }
        }
    }

    public Integer deleteScoreByEventService(DeleteScoreByEventDto dto) {
        return repo.deleteByEventId(dto.getEvent_id());
    }
}
