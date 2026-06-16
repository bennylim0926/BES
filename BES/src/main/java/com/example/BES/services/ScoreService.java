package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.AspectScoreDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.ParticipantScoreDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ScoreRepo;

@Service
public class ScoreService {
    @Autowired
    ScoreRepo repo;

    @Autowired
    EventCategoryParticipantRepo eventCategoryParticipantRepo;

    @Autowired
    JudgeRepo judgeRepo;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    // Sent after any score mutation so Score.vue (and any future live scoreboard)
    // refetches without polling. Payload is intentionally tiny — clients re-fetch
    // /score on receipt.
    private void broadcastScoreChange(String eventName, String reason) {
        if (eventName == null) return;
        messagingTemplate.convertAndSend(
            "/topic/score/" + eventName,
            Map.of("type", reason, "eventName", eventName)
        );
    }

    public List<GetParticipatnScoreDto> getAllScore(String eventName) {
        List<Score> scoreList = repo.findbyEvent(eventName);
        List<GetParticipatnScoreDto> scoreListDto = new ArrayList<>();
        for (Score s : scoreList) {
            if (s.getJudge() == null || s.getEventCategoryParticipant() == null) continue;
            GetParticipatnScoreDto dto = new GetParticipatnScoreDto();
            dto.participantId = s.getEventCategoryParticipant().getId().getParticipantId();
            dto.eventName = s.getEventCategoryParticipant().getEvent().getEventName();
            dto.genreName = s.getEventCategoryParticipant().getEventCategory().getName();
            dto.judgeName = s.getJudge().getName();
            dto.participantName = s.getEventCategoryParticipant().getDisplayName();
            dto.score = s.getValue();
            dto.aspect = s.getAspect() != null ? s.getAspect() : "";
            dto.format = s.getEventCategoryParticipant().getFormat();
            dto.auditionNumber = s.getEventCategoryParticipant().getAuditionNumber();
            scoreListDto.add(dto);
        }
        return scoreListDto;
    }

    @Transactional
    public void updateParticipantScoreService(UpdateParticipantsScoreDto dto) {
        for (ParticipantScoreDto d : dto.participantScore) {
            EventCategoryParticipant record = (d.auditionNumber != null)
                    ? eventCategoryParticipantRepo
                            .findByEventNameAndCategoryNameAndAuditionNumber(dto.eventName, dto.genreName, d.auditionNumber)
                            .orElse(null)
                    : eventCategoryParticipantRepo
                            .findByEventCategoryParticipant(dto.eventName, dto.genreName, d.participantName)
                            .orElse(null);
            Judge judge = judgeRepo.findFirstByName(dto.judgeName).orElse(null);
            if (record == null || judge == null) continue;

            if (d.aspects != null && !d.aspects.isEmpty()) {
                // Multi-criteria mode: one Score row per aspect
                for (AspectScoreDto aspectScore : d.aspects) {
                    Score score = repo.findByEventCategoryParticipantAndJudgeAndAspect(record, judge, aspectScore.aspect)
                            .orElse(null);
                    if (score == null) {
                        Score newScore = new Score();
                        newScore.setJudge(judge);
                        newScore.setEventCategoryParticipant(record);
                        newScore.setAspect(aspectScore.aspect);
                        newScore.setValue(aspectScore.score);
                        repo.save(newScore);
                    } else {
                        score.setValue(aspectScore.score);
                        repo.save(score);
                    }
                }
            } else {
                // Legacy single-score mode: delete any existing rows (could be aspect-based) then save one row
                repo.deleteByEventCategoryParticipantAndJudge(record, judge);
                Score newScore = new Score();
                newScore.setJudge(judge);
                newScore.setEventCategoryParticipant(record);
                newScore.setAspect("");
                newScore.setValue(d.score);
                repo.save(newScore);
            }
        }
        broadcastScoreChange(dto.eventName, "score-updated");
    }

    public Integer deleteScoreByEventService(DeleteScoreByEventDto dto) {
        return repo.deleteByEventId(dto.getEvent_id());
    }

    @Transactional
    public void resetScoresByJudge(String eventName, String genreName, String judgeName) {
        repo.deleteByEventNameAndGenreNameAndJudgeName(eventName, genreName, judgeName);
        broadcastScoreChange(eventName, "score-reset");
    }
}
