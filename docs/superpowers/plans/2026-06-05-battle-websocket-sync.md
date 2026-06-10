# Battle WebSocket Sync Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all localStorage-based battle state caching in BattleControl.vue with DB-backed WebSocket state sync, and add reconnect re-hydration to all battle views (Overlay, Bracket, Judge, Chart).

**Architecture:** The backend already persists every mutation to `battle_genre_state` (DB) and broadcasts full state snapshots via `/topic/battle/state`. This plan makes ALL battle views consume that WS feed with a "subscribe first, hydrate second" pattern that eliminates the gap between WS connection and state sync. Diff logic prevents unnecessary re-renders/animations.

**Tech Stack:** Java 17, Spring Boot 3.x, PostgreSQL (Flyway), Vue 3 (Composition API), STOMP/WebSocket

---

## File Structure

| File | Responsibility |
|------|---------------|
| `V32__add_tiebreaker_to_battle_genre_state.sql` | New DB column for tie-breaker list |
| `BattleGenreState.java` | JPA entity — add resolvedParticipantsJson field |
| `SetResolvedParticipantsDto.java` | New DTO for tie-breaker save endpoint |
| `BattleService.java` | Include tie-breaker in `getBattleStateService()` |
| `BattleController.java` | New `POST /resolved-participants` endpoint |
| `api.js` | Two new API functions; add `subscribeToChannel` import |
| `Score.vue` | Replace localStorage.setItem with API call |
| `BattleControl.vue` | Remove ~60 localStorage calls, add hydrateFromState + WS sub + diff |
| `BattleOverlay.vue` | Add `/topic/battle/state` sub with diff, reconnect re-hydrate |
| `BracketVisualization.vue` | Add diff logic to existing `/topic/battle/state` sub, reconnect re-hydrate |
| `BattleJudge.vue` | Add `/topic/battle/state` sub with diff, reconnect re-hydrate |
| `Chart.vue` | Add `/topic/battle/state` sub with diff, reconnect re-hydrate |

---

### Task 1: DB Migration

**Files:**
- Create: `BES/src/main/resources/db/migration/V32__add_tiebreaker_to_battle_genre_state.sql`

- [ ] **Step 1: Create the migration file**

```sql
ALTER TABLE battle_genre_state ADD COLUMN resolved_participants_json TEXT;
```

- [ ] **Step 2: Verify migration version is unique**

```bash
ls BES/src/main/resources/db/migration/V3*.sql
```
Expected: V32 does not already exist.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V32__add_tiebreaker_to_battle_genre_state.sql
git commit -m "feat: add resolved_participants_json column to battle_genre_state"
```

---

### Task 2: Entity Update

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/BattleGenreState.java`

- [ ] **Step 1: Add the new field to BattleGenreState**

Add this field alongside the existing `@Column(name = "smoke_list_json")` field (before `updatedAt`):

```java
@Column(name = "resolved_participants_json", columnDefinition = "TEXT")
private String resolvedParticipantsJson;
```

- [ ] **Step 2: Verify the file compiles**

```bash
cd BES && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/BattleGenreState.java
git commit -m "feat: add resolvedParticipantsJson field to BattleGenreState entity"
```

---

### Task 3: DTO

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/battle/SetResolvedParticipantsDto.java`

- [ ] **Step 1: Create the DTO class**

```java
package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class SetResolvedParticipantsDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String genreName;

    private List<String> participants;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getGenreName() { return genreName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd BES && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/battle/SetResolvedParticipantsDto.java
git commit -m "feat: add SetResolvedParticipantsDto"
```

---

### Task 4: BattleService Update

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

- [ ] **Step 1: Add resolved participants to getBattleStateService()**

In the `getBattleStateService()` method (~line 384), add after `state.put("champion", champion);`:

```java
// Restore tie-breaker resolved list from DB if present
String resolvedJson = null;
if (activeEventName != null && activeGenreName != null) {
    var st = battleGenreStateRepository
        .findByEventNameAndGenreName(activeEventName, activeGenreName).orElse(null);
    if (st != null) resolvedJson = st.getResolvedParticipantsJson();
}
state.put("resolvedParticipants", resolvedJson != null ? resolvedJson : "");
```

- [ ] **Step 2: Add save method for resolved participants**

Add this method to BattleService (near the existing persistence methods, after `getChampionsForEvent`):

```java
@Transactional
public void setResolvedParticipants(String eventName, String genreName, List<String> participants) {
    try {
        BattleGenreState s = battleGenreStateRepository
            .findByEventNameAndGenreName(eventName, genreName)
            .orElse(new BattleGenreState());
        s.setEventName(eventName);
        s.setGenreName(genreName);
        s.setResolvedParticipantsJson(
            participants != null ? objectMapper.writeValueAsString(participants) : null);
        s.setUpdatedAt(LocalDateTime.now());
        battleGenreStateRepository.save(s);
    } catch (Exception e) {
        System.err.println("Failed to save resolved participants: " + e.getMessage());
    }
}
```

Also add the import for `LocalDateTime` if not already present (it is — line 3).

- [ ] **Step 3: Also include resolvedParticipants in `loadGenreStateIntoMemory` for completeness**

No change needed — `resolvedParticipantsJson` is read from DB on-demand in `getBattleStateService()`.

- [ ] **Step 4: Verify compilation**

```bash
cd BES && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "feat: add resolved participants persistence to BattleService"
```

---

### Task 5: BattleController Update

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

- [ ] **Step 1: Add the import for the new DTO**

Add to the imports block (near line 46, after the `SetVoteDto` import):

```java
import com.example.BES.dtos.battle.SetResolvedParticipantsDto;
```

- [ ] **Step 2: Add the POST endpoint**

Add before the closing `}` of the class (after the `getBattleState()` method, around line 377):

```java
@PostMapping("/resolved-participants")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setResolvedParticipants(@Valid @RequestBody SetResolvedParticipantsDto dto) {
    battleService.setResolvedParticipants(
        dto.getEventName(), dto.getGenreName(), dto.getParticipants());
    return ResponseEntity.ok(Map.of("message", "Resolved participants saved"));
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd BES && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add POST /resolved-participants endpoint"
```

---

### Task 6: Frontend API Functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add `setResolvedParticipants` function**

Add after the `getSmokeList` function (~line 740):

```javascript
export const setResolvedParticipants = async (eventName, genreName, participants) => {
  try {
    return await fetch(`${domain}/api/v1/battle/resolved-participants`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, genreName, participants })
    })
  } catch (e) { console.error(e) }
}
```

- [ ] **Step 2: Verify the file parses without syntax errors**

```bash
cd BES-frontend && node -e "require('./src/utils/api.js')" 2>&1 || true
```
Expected: No unexpected syntax errors (module errors from import.meta are fine).

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add setResolvedParticipants API function"
```

---

### Task 7: Score.vue — Replace localStorage with API

**Files:**
- Modify: `BES-frontend/src/views/Score.vue`

- [ ] **Step 1: Import the new API function**

Add to the imports (find the existing `import { ... } from '@/utils/api'` block):

```javascript
import { ..., setResolvedParticipants } from '@/utils/api'
```
(Add `setResolvedParticipants` to the existing import list.)

- [ ] **Step 2: Replace localStorage setItem with API call in `confirmTieBreaker`**

Find the `confirmTieBreaker` function (~line 196). Replace:

```javascript
const confirmTieBreaker = () => {
  if (tieBreakerWinners.value.size === spotsFromTie.value) {
    tieBreakerConfirmed.value = true
    saveTieBreaker()
    // Save resolved names for BattleControl to consume
    const resolved = finalRows.value.map(r => r.participantName)
    localStorage.setItem(tbResolvedKey.value, JSON.stringify(resolved))
  }
}
```

With:

```javascript
const confirmTieBreaker = async () => {
  if (tieBreakerWinners.value.size === spotsFromTie.value) {
    tieBreakerConfirmed.value = true
    saveTieBreaker()
    // Save resolved names server-side so BattleControl reads them from DB
    const resolved = finalRows.value.map(r => r.participantName)
    await setResolvedParticipants(selectedEvent.value, selectedGenre.value, resolved)
  }
}
```

- [ ] **Step 3: Remove localStorage from `clearTieBreaker` (the reset function)**

Find the `clearTieBreaker` function (around line 69). Remove the line:
```javascript
localStorage.removeItem(tbResolvedKey.value)
```

- [ ] **Step 4: Remove the `tbResolvedKey` computed property** since it's now unused

Remove this computed:
```javascript
const tbResolvedKey = computed(() => {
  const n = bracketSize.value
  return `tbResolved_${selectedEvent.value}_${selectedGenre.value}_${n}`
})
```

Also remove the `bracketSize` import if it was only used there — but `bracketSize` is used elsewhere in Score.vue, so keep it.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "refactor: replace localStorage tie-breaker with API call in Score.vue"
```

---

### Task 8: BattleControl.vue — Part 1: Add hydrateFromState + WS

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

This is the core refactor. We'll do it in three sub-tasks to keep the reviewable units small.

- [ ] **Step 1: Import `subscribeToChannel`**

Add `subscribeToChannel` to the websocket import (line 8):

```javascript
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'
```

- [ ] **Step 2: Import `setResolvedParticipants` from API (if not already)**

Add `setResolvedParticipants` to the API import (line 2). The full API import already has most functions; add `setResolvedParticipants` to the list.

- [ ] **Step 3: Add `lastAppliedState` ref for diff tracking**

After line 34 (`const genreChampions = ref({})`), add:

```javascript
const lastAppliedState = ref('')  // JSON string of last applied /topic/battle/state snapshot
```

- [ ] **Step 4: Add the `hydrateFromState` function**

Add after the `lastAppliedState` ref declaration:

```javascript
// Single entry point for full-state hydration from /topic/battle/state or REST API.
// Diffs against lastAppliedState to skip no-op updates and prevent animation disruption.
const hydrateFromState = (state) => {
  if (!state) return
  const snapshot = JSON.stringify(state)
  if (snapshot === lastAppliedState.value) return
  lastAppliedState.value = snapshot

  if (state.bracket) {
    if (state.bracket.topSize !== undefined) {
      const size = Number(state.bracket.topSize)
      if (!isNaN(size) && size !== Number(topSize.value)) {
        skipSizeChangeClear = true
        topSize.value = size
      }
    }
    if (state.bracket.rounds) {
      rounds.value = state.bracket.rounds
    }
  }
  if (state.currentRoundIndex !== undefined) {
    currentRound.value = state.currentRoundIndex
  }
  if (state.currentPair) {
    const pair = state.currentPair
    if (pair.left || pair.right) {
      // Find which round and match this pair belongs to
      const bracketRounds = state.bracket?.rounds ?? rounds.value
      let topKey = pair.isFinal ? 'Top2' : null
      if (!topKey && bracketRounds) {
        for (const key of Object.keys(bracketRounds)) {
          const matchList = bracketRounds[key]
          if (!Array.isArray(matchList)) continue
          if (matchList.some(m => Array.isArray(m) && m[0] === pair.left && m[1] === pair.right)) {
            topKey = key; break
          }
        }
      }
      if (topKey) {
        currentTop.value = topKey
        const pairList = bracketRounds[topKey] ?? []
        const pairIdx = pairList.findIndex(
          m => Array.isArray(m) && m[0] === pair.left && m[1] === pair.right
        )
        if (pairIdx >= 0) currentRound.value = pairIdx
        currentBattle.value = [pairIdx >= 0 ? pairIdx : 0, pairList]
      }
    } else {
      currentBattle.value = []
      currentTop.value = ''
    }
  }
  if (state.battlePhase) {
    battlePhase.value = state.battlePhase
  }
  if (state.champion) {
    genreChampions.value = { ...genreChampions.value, [activeGenreNameForHydrate(state.genreName)]: state.champion }
  }
  if (state.judges?.length) {
    battleJudges.value = { judges: state.judges }
  }
  if (state.resolvedParticipants && state.resolvedParticipants !== '') {
    try {
      resolvedParticipants.value = JSON.parse(state.resolvedParticipants)
    } catch (_) { resolvedParticipants.value = null }
  }
}

// Helper: returns the current genre name for champion tracking
const activeGenreNameForHydrate = (genreName) => {
  return genreName || selectedGenre.value
}
```

- [ ] **Step 5: Modify `onMounted` to use subscribe+hydrate pattern**

Replace the current WS setup in `onMounted` (starting at line 1895):

```javascript
// Old (lines 1895-1917):
//   wsClient.value = createClient()
//   wsClient.value.onConnect = () => {
//     wsClient.value.subscribe('/topic/battle/phase', ...)
//     syncJudgeVoteSubscriptions()
//   }
//   wsClient.value.activate()

// New:
wsClient.value = createClient()
wsClient.value.onConnect = () => {
  // Subscribe to full state snapshots — used for initial hydration, genre switch, and reconnect recovery.
  // Diff logic prevents re-rendering already-current state.
  subscribeToChannel(wsClient.value, '/topic/battle/state', (msg) => {
    // Guard: ignore state broadcasts for a different genre (stale WS from genre switch)
    if (msg.genreName && msg.genreName !== selectedGenre.value) return
    hydrateFromState(msg)
    syncJudgeVoteSubscriptions()
  })

  // Phase subscription — keep for real-time phase transitions
  wsClient.value.subscribe('/topic/battle/phase', (raw) => {
    const msg = JSON.parse(raw.body)
    if (msg.genre && msg.genre !== selectedGenre.value) return
    battlePhase.value = msg.phase
    if (msg.phase === 'DECIDED' && msg.champion) {
      genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: msg.champion }
    }
  })

  syncJudgeVoteSubscriptions()
}
wsClient.value.activate()

// After WS activation, hydrate from REST to cover the gap before subscription went live
if (selectedEvent.value && selectedGenre.value) {
  const battleState = await getBattleState()
  if (battleState?.battlePhase && battleState.battlePhase !== 'IDLE' && battleState.currentPair?.left) {
    recoveryState.value = battleState
    await jumpToRecoveredPair()
    showRecoveryBanner.value = true
  }
  // Hydrate from REST response (WS may already have delivered it, diff will skip)
  hydrateFromState(battleState)
}
```

Remove the separate `getBattleState()` call from lines 1889-1893 since it's now part of the post-activation hydrate above.

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "refactor: add hydrateFromState + WS state subscription to BattleControl"
```

---

### Task 9: BattleControl.vue — Part 2: Remove localStorage from mutations

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Remove localStorage.setItem calls from `startBattleRound` (~line 270)**

Remove lines 270 and 281:
```javascript
// Remove:
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
// (line 270)
localStorage.setItem('currentTop', top)
// (line 281)
```

Keep the `saveGenreBattleState(selectedGenre.value)` calls — but note: we'll remove `saveGenreBattleState` entirely in Part 3.

- [ ] **Step 2: Remove localStorage from `initiateBattlePair` (~line 307, 318)**

Remove lines 307 and 318:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
localStorage.setItem('currentTop', top)
```

- [ ] **Step 3: Remove localStorage from `nextPair` (~line 352)**

Remove `localStorage.removeItem('currentTop')` (line 352).

- [ ] **Step 4: Remove localStorage from `seedSmokeBracket` (~line 596)**

Remove:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
```

- [ ] **Step 5: Remove localStorage from `seedStandardBracket` — end of for loop (~line 632)**

Remove:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
```

- [ ] **Step 6: Remove localStorage from `applyToFirstRound` (~line 645, 662)**

Remove two occurrences of:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
```

- [ ] **Step 7: Remove localStorage from `onDrop` (~line 710, 729)**

Remove two occurrences of:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
```

- [ ] **Step 8: Remove localStorage from `onSmokeDrop` (~line 744, 758)**

Remove two occurrences of:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
```

- [ ] **Step 9: Remove localStorage from `setWinner` (~line 892, 900)**

Remove two occurrences of:
```javascript
localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
```

- [ ] **Step 10: Remove localStorage from 7-to-smoke round manipulation functions**

Remove the localStorage.setItem calls in:
- `updateSmokePair` (~line 931)
- `update7toSmokeMatch` (~line 936)
- `advanceSmokeByOne` (~line 950)
- `setNextSmokePair` (~line 1354)

All are the same pattern: `localStorage.setItem(\`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds\`, JSON.stringify(toRaw(rounds.value)))`

- [ ] **Step 11: Remove localStorage from `lockChampion` (~line 1413)**

Remove:
```javascript
localStorage.setItem(genreChampionLocalKey(selectedGenre.value), winner)
```

- [ ] **Step 12: Remove localStorage from `unlockChampion` (~line 1422)**

Remove:
```javascript
localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
```

- [ ] **Step 13: Remove localStorage from `confirmResetBracket` (~line 1476, 1489-1490, 1497)**

Remove:
```javascript
localStorage.removeItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`)
localStorage.removeItem('currentTop')
localStorage.removeItem(sizeStateKey(topSize.value))
localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
```

- [ ] **Step 14: Remove localStorage from `confirmSizeChange` (~line 1512-1513)**

Remove:
```javascript
localStorage.removeItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`)
localStorage.removeItem(sizeStateKey(topSize.value))
```

- [ ] **Step 15: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "refactor: remove localStorage writes from all battle mutation functions"
```

---

### Task 10: BattleControl.vue — Part 3: Remove localStorage from watchers, helpers, onMounted

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Remove localStorage key helper functions**

Delete these helper functions (and the `genreTopSizeKey`, `genreChampionLocalKey`, `genreJudgeKey`, `sizeStateKey`, `roundIdxKey` functions):

| Function | Lines |
|---|---|
| `genreBattleStateKey` | ~1059 |
| `genreTopSizeKey` | ~1602 |
| `genreChampionLocalKey` | ~1603 |
| `genreJudgeKey` | ~1223 |
| `sizeStateKey` | ~1714 |
| `roundIdxKey` | ~1715 |

Also delete:
- `saveGenreBattleState` function (~1061-1073)
- `saveGenreJudges` function (~1226-1229)

- [ ] **Step 2: Replace `restoreAndBroadcastGenreBattle` to use WS state instead of localStorage**

Replace the function (~1075-1141) with a simplified version:

```javascript
const restoreAndBroadcastGenreBattle = async (genre) => {
  // Reset local state first, then wait for /topic/battle/state WS to deliver the
  // real state from the backend. switchActiveGenreService already broadcast it.
  currentBattle.value = []
  currentTop.value = ''
  currentRound.value = 0
  currentWinner.value = -2
  battlePhase.value = 'IDLE'

  // Fetch state from REST as immediate source; WS will keep it in sync thereafter
  const state = await getBattleState()
  if (state) hydrateFromState(state)

  // Also re-fetch judges (WS /topic/battle/judges will keep them in sync)
  const judges = await getBattleJudges()
  if (judges) battleJudges.value = judges
}
```

- [ ] **Step 3: Remove localStorage from `watch(selectedGenre)`**

In the `watch(selectedGenre, ...)` block (~1640-1712), remove:

```javascript
// Remove these lines:
if (oldVal) localStorage.setItem(genreTopSizeKey(oldVal), String(topSize.value))
// (~1646)

localStorage.setItem('topSize', '7')
// (~1656)

const savedSize = localStorage.getItem(genreTopSizeKey(newVal))
// (~1659) — replace with: fetch from backend state instead

localStorage.setItem('topSize', String(restoredSize))
// (~1664)

const pending = localStorage.getItem(genreChampionLocalKey(newVal))
if (pending) genreChampions.value = { ...genreChampions.value, [newVal]: pending }
// (~1669-1672) — champion is loaded from backend /topic/battle/state now

localStorage.setItem("selectedGenre", newVal)
// (~1673) — KEEP this line, it's nav state not battle state

const storedRounds = localStorage.getItem(`Top${topSize.value}${selectedEvent.value}${newVal}Rounds`)
rounds.value = JSON.parse(storedRounds) || initRounds()
// (~1674-1675) — replace with: rounds come from /topic/battle/state
```

New simplified `watch(selectedGenre)` topSize restore:

```javascript
// Replace the topSize restore block (~1648-1665):
// Restore per-genre topSize — smoke auto-detection takes priority.
const genreNeedsSmoke = newVal.toLowerCase().includes('7 to smoke') || newVal.toLowerCase().includes('7tosmoke')
if (genreNeedsSmoke) {
  if (Number(topSize.value) !== 7) {
    topSize.value = 7
  }
} else {
  // Fall back to 16 for a genre switch (don't inherit outgoing genre's size).
  // The DB-backed state from /topic/battle/state will override this if saved.
  if (oldVal) {
    const state = await getBattleState()
    if (state?.bracket?.topSize !== undefined) {
      const dbSize = Number(state.bracket.topSize)
      if (!isNaN(dbSize) && dbSize !== Number(topSize.value)) {
        skipSizeChangeClear = true
        topSize.value = dbSize
      }
    } else if (!oldVal) {
      // Initial load — keep current topSize
    } else {
      topSize.value = 16
    }
  }
}
```

Replace the rounds restore (~1674-1675):
```javascript
// Rounds are restored by hydrateFromState when /topic/battle/state arrives
// after the setActiveGenre call below. Init to empty for now.
rounds.value = initRounds()
```

Replace champion restore (~1668-1672):
```javascript
// Champion is loaded from backend via /topic/battle/state or getBattleState().
// No localStorage fallback needed.
```

Remove the `if (storedRounds && oldVal) broadcastBracket()` line (~1686) — replaced by:
```javascript
if (oldVal) broadcastBracket()
```
(simply always broadcast on genre switch — the rounds are now initRounds() or will be hydrated)

- [ ] **Step 4: Remove localStorage from `watch(activeRoundIdx)` (~1720-1724)**

Remove the entire `localStorage.setItem(roundIdxKey(), String(idx))` line. Keep the watcher but empty its body or remove the watcher entirely:

```javascript
// Round tab selection is transient — no persistence needed since
// /topic/battle/state recovers the active pair on reconnect.
```

Delete the `watch(activeRoundIdx, ...)` block.

- [ ] **Step 5: Remove localStorage from `watch(topSize)` (~1726-1783)**

Remove:
```javascript
localStorage.setItem(sizeStateKey(oldVal), JSON.stringify({...}))
// (~1736-1741) — entire save block

localStorage.setItem("topSize", newVal)
// (~1743)

localStorage.setItem(genreTopSizeKey(selectedGenre.value), String(newVal))
// (~1746)

const storedRounds = localStorage.getItem(`Top${newVal}${selectedEvent.value}${selectedGenre.value}Rounds`)
rounds.value = JSON.parse(storedRounds) || initRounds()
// (~1748-1749) — replace with: rounds.value = initRounds()

const savedSizeState = localStorage.getItem(sizeStateKey(newVal))
// (~1767-1777) — entire restore block
```

Replace the rounds line (~1748-1749) with:
```javascript
rounds.value = initRounds()
```

Replace the savedRoundIdx line (~1731-1732) with:
```javascript
activeRoundIdx.value = 0
```

- [ ] **Step 6: Remove localStorage from `onMounted`**

Remove these lines from onMounted:
```javascript
// (~1845-1849) topSize restore:
const savedSize = localStorage.getItem(genreTopSizeKey(selectedGenre.value))
if (savedSize) {
  topSize.value = Number(savedSize)
  localStorage.setItem('topSize', savedSize)
}
// Replace with: topSize comes from DB state via getBattleState()

// (~1852-1855) round index restore:
const savedRoundIdx = localStorage.getItem(roundIdxKey())
if (savedRoundIdx !== null) {
  activeRoundIdx.value = Math.min(Number(savedRoundIdx), roundSizes.value.length - 1)
}
// Replace with: activeRoundIdx.value = 0

// (~1864-1867) judge localStorage seeding:
const savedRaw = localStorage.getItem(genreJudgeKey(selectedGenre.value))
if (savedRaw === null) {
  saveGenreJudges(selectedGenre.value)
}
// Replace with: nothing — judges come from backend

// (~1885-1887) currentTop restore:
if (battlePhase.value !== 'IDLE') {
  const storedTop = localStorage.getItem('currentTop')
  if (storedTop) currentTop.value = storedTop
}
// Replace with: hydrateFromState handles currentTop
```

- [ ] **Step 7: Remove the `watch(uniqueGenres)` localStorage champion loading (~1605-1615)**

Remove the entire block that reads `localStorage.getItem(genreChampionLocalKey(g))`. Champions are loaded from backend:

```javascript
// Replace with:
watch(uniqueGenres, (genres) => {
  if (!selectedEvent.value || !genres?.length) return
  // Champions are loaded from backend via getBattleChampions() in onMounted
}, { immediate: true })
```

- [ ] **Step 8: Remove localStorage.removeItem calls from champion tie watchers (~1624, 1636)**

Remove:
```javascript
localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
```
from both `watch(showFinalReveal)` and `watch([allJudgesVoted, tentativeWinner])`.

- [ ] **Step 9: Remove localStorage from `syncJudgesForGenre` (~1250)**

Replace:
```javascript
const raw = JSON.parse(localStorage.getItem(genreJudgeKey(newGenre)) ?? '[]')
```
With:
```javascript
// Judges are persisted in DB via battle_genre_state.judges_json.
// On genre switch, the backend already loaded the incoming genre's judges.
// Fetch them from the backend REST endpoint.
const freshJudges = await getBattleJudges()
const raw = freshJudges?.judges ?? []
```

- [ ] **Step 10: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "refactor: remove localStorage from BattleControl watchers and onMounted"
```

---

### Task 11: BattleOverlay.vue — Add /topic/battle/state with diff

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

- [ ] **Step 1: Add `lastAppliedState` ref and diff helper**

After line 28 (`const battleJudges = ref([])`), add:

```javascript
const lastOverlayState = ref('')
```

- [ ] **Step 2: Add the `hydrateOverlayFromState` function**

Add after the `lastOverlayState` ref:

```javascript
const hydrateOverlayFromState = async (state) => {
  if (!state) return
  const snapshot = JSON.stringify(state)
  if (snapshot === lastOverlayState.value) return
  const prev = lastOverlayState.value
  lastOverlayState.value = snapshot

  // Only update genre if it changed
  if (state.genreName !== undefined) {
    const wasSmoke = isSmoke.value
    isSmoke.value = genreNameIsSmoke(state.genreName)
    activeGenreName.value = state.genreName
    // If switching back from smoke, need to re-sub to battle channels
    if (wasSmoke && !isSmoke.value) {
      const pair = state.currentPair?.left ? state.currentPair : await getCurrentBattlePair()
      if (pair) await updateBattlePair(pair)
    }
  }

  // Only update pair if it changed (prevents re-triggering entrance animation)
  if (state.currentPair?.left && !isSmoke.value) {
    const prevState = prev ? JSON.parse(prev) : null
    const pairChanged = !prevState?.currentPair ||
      prevState.currentPair.left !== state.currentPair.left ||
      prevState.currentPair.right !== state.currentPair.right
    if (pairChanged) {
      await updateBattlePair(state.currentPair)
    }
  }

  // Phase — only update if changed
  if (state.battlePhase && state.battlePhase !== battlePhase.value) {
    battlePhase.value = state.battlePhase
    showVotingIndicator.value = state.battlePhase === 'VOTING'
  }

  // Judges — only if list actually changed
  if (state.judges?.length) {
    const newJudgeIds = state.judges.map(j => j.id).sort().join(',')
    const oldJudgeIds = (battleJudges.value?.judges ?? []).map(j => j.id).sort().join(',')
    if (newJudgeIds !== oldJudgeIds) {
      battleJudges.value = { judges: state.judges }
    }
  }

  // Restore winner visual for REVEALED/DECIDED without replaying animation
  if ((state.battlePhase === 'REVEALED' || state.battlePhase === 'DECIDED') && state.bracket?.rounds) {
    restoreRevealedState(state.bracket.rounds)
  }

  // Champion
  if (state.champion && state.champion !== lastChampion) {
    lastChampion = state.champion
  }
}
let lastChampion = null
```

- [ ] **Step 3: Subscribe to `/topic/battle/state` in onMounted**

Add after the overlay config subscription (~line 366), before the phase subscription:

```javascript
// Full-state snapshot — hydrates on mount, genre switch, and reconnect recovery
const cState = createClient(); clients.push(cState)
subscribeToChannel(cState, '/topic/battle/state', (msg) => {
  hydrateOverlayFromState(msg)
})
```

- [ ] **Step 4: Remove the standalone `getBattleState()` call (~lines 398-415)**

Replace the standalone state fetch with the subscription-driven approach. The initial REST fetch is still done above; keep it as a fallback for OBS refresh:

Keep lines 397-415 as a fallback (OBS refresh may load before WS connects). The diff logic in `hydrateOverlayFromState` prevents double-application.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "refactor: add /topic/battle/state subscription with diff to BattleOverlay"
```

---

### Task 12: BracketVisualization.vue — Add diff logic to existing /topic/battle/state sub

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`

- [ ] **Step 1: Add `lastBracketState` ref**

Find the existing `bracketState` ref and add after it:

```javascript
const lastBracketSnapshot = ref('')
```

- [ ] **Step 2: Modify the existing `/topic/battle/state` subscription to add diff guard**

The existing subscription is at ~line 313. Add diff logic at the top of the callback:

```javascript
wsClient.subscribe('/topic/battle/state', (msg) => {
  const data = JSON.parse(msg.body)
  // Diff guard: skip if snapshot hasn't changed
  const snapshot = JSON.stringify(data)
  if (snapshot === lastBracketSnapshot.value) return
  lastBracketSnapshot.value = snapshot

  // Existing bracket handling...
  if (data?.bracket && (data.bracket.topSize || data.bracket.rounds)) {
    // ... (keep existing logic)
  }
  // ... (keep existing pair, genre handling)
})
```

- [ ] **Step 3: Add re-hydrate on reconnect**

The existing `onConnect` handler already subscribes to `/topic/battle/state`. Add a REST fetch after subscriptions inside `onConnect` to cover reconnects:

```javascript
wsClient.onConnect = () => {
  // ... existing subscriptions ...

  // Re-hydrate on reconnect — REST call covers the gap while WS was disconnected
  getBattleState().then(state => {
    if (state && (state.bracket?.topSize || state.bracket?.rounds)) {
      const snapshot = JSON.stringify(state)
      if (snapshot !== lastBracketSnapshot.value) {
        lastBracketSnapshot.value = snapshot
        if (state.bracket && !animRunning) {
          bracketState.value = state.bracket
        }
      }
    }
  }).catch(() => {})
}
```

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue
git commit -m "refactor: add diff logic + reconnect re-hydrate to BracketVisualization"
```

---

### Task 13: BattleJudge.vue — Add /topic/battle/state with diff

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue`

- [ ] **Step 1: Add `lastJudgeState` ref**

Find the existing refs (after the imports) and add:

```javascript
const lastJudgeState = ref('')
```

- [ ] **Step 2: Add `hydrateJudgeFromState` function**

```javascript
const hydrateJudgeFromState = (state) => {
  if (!state) return
  const snapshot = JSON.stringify(state)
  if (snapshot === lastJudgeState.value) return
  lastJudgeState.value = snapshot

  // Phase — only update if changed
  if (state.battlePhase && state.battlePhase !== battlePhase.value) {
    battlePhase.value = state.battlePhase
    if (state.battlePhase === 'LOCKED') {
      clearVote()
      revealedWinner.value = -2
    }
  }

  // Pair — only update if changed (prevents unnecessary clearVote)
  if (state.currentPair?.left) {
    const pairChanged = leftName.value !== state.currentPair.left ||
                        rightName.value !== state.currentPair.right
    if (pairChanged) {
      leftName.value = state.currentPair.left
      rightName.value = state.currentPair.right
      clearVote()
    }
  }

  // Judges — check if current judge was re-added after block
  if (state.judges?.length) {
    battleJudges.value = { judges: state.judges }
    const authStore = useAuthStore()
    if (notAssigned.value && authStore.judgeName) {
      const match = state.judges.find(j => j.name === authStore.judgeName)
      if (match) {
        judgeId.value = match.id
        judgeName.value = match.name
        notAssigned.value = false
        setupVoteSubscription()
      }
    }
  }
}
```

- [ ] **Step 3: Add `/topic/battle/state` subscription and reconnect re-hydrate in onMounted**

Add a new WS client for state after the judges subscription (~line 180):

```javascript
// Full-state snapshot for initial hydration and reconnect recovery
const cState = createClient(); wsClients.push(cState)
subscribeToChannel(cState, '/topic/battle/state', (msg) => {
  hydrateJudgeFromState(msg)
})
```

And add a re-hydrate REST call after all subscriptions:

```javascript
// Re-hydrate after reconnect — fetch current state from backend
const initialState = await getBattleState()
if (initialState) hydrateJudgeFromState(initialState)
```

- [ ] **Step 4: Add the `getBattleState` import**

Add `getBattleState` to the API import line (line 1 or wherever API functions are imported from `@/utils/api`).

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "refactor: add /topic/battle/state subscription with diff to BattleJudge"
```

---

### Task 14: Chart.vue — Add /topic/battle/state with diff

**Files:**
- Modify: `BES-frontend/src/views/Chart.vue`

- [ ] **Step 1: Add `lastChartState` ref**

After the existing refs:

```javascript
const lastChartState = ref('')
```

- [ ] **Step 2: Add `hydrateChartFromState` function**

```javascript
const hydrateChartFromState = (state) => {
  if (!state) return
  const snapshot = JSON.stringify(state)
  if (snapshot === lastChartState.value) return
  lastChartState.value = snapshot

  // Smoke list — only update if changed
  if (state.smokeList?.length) {
    const newNames = state.smokeList.map(b => b.name).join(',')
    const oldNames = smokeParticipants.value.map(b => b.name).join(',')
    if (newNames !== oldNames) {
      smokeParticipants.value = state.smokeList
    }
  }

  // Phase — only update if changed
  if (state.battlePhase && state.battlePhase !== battlePhase.value) {
    battlePhase.value = state.battlePhase
    if (state.battlePhase === 'LOCKED') showResult.value = false
  }

  // Judges — only if list actually changed
  if (state.judges?.length) {
    const newJudgeIds = state.judges.map(j => j.id).sort().join(',')
    const oldJudgeIds = (battleJudges.value?.judges ?? []).map(j => j.id).sort().join(',')
    if (newJudgeIds !== oldJudgeIds) {
      battleJudges.value = { judges: state.judges }
      updateBattleJudge({ judges: state.judges })
    }
  }
}
```

- [ ] **Step 3: Add `/topic/battle/state` subscription and reconnect re-hydrate**

Add after the phase subscription (~line 185):

```javascript
const cState = createClient(); clients.push(cState)
subscribeToChannel(cState, '/topic/battle/state', (msg) => {
  hydrateChartFromState(msg)
})
```

And add a re-hydrate REST call:

```javascript
// Initial hydration + reconnect recovery
const initialState = await getBattleState()
if (initialState) hydrateChartFromState(initialState)
```

- [ ] **Step 4: Add the `getBattleState` import**

Add `getBattleState` to the API import line.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/Chart.vue
git commit -m "refactor: add /topic/battle/state subscription with diff to Chart"
```

---

### Task 15: Build and Verify

**Files:** None (verification only)

- [ ] **Step 1: Build backend**

```bash
cd BES && mvn clean package -DskipTests -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: Build frontend**

```bash
cd BES-frontend && npm run build
```
Expected: Build completes without errors

- [ ] **Step 3: Run backend tests**

```bash
cd BES && mvn test
```
Expected: All tests pass

- [ ] **Step 4: Run frontend tests**

```bash
cd BES-frontend && npm test
```
Expected: All tests pass

- [ ] **Step 5: Verify with Docker**

```bash
docker-compose up --build --no-cache
```

Check all three containers are healthy:
```bash
docker ps
```

- [ ] **Step 6: Commit**

```bash
# Only if any fixes were applied during verification
git add . && git commit -m "chore: final verification fixes for battle WS sync"
```
