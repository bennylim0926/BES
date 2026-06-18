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
    void addParticipant_createsAndSaves() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.setParticipantName("Test");
        Participant saved = new Participant();
        when(repo.saveAndFlush(any())).thenReturn(saved);

        Participant result = service.addParticpantService(dto);

        assertThat(result).isSameAs(saved);
        verify(repo).saveAndFlush(any(Participant.class));
    }

    @Test
    void addWalkIn_returnsExistingWhenNameMatches() {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name = "Existing";
        Participant existing = new Participant();
        existing.setParticipantName("Existing");
        when(repo.findFirstByParticipantNameIgnoreCase("Existing")).thenReturn(Optional.of(existing));

        Participant result = service.addWalkInService(dto);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void addWalkIn_createsNewWhenNotFound() {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name = "Newbie";
        Participant empty = new Participant();
        when(repo.findFirstByParticipantNameIgnoreCase("Newbie")).thenReturn(Optional.of(empty));
        when(repo.saveAndFlush(any())).thenReturn(empty);

        service.addWalkInService(dto);

        verify(repo).saveAndFlush(empty);
    }
}
