package com.example.BES.services;

import com.example.BES.dtos.SubmitAuditionFeedbackDto;
import com.example.BES.dtos.admin.GetFeedbackGroupDto;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditionFeedbackServiceTest {

    @Mock AuditionFeedbackRepository feedbackRepo;
    @Mock FeedbackTagGroupRepository tagGroupRepo;
    @Mock FeedbackTagRepository tagRepo;
    @Mock EventGenreParticpantRepo egpRepo;
    @Mock JudgeRepo judgeRepo;
    @InjectMocks AuditionFeedbackService service;

    @Test
    void getAllFeedbackGroups_mapsToDto() {
        FeedbackTagGroup group = new FeedbackTagGroup();
        group.setId(1L);
        group.setName("Energy");
        FeedbackTag tag = new FeedbackTag();
        tag.setId(10L);
        tag.setLabel("High");
        tag.setGroup(group);
        group.setTags(List.of(tag));
        when(tagGroupRepo.findAll()).thenReturn(List.of(group));

        List<GetFeedbackGroupDto> result = service.getAllFeedbackGroups();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Energy");
        assertThat(result.get(0).getTags()).hasSize(1);
    }

    @Test
    void addFeedbackGroup_savesAndReturnsAll() {
        FeedbackTagGroup group = new FeedbackTagGroup();
        group.setId(1L);
        group.setName("Energy");
        group.setTags(new ArrayList<>());
        when(tagGroupRepo.findAll()).thenReturn(List.of(group));

        service.addFeedbackGroup("Energy");

        verify(tagGroupRepo).save(any(FeedbackTagGroup.class));
    }

    @Test
    void deleteFeedbackGroup_delegatesToRepo() {
        service.deleteFeedbackGroup(1L);
        verify(tagGroupRepo).deleteById(1L);
    }

    @Test
    void addFeedbackTag_returnsAllGroupsWhenGroupNotFound() {
        when(tagGroupRepo.findById(99L)).thenReturn(Optional.empty());
        when(tagGroupRepo.findAll()).thenReturn(List.of());

        List<GetFeedbackGroupDto> result = service.addFeedbackTag(99L, "label");

        verify(tagRepo, never()).save(any());
        assertThat(result).isEmpty();
    }

    @Test
    void submitFeedback_doesNothingWhenEgpOrJudgeNull() {
        SubmitAuditionFeedbackDto dto = new SubmitAuditionFeedbackDto();
        dto.setEventName("Fest");
        dto.setGenreName("breaking");
        dto.setAuditionNumber(1);
        dto.setJudgeName("Ghost");
        when(egpRepo.findByEventNameAndGenreNameAndAuditionNumber("Fest", "breaking", 1))
            .thenReturn(Optional.empty());

        service.submitFeedback(dto);

        verify(feedbackRepo, never()).save(any());
    }

    @Test
    void submitFeedback_savesWhenEgpAndJudgeFound() {
        SubmitAuditionFeedbackDto dto = new SubmitAuditionFeedbackDto();
        dto.setEventName("Fest");
        dto.setGenreName("breaking");
        dto.setAuditionNumber(1);
        dto.setJudgeName("Mike");
        dto.setTagIds(List.of());
        dto.setNote("Great energy");

        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge(); judge.setName("Mike");
        when(egpRepo.findByEventNameAndGenreNameAndAuditionNumber("Fest", "breaking", 1))
            .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Mike")).thenReturn(Optional.of(judge));
        when(feedbackRepo.findByEventGenreParticipantAndJudge(egp, judge))
            .thenReturn(Optional.empty());

        service.submitFeedback(dto);

        verify(feedbackRepo).save(any(AuditionFeedback.class));
    }

    @Test
    void getAllFeedbackForParticipant_returnsEmptyWhenEgpNotFound() {
        when(egpRepo.findByEventGenreParticipant("Fest", "breaking", "Player1"))
            .thenReturn(Optional.empty());

        assertThat(service.getAllFeedbackForParticipant("Fest", "breaking", "Player1")).isEmpty();
    }
}
