package com.example.BES.services;

import com.example.BES.models.Event;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.mapper.EventParticipantDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventParticpantServiceTest {

    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventParticipantDtoMapper eventParticipantDtoMapper;
    @InjectMocks EventParticpantService service;

    @Test
    void getAllParticipantsByEvent_returnsNullWhenEventNotFound() {
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThat(service.getAllParticipantsByEvent("Missing")).isNull();
    }

    @Test
    void getParticipantRefs_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThat(service.getParticipantRefs("Missing")).isEmpty();
    }

    @Test
    void getParticipantRefs_skipsNullReferenceCodes() {
        Event e = new Event(); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantName("Player1");
        EventParticipant ep1 = new EventParticipant();
        ep1.setDisplayName("Player1");
        ep1.setReferenceCode(null); // should be skipped
        EventParticipant ep2 = new EventParticipant();
        ep2.setDisplayName("Player2");
        ep2.setReferenceCode("ABC123");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep1, ep2));

        List<Map<String, String>> result = service.getParticipantRefs("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("referenceCode")).isEqualTo("ABC123");
    }

    @Test
    void addNewWalkIn_returnsNullWhenEventNotFound() {
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());
        Participant p = new Participant();

        assertThat(service.addNewWalkInInEventService(p, "Missing")).isNull();
    }

    @Test
    void addNewWalkIn_returnsExistingEpWhenAlreadyRegistered() {
        Event e = new Event(); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantName("Player1");
        EventParticipant existing = new EventParticipant();
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(existing));

        EventParticipant result = service.addNewWalkInInEventService(p, "Fest");

        assertThat(result).isSameAs(existing);
    }
}
