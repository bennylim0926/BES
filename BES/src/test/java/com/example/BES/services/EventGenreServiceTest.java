package com.example.BES.services;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
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
        eg.setFormat("1v1");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventGenreRepo.findByEvent(e)).thenReturn(List.of(eg));

        List<GetGenreDto> result = service.getGenresByEventService("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).genreName).isEqualTo("breaking");
        assertThat(result.get(0).format).isEqualTo("1v1");
    }

    @Test
    void addGenreToEvent_throwsWhenGenreNotFound() {
        Event e = event("Fest");
        AddGenreToEventDto dto = new AddGenreToEventDto();
        dto.eventName = "Fest";
        dto.genreName = List.of("unknown");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addGenreToEventService(dto))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addGenreToEvent_throwsOnDuplicate() {
        Event e = event("Fest");
        Genre g = genre(1L, "breaking");
        AddGenreToEventDto dto = new AddGenreToEventDto();
        dto.eventName = "Fest";
        dto.genreName = List.of("breaking");
        EventGenre existing = new EventGenre();
        existing.setGenre(g); // non-null genre signals duplicate
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.of(g));
        when(eventGenreRepo.findByEventAndGenre(e, g)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addGenreToEventService(dto))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateFormat_throwsWhenEventOrGenreNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEventGenreFormat("Missing", "breaking", "1v1"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateFormat_savesFormat() {
        Event e = event("Fest");
        Genre g = genre(1L, "breaking");
        EventGenre eg = new EventGenre();
        eg.setGenre(g);
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.of(g));
        when(eventGenreRepo.findByEventAndGenre(e, g)).thenReturn(Optional.of(eg));

        service.updateEventGenreFormat("Fest", "breaking", "2v2");

        assertThat(eg.getFormat()).isEqualTo("2v2");
        verify(eventGenreRepo).save(eg);
    }
}
