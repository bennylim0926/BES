package com.example.BES.services;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.models.Participant;
import com.example.BES.respositories.ParticipantRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock ParticipantRepo repo;
    @InjectMocks ParticipantService service;

    @Test
    void addParticipant_returnsExistingWhenEmailMatches() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.participantEmail = "test@example.com";
        dto.participantName = "Test";
        Participant existing = new Participant();
        existing.setParticipantEmail("test@example.com");
        when(repo.findByParticipantEmail("test@example.com")).thenReturn(Optional.of(existing));

        Participant result = service.addParticpantService(dto);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void addParticipant_createsNewWhenEmailNotFound() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.participantEmail = "new@example.com";
        dto.participantName = "New Person";
        when(repo.findByParticipantEmail("new@example.com")).thenReturn(Optional.empty());
        Participant saved = new Participant();
        when(repo.save(any())).thenReturn(saved);

        Participant result = service.addParticpantService(dto);

        assertThat(result).isSameAs(saved);
        verify(repo).save(any(Participant.class));
    }

    @Test
    void addParticipant_savesWhenEmailIsNull() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.participantEmail = null;
        dto.participantName = "Walk-in";
        Participant saved = new Participant();
        when(repo.save(any())).thenReturn(saved);

        service.addParticpantService(dto);

        verify(repo).save(any(Participant.class));
    }

    @Test
    void addWalkIn_returnsExistingWhenNameMatches() {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name = "Existing";
        Participant existing = new Participant();
        existing.setParticipantName("Existing");
        when(repo.findByParticipantName("Existing")).thenReturn(Optional.of(existing));

        Participant result = service.addWalkInService(dto);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void addWalkIn_createsNewWhenNotFound() {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name = "Newbie";
        Participant empty = new Participant(); // participantName is null
        when(repo.findByParticipantName("Newbie")).thenReturn(Optional.of(empty));
        when(repo.save(any())).thenReturn(empty);

        service.addWalkInService(dto);

        verify(repo).save(empty);
    }
}
