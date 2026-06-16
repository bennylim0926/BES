package com.example.BES.services;

import com.example.BES.dtos.AspectScoreDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.ParticipantScoreDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventCategoryParticipantId;
import com.example.BES.models.EventCategory;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ScoreRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock ScoreRepo repo;
    @Mock EventCategoryParticipantRepo eventCategoryParticipantRepo;
    @Mock JudgeRepo judgeRepo;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks ScoreService service;

    // -----------------------------------------------------------------------
    // getAllScore
    // -----------------------------------------------------------------------

    @Test
    void getAllScore_skipsRowsWithNullJudgeOrEgp() {
        // Score 1: valid — has both judge and EGP
        Score validScore = mock(Score.class);
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        EventCategoryParticipantId egpId = new EventCategoryParticipantId(1L, 2L, 3L);
        Event event = new Event();
        event.setEventName("Spring Battle");
        EventCategory eventGenre = new EventCategory();
        eventGenre.setName("Breaking");
        Judge judge = new Judge();
        judge.setName("Jay");

        when(validScore.getJudge()).thenReturn(judge);
        when(validScore.getEventCategoryParticipant()).thenReturn(egp);
        when(egp.getId()).thenReturn(egpId);
        when(egp.getEvent()).thenReturn(event);
        when(egp.getEventCategory()).thenReturn(eventGenre);
        when(egp.getDisplayName()).thenReturn("B-Boy Spark");
        when(egp.getFormat()).thenReturn("solo");
        when(validScore.getValue()).thenReturn(8.5);
        when(validScore.getAspect()).thenReturn("Overall");

        // Score 2: invalid — null judge
        Score nullJudgeScore = mock(Score.class);
        when(nullJudgeScore.getJudge()).thenReturn(null);

        when(repo.findbyEvent("Spring Battle")).thenReturn(List.of(validScore, nullJudgeScore));

        List<GetParticipatnScoreDto> result = service.getAllScore("Spring Battle");

        assertThat(result).hasSize(1);
        GetParticipatnScoreDto dto = result.get(0);
        assertThat(dto.participantId).isEqualTo(3L);
        assertThat(dto.eventName).isEqualTo("Spring Battle");
        assertThat(dto.categoryName).isEqualTo("Breaking");
        assertThat(dto.judgeName).isEqualTo("Jay");
        assertThat(dto.participantName).isEqualTo("B-Boy Spark");
        assertThat(dto.score).isEqualTo(8.5);
        assertThat(dto.aspect).isEqualTo("Overall");
        assertThat(dto.format).isEqualTo("solo");
    }

    @Test
    void getAllScore_nullAspectBecomesEmptyString() {
        Score s = mock(Score.class);
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        EventCategoryParticipantId egpId = new EventCategoryParticipantId(1L, 2L, 4L);
        Event event = new Event();
        event.setEventName("Fest");
        EventCategory eventGenre = new EventCategory();
        eventGenre.setName("Locking");
        Judge judge = new Judge();
        judge.setName("Sam");

        when(s.getJudge()).thenReturn(judge);
        when(s.getEventCategoryParticipant()).thenReturn(egp);
        when(egp.getId()).thenReturn(egpId);
        when(egp.getEvent()).thenReturn(event);
        when(egp.getEventCategory()).thenReturn(eventGenre);
        when(egp.getDisplayName()).thenReturn("Locker A");
        when(egp.getFormat()).thenReturn("duo");
        when(s.getValue()).thenReturn(7.0);
        when(s.getAspect()).thenReturn(null);

        when(repo.findbyEvent("Fest")).thenReturn(List.of(s));

        List<GetParticipatnScoreDto> result = service.getAllScore("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).aspect).isEmpty();
    }

    // -----------------------------------------------------------------------
    // updateParticipantScoreService — single-score mode
    // -----------------------------------------------------------------------

    @Test
    void updateScore_singleScoreMode_deletesOldAndSavesNew() {
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        Judge judge = new Judge();
        judge.setName("Ray");

        when(eventCategoryParticipantRepo.findByEventCategoryParticipant("Fest", "Popping", "Alice"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findFirstByName("Ray")).thenReturn(Optional.of(judge));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Alice";
        pScore.score = 9.0;
        pScore.aspects = null; // single-score mode

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Fest";
        dto.categoryName = "Popping";
        dto.judgeName = "Ray";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo).deleteByEventCategoryParticipantAndJudge(egp, judge);
        ArgumentCaptor<Score> saved = ArgumentCaptor.forClass(Score.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo(9.0);
        assertThat(saved.getValue().getAspect()).isEmpty();
        assertThat(saved.getValue().getJudge()).isSameAs(judge);
        assertThat(saved.getValue().getEventCategoryParticipant()).isSameAs(egp);
    }

    // -----------------------------------------------------------------------
    // updateParticipantScoreService — aspect (multi-criteria) mode
    // -----------------------------------------------------------------------

    @Test
    void updateScore_aspectMode_savesPerAspect_noDeleteCalled() {
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        Judge judge = new Judge();
        judge.setName("Maya");

        when(eventCategoryParticipantRepo.findByEventCategoryParticipant("Jam", "Breaking", "Bob"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findFirstByName("Maya")).thenReturn(Optional.of(judge));

        // Both aspects have no existing score row → create new
        when(repo.findByEventCategoryParticipantAndJudgeAndAspect(egp, judge, "Musicality"))
                .thenReturn(Optional.empty());
        when(repo.findByEventCategoryParticipantAndJudgeAndAspect(egp, judge, "Creativity"))
                .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AspectScoreDto a1 = new AspectScoreDto();
        a1.aspect = "Musicality";
        a1.score = 8.0;
        AspectScoreDto a2 = new AspectScoreDto();
        a2.aspect = "Creativity";
        a2.score = 7.5;

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Bob";
        pScore.score = null;
        pScore.aspects = List.of(a1, a2);

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Jam";
        dto.categoryName = "Breaking";
        dto.judgeName = "Maya";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        // delete should never be called in aspect mode
        verify(repo, never()).deleteByEventCategoryParticipantAndJudge(any(), any());

        // Two saves — one per aspect
        ArgumentCaptor<Score> saved = ArgumentCaptor.forClass(Score.class);
        verify(repo, times(2)).save(saved.capture());
        List<Score> savedScores = saved.getAllValues();
        assertThat(savedScores).extracting(Score::getAspect)
                .containsExactlyInAnyOrder("Musicality", "Creativity");
        assertThat(savedScores).extracting(Score::getValue)
                .containsExactlyInAnyOrder(8.0, 7.5);
    }

    @Test
    void updateScore_aspectMode_updatesExistingAspectRow() {
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        Judge judge = new Judge();
        judge.setName("Nina");

        when(eventCategoryParticipantRepo.findByEventCategoryParticipant("Jam", "Waacking", "Carol"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findFirstByName("Nina")).thenReturn(Optional.of(judge));

        Score existing = new Score();
        existing.setAspect("Style");
        existing.setValue(6.0);

        when(repo.findByEventCategoryParticipantAndJudgeAndAspect(egp, judge, "Style"))
                .thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AspectScoreDto a1 = new AspectScoreDto();
        a1.aspect = "Style";
        a1.score = 9.5;

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Carol";
        pScore.aspects = List.of(a1);

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Jam";
        dto.categoryName = "Waacking";
        dto.judgeName = "Nina";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo, never()).deleteByEventCategoryParticipantAndJudge(any(), any());
        ArgumentCaptor<Score> saved = ArgumentCaptor.forClass(Score.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo(9.5);
    }

    // -----------------------------------------------------------------------
    // updateParticipantScoreService — skip when EGP or Judge not found
    // -----------------------------------------------------------------------

    @Test
    void updateScore_skipsWhenEgpNotFound() {
        when(eventCategoryParticipantRepo.findByEventCategoryParticipant("X", "Y", "Dave"))
                .thenReturn(Optional.empty());
        when(judgeRepo.findFirstByName("Tom")).thenReturn(Optional.of(new Judge()));

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Dave";
        pScore.score = 5.0;
        pScore.aspects = null;

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "X";
        dto.categoryName = "Y";
        dto.judgeName = "Tom";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo, never()).save(any());
        verify(repo, never()).deleteByEventCategoryParticipantAndJudge(any(), any());
    }

    @Test
    void updateScore_skipsWhenJudgeNotFound() {
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        when(eventCategoryParticipantRepo.findByEventCategoryParticipant("X", "Y", "Eve"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findFirstByName("Ghost")).thenReturn(Optional.empty());

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Eve";
        pScore.score = 5.0;
        pScore.aspects = null;

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "X";
        dto.categoryName = "Y";
        dto.judgeName = "Ghost";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo, never()).save(any());
        verify(repo, never()).deleteByEventCategoryParticipantAndJudge(any(), any());
    }

    // -----------------------------------------------------------------------
    // deleteScoreByEventService
    // -----------------------------------------------------------------------

    @Test
    void deleteScoreByEvent_delegatesToRepo() {
        DeleteScoreByEventDto dto = mock(DeleteScoreByEventDto.class);
        when(dto.getEvent_id()).thenReturn(42L);
        when(repo.deleteByEventId(42L)).thenReturn(5);

        Integer result = service.deleteScoreByEventService(dto);

        verify(repo).deleteByEventId(42L);
        assertThat(result).isEqualTo(5);
    }
}
