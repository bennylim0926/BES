package com.example.BES.services;

import com.example.BES.dtos.GetAuditionFeedbackDto;
import com.example.BES.dtos.GetParticipantFeedbackDto;
import com.example.BES.dtos.SubmitAuditionFeedbackDto;
import com.example.BES.dtos.admin.GetFeedbackGroupDto;
import com.example.BES.dtos.admin.GetFeedbackTagDto;
import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.FeedbackTag;
import com.example.BES.models.FeedbackTagGroup;
import com.example.BES.models.Judge;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.FeedbackTagGroupRepository;
import com.example.BES.respositories.FeedbackTagRepository;
import com.example.BES.respositories.JudgeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuditionFeedbackService {

    @Autowired
    AuditionFeedbackRepository feedbackRepo;

    @Autowired
    FeedbackTagGroupRepository tagGroupRepo;

    @Autowired
    FeedbackTagRepository tagRepo;

    @Autowired
    EventGenreParticpantRepo egpRepo;

    @Autowired
    JudgeRepo judgeRepo;

    public List<GetFeedbackGroupDto> getAllFeedbackGroups() {
        List<FeedbackTagGroup> groups = tagGroupRepo.findAll();
        List<GetFeedbackGroupDto> result = new ArrayList<>();
        for (FeedbackTagGroup g : groups) {
            List<GetFeedbackTagDto> tagDtos = new ArrayList<>();
            for (FeedbackTag t : g.getTags()) {
                tagDtos.add(new GetFeedbackTagDto(t.getId(), t.getLabel(), g.getId()));
            }
            result.add(new GetFeedbackGroupDto(g.getId(), g.getName(), tagDtos));
        }
        return result;
    }

    public List<GetFeedbackGroupDto> addFeedbackGroup(String name) {
        FeedbackTagGroup group = new FeedbackTagGroup();
        group.setName(name);
        tagGroupRepo.save(group);
        return getAllFeedbackGroups();
    }

    public void deleteFeedbackGroup(Long id) {
        tagGroupRepo.deleteById(id);
    }

    public List<GetFeedbackGroupDto> addFeedbackTag(Long groupId, String label) {
        FeedbackTagGroup group = tagGroupRepo.findById(groupId).orElse(null);
        if (group == null) return getAllFeedbackGroups();
        FeedbackTag tag = new FeedbackTag();
        tag.setLabel(label);
        tag.setGroup(group);
        tagRepo.save(tag);
        return getAllFeedbackGroups();
    }

    public void deleteFeedbackTag(Long id) {
        tagRepo.deleteById(id);
    }

    public void submitFeedback(SubmitAuditionFeedbackDto dto) {
        EventGenreParticipant egp = egpRepo.findByEventNameAndGenreNameAndAuditionNumber(
            dto.getEventName(), dto.getGenreName(), dto.getAuditionNumber()
        ).orElse(null);
        Judge judge = judgeRepo.findByName(dto.getJudgeName()).orElse(null);

        if (egp == null || judge == null) return;

        AuditionFeedback feedback = feedbackRepo
            .findByEventGenreParticipantAndJudge(egp, judge)
            .orElse(new AuditionFeedback());

        Set<FeedbackTag> selectedTags = new HashSet<>();
        if (dto.getTagIds() != null) {
            for (Long tagId : dto.getTagIds()) {
                tagRepo.findById(tagId).ifPresent(selectedTags::add);
            }
        }

        feedback.setEventGenreParticipant(egp);
        feedback.setJudge(judge);
        feedback.setTags(selectedTags);
        feedback.setNote(dto.getNote());
        feedbackRepo.save(feedback);
    }

    public List<GetParticipantFeedbackDto> getAllFeedbackForParticipant(
            String eventName, String genreName, String participantName) {
        EventGenreParticipant egp = egpRepo.findByEventGenreParticipant(eventName, genreName, participantName).orElse(null);
        if (egp == null) return new ArrayList<>();

        List<AuditionFeedback> feedbacks = feedbackRepo.findByEventGenreParticipant(egp);
        List<GetParticipantFeedbackDto> result = new ArrayList<>();
        for (AuditionFeedback f : feedbacks) {
            List<GetParticipantFeedbackDto.TagEntry> tagEntries = new ArrayList<>();
            for (FeedbackTag t : f.getTags()) {
                tagEntries.add(new GetParticipantFeedbackDto.TagEntry(t.getLabel(), t.getGroup().getName()));
            }
            result.add(new GetParticipantFeedbackDto(f.getJudge().getName(), tagEntries, f.getNote()));
        }
        return result;
    }

    public GetAuditionFeedbackDto getFeedback(String eventName, String genreName,
                                               String judgeName, Integer auditionNumber) {
        EventGenreParticipant egp = egpRepo.findByEventNameAndGenreNameAndAuditionNumber(
            eventName, genreName, auditionNumber
        ).orElse(null);
        Judge judge = judgeRepo.findByName(judgeName).orElse(null);

        if (egp == null || judge == null) return new GetAuditionFeedbackDto(List.of(), null);

        Optional<AuditionFeedback> feedback = feedbackRepo.findByEventGenreParticipantAndJudge(egp, judge);
        if (feedback.isEmpty()) return new GetAuditionFeedbackDto(List.of(), null);

        AuditionFeedback f = feedback.get();
        List<Long> tagIds = new ArrayList<>();
        for (FeedbackTag t : f.getTags()) {
            tagIds.add(t.getId());
        }
        return new GetAuditionFeedbackDto(tagIds, f.getNote());
    }
}
