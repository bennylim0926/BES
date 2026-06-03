# Battle Division State Persistence — Design Spec
**Date:** 2026-06-02  
**Status:** Approved  
**GitHub Issue:** #42  

---

## Goal

Allow operators to switch between divisions (genres) mid-event and recover from page refreshes without losing battle progress. All four battle views — `BattleControl`, `BracketVisualization`, `BattleOverlay`, `BattleJudge` — must restore to the exact correct state on a cold page load, including after a backend restart.

---

## Design Decisions Summary

| Decision | Choice | Rationale |
|---|---|---|
| Persistence approach | Option A — thin backend persistence layer | Minimal blast radius; localStorage stays as warm cache |
| State scope | Per (eventName, genreName) | Matches existing frontend localStorage key structure |
| Backend state model | Active-genre singleton + per-genre state rows | Keeps existing API endpoints unchanged |
| Restart recovery | DB-backed (`@PostConstruct` reload) | Backend restart mid-event must be recoverable |
| Cross-client sync | New `/topic/battle/state` WS topic + `GET /api/v1/battle/state` REST | Single snapshot serves all clients on mount and genre switch |
| Recovery UX | Confirmation prompt in BattleControl; flexible manual navigation always available | Operator can dismiss prompt and navigate to any pair manually |
| Judge vote recovery | Votes persisted in DB; restored via `GET /api/v1/battle/state` on mount | Judges see their confirmed vote immediately on refresh without re-voting |

---

## 1. Database Schema

Two new tables. Next migration: `V17`.

### `battle_genre_state`

One row per `(event_name, genre_name)`. Upserted on every significant state mutation.

```sql
CREATE TABLE battle_genre_state (
  id                        BIGSERIAL PRIMARY KEY,
  event_name                VARCHAR(255) NOT NULL,
  genre_name                VARCHAR(255) NOT NULL,
  bracket_json              TEXT,
  top_size                  INTEGER,
  current_round_index       INTEGER      DEFAULT 0,
  current_pair_left         VARCHAR(255),
  current_pair_left_members TEXT,
  current_pair_right        VARCHAR(255),
  current_pair_right_members TEXT,
  is_final                  BOOLEAN      DEFAULT FALSE,
  battle_phase              VARCHAR(20)  DEFAULT 'IDLE',
  judges_json               TEXT,
  updated_at                TIMESTAMP    DEFAULT NOW(),
  UNIQUE (event_name, genre_name)
);
```

- `bracket_json` — serialised `{ topSize, rounds }` object (matches `SetBracketStateDto` shape)
- `current_pair_left_members` / `current_pair_right_members` — JSON arrays of member name strings
- `judges_json` — JSON array of `{ id, name, vote }` objects; votes included so judge view restores confirmed votes after a backend restart
- Upsert key: `(event_name, genre_name)` — `ON CONFLICT DO UPDATE SET ...`

### `battle_active_genre`

Singleton (always one row, `id = 1`) tracking which event+genre is currently live.

```sql
CREATE TABLE battle_active_genre (
  id         INTEGER PRIMARY KEY DEFAULT 1,
  event_name VARCHAR(255),
  genre_name VARCHAR(255)
);

INSERT INTO battle_active_genre (id, event_name, genre_name) VALUES (1, NULL, NULL);
```

---

## 2. Backend — New Endpoints

All added to `BattleController`. Existing endpoints are **unchanged**.

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/v1/battle/active-genre` | Admin, Organiser | Switch active event+genre; saves current in-memory state to DB, loads DB state for new genre, broadcasts full snapshot |
| `GET`  | `/api/v1/battle/active-genre` | Public | Returns `{ eventName, genreName }` |
| `GET`  | `/api/v1/battle/state`        | Public | Returns full state snapshot for the active genre |

### `GET /api/v1/battle/state` response shape

```json
{
  "eventName": "Spring Jam 2026",
  "genreName": "Breaking Top 16",
  "bracket": { "topSize": 16, "rounds": { "Top16": [...], "Top8": [...] } },
  "currentRoundIndex": 2,
  "currentPair": {
    "left": "Alpha", "leftMembers": [],
    "right": "Bravo", "rightMembers": [],
    "isFinal": false
  },
  "battlePhase": "LOCKED",
  "judges": [
    { "id": 1, "name": "Judge A", "vote": 0 },
    { "id": 2, "name": "Judge B", "vote": -1 }
  ]
}
```

Returns `{}` if no active genre is set.

### New WS topic: `/topic/battle/state`

Broadcast whenever `POST /api/v1/battle/active-genre` is called. Payload is the same shape as `GET /api/v1/battle/state`. All clients subscribing to this topic can react to genre switches without polling.

---

## 3. Backend — `BattleService` Changes

### New in-memory fields

```java
private String activeEventName;
private String activeGenreName;
private Integer currentRoundIndex = 0;
```

`currentRoundIndex` is updated by `BattleControl.vue` via the existing `POST /api/v1/battle/bracket` call — the bracket DTO already includes `topSize` and `rounds`; `currentRoundIndex` is added to `SetBracketStateDto` so the frontend can pass it explicitly whenever it advances a round. `persistActiveState()` then writes it to DB as part of every `setBracketStateService` call.

### New method: `switchActiveGenreService(String eventName, String genreName)`

1. Persist current in-memory state to `battle_genre_state` for `(activeEventName, activeGenreName)` (skip if either is null)
2. Update `battle_active_genre` singleton to `(eventName, genreName)`
3. Load `battle_genre_state` row for `(eventName, genreName)` from DB — if no row exists, start fresh (IDLE, empty bracket, no pair)
4. Populate in-memory fields from loaded row
5. Broadcast full state snapshot to `/topic/battle/state`

### New private method: `persistActiveState()`

Upserts the current in-memory state into `battle_genre_state` for `(activeEventName, activeGenreName)`. Called at the end of every existing mutation:
- `setBracketStateService`
- `setBattlerPairService`
- `setBattlePhaseService`
- `setBattleJudgeService`
- `removeBattleJudgeService`
- `resetJudgeVotesService`
- `setScoreService` (on successful score, after phase auto-transition)

Skip persist if `activeEventName` or `activeGenreName` is null.

### `@PostConstruct` startup reload

On Spring startup, read `battle_active_genre` singleton. If `event_name` and `genre_name` are set, load the corresponding `battle_genre_state` row into memory. This restores all in-memory state after a backend restart.

---

## 4. New DTOs

### `SetActiveGenreDto`
```java
@NotBlank String eventName;
@NotBlank String genreName;
```

### `BattleStateDto` (response)
```java
String eventName;
String genreName;
Object bracket;           // { topSize, rounds }
Integer currentRoundIndex;
CurrentPairDto currentPair;
String battlePhase;
List<JudgeStateDto> judges;
```

---

## 5. Frontend — Per-View Changes

### 5.1 BattleControl.vue

**Genre switch (existing `watch(selectedGenre)`):**

After the existing localStorage save + `broadcastBracket()` + `restoreAndBroadcastGenreBattle()` calls, add:
```js
await setActiveGenre(selectedEvent.value, newVal)  // POST /api/v1/battle/active-genre
```
This triggers the backend to save old genre state, load new genre state from DB, and broadcast the full snapshot to `BracketVisualization` and `BattleOverlay` on other machines.

**On mount — restore current round + recovery prompt:**

After `initialiseDropdown()` and before wiring WS subscriptions, call `GET /api/v1/battle/state`. If `state.battlePhase !== 'IDLE'` and `state.currentPair.left`:

1. Set `currentRound.value = state.currentRoundIndex`
2. Show a dismissable recovery banner at the top of BattleControl:

   > **"IN PROGRESS: Round [X] — [Left] vs [Right]"**  `[Jump to pair]`  `[Dismiss]`

   "Jump to pair" sets the bracket selection to that pair (same logic as `restoreAndBroadcastGenreBattle` but using backend-sourced values).  
   "Dismiss" hides the banner. The bracket grid is already correct from localStorage warm cache; the operator can navigate to any pair manually.

The `if (oldVal)` guard in `restoreAndBroadcastGenreBattle` is left unchanged — it handles genre-switch restores. Mount restore goes through the new backend-state path above.

---

### 5.2 BracketVisualization.vue

**On mount:** Replace the existing `getBracketState()` call with `GET /api/v1/battle/state`:
```js
const state = await getBattleState()
if (state?.bracket) bracketState.value = state.bracket
if (state?.currentPair?.left) activePair.value = { left: state.currentPair.left, right: state.currentPair.right }
if (state?.genreName) currentGenre.value = state.genreName
```

This also fixes the broken genre ticker (currently `currentGenre` is only updated via `/topic/battle/genre` which has no publisher).

**New WS subscription (inside `wsClient.onConnect`):** Subscribe to `/topic/battle/state`. On receipt, replace `bracketState`, `activePair`, and `currentGenre` from the snapshot. This handles genre switches from the operator on another machine.

Keep existing fine-grained subscriptions (`/topic/battle/bracket`, `/topic/battle/battle-pair`, `/topic/battle/score`, `/topic/battle/champion-reveal`, `/topic/battle/overlay-config`) — they continue to handle live match updates within the active genre.

---

### 5.3 BattleOverlay.vue

**On mount:** After fetching `getOverlayConfig()`, call `GET /api/v1/battle/state`:
```js
const state = await getBattleState()
if (state?.battlePhase) battlePhase.value = state.battlePhase
if (state?.currentPair?.left) await updateBattlePair(state.currentPair)
if (state?.judges?.length) updateBattleJudge({ judges: state.judges })
```

This fixes: overlay refreshing during VOTING showing IDLE until next broadcast.

**New WS subscription:** Subscribe to `/topic/battle/state`. On receipt, call `updateBattlePair` with the new pair. This handles cross-machine genre switches — overlay resets to the new genre's current pair automatically.

---

### 5.4 BattleJudge.vue

**Step 5 on mount (vote restore) — change priority order:**

```js
// After step 4 (getBattleJudges + resolveJudgeIdentity):
if (battlePhase.value === 'VOTING' && judgeId.value != null) {
  const backendJudge = battleJudges.value?.judges?.find(j => j.id === judgeId.value)
  const backendVote  = backendJudge?.vote
  if (backendVote === 0 || backendVote === 1 || backendVote === -1) {
    confirmedVote.value = backendVote   // backend is authoritative after restart
  } else {
    const stored = restoreVoteFromStorage()
    if (stored !== null) confirmedVote.value = stored  // localStorage fallback
  }
}
```

Backend vote (`judges_json` restored from DB) takes priority. localStorage fallback handles the window where the vote was just cast but the DB hasn't been written yet (race condition between WS broadcast and DB persist is < 1 ms in practice).

No change to the existing real-time vote subscription (`/topic/battle/vote/${judgeId}`).

---

## 6. New Frontend API Function

Add to `utils/api.js`:

```js
export const setActiveGenre = async (eventName, genreName) => {
  return await fetch(`${domain}/api/v1/battle/active-genre`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ eventName, genreName })
  })
}

export const getBattleState = async () => {
  const res = await fetch(`${domain}/api/v1/battle/state`, { credentials: 'include' })
  if (!res.ok) return null
  return await res.json()
}

export const getActiveGenre = async () => {
  const res = await fetch(`${domain}/api/v1/battle/active-genre`, { credentials: 'include' })
  if (!res.ok) return null
  return await res.json()
}
```

---

## 7. Error Handling

| Scenario | Behaviour |
|----------|-----------|
| `GET /api/v1/battle/state` returns `{}` (no active genre) | All views render in empty/idle state — same as today |
| DB unavailable on startup | Log error, continue with empty in-memory state — degrade gracefully |
| `persistActiveState()` fails | Log error, do not block the mutation or WS broadcast |
| Genre switch while a match is VOTING | Persist current state first (including in-flight votes), then switch — operator must manage this deliberately |
| Backend restart during VOTING | Phase restored to VOTING from DB; judges' existing votes restored; judges who haven't voted yet can still vote |

---

## 8. Out of Scope (tracked in #42 comment)

- **Option B** — removing frontend localStorage entirely; backend as sole source of truth
- **Option C** — scoping all battle endpoints by event+genre in the request body
