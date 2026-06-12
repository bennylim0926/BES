package com.example.BES.services;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class ActiveSessionStore {

    private static final long IDLE_THRESHOLD_SECONDS = 60; // 1 min without heartbeat = browser gone

    private static class Entry {
        final String sessionId;
        final AtomicLong lastSeen = new AtomicLong();

        Entry(String sessionId) {
            this.sessionId = sessionId;
            this.lastSeen.set(Instant.now().getEpochSecond());
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public boolean isActive(String username) {
        Entry entry = store.get(username);
        if (entry == null) return false;
        boolean stale = Instant.now().getEpochSecond() - entry.lastSeen.get() > IDLE_THRESHOLD_SECONDS;
        if (stale) store.remove(username);
        return !stale;
    }

    public String getSessionId(String username) {
        Entry entry = store.get(username);
        return entry != null ? entry.sessionId : null;
    }

    public void register(String username, String sessionId) {
        store.put(username, new Entry(sessionId));
    }

    public void heartbeat(String sessionId) {
        store.values().stream()
            .filter(e -> e.sessionId.equals(sessionId))
            .forEach(e -> e.lastSeen.set(Instant.now().getEpochSecond()));
    }

    public void deregisterBySessionId(String sessionId) {
        store.entrySet().removeIf(e -> e.getValue().sessionId.equals(sessionId));
    }
}
