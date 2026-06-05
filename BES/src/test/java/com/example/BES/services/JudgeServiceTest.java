package com.example.BES.services;

import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
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
class JudgeServiceTest {

    @Mock JudgeRepo judgeRepo;
    @Mock EventRepo eventRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @InjectMocks JudgeService service;

    private Judge judge(Long id, String name) {
        Judge j = new Judge();
        j.setJudgeId(id);
        j.setName(name);
        return j;
    }

    @Test
    void addJudge_savesAndReturns() {
        AddJudgeDto dto = new AddJudgeDto();
        dto.judgeName = "Mike";
        Judge saved = judge(1L, "Mike");
        when(judgeRepo.save(any())).thenReturn(saved);

        Judge result = service.addJudgeService(dto);

        assertThat(result.getName()).isEqualTo("Mike");
    }

    @Test
    void getAllJudges_mapsToDto() {
        when(judgeRepo.findAll()).thenReturn(List.of(judge(1L, "Mike")));

        List<GetJudgeDto> result = service.getAllJudges();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).judgeName).isEqualTo("Mike");
    }

    @Test
    void getJudgeById_returnsNullWhenMissing() {
        when(judgeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.getJudgeById(99L)).isNull();
    }

    @Test
    void updateJudge_updatesName() {
        Judge j = judge(1L, "Old");
        UpdateJudgeDto dto = mock(UpdateJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        when(dto.getNewName()).thenReturn("New");
        when(judgeRepo.findById(1L)).thenReturn(Optional.of(j));
        when(judgeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Judge result = service.updateJudgeService(dto);

        assertThat(result.getName()).isEqualTo("New");
    }

    @Test
    void deleteJudge_deletesAndReturnsName() {
        Judge j = judge(1L, "Mike");
        DeleteJudgeDto dto = mock(DeleteJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        when(judgeRepo.findById(1L)).thenReturn(Optional.of(j));

        String name = service.deleteJudgeService(dto);

        assertThat(name).isEqualTo("Mike");
        verify(judgeRepo).delete(j);
    }

    @Test
    void getJudgesByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetJudgeDto> result = service.getJudgesByEvent("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void addJudgeToEvent_returnsNullWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.addJudgeToEvent("Missing", "Mike")).isNull();
    }

    @Test
    void removeJudgeFromEvent_doesNothingWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        service.removeJudgeFromEvent("Missing", 1L);

        verify(eventRepo).findByEventNameIgnoreCase("Missing");
        verifyNoMoreInteractions(eventGenreRepo);
    }

    @Test
    void removeJudgeFromEvent_removesJudgeFromDivision() {
        Judge j = judge(1L, "Mike");
        Event e = new Event();
        e.setEventName("Fest");
        EventGenre eg = new EventGenre();
        eg.setId(10L);
        eg.setEvent(e);
        eg.setJudges(new ArrayList<>(List.of(j)));
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventGenreRepo.findByEvent(e)).thenReturn(List.of(eg));

        service.removeJudgeFromEvent("Fest", 1L);

        assertThat(eg.getJudges()).isEmpty();
        verify(eventGenreRepo).saveAll(List.of(eg));
    }

    @Test
    void getJudgesByDivision_returnsEmptyWhenDivisionNotFound() {
        when(eventGenreRepo.findById(99L)).thenReturn(Optional.empty());

        List<GetJudgeDto> result = service.getJudgesByDivision(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void addJudgeToDivision_savesAndAddsJudge() {
        Judge j = judge(1L, "Mike");
        Event e = new Event();
        e.setEventId(1L);
        EventGenre eg = new EventGenre();
        eg.setId(10L);
        eg.setEvent(e);
        eg.setJudges(new ArrayList<>());
        when(eventGenreRepo.findById(10L)).thenReturn(Optional.of(eg));
        when(judgeRepo.save(any())).thenReturn(j);
        when(eventGenreRepo.save(eg)).thenReturn(eg);

        List<GetJudgeDto> result = service.addJudgeToDivision(10L, "Mike");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).judgeName).isEqualTo("Mike");
    }

    @Test
    void removeJudgeFromDivision_removesJudge() {
        Judge j = judge(1L, "Mike");
        EventGenre eg = new EventGenre();
        eg.setId(10L);
        eg.setJudges(new ArrayList<>(List.of(j)));
        when(eventGenreRepo.findById(10L)).thenReturn(Optional.of(eg));
        when(eventGenreRepo.save(eg)).thenReturn(eg);

        List<GetJudgeDto> result = service.removeJudgeFromDivision(10L, 1L);

        assertThat(result).isEmpty();
    }
}
