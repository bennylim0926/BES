package com.example.BES.services;

import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;
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
class RegistrationServiceTest {

    @Mock GoogleSheetService sheetService;
    @Mock MailSenderService mailService;
    @Mock EventRepo eventRepo;
    @Mock ParticipantService participantService;
    @Mock ParticipantRepo participantRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreParticpantRepo eventGenreParticipantRepo;
    @Mock EventParticipantTeamMemberRepo teamMemberRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @InjectMocks RegistrationService service;

    @Test
    void getUnverifiedParticipants_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetUnverifiedParticipantDto> result = service.getUnverifiedParticipantsFromDb("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void getUnverifiedParticipants_returnsListWhenFound() {
        Event e = new Event();
        e.setEventId(1L);
        e.setEventName("Fest");
        Participant p = new Participant();
        p.setParticipantId(10L);
        p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setDisplayName("Player1");
        ep.setScreenshotUrl("http://img.png");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEventAndPaymentVerifiedFalse(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L))
            .thenReturn(List.of());

        List<GetUnverifiedParticipantDto> result = service.getUnverifiedParticipantsFromDb("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Player1");
    }

    @Test
    void verifyAndEmail_throwsWhenEventOrParticipantNotFound() {
        when(eventRepo.findById(1L)).thenReturn(Optional.empty());
        when(participantRepo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndEmail(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyAndEmail_throwsWhenEventParticipantNotFound() {
        Event e = new Event(); e.setEventId(1L);
        Participant p = new Participant(); p.setParticipantId(10L);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndEmail(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyAndEmail_setsPaymentVerifiedAndSkipsEmailIfAlreadySent() throws Exception {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L);
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setEmailSent(true); // already sent
        ep.setPaymentVerified(false);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(ep));

        service.verifyAndEmail(10L, 1L);

        assertThat(ep.isPaymentVerified()).isTrue();
        verify(mailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
        verify(eventParticipantRepo).save(ep);
    }
}
