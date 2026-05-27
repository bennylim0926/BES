package com.example.BES.services;

import com.example.BES.dtos.battle.ChampionRevealDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.models.Judge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleServiceTest {

    @Mock
    JudgeService judgeService;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    BattleService service;

    @Test
    void initialPhaseIsIDLE() {
        assertThat(service.getBattlePhase()).isEqualTo("IDLE");
    }

    @Test
    void setBattlerPair_setsNamesAndTransitionsToLOCKED() {
        SetBattlerPairDto dto = mock(SetBattlerPairDto.class);
        when(dto.getLeftBattler()).thenReturn("Alice");
        when(dto.getRightBattler()).thenReturn("Bob");

        service.setBattlerPairService(dto);

        assertThat(service.getCurrentPair().getLeftBattler().getName()).isEqualTo("Alice");
        assertThat(service.getCurrentPair().getRightBattler().getName()).isEqualTo("Bob");
        assertThat(service.getBattlePhase()).isEqualTo("LOCKED");
    }

    @Test
    void setBattlePhase_cannotManuallySetREVEALED() {
        service.setBattlePhaseService("REVEALED");

        assertThat(service.getBattlePhase()).isEqualTo("IDLE");
    }

    @Test
    void setBattlePhase_setsVOTING() {
        service.setBattlePhaseService("VOTING");

        assertThat(service.getBattlePhase()).isEqualTo("VOTING");
    }

    @Test
    void setScore_returnsMinusOneWhenNoJudges() {
        // Empty judges list: score list is empty, frequency(0)==frequency(1) → tie → returns -1
        assertThat(service.setScoreService(false)).isEqualTo(-1);
    }

    @Test
    void setScore_finalTie_returnsMinusThreeToSignalBlock() {
        // empty judges → tie, isFinal=true → returns -3 (blocked)
        assertThat(service.setScoreService(true)).isEqualTo(-3);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/battle/score"), any(Map.class));
    }

    @Test
    void setScore_nonFinalTie_returnsMinusOne() {
        // empty judges → tie, isFinal=false → normal tie return -1
        assertThat(service.setScoreService(false)).isEqualTo(-1);
    }

    @Test
    void setScore_finalWinner_returnsZeroAndTransitionsToREVEALED() {
        Judge j = new Judge();
        j.setJudgeId(10L);
        j.setName("Judge_final");
        when(judgeService.getJudgeById(10L)).thenReturn(j);

        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(10L);
        service.setBattleJudgeService(jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(10L);
        when(vDto.getVote()).thenReturn(0);
        service.setVoteService(vDto);

        assertThat(service.setScoreService(true)).isEqualTo(0);
        assertThat(service.getBattlePhase()).isEqualTo("REVEALED");
    }

    @Test
    void setScore_leftWins_returnsZeroAndTransitionsToREVEALED() {
        Judge j = new Judge();
        j.setJudgeId(1L);
        j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);

        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(1L);
        service.setBattleJudgeService(jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(1L);
        when(vDto.getVote()).thenReturn(0); // vote for left
        service.setVoteService(vDto);

        Integer result = service.setScoreService(false);

        assertThat(result).isEqualTo(0);
        assertThat(service.getBattlePhase()).isEqualTo("REVEALED");
    }

    @Test
    void setScore_rightWins_returnsOne() {
        Judge j = new Judge();
        j.setJudgeId(2L);
        j.setName("Sara");
        when(judgeService.getJudgeById(2L)).thenReturn(j);

        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(2L);
        service.setBattleJudgeService(jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(2L);
        when(vDto.getVote()).thenReturn(1); // vote for right
        service.setVoteService(vDto);

        assertThat(service.setScoreService(false)).isEqualTo(1);
        assertThat(service.getBattlePhase()).isEqualTo("REVEALED");
    }

    @Test
    void setBattleJudge_returnsDuplicateCodeWhenAlreadyAdded() {
        Judge j = new Judge();
        j.setJudgeId(1L);
        j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);

        SetJudgeDto dto = mock(SetJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        service.setBattleJudgeService(dto);

        Integer result = service.setBattleJudgeService(dto);

        assertThat(result).isEqualTo(0); // already exists
    }

    @Test
    void setBattleJudge_returnsMinusOneWhenJudgeNotFound() {
        when(judgeService.getJudgeById(99L)).thenReturn(null);

        SetJudgeDto dto = mock(SetJudgeDto.class);
        when(dto.getId()).thenReturn(99L);

        assertThat(service.setBattleJudgeService(dto)).isEqualTo(-1);
    }

    @Test
    void removeBattleJudge_removesFromList() {
        Judge j = new Judge();
        j.setJudgeId(1L);
        j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);

        SetJudgeDto addDto = mock(SetJudgeDto.class);
        when(addDto.getId()).thenReturn(1L);
        service.setBattleJudgeService(addDto);
        assertThat(service.getJudges()).hasSize(1);

        SetJudgeDto removeDto = mock(SetJudgeDto.class);
        when(removeDto.getId()).thenReturn(1L);
        service.removeBattleJudgeService(removeDto);

        assertThat(service.getJudges()).isEmpty();
    }

    @Test
    void setVote_returnsMinusTwoWhenJudgeNotInList() {
        // judges list is empty, stream filter never calls dto.getId() — use lenient to avoid UnnecessaryStubbingException
        SetVoteDto dto = mock(SetVoteDto.class);
        lenient().when(dto.getId()).thenReturn(99L);

        assertThat(service.setVoteService(dto)).isEqualTo(-2);
    }

    @Test
    void getOverlayConfig_returnsDefaults() {
        Map<String, Object> config = service.getOverlayConfig();
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

        service.setOverlayConfigService(dto);

        Map<String, Object> config = service.getOverlayConfig();
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

        service.setOverlayConfigService(dto);

        verify(messagingTemplate).convertAndSend(
            eq("/topic/battle/overlay-config"),
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
        service.setBattleJudgeService(addDto);

        SetVoteDto voteDto = mock(SetVoteDto.class);
        when(voteDto.getId()).thenReturn(5L);
        when(voteDto.getVote()).thenReturn(0);
        service.setVoteService(voteDto);

        service.resetJudgeVotesService();

        assertThat(service.getJudges().get(0).getVote()).isEqualTo(-3);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            eq("/topic/battle/judges"), any(Map.class));
    }

    @Test
    void broadcastChampionReveal_sendsToCorrectTopic() {
        ChampionRevealDto dto = mock(ChampionRevealDto.class);
        when(dto.getGenreName()).thenReturn("B-Boy/B-Girl");
        when(dto.getChampionName()).thenReturn("PULSE");
        when(dto.isDismiss()).thenReturn(false);

        service.broadcastChampionReveal(dto);

        verify(messagingTemplate).convertAndSend(
            eq("/topic/battle/champion-reveal"),
            any(Map.class)
        );
    }
}
