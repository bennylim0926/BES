package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetResultsDto;
import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.FeedbackTag;
import com.example.BES.models.Score;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.ScoreRepo;

@Service
public class ResultsService {

    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventCategoryParticipantRepo egpRepo;

    @Autowired
    ScoreRepo scoreRepo;

    @Autowired
    AuditionFeedbackRepository feedbackRepo;

    public GetResultsDto getResultsByRefCode(String refCode) {
        EventParticipant ep = eventParticipantRepo.findByReferenceCode(refCode).orElse(null);
        if (ep == null) return null;

        String mode = ep.getEvent().getResultsReleaseMode();
        if ("NONE".equals(mode)) return null;

        List<EventCategoryParticipant> egps = egpRepo.findByEventIdAndParticipantId(
            ep.getEvent().getEventId(),
            ep.getParticipant().getParticipantId()
        );

        List<GetResultsDto.CategoryResult> categoryResults = new ArrayList<>();
        for (EventCategoryParticipant egp : egps) {
            List<Score> scores = scoreRepo.findByEventCategoryParticipant(egp);
            List<AuditionFeedback> feedbacks = feedbackRepo.findByEventCategoryParticipant(egp);

            List<GetResultsDto.ScoreEntry> scoreEntries = scores.stream()
                .filter(s -> s.getJudge() != null)
                .map(s -> new GetResultsDto.ScoreEntry(
                        s.getJudge().getName(),
                        s.getValue(),
                        s.getAspect() != null ? s.getAspect() : ""))
                .collect(Collectors.toList());

            List<GetResultsDto.FeedbackEntry> feedbackEntries = new ArrayList<>();
            for (AuditionFeedback f : feedbacks) {
                List<GetResultsDto.TagEntry> tagEntries = new ArrayList<>();
                for (FeedbackTag t : f.getTags()) {
                    tagEntries.add(new GetResultsDto.TagEntry(t.getLabel(), t.getGroup().getName()));
                }
                feedbackEntries.add(new GetResultsDto.FeedbackEntry(
                    f.getJudge().getName(), tagEntries, f.getNote()));
            }

            categoryResults.add(new GetResultsDto.CategoryResult(
                egp.getEventCategory().getName(),
                egp.getFormat(),
                egp.getAuditionNumber(),
                scoreEntries,
                feedbackEntries
            ));
        }

        return new GetResultsDto(
            ep.getDisplayName(),
            ep.getEvent().getEventName(),
            mode,
            categoryResults
        );
    }

    // Check if ref code exists (for validation purposes)
    public boolean refCodeExists(String refCode) {
        return eventParticipantRepo.findByReferenceCode(refCode).isPresent();
    }
}
