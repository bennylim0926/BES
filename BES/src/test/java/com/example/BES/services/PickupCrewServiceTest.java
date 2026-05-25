package com.example.BES.services;

import com.example.BES.models.Event;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;
import com.example.BES.respositories.PickupCrewRepo;
import com.example.BES.respositories.ScoreRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickupCrewServiceTest {

    @Mock PickupCrewRepo crewRepo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock ParticipantRepo participantRepo;
    @Mock EventGenreParticpantRepo egpRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @Mock ScoreRepo scoreRepo;
    @InjectMocks PickupCrewService service;

    @Test
    void getCrewsForEventGenre_returnsEmptyWhenEventOrGenreNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.empty());

        assertThat(service.getCrewsForEventGenre("Missing", "breaking")).isEmpty();
    }

    @Test
    void getCrewsForEventGenre_returnsEmptyWhenNoCrews() {
        Event e = new Event(); e.setEventName("Fest");
        Genre g = new Genre(); g.setGenreName("breaking");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.of(g));
        when(crewRepo.findByEventAndGenre(e, g)).thenReturn(List.of());

        assertThat(service.getCrewsForEventGenre("Fest", "breaking")).isEmpty();
    }

    @Test
    void deleteCrew_delegatesToRepo() {
        service.deleteCrew(1L);
        verify(crewRepo).deleteById(1L);
    }
}
