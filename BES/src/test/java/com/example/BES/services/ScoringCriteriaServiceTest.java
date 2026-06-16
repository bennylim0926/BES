package com.example.BES.services;

import com.example.BES.dtos.AddScoringCriteriaDto;
import com.example.BES.dtos.GetScoringCriteriaDto;
import com.example.BES.dtos.UpdateScoringCriteriaDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.ScoringCriteria;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.ScoringCriteriaRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringCriteriaServiceTest {

    @Mock
    ScoringCriteriaRepo repo;
    @Mock
    EventRepo eventRepo;
    @Mock
    EventCategoryRepo eventCategoryRepo;
    @InjectMocks
    ScoringCriteriaService service;

    private ScoringCriteria criteria(Long id, String name, double weight) {
        ScoringCriteria sc = new ScoringCriteria();
        sc.setId(id);
        sc.setName(name);
        sc.setWeight(weight);
        sc.setDisplayOrder(0);
        return sc;
    }

    @Test
    void getCriteria_withGenre_fallsBackToEventLevel() {
        when(repo.findByEventNameAndCategoryName("Fest", "breaking")).thenReturn(List.of());
        when(repo.findEventLevelByEventName("Fest")).thenReturn(List.of(criteria(1L, "Technique", 1.0)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", "breaking");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Technique");
    }

    @Test
    void getCriteria_withGenre_returnsGenreSpecificWhenPresent() {
        when(repo.findByEventNameAndCategoryName("Fest", "breaking"))
            .thenReturn(List.of(criteria(2L, "Musicality", 0.5)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", "breaking");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Musicality");
    }

    @Test
    void getCriteria_withoutGenre_returnsEventLevel() {
        when(repo.findEventLevelByEventName("Fest"))
            .thenReturn(List.of(criteria(1L, "Overall", 1.0)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getCriteria_blankGenreName_treatsAsNull() {
        when(repo.findEventLevelByEventName("Fest"))
            .thenReturn(List.of(criteria(1L, "Overall", 1.0)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", "");

        assertThat(result).hasSize(1);
        verify(repo, never()).findByEventNameAndCategoryName(anyString(), anyString());
    }

    @Test
    void addCriteria_throwsWhenEventNotFound() {
        AddScoringCriteriaDto dto = new AddScoringCriteriaDto();
        dto.eventName = "Missing";
        dto.name = "Technique";
        dto.weight = 1.0;
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addCriteria(dto))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addCriteria_throwsWhenGenreNotFound() {
        AddScoringCriteriaDto dto = new AddScoringCriteriaDto();
        dto.eventName = "Fest";
        dto.categoryName = "breaking";
        dto.name = "Technique";
        dto.weight = 1.0;
        Event e = new Event();
        e.setEventName("Fest");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventCategoryRepo.findByEventAndName(e, "breaking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addCriteria(dto))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addCriteria_savesWithoutGenre() {
        AddScoringCriteriaDto dto = new AddScoringCriteriaDto();
        dto.eventName = "Fest";
        dto.name = "Technique";
        dto.weight = 1.0;
        dto.categoryName = null;
        Event e = new Event();
        e.setEventName("Fest");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        ScoringCriteria saved = criteria(1L, "Technique", 1.0);
        saved.setEvent(e);
        when(repo.save(any())).thenReturn(saved);

        GetScoringCriteriaDto result = service.addCriteria(dto);

        assertThat(result.name).isEqualTo("Technique");
        verify(repo).save(any(ScoringCriteria.class));
    }

    @Test
    void addCriteria_savesWithGenre() {
        AddScoringCriteriaDto dto = new AddScoringCriteriaDto();
        dto.eventName = "Fest";
        dto.categoryName = "breaking";
        dto.name = "Musicality";
        dto.weight = 0.5;
        Event e = new Event();
        e.setEventName("Fest");
        EventCategory eg = new EventCategory();
        eg.setName("breaking");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventCategoryRepo.findByEventAndName(e, "breaking")).thenReturn(Optional.of(eg));
        ScoringCriteria saved = criteria(2L, "Musicality", 0.5);
        saved.setEvent(e);
        saved.setEventCategory(eg);
        when(repo.save(any())).thenReturn(saved);

        GetScoringCriteriaDto result = service.addCriteria(dto);

        assertThat(result.name).isEqualTo("Musicality");
        verify(repo).save(any(ScoringCriteria.class));
    }

    @Test
    void updateCriteria_throwsWhenNotFound() {
        UpdateScoringCriteriaDto dto = new UpdateScoringCriteriaDto();
        dto.name = "New";
        dto.weight = 0.5;
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCriteria(99L, dto))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateCriteria_updatesNameAndWeight() {
        UpdateScoringCriteriaDto dto = new UpdateScoringCriteriaDto();
        dto.name = "Updated";
        dto.weight = 0.75;
        ScoringCriteria existing = criteria(1L, "Old", 1.0);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenReturn(existing);

        service.updateCriteria(1L, dto);

        verify(repo).save(any(ScoringCriteria.class));
    }

    @Test
    void removeCriteria_delegatesToRepo() {
        service.removeCriteria(1L);

        verify(repo).deleteById(1L);
    }

    @Test
    void deleteAllCriteria_withGenre_deletesGenreSpecific() {
        List<ScoringCriteria> list = List.of(criteria(1L, "T", 1.0));
        when(repo.findByEventNameAndCategoryName("Fest", "breaking")).thenReturn(list);

        service.deleteAllCriteria("Fest", "breaking");

        verify(repo).deleteAll(list);
    }

    @Test
    void deleteAllCriteria_withoutGenre_deletesEventLevel() {
        List<ScoringCriteria> list = List.of(criteria(2L, "Overall", 1.0));
        when(repo.findEventLevelByEventName("Fest")).thenReturn(list);

        service.deleteAllCriteria("Fest", null);

        verify(repo).deleteAll(list);
    }
}
