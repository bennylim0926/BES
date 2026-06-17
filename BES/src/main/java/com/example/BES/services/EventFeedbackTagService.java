package com.example.BES.services;

import com.example.BES.dtos.admin.FeedbackTagOverrideDto;
import com.example.BES.dtos.event.EventFeedbackGroupDto;
import com.example.BES.dtos.event.EventFeedbackTagDto;
import com.example.BES.models.Event;
import com.example.BES.models.FeedbackTag;
import com.example.BES.models.FeedbackTagGroup;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.FeedbackTagGroupRepository;
import com.example.BES.respositories.FeedbackTagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EventFeedbackTagService {

    static final String SCOPE_GLOBAL = "GLOBAL";
    static final String SCOPE_EVENT = "EVENT";

    @Autowired
    EventRepo eventRepo;

    @Autowired
    FeedbackTagGroupRepository tagGroupRepo;

    @Autowired
    FeedbackTagRepository tagRepo;

    /**
     * Returns the resolved taxonomy for an event: global groups + event-scoped groups.
     * If a global group and an event-scoped group share a (case-insensitive) name, the
     * event-scoped group fully replaces the global group — its tags are the only tags
     * shown for that group name.
     */
    public List<EventFeedbackGroupDto> getResolvedGroups(String eventName) {
        Event event = requireEvent(eventName);

        Map<String, EventFeedbackGroupDto> byName = new LinkedHashMap<>();

        for (FeedbackTagGroup g : tagGroupRepo.findByEventIsNull()) {
            byName.put(g.getName().toLowerCase(), toDto(g, SCOPE_GLOBAL));
        }
        // Event-scoped groups override global groups with the same name.
        for (FeedbackTagGroup g : tagGroupRepo.findByEventEventId(event.getEventId())) {
            byName.put(g.getName().toLowerCase(), toDto(g, SCOPE_EVENT));
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * For each global group whose name is also used by at least one event-scoped group,
     * returns the global group id + name + list of event names that override it.
     * Informational only — used by AdminPage to show an "Overridden in N event(s)" chip.
     */
    public List<FeedbackTagOverrideDto> getOverrides() {
        List<FeedbackTagGroup> globalGroups = tagGroupRepo.findByEventIsNull();
        Map<String, FeedbackTagGroup> globalByName = new HashMap<>();
        for (FeedbackTagGroup g : globalGroups) {
            globalByName.put(g.getName().toLowerCase(Locale.ROOT), g);
        }

        // Map: global group id → set of event names that override it.
        Map<Long, List<String>> overridesByGlobalId = new LinkedHashMap<>();
        for (FeedbackTagGroup scoped : tagGroupRepo.findAll()) {
            if (scoped.getEvent() == null) continue;
            FeedbackTagGroup match = globalByName.get(scoped.getName().toLowerCase(Locale.ROOT));
            if (match == null) continue;
            overridesByGlobalId
                    .computeIfAbsent(match.getId(), k -> new ArrayList<>())
                    .add(scoped.getEvent().getEventName());
        }

        List<FeedbackTagOverrideDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : overridesByGlobalId.entrySet()) {
            FeedbackTagGroup global = globalGroups.stream()
                    .filter(g -> g.getId().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (global == null) continue;
            result.add(new FeedbackTagOverrideDto(global.getId(), global.getName(), entry.getValue()));
        }
        return result;
    }

    public List<EventFeedbackGroupDto> addEventScopedGroup(String eventName, String name) {
        Event event = requireEvent(eventName);
        FeedbackTagGroup group = new FeedbackTagGroup();
        group.setName(name);
        group.setEvent(event);
        tagGroupRepo.save(group);
        return getResolvedGroups(eventName);
    }

    public List<EventFeedbackGroupDto> addEventScopedTag(String eventName, Long groupId, String label) {
        Event event = requireEvent(eventName);
        FeedbackTagGroup group = tagGroupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        if (group.getEvent() == null || !group.getEvent().getEventId().equals(event.getEventId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot add a tag to a group that is not event-scoped to this event");
        }
        FeedbackTag tag = new FeedbackTag();
        tag.setLabel(label);
        tag.setGroup(group);
        tag.setEvent(event);
        tagRepo.save(tag);
        return getResolvedGroups(eventName);
    }

    public List<EventFeedbackGroupDto> updateEventScopedTag(String eventName, Long tagId, String label) {
        Event event = requireEvent(eventName);
        FeedbackTag tag = tagRepo.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
        requireOwnedByEvent(tag, event);
        tag.setLabel(label);
        tagRepo.save(tag);
        return getResolvedGroups(eventName);
    }

    public List<EventFeedbackGroupDto> deleteEventScopedTag(String eventName, Long tagId) {
        Event event = requireEvent(eventName);
        FeedbackTag tag = tagRepo.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
        requireOwnedByEvent(tag, event);
        tagRepo.delete(tag);
        return getResolvedGroups(eventName);
    }

    public List<EventFeedbackGroupDto> deleteEventScopedGroup(String eventName, Long groupId) {
        Event event = requireEvent(eventName);
        FeedbackTagGroup group = tagGroupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        requireOwnedByEvent(group, event);
        tagGroupRepo.delete(group);
        return getResolvedGroups(eventName);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Event requireEvent(String eventName) {
        return eventRepo.findByEventName(eventName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventName));
    }

    private void requireOwnedByEvent(FeedbackTag tag, Event event) {
        if (tag.getEvent() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify a global tag via event endpoint — use /admin");
        }
        if (!tag.getEvent().getEventId().equals(event.getEventId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag belongs to a different event");
        }
    }

    private void requireOwnedByEvent(FeedbackTagGroup group, Event event) {
        if (group.getEvent() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify a global group via event endpoint — use /admin");
        }
        if (!group.getEvent().getEventId().equals(event.getEventId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Group belongs to a different event");
        }
    }

    private EventFeedbackGroupDto toDto(FeedbackTagGroup group, String scope) {
        List<EventFeedbackTagDto> tags = new ArrayList<>();
        if (group.getTags() != null) {
            for (FeedbackTag t : group.getTags()) {
                tags.add(new EventFeedbackTagDto(
                        t.getId(),
                        t.getLabel(),
                        group.getId(),
                        scope
                ));
            }
        }
        return new EventFeedbackGroupDto(group.getId(), group.getName(), scope, tags);
    }
}
