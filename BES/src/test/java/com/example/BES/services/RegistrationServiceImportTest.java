package com.example.BES.services;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.ImportResultDto;
import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImportTest {

    @InjectMocks RegistrationService service;
    @Mock EventRepo eventRepo;
    @Mock ParticipantService participantService;
    @Mock ParticipantRepo participantRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventCategoryParticipantRepo eventGenreParticipantRepo;
    @Mock EventParticipantTeamMemberRepo teamMemberRepo;
    @Mock EventCategoryRepo eventGenreRepo;
    @Mock EventCategoryParticipantMemberRepo egpMemberRepo;
    @Mock GoogleSheetService sheetService;

    private Event mockEvent;
    private EventCategory mockEventGenre;

    @BeforeEach
    void setUp() {
        mockEvent = new Event();
        mockEvent.setEventId(1L);
        mockEvent.setEventName("TestEvent");
        mockEvent.setPaymentRequired(false);

        mockEventGenre = new EventCategory();
        mockEventGenre.setFormat("2v2");
        mockEventGenre.setName("popping");
        mockEventGenre.setSoloAllowed(false);

        when(eventRepo.findByEventName("TestEvent")).thenReturn(Optional.of(mockEvent));
        when(eventGenreRepo.findByEvent(any())).thenReturn(List.of(mockEventGenre));
    }

    @Test
    void rowWithTeamFormatGenre_missingEntryType_isSkipped() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Alice");
        participant.setStageName("Alice");
        participant.setGenres(List.of("popping"));
        participant.setEntryType(null);

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        AddParticipantToEventDto dto = new AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(1);
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0).reason).contains("entry type");
        assertThat(result.errors.get(0).name).isEqualTo("Alice");
        assertThat(result.errors.get(0).row).isEqualTo(2);
    }

    @Test
    void rowWithTeamEntry_missingTeamName_isSkipped() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Bob");
        participant.setStageName("Bob");
        participant.setGenres(List.of("popping"));
        participant.setEntryType("team");
        participant.setTeamName("");
        participant.setMemberNames(List.of("C"));

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        AddParticipantToEventDto dto = new AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(1);
        assertThat(result.errors.get(0).reason).contains("Team name");
    }

    @Test
    void soloEntry_withTeamFormatGenre_writesFormatNull() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Charlie");
        participant.setStageName("Charlie");
        participant.setGenres(List.of("popping"));
        participant.setEntryType("solo");

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        Participant saved = new Participant();
        saved.setParticipantId(99L);
        when(participantService.addParticpantService(any())).thenReturn(saved);
        when(eventParticipantRepo.findByEventAndParticipant(any(), any())).thenReturn(Optional.empty());

        AddParticipantToEventDto dto = new AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(1);
        ArgumentCaptor<EventCategoryParticipant> captor = ArgumentCaptor.forClass(EventCategoryParticipant.class);
        verify(eventGenreParticipantRepo).save(captor.capture());
        EventCategoryParticipant savedEgp = captor.getValue();
        assertThat(savedEgp.getFormat()).isNull();
        assertThat(savedEgp.getTeamName()).isNull();
    }

    @Test
    void validTeamEntry_writesEgpTeamData() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Diana");
        participant.setStageName("Diana");
        participant.setGenres(List.of("popping"));
        participant.setEntryType("team");
        participant.setTeamName("Crew D");
        participant.setMemberNames(List.of("Member1"));

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        Participant saved = new Participant();
        saved.setParticipantId(88L);
        when(participantService.addParticpantService(any())).thenReturn(saved);
        when(eventParticipantRepo.findByEventAndParticipant(any(), any())).thenReturn(Optional.empty());

        AddParticipantToEventDto dto = new AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(1);
        ArgumentCaptor<EventCategoryParticipant> captor = ArgumentCaptor.forClass(EventCategoryParticipant.class);
        verify(eventGenreParticipantRepo).save(captor.capture());
        EventCategoryParticipant savedEgp = captor.getValue();
        assertThat(savedEgp.getFormat()).isEqualTo("2v2");
        assertThat(savedEgp.getTeamName()).isEqualTo("Crew D");
        assertThat(savedEgp.getDisplayName()).isEqualTo("Crew D");
        verify(egpMemberRepo).save(any(EventCategoryParticipantMember.class));
    }

    @Test
    void rowWithMemberCountMismatch_isSkipped() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Eve");
        participant.setStageName("Eve");
        participant.setGenres(List.of("popping"));
        participant.setEntryType("team");
        participant.setTeamName("Team E");
        participant.setMemberNames(List.of());

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        AddParticipantToEventDto dto = new AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(1);
        assertThat(result.errors.get(0).reason).contains("Member count mismatch");
    }

    @Test
    void alreadyImportedParticipant_isSkippedWithoutError() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Frank");
        participant.setStageName("Frank");
        participant.setGenres(List.of("popping"));
        participant.setEntryType("solo");

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        Participant existing = new Participant();
        existing.setParticipantId(77L);
        when(participantService.addParticpantService(any())).thenReturn(existing);
        EventParticipant existingEp = new EventParticipant();
        when(eventParticipantRepo.findByEventAndParticipant(any(), any())).thenReturn(Optional.of(existingEp));

        AddParticipantToEventDto dto = new AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(0);
        assertThat(result.errors).isEmpty();
    }
}
