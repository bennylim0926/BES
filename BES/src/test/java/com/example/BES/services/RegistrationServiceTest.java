package com.example.BES.services;

import com.example.BES.dtos.GetCheckinListDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventCategoryParticipantMemberRepo;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.EventRepo;
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
    @Mock EventRepo eventRepo;
    @Mock ParticipantService participantService;
    @Mock ParticipantRepo participantRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventCategoryParticipantRepo eventGenreParticipantRepo;
    @Mock EventCategoryParticipantMemberRepo egpMemberRepo;
    @Mock EventParticipantTeamMemberRepo teamMemberRepo;
    @Mock EventCategoryRepo eventGenreRepo;
    @InjectMocks RegistrationService service;

    // ── getUnverifiedParticipantsFromDb ──────────────────────────────────────

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

    // ── verifyPayment ────────────────────────────────────────────────────────

    @Test
    void verifyPayment_throwsWhenEventOrParticipantNotFound() {
        when(eventRepo.findById(1L)).thenReturn(Optional.empty());
        when(participantRepo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyPayment(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyPayment_throwsWhenEventParticipantNotFound() {
        Event e = new Event(); e.setEventId(1L);
        Participant p = new Participant(); p.setParticipantId(10L);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyPayment(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyPayment_setsPaymentVerifiedAndSaves() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L);
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setPaymentVerified(false);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(ep));

        service.verifyPayment(10L, 1L);

        assertThat(ep.isPaymentVerified()).isTrue();
        verify(eventParticipantRepo).save(ep);
    }

    // ── getCheckinList ───────────────────────────────────────────────────────

    @Test
    void getCheckinList_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.getCheckinList("Missing")).isEmpty();
    }

    @Test
    void getCheckinList_usesStageNameAsLabel() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L); p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e); ep.setParticipant(p);
        ep.setStageName("StageName"); ep.setDisplayName("DisplayName");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of());

        List<GetCheckinListDto> result = service.getCheckinList("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label).isEqualTo("StageName");
        assertThat(result.get(0).participantId).isEqualTo(10L);
        assertThat(result.get(0).eventId).isEqualTo(1L);
    }

    @Test
    void getCheckinList_fallsBackToDisplayNameWhenNoStageName() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L); p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e); ep.setParticipant(p);
        ep.setStageName(null); ep.setDisplayName("DisplayName");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of());

        List<GetCheckinListDto> result = service.getCheckinList("Fest");

        assertThat(result.get(0).label).isEqualTo("DisplayName");
    }

    @Test
    void getCheckinList_includesGenreAuditionStatus() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L); p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e); ep.setParticipant(p); ep.setDisplayName("Player1");
        EventCategory eventGenre = new EventCategory(); eventGenre.setName("popping");
        EventCategoryParticipant egp = new EventCategoryParticipant();
        egp.setEventCategory(eventGenre); egp.setAuditionNumber(5);
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of(egp));

        List<GetCheckinListDto> result = service.getCheckinList("Fest");

        assertThat(result.get(0).genres).hasSize(1);
        assertThat(result.get(0).genres.get(0).genreName).isEqualTo("popping");
        assertThat(result.get(0).genres.get(0).auditionNumber).isEqualTo(5);
    }
}
