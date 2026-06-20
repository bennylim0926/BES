# Judge Active-Status Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hard 409 "judge session already active" block with a soft, heartbeat-tracked confirm prompt on `JudgeSessionView`, matching the existing emcee pattern.

**Architecture:** Add `ROLE_JUDGE` to `UNLIMITED_ROLES` so `redeemToken()` no longer blocks. Introduce a `JudgeActiveStore` (in-memory `ConcurrentHashMap` keyed by `sessionId`) modeled directly on `EmceeCategoryStore`, with the same 60s idle threshold and 30s pruner. Add three `AuthController` endpoints (check / claim / release), wire heartbeat through the existing `/auth/heartbeat`, and clean up on session destroy via `SessionExpiryListener`. Frontend: `JudgeSessionView` calls a check on mount and shows the existing confirm dialog when another active session exists for the same judge.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, JUnit 5, MockMvc, Vue 3, Pinia, Vitest.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-20-judge-active-status-prompt-design.md`. Spec language is authoritative when this plan and spec diverge.
- All backend additions live under `com.example.BES.*` following the existing package layout (services, controllers, config).
- Follow the existing emcee pattern: 60s idle threshold, 30s pruner (`@Scheduled(fixedRate = 30_000)`), `Instant.now().getEpochSecond()` timestamps. Do not invent a new convention.
- Granularity is **per judge identity scoped to event**, not per division. Filter on both `judgeId` and `eventName`.
- No WebSocket broadcasts. The prompt is one-shot at mount; no reactive updates needed.
- Backend additive only. No DB migration. No Flyway file.
- Frontend defaults to fail-open: if the active-check endpoint returns non-OK, treat as `activeElsewhere: false`.
- Run `mvn test` from `BES/` and `npm test` from `BES-frontend/` before any push (pre-push-verify skill handles this).

---

### Task 1: `JudgeActiveStore` service

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/JudgeActiveStore.java`
- Test: `BES/src/test/java/com/example/BES/services/JudgeActiveStoreTest.java`

**Interfaces:**
- Consumes: nothing (leaf service).
- Produces:
  - `void claim(String sessionId, Long judgeId, String eventName)`
  - `void release(String sessionId)`
  - `void heartbeat(String sessionId)`
  - `Set<String> getActiveSessions(Long judgeId, String eventName)` — returns sessionIds whose last-seen is within 60s
  - `void pruneStale()` — `@Scheduled(fixedRate = 30_000)` self-trigger

- [ ] **Step 1: Write the failing test**

Create `BES/src/test/java/com/example/BES/services/JudgeActiveStoreTest.java`:

```java
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd BES && mvn test -Dtest=JudgeActiveStoreTest`
Expected: COMPILE ERROR — `JudgeActiveStore` does not exist.

- [ ] **Step 3: Implement `JudgeActiveStore`**

Create `BES/src/main/java/com/example/BES/services/JudgeActiveStore.java`:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd BES && mvn test -Dtest=JudgeActiveStoreTest`
Expected: PASS — 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/JudgeActiveStore.java \
        BES/src/test/java/com/example/BES/services/JudgeActiveStoreTest.java
git commit -m "feat(judge-session): add JudgeActiveStore for heartbeat-tracked judge presence"
```

---

### Task 2: `AuthController` — unlimited judges + claim/release/check endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/AuthController.java`
- Test: `BES/src/test/java/com/example/BES/controllers/AuthControllerJudgeActiveTest.java`

**Interfaces:**
- Consumes: `JudgeActiveStore` from Task 1.
- Produces (HTTP endpoints):
  - `GET  /api/v1/auth/judge/active-elsewhere?judgeId={id}` → `{ "activeElsewhere": boolean }`
  - `POST /api/v1/auth/judge/claim` → 200 on success, 400 if `judgeId`/`eventName` missing on session, 401 if no session
  - `DELETE /api/v1/auth/judge/release` → 200
- Produces (behavior): redeeming a judge token a second time in another browser no longer returns 409.

- [ ] **Step 1: Write the failing integration test**

Create `BES/src/test/java/com/example/BES/controllers/AuthControllerJudgeActiveTest.java`:

```java
package com.example.BES.controllers;

import com.example.BES.services.JudgeActiveStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerJudgeActiveTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JudgeActiveStore judgeActiveStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // Clear store between tests
        judgeActiveStore.getActiveSessions(42L, "Battle 2026").forEach(judgeActiveStore::release);
    }

    private MockHttpSession judgeSession(Long judgeId, String eventName) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("judgeId", judgeId);
        session.setAttribute("eventName", eventName);
        return session;
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void activeElsewhereFalseForFreshSession() throws Exception {
        MockHttpSession session = judgeSession(42L, "Battle 2026");

        mockMvc.perform(get("/api/v1/auth/judge/active-elsewhere")
                .param("judgeId", "42")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeElsewhere").value(false));
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void activeElsewhereTrueWhenAnotherSessionClaimed() throws Exception {
        MockHttpSession sessionA = judgeSession(42L, "Battle 2026");
        mockMvc.perform(post("/api/v1/auth/judge/claim").session(sessionA))
            .andExpect(status().isOk());

        MockHttpSession sessionB = judgeSession(42L, "Battle 2026");
        mockMvc.perform(get("/api/v1/auth/judge/active-elsewhere")
                .param("judgeId", "42")
                .session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeElsewhere").value(true));
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void releaseClearsActiveSession() throws Exception {
        MockHttpSession sessionA = judgeSession(42L, "Battle 2026");
        mockMvc.perform(post("/api/v1/auth/judge/claim").session(sessionA))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/auth/judge/release").session(sessionA))
            .andExpect(status().isOk());

        MockHttpSession sessionB = judgeSession(42L, "Battle 2026");
        mockMvc.perform(get("/api/v1/auth/judge/active-elsewhere")
                .param("judgeId", "42")
                .session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeElsewhere").value(false));
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void claimReturns400WhenSessionMissingJudgeId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("eventName", "Battle 2026");

        mockMvc.perform(post("/api/v1/auth/judge/claim").session(session))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd BES && mvn test -Dtest=AuthControllerJudgeActiveTest`
Expected: FAIL — 404 on all three new endpoints (they don't exist yet).

- [ ] **Step 3: Modify `AuthController.java` — add `ROLE_JUDGE` to `UNLIMITED_ROLES`**

In `BES/src/main/java/com/example/BES/controllers/AuthController.java`, find line 76–77:

```java
    private static final java.util.Set<String> UNLIMITED_ROLES =
        java.util.Set.of("ROLE_ADMIN", "ROLE_HELPER", "ROLE_EMCEE", "ROLE_ORGANISER");
```

Replace with:

```java
    private static final java.util.Set<String> UNLIMITED_ROLES =
        java.util.Set.of("ROLE_ADMIN", "ROLE_HELPER", "ROLE_EMCEE", "ROLE_ORGANISER", "ROLE_JUDGE");
```

- [ ] **Step 4: Inject `JudgeActiveStore` and wire into heartbeat**

In `AuthController.java`, add the import alongside the existing service imports near the top:

```java
import com.example.BES.services.JudgeActiveStore;
```

Add the field below the existing `emceeCategoryStore` autowire (around line 67–68):

```java
    @Autowired
    private JudgeActiveStore judgeActiveStore;
```

Find the existing `/heartbeat` endpoint (around line 167–176):

```java
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            activeSessionStore.heartbeat(session.getId());
            emceeCategoryStore.heartbeat(session.getId());
        }
        return ResponseEntity.ok().build();
    }
```

Add the judge heartbeat call:

```java
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            activeSessionStore.heartbeat(session.getId());
            emceeCategoryStore.heartbeat(session.getId());
            judgeActiveStore.heartbeat(session.getId());
        }
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 5: Add the three judge endpoints**

Insert these methods inside `AuthController` (just before the closing brace of the class, after `redeemToken`):

```java
    @Operation(summary = "Check Judge Active Elsewhere",
        description = "Returns true if another active session exists for the given judge (excluding caller)")
    @PreAuthorize("hasRole('JUDGE')")
    @GetMapping("/judge/active-elsewhere")
    public ResponseEntity<Map<String, Boolean>> judgeActiveElsewhere(
            @RequestParam Long judgeId,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String mySessionId = session != null ? session.getId() : null;
        String eventName = session != null ? (String) session.getAttribute("eventName") : null;
        java.util.Set<String> active = judgeActiveStore.getActiveSessions(judgeId, eventName);
        boolean elsewhere = active.stream().anyMatch(s -> !s.equals(mySessionId));
        return ResponseEntity.ok(Map.of("activeElsewhere", elsewhere));
    }

    @Operation(summary = "Claim Judge Active",
        description = "Registers caller's session in the judge active-store")
    @PreAuthorize("hasRole('JUDGE')")
    @PostMapping("/judge/claim")
    public ResponseEntity<?> claimJudgeActive(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return ResponseEntity.status(401).build();
        Long judgeId = (Long) session.getAttribute("judgeId");
        String eventName = (String) session.getAttribute("eventName");
        if (judgeId == null || eventName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Judge or event missing on session"));
        }
        judgeActiveStore.claim(session.getId(), judgeId, eventName);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Release Judge Active",
        description = "Removes caller's session from the judge active-store")
    @PreAuthorize("hasRole('JUDGE')")
    @DeleteMapping("/judge/release")
    public ResponseEntity<?> releaseJudgeActive(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) judgeActiveStore.release(session.getId());
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd BES && mvn test -Dtest=AuthControllerJudgeActiveTest`
Expected: PASS — 4 tests green.

- [ ] **Step 7: Run the full backend test suite for regression**

Run: `cd BES && mvn test`
Expected: All existing tests pass; the change to `UNLIMITED_ROLES` does not break any existing `AuthController` test.

If any pre-existing test asserted the 409 conflict for a judge token re-redeem, update it: the new expectation is 200 OK on second redeem. (No such test is currently expected based on the spec.)

- [ ] **Step 8: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/AuthController.java \
        BES/src/test/java/com/example/BES/controllers/AuthControllerJudgeActiveTest.java
git commit -m "feat(judge-session): allow concurrent judge sessions, add active-elsewhere check"
```

---

### Task 3: `SessionExpiryListener` — release on session destroy

**Files:**
- Modify: `BES/src/main/java/com/example/BES/config/SessionExpiryListener.java`

**Interfaces:**
- Consumes: `JudgeActiveStore.release(String sessionId)` from Task 1.
- Produces: nothing (event handler side-effect only).

- [ ] **Step 1: Modify the listener**

In `BES/src/main/java/com/example/BES/config/SessionExpiryListener.java`, add the import next to the existing service imports:

```java
import com.example.BES.services.JudgeActiveStore;
```

Add the autowire below the existing `emceeCategoryStore` autowire:

```java
    @Autowired
    private JudgeActiveStore judgeActiveStore;
```

In `onSessionDestroyed`, add the release call beneath the existing `emceeCategoryStore.release(...)` call:

```java
        emceeCategoryStore.release(event.getId());
        judgeActiveStore.release(event.getId());
```

Final relevant block of `onSessionDestroyed`:

```java
    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        activeSessionStore.deregisterBySessionId(event.getId());
        emceeCategoryStore.release(event.getId());
        judgeActiveStore.release(event.getId());

        // Clean up demo sandbox if this session was a demo
        try {
            HttpSession session = event.getSession();
            if (session != null) {
                String eventName = (String) session.getAttribute("eventName");
                if (eventName != null && eventName.startsWith("Kyrove Demo-")) {
                    Long eventId = (Long) session.getAttribute("eventId");
                    if (eventId != null) {
                        demoService.purgeSandbox(eventId);
                    }
                }
            }
        } catch (IllegalStateException e) {
            // Session already invalidated — cannot read attributes, skip demo cleanup
        }
    }
```

- [ ] **Step 2: Verify the change compiles and full suite still passes**

Run: `cd BES && mvn test`
Expected: All tests pass. (No new test added — this is a one-line wiring change that's exercised when integration tests destroy mock sessions; manual verification covers the production path.)

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/config/SessionExpiryListener.java
git commit -m "feat(judge-session): release judge active-store on session destroy"
```

---

### Task 4: Frontend API helpers

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

**Interfaces:**
- Consumes: the three judge endpoints from Task 2.
- Produces:
  - `judgeActiveElsewhere(judgeId) -> Promise<boolean>` — fail-open, returns `false` on any non-OK / network error
  - `claimJudgeActive() -> Promise<void>`
  - `releaseJudgeActive() -> Promise<void>`

- [ ] **Step 1: Add the three helpers**

In `BES-frontend/src/utils/api.js`, locate the emcee active-category helpers (around lines 1706–1730) and append the judge helpers right below them:

```javascript
export const judgeActiveElsewhere = async (judgeId) => {
  try {
    const res = await fetch(
      `${domain}/api/v1/auth/judge/active-elsewhere?judgeId=${judgeId}`,
      { credentials: 'include', headers: { 'Accept': 'application/json' } }
    )
    if (!res.ok) return false
    const body = await res.json()
    return !!body.activeElsewhere
  } catch (e) {
    console.error(e)
    return false
  }
}

export const claimJudgeActive = async () => {
  try {
    await fetch(`${domain}/api/v1/auth/judge/claim`, {
      method: 'POST',
      credentials: 'include'
    })
  } catch (e) { console.error(e) }
}

export const releaseJudgeActive = async () => {
  try {
    await fetch(`${domain}/api/v1/auth/judge/release`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.error(e) }
}
```

- [ ] **Step 2: Smoke-build the frontend to catch syntax errors**

Run: `cd BES-frontend && npm run build`
Expected: Build succeeds. Vite reports no syntax errors.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat(judge-session): add judge active-elsewhere/claim/release API helpers"
```

---

### Task 5: `JudgeSessionView` — mount-time check, confirm prompt, release on logout

**Files:**
- Modify: `BES-frontend/src/views/JudgeSessionView.vue`

**Interfaces:**
- Consumes: `judgeActiveElsewhere`, `claimJudgeActive`, `releaseJudgeActive` from Task 4.
- Produces: UX behavior — judge sees confirm dialog when another session is active for their identity on the same event.

- [ ] **Step 1: Update the imports**

In `BES-frontend/src/views/JudgeSessionView.vue`, find the existing import line near the top of `<script setup>`:

```javascript
import { getJudgeDivisions, whoami, logout } from '@/utils/api'
```

Replace with:

```javascript
import {
  getJudgeDivisions,
  whoami,
  logout,
  judgeActiveElsewhere,
  claimJudgeActive,
  releaseJudgeActive
} from '@/utils/api'
```

- [ ] **Step 2: Extend `askConfirm` to support an onCancel callback**

Current `askConfirm` / `confirmNo` (around lines 17–28):

```javascript
const confirmDialog = ref({ show: false, title: '', message: '', onConfirm: null })
const askConfirm = (title, message, onConfirm) => {
  confirmDialog.value = { show: true, title, message, onConfirm }
}
const confirmYes = () => {
  confirmDialog.value.onConfirm?.()
  confirmDialog.value = { show: false, title: '', message: '', onConfirm: null }
}
const confirmNo = () => {
  confirmDialog.value = { show: false, title: '', message: '', onConfirm: null }
}
```

Replace with:

```javascript
const confirmDialog = ref({ show: false, title: '', message: '', onConfirm: null, onCancel: null })
const askConfirm = (title, message, onConfirm, onCancel = null) => {
  confirmDialog.value = { show: true, title, message, onConfirm, onCancel }
}
const confirmYes = () => {
  confirmDialog.value.onConfirm?.()
  confirmDialog.value = { show: false, title: '', message: '', onConfirm: null, onCancel: null }
}
const confirmNo = () => {
  confirmDialog.value.onCancel?.()
  confirmDialog.value = { show: false, title: '', message: '', onConfirm: null, onCancel: null }
}
```

- [ ] **Step 3: Add a helper that performs the active-elsewhere check + claim**

Add this function below `loadDivisions()` (before `onMounted`), around line 42:

```javascript
async function checkAndClaimActive() {
  const elsewhere = await judgeActiveElsewhere(authStore.judgeId)
  if (elsewhere) {
    askConfirm(
      'Judge Already Active',
      `"${judgeName.value}" is already signed in on another device. Continue anyway? Scores from both sessions will be saved, but two devices on the same judge can confuse the flow.`,
      async () => {
        await claimJudgeActive()
        await loadDivisions()
      },
      async () => {
        await releaseJudgeActive()
        await logout()
        authStore.logout()
        router.push('/login')
      }
    )
  } else {
    await claimJudgeActive()
    await loadDivisions()
  }
}
```

- [ ] **Step 4: Wire `checkAndClaimActive` into `onMounted`**

Locate the current `onMounted` (around lines 44–65). The structure is roughly:

```javascript
onMounted(async () => {
  if (!isJudgeSession.value) {
    // restore via whoami...
  }
  // existing: loadDivisions()
})
```

Read the current contents and update so that:
- The existing whoami-restore block runs first.
- After session is confirmed (`isJudgeSession.value` is true), call `await checkAndClaimActive()` instead of `await loadDivisions()`.

Concretely — the call site that used to be `await loadDivisions()` becomes `await checkAndClaimActive()`. Do not add a second `loadDivisions` call: `checkAndClaimActive` handles both branches.

If the existing code has `loadDivisions()` called unconditionally at the end of `onMounted`, replace that single call with `checkAndClaimActive()`. Leave the rest of the whoami / session-restore logic unchanged.

- [ ] **Step 5: Update `handleLogout` to release before logout**

Current `handleLogout` (around lines 80–90):

```javascript
function handleLogout() {
  askConfirm(
    'Leave Session?',
    'You will be returned to the login screen.',
    async () => {
      await logout()
      authStore.logout()
      router.push('/login')
    }
  )
}
```

Replace with:

```javascript
function handleLogout() {
  askConfirm(
    'Leave Session?',
    'You will be returned to the login screen.',
    async () => {
      await releaseJudgeActive()
      await logout()
      authStore.logout()
      router.push('/login')
    }
  )
}
```

- [ ] **Step 6: Smoke-build to catch syntax errors**

Run: `cd BES-frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 7: Manual verification**

Start the app via `docker compose up -d --build` (no `--no-cache` — Dockerfile / pom / package.json unchanged), then:

1. Generate a judge session link from `/events/<eventName>` → Session Links.
2. Open the link in Browser A (e.g. Chrome). Verify divisions load, no prompt.
3. Open the same link in Browser B (e.g. Safari, or a private window). Verify:
   - The 409 redirect to "Link invalid" does NOT happen.
   - On `/judge/session`, a confirm dialog appears: "Judge X is already signed in on another device. Continue anyway?".
   - **Continue** → divisions load; both browsers can navigate to audition list.
   - Reload Browser B and choose **Cancel** → returned to `/login`.
4. In Browser A, click Logout. In Browser B, refresh `/judge/session` — no prompt this time (A released its claim).
5. Same browser, refresh the tab — no prompt (the caller's own sessionId is excluded).

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/views/JudgeSessionView.vue
git commit -m "feat(judge-session): prompt instead of block when judge active elsewhere"
```

---

### Task 6: End-to-end check and PR

**Files:** none modified — verification only.

- [ ] **Step 1: Run pre-push verification**

Invoke the pre-push-verify skill (or run manually):
- `cd BES && mvn test`
- `cd BES-frontend && npm test`
- `cd BES-frontend && npm run build`

Expected: all green.

- [ ] **Step 2: Push the branch and open PR**

Standard flow — push current branch and `gh pr create` with title "feat(judge-session): replace hard session limit with active-status prompt" and body summarising the change with a link to the spec:

```
## Summary
- Add `ROLE_JUDGE` to `UNLIMITED_ROLES` — second device on the same judge token no longer hits 409
- New `JudgeActiveStore` (in-memory, 60s idle threshold, mirrors `EmceeCategoryStore`)
- `AuthController`: new `/auth/judge/active-elsewhere`, `/auth/judge/claim`, `/auth/judge/release`; heartbeat now refreshes judge presence
- `SessionExpiryListener`: release judge claim on session destroy
- `JudgeSessionView`: mount-time check; confirm dialog when same judge is active on another device

Spec: docs/superpowers/specs/2026-06-20-judge-active-status-prompt-design.md

## Test plan
- [ ] Backend `mvn test` passes (incl. new `JudgeActiveStoreTest`, `AuthControllerJudgeActiveTest`)
- [ ] Frontend `npm test` and `npm run build` pass
- [ ] Manual: two browsers, same judge token — second sees prompt, not 409
- [ ] Manual: refresh same tab — no prompt
- [ ] Manual: logout from one device — other device sees no prompt on reload
```

---

## Self-review

**Spec coverage check** — every spec section is covered:
- `JudgeActiveStore` (spec §"Backend changes" → store): Task 1 ✓
- `ROLE_JUDGE` in UNLIMITED_ROLES + heartbeat wiring + 3 endpoints: Task 2 ✓
- `SessionExpiryListener` release: Task 3 ✓
- Frontend API helpers (3): Task 4 ✓
- `JudgeSessionView` mount-time check, confirm dialog, release on logout: Task 5 ✓
- Testing (backend store test, backend controller test, manual): Tasks 1, 2, 5 ✓
- Behavior matrix (rows: first device OK, second prompts, refresh same tab silent, logout releases, network drop expires after 60s): covered by combination of Tasks 1–5; manual verification in Task 5 ✓
- Non-goals (password login, per-division granularity, WS broadcast): explicitly skipped, no tasks ✓

**Placeholder scan:** No "TBD", no "add appropriate error handling", no "similar to Task N". Every code-changing step shows actual code.

**Type / name consistency:**
- `JudgeActiveStore` methods (`claim`, `release`, `heartbeat`, `getActiveSessions`, `pruneStale`) used in Tasks 1, 2, 3 — match across all tasks.
- Frontend helper names (`judgeActiveElsewhere`, `claimJudgeActive`, `releaseJudgeActive`) — match between Task 4 (definition) and Task 5 (consumption).
- Endpoint paths (`/auth/judge/active-elsewhere`, `/auth/judge/claim`, `/auth/judge/release`) — match between Task 2 (controller) and Task 4 (frontend).

No issues found.
