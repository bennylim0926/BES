package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetResultsDto;
import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.FeedbackTag;
import com.example.BES.models.Score;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.ScoreRepo;

@Service
public class ResultsService {

    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventGenreParticpantRepo egpRepo;

    @Autowired
    ScoreRepo scoreRepo;

    @Autowired
    AuditionFeedbackRepository feedbackRepo;

    public GetResultsDto getResultsByRefCode(String refCode) {
        EventParticipant ep = eventParticipantRepo.findByReferenceCode(refCode).orElse(null);
        if (ep == null) return null;

        if (!ep.getEvent().isResultsReleased()) return null;

        List<EventGenreParticipant> egps = egpRepo.findByEventIdAndParticipantId(
            ep.getEvent().getEventId(),
            ep.getParticipant().getParticipantId()
        );

        List<GetResultsDto.GenreResult> genreResults = new ArrayList<>();
        for (EventGenreParticipant egp : egps) {
            List<Score> scores = scoreRepo.findByEventGenreParticipant(egp);
            List<AuditionFeedback> feedbacks = feedbackRepo.findByEventGenreParticipant(egp);

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

            genreResults.add(new GetResultsDto.GenreResult(
                egp.getGenre().getGenreName(),
                egp.getFormat(),
                egp.getAuditionNumber(),
                scoreEntries,
                feedbackEntries
            ));
        }

        return new GetResultsDto(
            ep.getDisplayName(),
            ep.getEvent().getEventName(),
            genreResults
        );
    }
}
