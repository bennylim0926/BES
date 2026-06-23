package com.example.BES.services;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeActiveStoreTest {

    @Test
    void claimRegistersSessionForJudgeAndEvent() {
        JudgeActiveStore store = new JudgeActiveStore();
        store.claim("session-A", 42L, "Battle 2026");

        Set<String> active = store.getActiveSessions(42L, "Battle 2026");

        assertEquals(Set.of("session-A"), active);
    }

    @Test
    void releaseRemovesSession() {
        JudgeActiveStore store = new JudgeActiveStore();
        store.claim("session-A", 42L, "Battle 2026");

        store.release("session-A");

        assertTrue(store.getActiveSessions(42L, "Battle 2026").isEmpty());
    }

    @Test
    void getActiveSessionsExcludesDifferentJudge() {
        JudgeActiveStore store = new JudgeActiveStore();
        store.claim("session-A", 42L, "Battle 2026");
        store.claim("session-B", 99L, "Battle 2026");

        assertEquals(Set.of("session-A"), store.getActiveSessions(42L, "Battle 2026"));
    }

    @Test
    void getActiveSessionsExcludesDifferentEvent() {
        JudgeActiveStore store = new JudgeActiveStore();
        store.claim("session-A", 42L, "Battle 2026");
        store.claim("session-B", 42L, "Other Event");

        assertEquals(Set.of("session-A"), store.getActiveSessions(42L, "Battle 2026"));
    }

    @Test
    void multipleSessionsForSameJudgeAreAllReturned() {
        JudgeActiveStore store = new JudgeActiveStore();
        store.claim("session-A", 42L, "Battle 2026");
        store.claim("session-B", 42L, "Battle 2026");

        Set<String> active = store.getActiveSessions(42L, "Battle 2026");

        assertEquals(Set.of("session-A", "session-B"), active);
    }

    @Test
    void getActiveSessionsMatchesEventNameCaseInsensitively() {
        JudgeActiveStore store = new JudgeActiveStore();
        store.claim("session-A", 42L, "Battle 2026");

        assertEquals(Set.of("session-A"), store.getActiveSessions(42L, "BATTLE 2026"));
        assertEquals(Set.of("session-A"), store.getActiveSessions(42L, "battle 2026"));
    }
}
