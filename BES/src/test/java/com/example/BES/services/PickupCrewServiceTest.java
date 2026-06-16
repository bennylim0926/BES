package com.example.BES.services;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
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
    @Mock EventCategoryRepo eventCategoryRepo;
    @Mock ParticipantRepo participantRepo;
    @Mock EventCategoryParticipantRepo egpRepo;
    @Mock ScoreRepo scoreRepo;
    @InjectMocks PickupCrewService service;

    @Test
    void getCrewsForEventCategory_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.getCrewsForEventCategory("Missing", "breaking")).isEmpty();
    }

    @Test
    void getCrewsForEventCategory_returnsEmptyWhenNoCrews() {
        Event e = new Event(); e.setEventName("Fest");
        EventCategory cat = new EventCategory(); cat.setName("breaking");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventCategoryRepo.findByEventAndName(e, "breaking")).thenReturn(Optional.of(cat));
        when(crewRepo.findByEventAndEventCategory(e, cat)).thenReturn(List.of());

        assertThat(service.getCrewsForEventCategory("Fest", "breaking")).isEmpty();
    }

    @Test
    void deleteCrew_delegatesToRepo() {
        service.deleteCrew(1L);
        verify(crewRepo).deleteById(1L);
    }
}
