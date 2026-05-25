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
        e.setAccessCode("1234");
        return e;
    }

    @Test
    void createEvent_skipsIfAlreadyExists() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "BattleFest";
        dto.accessCode = "1234";
        when(repo.findByEventName("BattleFest")).thenReturn(Optional.of(event("BattleFest")));

        service.createEventService(dto);

        verify(repo, never()).save(any());
    }

    @Test
    void createEvent_savesNewEventAndCreatesTemplate() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "NewEvent";
        dto.accessCode = "5678";
        when(repo.findByEventName("NewEvent")).thenReturn(Optional.empty());
        Event saved = event("NewEvent");
        when(repo.save(any())).thenReturn(saved);

        service.createEventService(dto);

        verify(repo).save(any(Event.class));
        verify(emailTemplateService).createDefaultTemplate(saved);
    }

    @Test
    void createEvent_generatesRandomCodeWhenInvalidProvided() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "RandEvent";
        dto.accessCode = "abc";
        when(repo.findByEventName("RandEvent")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createEventService(dto);

        verify(repo).save(argThat(e -> ((Event) e).getAccessCode().matches("\\d{4}")));
    }

    @Test
    void verifyAccessCode_trueOnMatch() {
        Event e = event("Test");
        e.setAccessCode("9999");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        assertThat(service.verifyAccessCode(1L, "9999")).isTrue();
    }

    @Test
    void verifyAccessCode_falseOnMismatch() {
        Event e = event("Test");
        e.setAccessCode("9999");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        assertThat(service.verifyAccessCode(1L, "0000")).isFalse();
    }

    @Test
    void verifyAccessCode_throwsWhenNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAccessCode(99L, "1234"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void releaseResults_setsFlag() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        service.releaseResults("Fest", true);

        assertThat(e.isResultsReleased()).isTrue();
        verify(repo).save(e);
    }

    @Test
    void releaseResults_throwsWhenNotFound() {
        when(repo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseResults("Missing", true))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateAccessCode_throwsOnInvalidCode() {
        assertThatThrownBy(() -> service.updateAccessCode(1L, "abc"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.updateAccessCode(1L, null))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateAccessCode_savesValidCode() {
        Event e = event("Fest");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        service.updateAccessCode(1L, "4321");

        assertThat(e.getAccessCode()).isEqualTo("4321");
        verify(repo).save(e);
    }

    @Test
    void getAllEvents_includesAccessCodeWhenFlagTrue() {
        Event e = event("Fest");
        when(repo.findAll()).thenReturn(List.of(e));

        List<GetEventDto> result = service.getAllEvents(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccessCode()).isEqualTo("1234");
    }

    @Test
    void getAllEvents_hidesAccessCodeWhenFlagFalse() {
        Event e = event("Fest");
        when(repo.findAll()).thenReturn(List.of(e));

        List<GetEventDto> result = service.getAllEvents(false);

        assertThat(result.get(0).getAccessCode()).isNull();
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
