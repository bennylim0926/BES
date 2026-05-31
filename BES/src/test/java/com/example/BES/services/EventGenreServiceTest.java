package com.example.BES.services;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetEventDivisionDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
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

    @Mock EventGenreRepo eventGenreRepo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventGenreParticpantRepo eventGenreParticipantRepo;
    @InjectMocks EventGenreService service;

    private Event event(String name) {
        Event e = new Event();
        e.setEventName(name);
        return e;
    }

    private Genre genre(Long id, String name) {
        Genre g = new Genre();
        g.setGenreId(id);
        g.setGenreName(name);
        return g;
    }

    @Test
    void getGenresByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.getGenresByEventService("Missing")).isEmpty();
    }

    @Test
    void getGenresByEvent_mapsToDto() {
        Event e = event("Fest");
        Genre g = genre(1L, "breaking");
        EventGenre eg = new EventGenre();
        eg.setGenre(g);
        eg.setName("breaking");
        eg.setFormat("1v1");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventGenreRepo.findByEvent(e)).thenReturn(List.of(eg));

        List<GetEventDivisionDto> result = service.getGenresByEventService("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("breaking");
        assertThat(result.get(0).format).isEqualTo("1v1");
    }

    private AddGenreToEventDto.Division division(String name, Long genreId) {
        AddGenreToEventDto.Division d = new AddGenreToEventDto.Division();
        d.name = name;
        d.genreId = genreId;
        return d;
    }

    @Test
    void addGenreToEvent_savesCustomDivisionWithoutGlobalGenre() {
        Event e = event("Fest");
        AddGenreToEventDto dto = new AddGenreToEventDto();
        dto.eventName = "Fest";
        dto.divisions = List.of(division("Junior Breaking", null));
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventGenreRepo.findByEventAndName(e, "Junior Breaking")).thenReturn(Optional.empty());

        service.addGenreToEventService(dto);

        verify(eventGenreRepo).save(argThat(eg -> "Junior Breaking".equals(eg.getName()) && eg.getGenre() == null));
    }

    @Test
    void addGenreToEvent_throwsOnDuplicate() {
        Event e = event("Fest");
        AddGenreToEventDto dto = new AddGenreToEventDto();
        dto.eventName = "Fest";
        dto.divisions = List.of(division("breaking", 1L));
        EventGenre existing = new EventGenre();
        existing.setEvent(e);
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventGenreRepo.findByEventAndName(e, "breaking")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addGenreToEventService(dto))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateFormat_throwsWhenEventGenreNotFound() {
        when(eventGenreRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEventGenreFormat(999L, "1v1"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateFormat_savesFormat() {
        EventGenre eg = new EventGenre();
        eg.setFormat("1v1");
        when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));

        service.updateEventGenreFormat(1L, "2v2");

        assertThat(eg.getFormat()).isEqualTo("2v2");
        verify(eventGenreRepo).save(eg);
    }

    @Test
    void renameDivision_saves() {
        EventGenre eg = new EventGenre();
        eg.setName("Old Name");
        when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));

        service.renameDivision(1L, "New Name");

        assertThat(eg.getName()).isEqualTo("New Name");
        verify(eventGenreRepo).save(eg);
    }

    @Test
    void renameDivision_throwsNotFound() {
        when(eventGenreRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renameDivision(999L, "Any"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Division not found");
    }

    @Test
    void updateAliases_saves() {
        EventGenre eg = new EventGenre();
        when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));

        service.updateAliases(1L, "hip-hop,breaking");

        assertThat(eg.getSheetAliases()).isEqualTo("hip-hop,breaking");
        verify(eventGenreRepo).save(eg);
    }

    @Test
    void updateAliases_clearsOnBlank() {
        EventGenre eg = new EventGenre();
        eg.setSheetAliases("old-alias");
        when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));

        service.updateAliases(1L, "  ");

        assertThat(eg.getSheetAliases()).isNull();
        verify(eventGenreRepo).save(eg);
    }

    @Test
    void deleteDivision_throwsNotFound() {
        when(eventGenreRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDivision(999L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Division not found");
    }

    @Test
    void deleteDivision_callsDeleteById() {
        EventGenre eg = new EventGenre();
        eg.setId(1L);
        when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));

        service.deleteDivision(1L);

        verify(eventGenreRepo).deleteById(1L);
    }
}
