package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class EmceeCategoryStore {

    private static final long IDLE_THRESHOLD_SECONDS = 60;

    private static class Entry {
        final String eventName;
        final String categoryName;
        final AtomicLong lastSeen = new AtomicLong();

        Entry(String eventName, String categoryName) {
            this.eventName = eventName;
            this.categoryName = categoryName;
            this.lastSeen.set(Instant.now().getEpochSecond());
        }
    }

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // sessionId → Entry
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public void claim(String sessionId, String eventName, String categoryName) {
        store.put(sessionId, new Entry(eventName, categoryName));
        broadcast(eventName);
    }

    public void release(String sessionId) {
        Entry entry = store.remove(sessionId);
        if (entry != null) broadcast(entry.eventName);
    }

    public void heartbeat(String sessionId) {
        Entry entry = store.get(sessionId);
        if (entry != null) entry.lastSeen.set(Instant.now().getEpochSecond());
    }

    public Set<String> getActiveCategories(String eventName) {
        long now = Instant.now().getEpochSecond();
        return store.values().stream()
            .filter(e -> e.eventName.equalsIgnoreCase(eventName))
            .filter(e -> now - e.lastSeen.get() <= IDLE_THRESHOLD_SECONDS)
            .map(e -> e.categoryName)
            .collect(Collectors.toSet());
    }

    /** Prune entries with no heartbeat for > 60s, then broadcast updated lists for affected events. */
    @Scheduled(fixedRate = 30_000)
    public void pruneStale() {
        long now = Instant.now().getEpochSecond();
        Set<String> affectedEvents = new HashSet<>();
        store.entrySet().removeIf(e -> {
            boolean stale = now - e.getValue().lastSeen.get() > IDLE_THRESHOLD_SECONDS;
            if (stale) affectedEvents.add(e.getValue().eventName);
            return stale;
        });
        affectedEvents.forEach(this::broadcast);
    }

    private void broadcast(String eventName) {
        messagingTemplate.convertAndSend(
            "/topic/emcee/active-categories/" + eventName,
            Map.of("categories", getActiveCategories(eventName))
        );
    }
}
