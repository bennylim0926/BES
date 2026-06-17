package com.example.BES.services;

import com.example.BES.dtos.event.EventFeedbackGroupDto;
import com.example.BES.models.Event;
import com.example.BES.models.FeedbackTag;
import com.example.BES.models.FeedbackTagGroup;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.FeedbackTagGroupRepository;
import com.example.BES.respositories.FeedbackTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventFeedbackTagServiceTest {

    @Mock EventRepo eventRepo;
    @Mock FeedbackTagGroupRepository tagGroupRepo;
    @Mock FeedbackTagRepository tagRepo;
    @InjectMocks EventFeedbackTagService service;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setEventId(7L);
        event.setEventName("Fest");
    }

    private FeedbackTagGroup group(Long id, String name, Event owner, FeedbackTag... tags) {
        FeedbackTagGroup g = new FeedbackTagGroup();
        g.setId(id);
        g.setName(name);
        g.setEvent(owner);
        g.setTags(new ArrayList<>(List.of(tags)));
        return g;
    }

    private FeedbackTag tag(Long id, String label, FeedbackTagGroup g, Event owner) {
        FeedbackTag t = new FeedbackTag();
        t.setId(id);
        t.setLabel(label);
        t.setGroup(g);
        t.setEvent(owner);
        return t;
    }

    @Test
    void getResolvedGroups_returnsGlobalOnlyWhenNoEventScoped() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        FeedbackTagGroup global = group(1L, "Strengths", null);
        global.setTags(List.of(tag(10L, "Sharp", global, null)));
        when(tagGroupRepo.findByEventIsNull()).thenReturn(List.of(global));
        when(tagGroupRepo.findByEventEventId(7L)).thenReturn(List.of());

        List<EventFeedbackGroupDto> result = service.getResolvedGroups("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Strengths");
        assertThat(result.get(0).getScope()).isEqualTo("GLOBAL");
        assertThat(result.get(0).getTags().get(0).getScope()).isEqualTo("GLOBAL");
    }

    @Test
    void getResolvedGroups_eventScopedOverridesGlobalByName() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        FeedbackTagGroup global = group(1L, "Strengths", null);
        global.setTags(List.of(tag(10L, "Sharp", global, null)));
        FeedbackTagGroup scoped = group(2L, "Strengths", event);
        scoped.setTags(List.of(tag(20L, "Power moves", scoped, event)));

        when(tagGroupRepo.findByEventIsNull()).thenReturn(List.of(global));
        when(tagGroupRepo.findByEventEventId(7L)).thenReturn(List.of(scoped));

        List<EventFeedbackGroupDto> result = service.getResolvedGroups("Fest");

        // event-scoped wins by name — exactly one group, scoped, with scoped tag only
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(0).getScope()).isEqualTo("EVENT");
        assertThat(result.get(0).getTags()).hasSize(1);
        assertThat(result.get(0).getTags().get(0).getLabel()).isEqualTo("Power moves");
    }

    @Test
    void getResolvedGroups_unionsDistinctNames() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        FeedbackTagGroup global = group(1L, "Strengths", null);
        FeedbackTagGroup scoped = group(2L, "Hip-hop only", event);
        when(tagGroupRepo.findByEventIsNull()).thenReturn(List.of(global));
        when(tagGroupRepo.findByEventEventId(7L)).thenReturn(List.of(scoped));

        List<EventFeedbackGroupDto> result = service.getResolvedGroups("Fest");

        assertThat(result).extracting(EventFeedbackGroupDto::getName)
                .containsExactlyInAnyOrder("Strengths", "Hip-hop only");
    }

    @Test
    void getResolvedGroups_throws404WhenEventMissing() {
        when(eventRepo.findByEventName("Nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResolvedGroups("Nope"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    void addEventScopedGroup_savesWithEventOwner() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        when(tagGroupRepo.findByEventIsNull()).thenReturn(List.of());
        when(tagGroupRepo.findByEventEventId(7L)).thenReturn(List.of());

        service.addEventScopedGroup("Fest", "Locking-specific");

        verify(tagGroupRepo).save(argThat(g ->
                "Locking-specific".equals(g.getName()) && event.equals(g.getEvent())
        ));
    }

    @Test
    void addEventScopedTag_rejectsGlobalGroup() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        FeedbackTagGroup global = group(1L, "Strengths", null);
        when(tagGroupRepo.findById(1L)).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.addEventScopedTag("Fest", 1L, "should fail"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("event-scoped");
        verify(tagRepo, never()).save(any());
    }

    @Test
    void updateEventScopedTag_rejectsGlobalTag() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        FeedbackTag globalTag = tag(10L, "Sharp", null, null);
        when(tagRepo.findById(10L)).thenReturn(Optional.of(globalTag));

        assertThatThrownBy(() -> service.updateEventScopedTag("Fest", 10L, "renamed"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("global tag");
        verify(tagRepo, never()).save(any());
    }

    @Test
    void deleteEventScopedTag_rejectsTagFromDifferentEvent() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        Event other = new Event();
        other.setEventId(99L);
        FeedbackTag otherTag = tag(10L, "Sharp", null, other);
        when(tagRepo.findById(10L)).thenReturn(Optional.of(otherTag));

        assertThatThrownBy(() -> service.deleteEventScopedTag("Fest", 10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different event");
        verify(tagRepo, never()).delete(any());
    }

    @Test
    void deleteEventScopedGroup_deletesWhenOwned() {
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(event));
        FeedbackTagGroup scoped = group(2L, "Hip-hop only", event);
        when(tagGroupRepo.findById(2L)).thenReturn(Optional.of(scoped));
        when(tagGroupRepo.findByEventIsNull()).thenReturn(List.of());
        when(tagGroupRepo.findByEventEventId(7L)).thenReturn(List.of());

        service.deleteEventScopedGroup("Fest", 2L);

        verify(tagGroupRepo).delete(scoped);
    }
}
