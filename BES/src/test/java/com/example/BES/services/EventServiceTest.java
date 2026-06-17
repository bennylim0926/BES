package com.example.BES.services;

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.models.Event;
import com.example.BES.respositories.EventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepo repo;
    @Mock EmailTemplateService emailTemplateService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks EventService service;

    private Event event(String name) {
        Event e = new Event();
        e.setEventName(name);
        return e;
    }

    @Test
    void createEvent_skipsIfAlreadyExists() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "BattleFest";
        when(repo.findByEventName("BattleFest")).thenReturn(Optional.of(event("BattleFest")));

        service.createEventService(dto);

        verify(repo, never()).save(any());
    }

    @Test
    void createEvent_savesNewEventAndCreatesTemplate() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "NewEvent";
        when(repo.findByEventName("NewEvent")).thenReturn(Optional.empty());
        Event saved = event("NewEvent");
        when(repo.save(any())).thenReturn(saved);

        service.createEventService(dto);

        verify(repo).save(any(Event.class));
        verify(emailTemplateService).createDefaultTemplate(saved);
    }

    @Test
    void setResultsReleaseMode_updatesMode() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        service.setResultsReleaseMode("Fest", "SCORE_ONLY");

        assertThat(e.getResultsReleaseMode()).isEqualTo("SCORE_ONLY");
        verify(repo).save(e);
        verify(messagingTemplate).convertAndSend("/topic/release-mode",
            java.util.Map.of("eventName", "Fest", "mode", "SCORE_ONLY"));
    }

    @Test
    void setResultsReleaseMode_allFourModes() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        for (String mode : new String[]{"NONE", "SCORE_ONLY", "FEEDBACK_ONLY", "BOTH"}) {
            service.setResultsReleaseMode("Fest", mode);
            assertThat(e.getResultsReleaseMode()).isEqualTo(mode);
        }
    }

    @Test
    void setResultsReleaseMode_throwsWhenNotFound() {
        when(repo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setResultsReleaseMode("Missing", "BOTH"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getResultsReleaseMode_returnsDto() {
        Event e = event("Fest");
        e.setResultsReleaseMode("BOTH");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        assertThat(service.getResultsReleaseMode("Fest").mode).isEqualTo("BOTH");
    }

    @Test
    void newEvent_defaultsToNone() {
        Event e = new Event();
        assertThat(e.getResultsReleaseMode()).isEqualTo("NONE");
    }

    @Test
    void getAllEvents_returnsAllFields() {
        Event e = event("Fest");
        e.setPaymentRequired(true);
        when(repo.findAll()).thenReturn(List.of(e));

        List<GetEventDto> result = service.getAllEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Fest");
        assertThat(result.get(0).isPaymentRequired()).isTrue();
    }

    @Test
    void setJudgingMode_updatesAndBroadcasts() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        service.setJudgingMode("Fest", "CUSTOM");

        assertThat(e.getJudgingMode()).isEqualTo("CUSTOM");
        verify(repo).save(e);
        verify(messagingTemplate).convertAndSend(eq("/topic/judging-mode/"), any(Object.class));
    }
}
