package com.example.BES.services;

import com.example.BES.dtos.GetResultsDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventParticipantRepo;
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
class ResultsServiceTest {

    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventCategoryParticipantRepo egpRepo;
    @Mock ScoreRepo scoreRepo;
    @Mock AuditionFeedbackRepository feedbackRepo;
    @InjectMocks ResultsService service;

    @Test
    void getResultsByRefCode_returnsNullWhenRefCodeNotFound() {
        when(eventParticipantRepo.findByReferenceCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.getResultsByRefCode("UNKNOWN")).isNull();
    }

    @Test
    void getResultsByRefCode_returnsNullWhenResultsNotReleased() {
        Event e = new Event();
        e.setResultsReleased(false);
        e.setEventName("Fest");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        Participant p = new Participant();
        ep.setParticipant(p);
        ep.setDisplayName("Player1");
        when(eventParticipantRepo.findByReferenceCode("ABC123")).thenReturn(Optional.of(ep));

        assertThat(service.getResultsByRefCode("ABC123")).isNull();
    }

    @Test
    void getResultsByRefCode_returnsResultsWhenReleased() {
        Event e = new Event();
        e.setEventId(1L);
        e.setEventName("Fest");
        e.setResultsReleased(true);
        Participant p = new Participant();
        p.setParticipantId(10L);
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setDisplayName("Player1");

        EventCategory eventCategory = new EventCategory();
        eventCategory.setName("breaking");
        EventCategoryParticipant egp = mock(EventCategoryParticipant.class);
        when(egp.getEventCategory()).thenReturn(eventCategory);
        when(egp.getFormat()).thenReturn("1v1");
        when(egp.getAuditionNumber()).thenReturn(1);

        when(eventParticipantRepo.findByReferenceCode("ABC123")).thenReturn(Optional.of(ep));
        when(egpRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of(egp));
        when(scoreRepo.findByEventCategoryParticipant(egp)).thenReturn(List.of());
        when(feedbackRepo.findByEventCategoryParticipant(egp)).thenReturn(List.of());

        GetResultsDto result = service.getResultsByRefCode("ABC123");

        assertThat(result).isNotNull();
        assertThat(result.getParticipantName()).isEqualTo("Player1");
        assertThat(result.getEventName()).isEqualTo("Fest");
        assertThat(result.getGenres()).hasSize(1);
    }
}
