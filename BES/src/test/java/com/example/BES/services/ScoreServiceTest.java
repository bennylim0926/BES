package com.example.BES.services;

import com.example.BES.dtos.AspectScoreDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.ParticipantScoreDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ScoreRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock ScoreRepo repo;
    @Mock EventGenreParticpantRepo eventGenreParticpantRepo;
    @Mock JudgeRepo judgeRepo;
    @InjectMocks ScoreService service;

    // -----------------------------------------------------------------------
    // getAllScore
    // -----------------------------------------------------------------------

    @Test
    void getAllScore_skipsRowsWithNullJudgeOrEgp() {
        // Score 1: valid — has both judge and EGP
        Score validScore = mock(Score.class);
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        EventGenreParticipantId egpId = new EventGenreParticipantId(1L, 2L, 3L);
        Event event = new Event();
        event.setEventName("Spring Battle");
        EventGenre eventGenre = new EventGenre();
        eventGenre.setName("Breaking");
        Judge judge = new Judge();
        judge.setName("Jay");

        when(validScore.getJudge()).thenReturn(judge);
        when(validScore.getEventGenreParticipant()).thenReturn(egp);
        when(egp.getId()).thenReturn(egpId);
        when(egp.getEvent()).thenReturn(event);
        when(egp.getEventGenre()).thenReturn(eventGenre);
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
        assertThat(dto.genreName).isEqualTo("Breaking");
        assertThat(dto.judgeName).isEqualTo("Jay");
        assertThat(dto.participantName).isEqualTo("B-Boy Spark");
        assertThat(dto.score).isEqualTo(8.5);
        assertThat(dto.aspect).isEqualTo("Overall");
        assertThat(dto.format).isEqualTo("solo");
    }

    @Test
    void getAllScore_nullAspectBecomesEmptyString() {
        Score s = mock(Score.class);
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        EventGenreParticipantId egpId = new EventGenreParticipantId(1L, 2L, 4L);
        Event event = new Event();
        event.setEventName("Fest");
        EventGenre eventGenre = new EventGenre();
        eventGenre.setName("Locking");
        Judge judge = new Judge();
        judge.setName("Sam");

        when(s.getJudge()).thenReturn(judge);
        when(s.getEventGenreParticipant()).thenReturn(egp);
        when(egp.getId()).thenReturn(egpId);
        when(egp.getEvent()).thenReturn(event);
        when(egp.getEventGenre()).thenReturn(eventGenre);
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
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge();
        judge.setName("Ray");

        when(eventGenreParticpantRepo.findByEventGenreParticipant("Fest", "Popping", "Alice"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Ray")).thenReturn(Optional.of(judge));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Alice";
        pScore.score = 9.0;
        pScore.aspects = null; // single-score mode

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Fest";
        dto.genreName = "Popping";
        dto.judgeName = "Ray";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo).deleteByEventGenreParticipantAndJudge(egp, judge);
        ArgumentCaptor<Score> saved = ArgumentCaptor.forClass(Score.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo(9.0);
        assertThat(saved.getValue().getAspect()).isEmpty();
        assertThat(saved.getValue().getJudge()).isSameAs(judge);
        assertThat(saved.getValue().getEventGenreParticipant()).isSameAs(egp);
    }

    // -----------------------------------------------------------------------
    // updateParticipantScoreService — aspect (multi-criteria) mode
    // -----------------------------------------------------------------------

    @Test
    void updateScore_aspectMode_savesPerAspect_noDeleteCalled() {
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge();
        judge.setName("Maya");

        when(eventGenreParticpantRepo.findByEventGenreParticipant("Jam", "Breaking", "Bob"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Maya")).thenReturn(Optional.of(judge));

        // Both aspects have no existing score row → create new
        when(repo.findByEventGenreParticipantAndJudgeAndAspect(egp, judge, "Musicality"))
                .thenReturn(Optional.empty());
        when(repo.findByEventGenreParticipantAndJudgeAndAspect(egp, judge, "Creativity"))
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
        dto.genreName = "Breaking";
        dto.judgeName = "Maya";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        // delete should never be called in aspect mode
        verify(repo, never()).deleteByEventGenreParticipantAndJudge(any(), any());

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
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge();
        judge.setName("Nina");

        when(eventGenreParticpantRepo.findByEventGenreParticipant("Jam", "Waacking", "Carol"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Nina")).thenReturn(Optional.of(judge));

        Score existing = new Score();
        existing.setAspect("Style");
        existing.setValue(6.0);

        when(repo.findByEventGenreParticipantAndJudgeAndAspect(egp, judge, "Style"))
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
        dto.genreName = "Waacking";
        dto.judgeName = "Nina";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo, never()).deleteByEventGenreParticipantAndJudge(any(), any());
        ArgumentCaptor<Score> saved = ArgumentCaptor.forClass(Score.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo(9.5);
    }

    // -----------------------------------------------------------------------
    // updateParticipantScoreService — skip when EGP or Judge not found
    // -----------------------------------------------------------------------

    @Test
    void updateScore_skipsWhenEgpNotFound() {
        when(eventGenreParticpantRepo.findByEventGenreParticipant("X", "Y", "Dave"))
                .thenReturn(Optional.empty());
        when(judgeRepo.findByName("Tom")).thenReturn(Optional.of(new Judge()));

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Dave";
        pScore.score = 5.0;
        pScore.aspects = null;

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "X";
        dto.genreName = "Y";
        dto.judgeName = "Tom";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo, never()).save(any());
        verify(repo, never()).deleteByEventGenreParticipantAndJudge(any(), any());
    }

    @Test
    void updateScore_skipsWhenJudgeNotFound() {
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        when(eventGenreParticpantRepo.findByEventGenreParticipant("X", "Y", "Eve"))
                .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Ghost")).thenReturn(Optional.empty());

        ParticipantScoreDto pScore = new ParticipantScoreDto();
        pScore.participantName = "Eve";
        pScore.score = 5.0;
        pScore.aspects = null;

        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "X";
        dto.genreName = "Y";
        dto.judgeName = "Ghost";
        dto.participantScore = List.of(pScore);

        service.updateParticipantScoreService(dto);

        verify(repo, never()).save(any());
        verify(repo, never()).deleteByEventGenreParticipantAndJudge(any(), any());
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
