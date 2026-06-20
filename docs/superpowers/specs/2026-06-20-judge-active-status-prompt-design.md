# Judge Active-Status Prompt (replace hard session limit)

**Date:** 2026-06-20
**Status:** Design approved, awaiting plan

## Problem

Judges authenticate via permanent per-judge session-token links (e.g. `/auth/token?t=...`). Today, `AuthController.redeemToken()` enforces a one-active-session-per-token rule for any role not in `UNLIMITED_ROLES`. `ROLE_JUDGE` is the only session-link role that is NOT unlimited — `ADMIN`, `EMCEE`, `HELPER`, `ORGANISER` are all whitelisted. As a result, if a judge opens their token link on a second device (or even a second tab where the cookie didn't carry over), they hit a hard 409 `"This session link is already active in another browser"` and cannot proceed without the first session expiring.

This is a problem in practice: judges legitimately move between phone / tablet / shared tablet during an event, and the hard block forces an organiser to regenerate the token or wait for session expiry.

Emcees already have a softer pattern. They are unlimited at redeem; `EmceeCategoryStore` tracks who is actively running which category via heartbeat, and `EmceeSessionView` shows an "ACTIVE" badge plus a confirm dialog when a second emcee picks a category that another emcee is already running. Continue-anyway is allowed; the user just gets a clear warning.

## Goal

Apply the same UX to judges. Remove the hard block; replace it with a heartbeat-tracked "active elsewhere" check and a confirm prompt on the judge session landing page.

Granularity is **per judge identity** (not per division). A single confirm prompt on `JudgeSessionView` is enough; we do not need ACTIVE badges per division. Rationale: judges score independently per (judge × participant × category), so a duplicate session is a UX confusion risk, not a data-integrity risk. One prompt at entry captures the case without spamming the UI.

## Non-goals

- Per-(judge × division) tracking. Deferred — can be added later if we observe real confusion mid-event.
- Password-login judges. Judges authenticate via token only; the `/auth/login` path is N/A.
- WebSocket broadcast of the active set. The prompt is one-shot at session entry; reactive updates are unnecessary.
- Changing emcee, helper, or organiser session behavior. This spec is judge-only.

## Architecture

```
┌───────────────────────────────────────────────────────────────┐
│  Browser A (Judge Alice's phone)                              │
│   /auth/token?t=… → /judge/session                            │
│     ├ judgeActiveElsewhere(judgeId) → { activeElsewhere: false }
│     └ claimJudgeActive() → registered                         │
│  (heartbeat every 30s via existing /auth/heartbeat)           │
└───────────────────────────────────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────┐
│  Backend                                                      │
│    AuthController                                             │
│      - UNLIMITED_ROLES now includes ROLE_JUDGE                │
│      - GET  /auth/judge/active-elsewhere?judgeId={id}         │
│      - POST /auth/judge/claim                                 │
│      - DELETE /auth/judge/release                             │
│      - /auth/heartbeat also calls judgeActiveStore.heartbeat  │
│                                                               │
│    JudgeActiveStore (NEW, in-memory)                          │
│      Map<sessionId, Entry(judgeId, eventName, lastHeartbeat)> │
│      90s grace window before considered inactive              │
│                                                               │
│    SessionExpiryListener                                      │
│      On session destroyed → judgeActiveStore.release(id)      │
└───────────────────────────────▲───────────────────────────────┘
                                │
┌───────────────────────────────────────────────────────────────┐
│  Browser B (Judge Alice's tablet, second device)              │
│   /auth/token?t=… → /judge/session                            │
│     ├ judgeActiveElsewhere(judgeId) → { activeElsewhere: true }
│     ├ ConfirmDialog: "Judge Alice is already active…"         │
│     │    Continue → claimJudgeActive() → divisions load       │
│     │    Cancel   → releaseJudgeActive() + logout() → /login  │
└───────────────────────────────────────────────────────────────┘
```

## Backend changes

### `BES/src/main/java/com/example/BES/services/JudgeActiveStore.java` (NEW)

Mirror of `EmceeCategoryStore`. Single in-memory map; not persisted.

```java
@Service
public class JudgeActiveStore {
    private static final Duration GRACE = Duration.ofSeconds(90);

    private record Entry(Long judgeId, String eventName, Instant lastHeartbeat) {}

    private final ConcurrentHashMap<String, Entry> bySession = new ConcurrentHashMap<>();

    public void claim(String sessionId, Long judgeId, String eventName) {
        bySession.put(sessionId, new Entry(judgeId, eventName, Instant.now()));
    }

    public void release(String sessionId) {
        bySession.remove(sessionId);
    }

    public void heartbeat(String sessionId) {
        bySession.computeIfPresent(sessionId,
            (k, v) -> new Entry(v.judgeId(), v.eventName(), Instant.now()));
    }

    /** sessionIds currently active for this (judge, event), excluding stale heartbeats. */
    public Set<String> getActiveSessions(Long judgeId, String eventName) {
        Instant cutoff = Instant.now().minus(GRACE);
        return bySession.entrySet().stream()
            .filter(e -> e.getValue().lastHeartbeat().isAfter(cutoff))
            .filter(e -> Objects.equals(e.getValue().judgeId(), judgeId))
            .filter(e -> Objects.equals(e.getValue().eventName(), eventName))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
}
```

### `BES/src/main/java/com/example/BES/controllers/AuthController.java`

1. Add `"ROLE_JUDGE"` to `UNLIMITED_ROLES`. The 409 conflict in `redeemToken()` (and `login()`) no longer applies to judges. The `activeSessionStore.register()` call still runs — it just becomes informational, no longer enforced.

2. Inject `JudgeActiveStore`. Update `/auth/heartbeat` to also call `judgeActiveStore.heartbeat(session.getId())` alongside the existing `activeSessionStore.heartbeat` and `emceeCategoryStore.heartbeat`.

3. New endpoints, all `@PreAuthorize("hasRole('JUDGE')")`:

   ```java
   @GetMapping("/judge/active-elsewhere")
   public ResponseEntity<Map<String, Boolean>> judgeActiveElsewhere(
           @RequestParam Long judgeId,
           HttpServletRequest request) {
       HttpSession session = request.getSession(false);
       String mySessionId = session != null ? session.getId() : null;
       String eventName = session != null ? (String) session.getAttribute("eventName") : null;
       Set<String> active = judgeActiveStore.getActiveSessions(judgeId, eventName);
       boolean elsewhere = active.stream().anyMatch(s -> !s.equals(mySessionId));
       return ResponseEntity.ok(Map.of("activeElsewhere", elsewhere));
   }

   @PostMapping("/judge/claim")
   public ResponseEntity<?> claimJudge(HttpServletRequest request) {
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

   @DeleteMapping("/judge/release")
   public ResponseEntity<?> releaseJudge(HttpServletRequest request) {
       HttpSession session = request.getSession(false);
       if (session != null) judgeActiveStore.release(session.getId());
       return ResponseEntity.ok().build();
   }
   ```

### `BES/src/main/java/com/example/BES/config/SessionExpiryListener.java`

Mirror the emcee wiring: when a Spring session is destroyed, also call `judgeActiveStore.release(sessionId)`. This handles the case where the judge closes the tab without explicit logout.

## Frontend changes

### `BES-frontend/src/utils/api.js`

Three new helpers, following the same pattern as `claimEmceeCategory` / `releaseEmceeCategory` / `getActiveEmceeCategories`:

```javascript
export const judgeActiveElsewhere = async (judgeId) => {
  try {
    const res = await fetch(
      `${domain}/api/v1/auth/judge/active-elsewhere?judgeId=${judgeId}`,
      { credentials: 'include', headers: { 'Accept': 'application/json' } }
    );
    if (!res.ok) return false;
    const body = await res.json();
    return !!body.activeElsewhere;
  } catch { return false; }
};

export const claimJudgeActive = async () => {
  try {
    await fetch(`${domain}/api/v1/auth/judge/claim`, {
      method: 'POST',
      credentials: 'include',
    });
  } catch (e) { console.error(e); }
};

export const releaseJudgeActive = async () => {
  try {
    await fetch(`${domain}/api/v1/auth/judge/release`, {
      method: 'DELETE',
      credentials: 'include',
    });
  } catch (e) { console.error(e); }
};
```

On failure of the check (network error / non-200), default to `false` — fail open. A transient blip should not block a judge from scoring.

### `BES-frontend/src/views/JudgeSessionView.vue`

Mount-time sequence after `whoami` restore succeeds:

```
1. Wait until isJudgeSession is true (judgeId + judgeName resolved)
2. const elsewhere = await judgeActiveElsewhere(authStore.judgeId)
3. If elsewhere:
     askConfirm(
       'Judge Already Active',
       `"${judgeName}" is already signed in on another device. Continue anyway?
        Scores from both sessions will be saved, but two devices on the
        same judge can confuse the flow.`,
       async () => {
         await claimJudgeActive();
         await loadDivisions();
       }
     )
     // Cancel path is wired via existing confirmNo + a new escape:
     //   on cancel → releaseJudgeActive() + logout() + router.push('/login')
   Else:
     await claimJudgeActive();
     await loadDivisions();
```

The existing `confirmNo` function clears the dialog only. We add a `cancelCallback` slot to the dialog (or call `handleLogout` directly inline) so cancel triggers logout. Keep the change minimal: the cleanest fit is to inline a second callback parameter on `askConfirm`.

Update `handleLogout` to call `releaseJudgeActive()` before `logout()`, mirroring `releaseEmceeCategory()` in `EmceeSessionView.handleLogout`.

Wording reuses the emcee dialog's tone for consistency.

## Behavior matrix

| Scenario | Before | After |
|---|---|---|
| Judge opens token link on first device | OK | OK; claimed in store |
| Judge opens same token on second device | 409 hard block | Lands on `/judge/session`, sees confirm prompt. Continue → both claimed. Cancel → released + back to login. |
| Judge refreshes their own tab | OK (sameSession in `redeemToken`) | OK. The check excludes the caller's own sessionId; `activeElsewhere` is false. |
| First device closes tab | First session held until container timeout | `SessionExpiryListener` releases the claim immediately. Second device sees no conflict. |
| First device drops network (no clean close) | Stuck until container timeout | Heartbeat gap exceeds 90s → considered inactive on next check. |
| Two devices, judge clicks Logout on one | Other device unaffected | Other device still active (its own session, its own claim). Expected. |

## Edge cases

- **`/auth/heartbeat` is fire-and-forget on the frontend.** If a heartbeat call fails, the next one succeeds and refreshes the timestamp. 90s grace covers a few missed beats. No special handling required.
- **`getActiveSessions` filters on both `judgeId` AND `eventName`.** A judge token is per-(event, judge), so cross-event collisions are impossible — but the filter is cheap and defensive.
- **Race: two devices land on `/judge/session` simultaneously.** Both call `judgeActiveElsewhere` before either has called `claim`. Both get `activeElsewhere: false` and continue silently. This is acceptable — the prompt is best-effort UX, not a lock. The window is small (sub-second), and the cost is one missed warning, not a data issue.
- **No `eventName` on session.** The redeem path always sets `eventName` from the token's event. If it's missing, `/judge/claim` returns 400; the frontend treats this as a soft failure and continues without claiming. Logged for diagnostics.

## Testing

### Backend (`BES/src/test/...`)

- `JudgeActiveStoreTest` — unit test the store directly:
  - `claim` + `getActiveSessions` returns the session
  - `release` removes it
  - Stale heartbeat (> 90s) excluded from `getActiveSessions`
  - Different `judgeId` or `eventName` excluded
- `AuthControllerJudgeActiveTest` — MockMvc integration:
  - `GET /auth/judge/active-elsewhere` returns `false` for fresh session
  - After a second session claims, the first session sees `activeElsewhere: true`
  - `DELETE /auth/judge/release` clears it
  - Token redeem no longer returns 409 for a duplicate judge token (the original block check)

### Frontend (`BES-frontend/src/utils/__tests__/...`)

- `judgeActiveElsewhere` returns `false` on non-OK response (fail-open)
- `JudgeSessionView` mount flow: when `judgeActiveElsewhere` resolves `true`, the confirm dialog is shown; when `false`, divisions load directly. (Component test with mocked api module.)

### Manual

- Two devices on the same judge token: verify prompt + continue + cancel paths.
- Refresh same tab: verify no prompt.
- Close first tab without logout: verify second device's next `active-elsewhere` call returns `false` after a few seconds (session destroy event) or after ~90s (heartbeat timeout).

## Migration / rollout

- Pure additive changes; no DB migration.
- The removal of the 409 path is the only behavior change. There is no client today that depends on receiving 409 for a judge token re-redeem (`TokenAuth.vue` treats any non-authenticated response as an error and shows "Link invalid"), so we may want to verify no other caller surfaces a special-case 409 message. Spot check: `redeemToken` in `api.js` returns `{ authenticated: false, status, error }` — callers only branch on `authenticated`.
- No flag-gating. The new behavior is strictly more permissive and matches the existing emcee UX.

## Files touched (summary)

| File | Kind |
|---|---|
| `BES/src/main/java/com/example/BES/services/JudgeActiveStore.java` | NEW |
| `BES/src/main/java/com/example/BES/controllers/AuthController.java` | MOD — add `ROLE_JUDGE` to `UNLIMITED_ROLES`, 3 new endpoints, heartbeat hook |
| `BES/src/main/java/com/example/BES/config/SessionExpiryListener.java` | MOD — release on session destroy |
| `BES/src/test/java/com/example/BES/services/JudgeActiveStoreTest.java` | NEW |
| `BES/src/test/java/com/example/BES/controllers/AuthControllerJudgeActiveTest.java` | NEW |
| `BES-frontend/src/utils/api.js` | MOD — 3 new helpers |
| `BES-frontend/src/views/JudgeSessionView.vue` | MOD — mount-time check + claim, release on logout |
| `BES-frontend/src/utils/__tests__/JudgeSessionView.spec.js` | NEW (or extend existing) |
