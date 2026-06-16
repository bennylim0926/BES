package com.example.BES.services;

import com.example.BES.dtos.battle.ChampionRevealDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.dtos.battle.UpdateJudgeWeightageDto;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleServiceTest {

    private static final String E = "TestEvent";

    @Mock
    JudgeService judgeService;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    EventRepo eventRepo;

    @InjectMocks
    BattleService service;

    @Test
    void initialPhaseIsIDLE() {
        assertThat(service.getBattlePhase(E)).isEqualTo("IDLE");
    }

    @Test
    void setBattlerPair_setsNamesAndTransitionsToLOCKED() {
        SetBattlerPairDto dto = mock(SetBattlerPairDto.class);
        when(dto.getLeftBattler()).thenReturn("Alice");
        when(dto.getRightBattler()).thenReturn("Bob");

        service.setBattlerPairService(E, dto);

        assertThat(service.getCurrentPair(E).getLeftBattler().getName()).isEqualTo("Alice");
        assertThat(service.getCurrentPair(E).getRightBattler().getName()).isEqualTo("Bob");
        assertThat(service.getBattlePhase(E)).isEqualTo("LOCKED");
    }

    @Test
    void setBattlePhase_cannotManuallySetREVEALED() {
        service.setBattlePhaseService(E, "REVEALED");

        assertThat(service.getBattlePhase(E)).isEqualTo("IDLE");
    }

    @Test
    void setBattlePhase_setsVOTING() {
        service.setBattlePhaseService(E, "VOTING");

        assertThat(service.getBattlePhase(E)).isEqualTo("VOTING");
    }

    @Test
    void setScore_returnsMinusOneWhenNoJudges() {
        assertThat(service.setScoreService(E, false)).isEqualTo(-1);
    }

    @Test
    void setScore_finalTie_returnsMinusThreeToSignalBlock() {
        assertThat(service.setScoreService(E, true)).isEqualTo(-3);
        verify(messagingTemplate, never()).convertAndSend(
            eq("/topic/battle/" + E + "/score"), any(Map.class));
    }

    @Test
    void setScore_nonFinalTie_returnsMinusOne() {
        assertThat(service.setScoreService(E, false)).isEqualTo(-1);
    }

    @Test
    void setScore_finalWinner_returnsZeroAndTransitionsToREVEALED() {
        Judge j = new Judge();
        j.setJudgeId(10L);
        j.setName("Judge_final");
        when(judgeService.getJudgeById(10L)).thenReturn(j);

        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(10L);
        service.setBattleJudgeService(E, jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(10L);
        when(vDto.getVote()).thenReturn(0);
        service.setVoteService(E, vDto);

        assertThat(service.setScoreService(E, true)).isEqualTo(0);
        assertThat(service.getBattlePhase(E)).isEqualTo("REVEALED");
    }

    @Test
    void setScore_leftWins_returnsZeroAndTransitionsToREVEALED() {
        Judge j = new Judge();
        j.setJudgeId(1L);
        j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);

        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(1L);
        service.setBattleJudgeService(E, jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(1L);
        when(vDto.getVote()).thenReturn(0);
        service.setVoteService(E, vDto);

        Integer result = service.setScoreService(E, false);

        assertThat(result).isEqualTo(0);
        assertThat(service.getBattlePhase(E)).isEqualTo("REVEALED");
    }

    @Test
    void setScore_rightWins_returnsOne() {
        Judge j = new Judge();
        j.setJudgeId(2L);
        j.setName("Sara");
        when(judgeService.getJudgeById(2L)).thenReturn(j);

        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(2L);
        service.setBattleJudgeService(E, jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(2L);
        when(vDto.getVote()).thenReturn(1);
        service.setVoteService(E, vDto);

        assertThat(service.setScoreService(E, false)).isEqualTo(1);
        assertThat(service.getBattlePhase(E)).isEqualTo("REVEALED");
    }

    @Test
    void setBattleJudge_returnsDuplicateCodeWhenAlreadyAdded() {
        Judge j = new Judge();
        j.setJudgeId(1L);
        j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);

        SetJudgeDto dto = mock(SetJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        service.setBattleJudgeService(E, dto);

        Integer result = service.setBattleJudgeService(E, dto);

        assertThat(result).isEqualTo(0);
    }

    @Test
    void setBattleJudge_returnsMinusOneWhenJudgeNotFound() {
        when(judgeService.getJudgeById(99L)).thenReturn(null);

        SetJudgeDto dto = mock(SetJudgeDto.class);
        when(dto.getId()).thenReturn(99L);

        assertThat(service.setBattleJudgeService(E, dto)).isEqualTo(-1);
    }

    @Test
    void removeBattleJudge_removesFromList() {
        Judge j = new Judge();
        j.setJudgeId(1L);
        j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);

        SetJudgeDto addDto = mock(SetJudgeDto.class);
        when(addDto.getId()).thenReturn(1L);
        service.setBattleJudgeService(E, addDto);
        assertThat(service.getJudges(E)).hasSize(1);

        SetJudgeDto removeDto = mock(SetJudgeDto.class);
        when(removeDto.getId()).thenReturn(1L);
        service.removeBattleJudgeService(E, removeDto);

        assertThat(service.getJudges(E)).isEmpty();
    }

    @Test
    void setVote_returnsMinusTwoWhenJudgeNotInList() {
        SetVoteDto dto = mock(SetVoteDto.class);
        lenient().when(dto.getId()).thenReturn(99L);

        assertThat(service.setVoteService(E, dto)).isEqualTo(-2);
    }

    @Test
    void getOverlayConfig_returnsDefaults() {
        Map<String, Object> config = service.getOverlayConfig(E);
        assertThat(config.get("showImages")).isEqualTo(true);
        assertThat(config.get("leftColor")).isEqualTo("#dc2626");
        assertThat(config.get("rightColor")).isEqualTo("#2563eb");
    }

    @Test
    void setOverlayConfigService_updatesInMemoryState() {
        SetOverlayConfigDto dto = mock(SetOverlayConfigDto.class);
        when(dto.isShowImages()).thenReturn(false);
        when(dto.getLeftColor()).thenReturn("#ff0000");
        when(dto.getRightColor()).thenReturn("#0000ff");

        service.setOverlayConfigService(E, dto);

        Map<String, Object> config = service.getOverlayConfig(E);
        assertThat(config.get("showImages")).isEqualTo(false);
        assertThat(config.get("leftColor")).isEqualTo("#ff0000");
        assertThat(config.get("rightColor")).isEqualTo("#0000ff");
    }

    @Test
    void setOverlayConfigService_broadcastsToWebSocket() {
        SetOverlayConfigDto dto = mock(SetOverlayConfigDto.class);
        when(dto.isShowImages()).thenReturn(true);
        when(dto.getLeftColor()).thenReturn("#aabbcc");
        when(dto.getRightColor()).thenReturn("#112233");

        service.setOverlayConfigService(E, dto);

        verify(messagingTemplate).convertAndSend(
            eq("/topic/battle/" + E + "/overlay-config"),
            any(Map.class)
        );
    }

    @Test
    void resetJudgeVotes_setsAllVotesToMinusThreeAndBroadcasts() {
        Judge j = new Judge();
        j.setJudgeId(5L);
        j.setName("Alex");
        when(judgeService.getJudgeById(5L)).thenReturn(j);

        SetJudgeDto addDto = mock(SetJudgeDto.class);
        when(addDto.getId()).thenReturn(5L);
        service.setBattleJudgeService(E, addDto);

        SetVoteDto voteDto = mock(SetVoteDto.class);
        when(voteDto.getId()).thenReturn(5L);
        when(voteDto.getVote()).thenReturn(0);
        service.setVoteService(E, voteDto);

        service.resetJudgeVotesService(E);

        assertThat(service.getJudges(E).get(0).getVote()).isEqualTo(-3);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            eq("/topic/battle/" + E + "/judges"), any(Map.class));
    }

    @Test
    void setBattlerPair_withIsFinalTrue_persistsFlag() {
        SetBattlerPairDto dto = mock(SetBattlerPairDto.class);
        when(dto.getLeftBattler()).thenReturn("W9");
        when(dto.getRightBattler()).thenReturn("W10");
        when(dto.isFinal()).thenReturn(true);

        service.setBattlerPairService(E, dto);

        assertThat(service.isCurrentFinal(E)).isTrue();
    }

    @Test
    void setBattlerPair_withIsFinalFalse_persistsFlag() {
        SetBattlerPairDto dto = mock(SetBattlerPairDto.class);
        when(dto.getLeftBattler()).thenReturn("Alice");
        when(dto.getRightBattler()).thenReturn("Bob");
        when(dto.isFinal()).thenReturn(false);

        service.setBattlerPairService(E, dto);

        assertThat(service.isCurrentFinal(E)).isFalse();
    }

    @Test
    void setBattlerPair_broadcastsIsFinalInPayload() {
        SetBattlerPairDto dto = mock(SetBattlerPairDto.class);
        when(dto.getLeftBattler()).thenReturn("W9");
        when(dto.getRightBattler()).thenReturn("W10");
        when(dto.isFinal()).thenReturn(true);

        service.setBattlerPairService(E, dto);

        verify(messagingTemplate).convertAndSend(
            eq("/topic/battle/" + E + "/battle-pair"),
            argThat((Map<String, Object> m) -> Boolean.TRUE.equals(m.get("isFinal")))
        );
    }

    @Test
    void broadcastChampionReveal_sendsToCorrectTopic() {
        ChampionRevealDto dto = mock(ChampionRevealDto.class);
        when(dto.getCategoryName()).thenReturn("B-Boy/B-Girl");
        when(dto.getChampionName()).thenReturn("PULSE");
        when(dto.isDismiss()).thenReturn(false);

        service.broadcastChampionReveal(E, dto);

        verify(messagingTemplate).convertAndSend(
            eq("/topic/battle/" + E + "/champion-reveal"),
            any(Map.class)
        );
    }

    @Test
    void setScore_heavierJudgeWins_overrulesHeadcount() {
        Judge judgeA = new Judge(); judgeA.setJudgeId(10L); judgeA.setName("A");
        Judge judgeB = new Judge(); judgeB.setJudgeId(11L); judgeB.setName("B");
        when(judgeService.getJudgeById(10L)).thenReturn(judgeA);
        when(judgeService.getJudgeById(11L)).thenReturn(judgeB);

        SetJudgeDto dtoA = mock(SetJudgeDto.class);
        when(dtoA.getId()).thenReturn(10L);
        when(dtoA.getWeightage()).thenReturn(2);
        service.setBattleJudgeService(E, dtoA);

        SetJudgeDto dtoB = mock(SetJudgeDto.class);
        when(dtoB.getId()).thenReturn(11L);
        when(dtoB.getWeightage()).thenReturn(1);
        service.setBattleJudgeService(E, dtoB);

        SetVoteDto vA = mock(SetVoteDto.class); when(vA.getId()).thenReturn(10L); when(vA.getVote()).thenReturn(0);
        SetVoteDto vB = mock(SetVoteDto.class); when(vB.getId()).thenReturn(11L); when(vB.getVote()).thenReturn(1);
        service.setVoteService(E, vA);
        service.setVoteService(E, vB);

        assertThat(service.setScoreService(E, false)).isEqualTo(0);
    }

    @Test
    void setScore_equalWeightedVotes_returnsTie() {
        Judge judgeA = new Judge(); judgeA.setJudgeId(12L); judgeA.setName("C");
        Judge judgeB = new Judge(); judgeB.setJudgeId(13L); judgeB.setName("D");
        when(judgeService.getJudgeById(12L)).thenReturn(judgeA);
        when(judgeService.getJudgeById(13L)).thenReturn(judgeB);

        SetJudgeDto dtoA = mock(SetJudgeDto.class);
        when(dtoA.getId()).thenReturn(12L);
        when(dtoA.getWeightage()).thenReturn(2);
        service.setBattleJudgeService(E, dtoA);

        SetJudgeDto dtoB = mock(SetJudgeDto.class);
        when(dtoB.getId()).thenReturn(13L);
        when(dtoB.getWeightage()).thenReturn(2);
        service.setBattleJudgeService(E, dtoB);

        SetVoteDto vA = mock(SetVoteDto.class); when(vA.getId()).thenReturn(12L); when(vA.getVote()).thenReturn(0);
        SetVoteDto vB = mock(SetVoteDto.class); when(vB.getId()).thenReturn(13L); when(vB.getVote()).thenReturn(1);
        service.setVoteService(E, vA);
        service.setVoteService(E, vB);

        assertThat(service.setScoreService(E, false)).isEqualTo(-1);
    }

    @Test
    void updateJudgeWeightage_setsNewWeightageAndBroadcasts() {
        Judge j = new Judge(); j.setJudgeId(14L); j.setName("WeightJudge");
        when(judgeService.getJudgeById(14L)).thenReturn(j);

        SetJudgeDto addDto = mock(SetJudgeDto.class);
        when(addDto.getId()).thenReturn(14L);
        service.setBattleJudgeService(E, addDto);

        UpdateJudgeWeightageDto updateDto = mock(UpdateJudgeWeightageDto.class);
        when(updateDto.getId()).thenReturn(14L);
        when(updateDto.getWeightage()).thenReturn(3);
        service.updateJudgeWeightageService(E, updateDto);

        assertThat(service.getJudges(E).get(0).getWeightage()).isEqualTo(3);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            eq("/topic/battle/" + E + "/judges"), any(Map.class));
    }

    @Test
    void twoEvents_statesAreIsolated() {
        SetBattlerPairDto dtoA = mock(SetBattlerPairDto.class);
        when(dtoA.getLeftBattler()).thenReturn("Alice");
        when(dtoA.getRightBattler()).thenReturn("Bob");
        lenient().when(dtoA.getLeftMembers()).thenReturn(new java.util.ArrayList<>());
        lenient().when(dtoA.getRightMembers()).thenReturn(new java.util.ArrayList<>());

        SetBattlerPairDto dtoB = mock(SetBattlerPairDto.class);
        when(dtoB.getLeftBattler()).thenReturn("Charlie");
        when(dtoB.getRightBattler()).thenReturn("Dave");
        lenient().when(dtoB.getLeftMembers()).thenReturn(new java.util.ArrayList<>());
        lenient().when(dtoB.getRightMembers()).thenReturn(new java.util.ArrayList<>());

        service.setBattlerPairService("EventAlpha", dtoA);
        service.setBattlerPairService("EventBeta", dtoB);

        assertThat(service.getCurrentPair("EventAlpha").getLeftBattler().getName()).isEqualTo("Alice");
        assertThat(service.getCurrentPair("EventBeta").getLeftBattler().getName()).isEqualTo("Charlie");
        assertThat(service.getBattlePhase("EventAlpha")).isEqualTo("LOCKED");
        assertThat(service.getBattlePhase("EventBeta")).isEqualTo("LOCKED");
    }
}
