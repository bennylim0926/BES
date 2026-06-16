package com.example.BES.services;

import com.example.BES.dtos.GetEventCategoryParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventCategoryParticipantId;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.EventCategoryParticipantMemberRepo;
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

    @Mock EventCategoryParticipantRepo repo;
    @Mock EventRepo eventRepo;
    @Mock Event event;
    @Mock ParticipantRepo participantRepo;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock JudgeRepo judgeRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventCategoryRepo eventCategoryRepo;
    @Mock EventCategoryParticipantMemberRepo egpMemberRepo;
    @InjectMocks EventCategoryParticipantService service;

    @Test
    void getAllByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetEventCategoryParticipantDto> result =
            service.getAllEventCategoryParticipantByEventService("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void removeParticipantFromCategory_doesNothingWhenEgpNotFound() {
        EventCategoryParticipantId id = new EventCategoryParticipantId(1L, 2L, 3L);
        when(repo.findById(id)).thenReturn(Optional.empty());

        service.removeParticipantFromCategory(3L, 1L, 2L);

        verify(repo, never()).delete(any());
    }

    @Test
    void addCategoryToExistingParticipant_throwsWhenCategoryNotFound() {
        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));
        when(eventCategoryRepo.findByEventAndName(event, "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addCategoryToExistingParticipant(1L, 1L, "ghost", null, null, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Event category not found");
    }
}
