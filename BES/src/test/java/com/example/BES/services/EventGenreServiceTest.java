package com.example.BES.services;

import com.example.BES.dtos.AddCategoryToEventDto;
import com.example.BES.dtos.GetEventCategoryDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventGenreServiceTest {

    @Mock EventCategoryRepo eventCategoryRepo;
    @Mock EventRepo eventRepo;
    @Mock EventCategoryParticipantRepo eventCategoryParticipantRepo;
    @InjectMocks EventCategoryService service;

    private Event event(String name) {
        Event e = new Event();
        e.setEventName(name);
        return e;
    }

    @Test
    void getCategoriesByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.getCategoriesByEventService("Missing")).isEmpty();
    }

    @Test
    void getCategoriesByEvent_mapsToDto() {
        Event e = event("Fest");
        EventCategory ec = new EventCategory();
        ec.setId(1L);
        ec.setName("breaking");
        ec.setFormat("1v1");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventCategoryRepo.findByEvent(e)).thenReturn(List.of(ec));

        List<GetEventCategoryDto> result = service.getCategoriesByEventService("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("breaking");
        assertThat(result.get(0).format).isEqualTo("1v1");
    }

    private AddCategoryToEventDto.Category category(String name) {
        AddCategoryToEventDto.Category cat = new AddCategoryToEventDto.Category();
        cat.name = name;
        return cat;
    }

    @Test
    void addCategoryToEvent_savesNewCategory() {
        Event e = event("Fest");
        AddCategoryToEventDto dto = new AddCategoryToEventDto();
        dto.eventName = "Fest";
        dto.categories = List.of(category("Junior Breaking"));
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventCategoryRepo.findByEventAndName(e, "Junior Breaking")).thenReturn(Optional.empty());

        service.addCategoryToEventService(dto);

        verify(eventCategoryRepo).save(argThat(ec -> "Junior Breaking".equals(ec.getName())));
    }

    @Test
    void addCategoryToEvent_throwsOnDuplicate() {
        Event e = event("Fest");
        AddCategoryToEventDto dto = new AddCategoryToEventDto();
        dto.eventName = "Fest";
        dto.categories = List.of(category("breaking"));
        EventCategory existing = new EventCategory();
        existing.setEvent(e);
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventCategoryRepo.findByEventAndName(e, "breaking")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addCategoryToEventService(dto))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateFormat_throwsWhenCategoryNotFound() {
        when(eventCategoryRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEventCategoryFormat(999L, "1v1"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateFormat_savesFormat() {
        EventCategory ec = new EventCategory();
        ec.setFormat("1v1");
        when(eventCategoryRepo.findById(1L)).thenReturn(Optional.of(ec));

        service.updateEventCategoryFormat(1L, "2v2");

        assertThat(ec.getFormat()).isEqualTo("2v2");
        verify(eventCategoryRepo).save(ec);
    }

    @Test
    void renameDivision_saves() {
        EventCategory ec = new EventCategory();
        ec.setName("Old Name");
        when(eventCategoryRepo.findById(1L)).thenReturn(Optional.of(ec));

        service.renameDivision(1L, "New Name");

        assertThat(ec.getName()).isEqualTo("New Name");
        verify(eventCategoryRepo).save(ec);
    }

    @Test
    void renameDivision_throwsNotFound() {
        when(eventCategoryRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renameDivision(999L, "Any"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Category not found");
    }

    @Test
    void updateAliases_saves() {
        EventCategory ec = new EventCategory();
        when(eventCategoryRepo.findById(1L)).thenReturn(Optional.of(ec));

        service.updateAliases(1L, "hip-hop,breaking");

        assertThat(ec.getSheetAliases()).isEqualTo("hip-hop,breaking");
        verify(eventCategoryRepo).save(ec);
    }

    @Test
    void updateAliases_clearsOnBlank() {
        EventCategory ec = new EventCategory();
        ec.setSheetAliases("old-alias");
        when(eventCategoryRepo.findById(1L)).thenReturn(Optional.of(ec));

        service.updateAliases(1L, "  ");

        assertThat(ec.getSheetAliases()).isNull();
        verify(eventCategoryRepo).save(ec);
    }

    @Test
    void deleteDivision_throwsNotFound() {
        when(eventCategoryRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDivision(999L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Category not found");
    }

    @Test
    void deleteDivision_callsDeleteById() {
        EventCategory ec = new EventCategory();
        ec.setId(1L);
        when(eventCategoryRepo.findById(1L)).thenReturn(Optional.of(ec));

        service.deleteDivision(1L);

        verify(eventCategoryRepo).deleteById(1L);
    }
}
