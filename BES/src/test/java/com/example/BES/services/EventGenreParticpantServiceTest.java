package com.example.BES.services;

import com.example.BES.dtos.GetEventGenreParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.EventGenreParticipantMemberRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ParticipantRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventGenreParticpantServiceTest {

    @Mock EventGenreParticpantRepo repo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock Event event;
    @Mock ParticipantRepo participantRepo;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock JudgeRepo judgeRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @Mock EventGenreParticipantMemberRepo egpMemberRepo;
    @InjectMocks EventGenreParticpantService service;

    @Test
    void getAllByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetEventGenreParticipantDto> result =
            service.getAllEventGenreParticipantByEventService("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void removeParticipantFromGenre_doesNothingWhenEgpNotFound() {
        EventGenreParticipantId id = new EventGenreParticipantId(1L, 2L, 3L);
        when(repo.findById(id)).thenReturn(Optional.empty());

        service.removeParticipantFromGenre(3L, 1L, 2L);

        verify(repo, never()).delete(any());
    }

    @Test
    void addGenreToExistingParticipant_throwsWhenGenreNotFound() {
        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventGenreRepo.findByEventAndName(event, "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addGenreToExistingParticipant(1L, 1L, "ghost"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Event genre not found");
    }
}
