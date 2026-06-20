package com.example.BES.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class JudgeActiveStore {

    private static final long IDLE_THRESHOLD_SECONDS = 60;

    private static class Entry {
        final Long judgeId;
        final String eventName;
        final AtomicLong lastSeen = new AtomicLong();

        Entry(Long judgeId, String eventName) {
            this.judgeId = judgeId;
            this.eventName = eventName;
            this.lastSeen.set(Instant.now().getEpochSecond());
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public void claim(String sessionId, Long judgeId, String eventName) {
        store.put(sessionId, new Entry(judgeId, eventName));
    }

    public void release(String sessionId) {
        store.remove(sessionId);
    }

    public void heartbeat(String sessionId) {
        Entry entry = store.get(sessionId);
        if (entry != null) entry.lastSeen.set(Instant.now().getEpochSecond());
    }

    public Set<String> getActiveSessions(Long judgeId, String eventName) {
        long now = Instant.now().getEpochSecond();
        return store.entrySet().stream()
            .filter(e -> Objects.equals(e.getValue().judgeId, judgeId))
            .filter(e -> e.getValue().eventName != null
                      && e.getValue().eventName.equalsIgnoreCase(eventName))
            .filter(e -> now - e.getValue().lastSeen.get() <= IDLE_THRESHOLD_SECONDS)
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    @Scheduled(fixedRate = 30_000)
    public void pruneStale() {
        long now = Instant.now().getEpochSecond();
        store.entrySet().removeIf(e -> now - e.getValue().lastSeen.get() > IDLE_THRESHOLD_SECONDS);
    }
}
