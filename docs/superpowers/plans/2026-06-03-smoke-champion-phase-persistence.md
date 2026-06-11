# 7-to-Smoke Champion Phase & State Persistence — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a 7-to-smoke battler reaches 7 smokes, the backend transitions to `DECIDED`, persists the smoke list, and BattleControl restores the full queue + champion on genre switch-back.

**Architecture:** Backend-driven champion detection — `setSmokeBattlersService()` checks for score ≥ 7, sets `battlePhase=DECIDED`, persists. A new `smoke_list_json` column on `battle_genre_state` survives genre switches and backend restarts. BattleControl adds a smoke restore branch to `restoreAndBroadcastGenreBattle()` and captures the champion name from the phase WS message.

**Tech Stack:** Spring Boot (Java 17), Flyway migrations, Jackson, Vue 3 Composition API, STOMP WebSocket

---

## File Map

| File | Action | What changes |
|------|--------|--------------|
| `BES/src/main/resources/db/migration/V30__add_smoke_list_to_battle_genre_state.sql` | Create | Adds `smoke_list_json TEXT` column |
| `BES/src/main/java/com/example/BES/models/BattleGenreState.java` | Modify | Add `smokeListJson` field |
| `BES/src/main/java/com/example/BES/services/BattleService.java` | Modify | persist/restore `battlers`; champion detection in `setSmokeBattlersService`; reset `battlers` in `resetToDefaults` |
| `BES-frontend/src/views/BattleControl.vue` | Modify | Add `getSmokeList` import; extend phase WS handler; add smoke branch to `restoreAndBroadcastGenreBattle` |

---

## Task 1: DB migration — add smoke_list_json column

**Files:**
- Create: `BES/src/main/resources/db/migration/V30__add_smoke_list_to_battle_genre_state.sql`

- [ ] **Step 1: Create the migration file**

```sql
ALTER TABLE battle_genre_state ADD COLUMN smoke_list_json TEXT;
```

- [ ] **Step 2: Verify Flyway picks it up**

From `BES/`:
```bash
mvn spring-boot:run
```
Watch startup logs for:
```
Successfully applied 1 migration to schema "public", now at version v30
```
Then stop the server (`Ctrl+C`).

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V30__add_smoke_list_to_battle_genre_state.sql
git commit -m "chore: V30 migration — add smoke_list_json to battle_genre_state"
```

---

## Task 2: Add smokeListJson field to BattleGenreState

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/BattleGenreState.java:58-63`

- [ ] **Step 1: Add the field**

Open `BattleGenreState.java`. After the `champion` field (currently lines 58-59):
```java
    @Column(name = "champion")
    private String champion;
```
Add:
```java
    @Column(name = "smoke_list_json", columnDefinition = "TEXT")
    private String smokeListJson;
```
Full block after change (lines 58-65):
```java
    @Column(name = "champion")
    private String champion;

    @Column(name = "smoke_list_json", columnDefinition = "TEXT")
    private String smokeListJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
```

- [ ] **Step 2: Verify the build compiles**

From `BES/`:
```bash
mvn clean package -DskipTests
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/BattleGenreState.java
git commit -m "feat: add smokeListJson field to BattleGenreState"
```

---

## Task 3: Persist and restore smoke battlers list

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

Two sub-changes: `persistActiveState()` and `loadGenreStateIntoMemory()`.

### 3a — Persist battlers in persistActiveState()

- [ ] **Step 1: Add serialization of battlers**

In `persistActiveState()` (around line 407), after:
```java
            s.setChampion(champion);
```
Add:
```java
            s.setSmokeListJson(objectMapper.writeValueAsString(battlers));
```

Full surrounding context after change:
```java
            s.setIsFinal(currentIsFinal);
            s.setBattlePhase(battlePhase);
            s.setChampion(champion);
            s.setSmokeListJson(objectMapper.writeValueAsString(battlers));
            synchronized (judges) {
                s.setJudgesJson(objectMapper.writeValueAsString(new ArrayList<>(judges)));
            }
```

### 3b — Restore battlers in loadGenreStateIntoMemory()

- [ ] **Step 2: Add deserialization of battlers**

In `loadGenreStateIntoMemory()` (around line 439), after:
```java
            champion = s.getChampion();
```
Add:
```java
            if (s.getSmokeListJson() != null) {
                battlers = objectMapper.readValue(s.getSmokeListJson(),
                    new TypeReference<List<Battler>>(){});
            } else {
                battlers = new ArrayList<>();
            }
```

Full surrounding context after change:
```java
            battlePhase = s.getBattlePhase() != null ? s.getBattlePhase() : "IDLE";
            champion = s.getChampion();
            if (s.getSmokeListJson() != null) {
                battlers = objectMapper.readValue(s.getSmokeListJson(),
                    new TypeReference<List<Battler>>(){});
            } else {
                battlers = new ArrayList<>();
            }
            synchronized (judges) {
```

### 3c — Reset battlers in resetToDefaults()

- [ ] **Step 3: Reset battlers in resetToDefaults()**

`resetToDefaults()` (around line 454) currently does not reset `battlers`, which means a stale smoke list can leak into a fresh genre. After:
```java
        champion = null;
```
Add:
```java
        battlers = new ArrayList<>();
```

Full block after change:
```java
    private void resetToDefaults() {
        bracketState = null;
        currentRoundIndex = 0;
        currentPair.getLeftBattler().setName("");
        currentPair.getLeftBattler().setScore(0);
        currentPair.getLeftBattler().setMembers(new ArrayList<>());
        currentPair.getRightBattler().setName("");
        currentPair.getRightBattler().setScore(0);
        currentPair.getRightBattler().setMembers(new ArrayList<>());
        currentIsFinal = false;
        battlePhase = "IDLE";
        champion = null;
        battlers = new ArrayList<>();
        synchronized (judges) { judges.clear(); }
    }
```

- [ ] **Step 4: Verify the build compiles**

```bash
mvn clean package -DskipTests
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "feat: persist and restore smoke battlers list across genre switches"
```

---

## Task 4: Backend champion detection in setSmokeBattlersService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java:105-109`

- [ ] **Step 1: Add champion detection after the smoke broadcast**

Current `setSmokeBattlersService()` (lines 105-109):
```java
    public void setSmokeBattlersService(SetSmokeBattlersDto dto) {
        battlers = new ArrayList<>();
        for (Battler battler : dto.getBattlers()) battlers.add(battler);
        messagingTemplate.convertAndSend("/topic/battle/smoke", Map.of("battlers", battlers));
    }
```

Replace with:
```java
    public void setSmokeBattlersService(SetSmokeBattlersDto dto) {
        battlers = new ArrayList<>();
        for (Battler battler : dto.getBattlers()) battlers.add(battler);
        messagingTemplate.convertAndSend("/topic/battle/smoke", Map.of("battlers", battlers));
        battlers.stream().filter(b -> b.getScore() >= 7).findFirst().ifPresent(champ -> {
            champion = champ.getName();
            battlePhase = "DECIDED";
            messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
                "phase", "DECIDED",
                "genre", activeGenreName != null ? activeGenreName : "",
                "champion", champion
            ));
            persistActiveState();
        });
    }
```

- [ ] **Step 2: Verify the build compiles**

```bash
mvn clean package -DskipTests
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Manual smoke test**

Start the backend:
```bash
mvn spring-boot:run
```
Using curl or a REST client, POST to `/api/v1/battle/smoke` with a battler whose score is 7:
```bash
curl -s -X POST http://localhost:5050/api/v1/battle/smoke \
  -H 'Content-Type: application/json' \
  -b 'SESSION=<your-session-cookie>' \
  -d '{"battlers":[{"name":"Alice","score":7,"members":[]},{"name":"Bob","score":2,"members":[]}]}'
```
Then GET the phase:
```bash
curl -s http://localhost:5050/api/v1/battle/phase
```
Expected: `{"phase":"DECIDED"}`

Then GET the state:
```bash
curl -s http://localhost:5050/api/v1/battle/state
```
Expected: `"champion":"Alice"` and `"battlePhase":"DECIDED"` in the response.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "feat: backend champion detection — set DECIDED when smoke score reaches 7"
```

---

## Task 5: Frontend — BattleControl smoke restore + phase WS champion capture

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue:3` (import)
- Modify: `BES-frontend/src/views/BattleControl.vue:1747-1753` (phase WS handler)
- Modify: `BES-frontend/src/views/BattleControl.vue:949-1001` (restoreAndBroadcastGenreBattle)

### 5a — Add getSmokeList to import

- [ ] **Step 1: Add getSmokeList to the api import**

Current line 3:
```js
import { addBattleJudge, addBattleGuest, battleJudgeVote, clearBattlePair, getBattleChampions, getBattleGuests, getBattleJudges, getBattlePhase, getBattleState, getOverlayConfig, getParticipantScore, getPickupCrews, getRegisteredParticipantsByEvent, removeBattleGuest, removeBattleJudge, resetBattleVotes, revealChampion, dismissChampionReveal, setActiveGenre, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateJudgeWeightage, updateSmokeList, uploadImage } from '@/utils/api'
```

Replace with (add `getSmokeList` after `getRegisteredParticipantsByEvent`):
```js
import { addBattleJudge, addBattleGuest, battleJudgeVote, clearBattlePair, getBattleChampions, getBattleGuests, getBattleJudges, getBattlePhase, getBattleState, getOverlayConfig, getParticipantScore, getPickupCrews, getRegisteredParticipantsByEvent, getSmokeList, removeBattleGuest, removeBattleJudge, resetBattleVotes, revealChampion, dismissChampionReveal, setActiveGenre, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateJudgeWeightage, updateSmokeList, uploadImage } from '@/utils/api'
```

### 5b — Extend phase WS handler to capture champion

- [ ] **Step 2: Extend the phase WS handler**

Current phase WS handler (lines 1747-1753):
```js
    wsClient.value.subscribe('/topic/battle/phase', (raw) => {
      const msg = JSON.parse(raw.body)
      // Ignore phase messages intended for a different genre — stale WS from a genre
      // switch can arrive after we've already switched back and set the correct phase.
      if (msg.genre && msg.genre !== selectedGenre.value) return
      battlePhase.value = msg.phase
    })
```

Replace with:
```js
    wsClient.value.subscribe('/topic/battle/phase', (raw) => {
      const msg = JSON.parse(raw.body)
      // Ignore phase messages intended for a different genre — stale WS from a genre
      // switch can arrive after we've already switched back and set the correct phase.
      if (msg.genre && msg.genre !== selectedGenre.value) return
      battlePhase.value = msg.phase
      if (msg.phase === 'DECIDED' && msg.champion && selectedGenre.value) {
        genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: msg.champion }
      }
    })
```

### 5c — Add smoke restore branch to restoreAndBroadcastGenreBattle

- [ ] **Step 3: Add the smoke restore branch**

In `restoreAndBroadcastGenreBattle(genre)` (lines 975-1001), the function currently ends with:
```js
  battlePhase.value = state.battlePhase ?? 'IDLE'
  currentWinner.value = -2
  if (!isSmoke.value && state.bracket?.rounds) {
    // Populate rounds.value from DB so the bracket UI is visible, then cache to localStorage
    // so future genre switches don't need to fall back to DB again.
    rounds.value = state.bracket.rounds
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${genre}Rounds`, JSON.stringify(state.bracket.rounds))
    const bRounds = state.bracket.rounds
    let topKey = state.currentPair.isFinal ? 'Top2' : null
    if (!topKey) {
      for (const key of Object.keys(bRounds)) {
        const matchList = bRounds[key]
        if (!Array.isArray(matchList)) continue
        if (matchList.some(m => m[0] === state.currentPair.left && m[1] === state.currentPair.right)) {
          topKey = key; break
        }
      }
    }
    if (topKey) {
      currentTop.value = topKey
      const pairList = bRounds[topKey] ?? []
      const nameIdx = pairList.findIndex(m => m[0] === state.currentPair.left && m[1] === state.currentPair.right)
      const resolvedIdx = nameIdx >= 0 ? nameIdx : (state.currentRoundIndex ?? 0)
      currentRound.value = resolvedIdx
      currentBattle.value = [resolvedIdx, pairList]
      saveGenreBattleState(genre)
    }
  }
}
```

Replace the closing `if (!isSmoke.value && ...)` block with:
```js
  battlePhase.value = state.battlePhase ?? 'IDLE'
  currentWinner.value = -2
  if (!isSmoke.value && state.bracket?.rounds) {
    // Populate rounds.value from DB so the bracket UI is visible, then cache to localStorage
    // so future genre switches don't need to fall back to DB again.
    rounds.value = state.bracket.rounds
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${genre}Rounds`, JSON.stringify(state.bracket.rounds))
    const bRounds = state.bracket.rounds
    let topKey = state.currentPair.isFinal ? 'Top2' : null
    if (!topKey) {
      for (const key of Object.keys(bRounds)) {
        const matchList = bRounds[key]
        if (!Array.isArray(matchList)) continue
        if (matchList.some(m => m[0] === state.currentPair.left && m[1] === state.currentPair.right)) {
          topKey = key; break
        }
      }
    }
    if (topKey) {
      currentTop.value = topKey
      const pairList = bRounds[topKey] ?? []
      const nameIdx = pairList.findIndex(m => m[0] === state.currentPair.left && m[1] === state.currentPair.right)
      const resolvedIdx = nameIdx >= 0 ? nameIdx : (state.currentRoundIndex ?? 0)
      currentRound.value = resolvedIdx
      currentBattle.value = [resolvedIdx, pairList]
      saveGenreBattleState(genre)
    }
  } else if (isSmoke.value) {
    // Smoke: restore queue + scores from backend (which now persists smoke_list_json).
    // localStorage was already checked and was empty — this is the backend fallback path.
    const smokeList = await getSmokeList()
    if (smokeList?.list?.length) {
      rounds.value = smokeList.list
      localStorage.setItem(
        `Top${topSize.value}${selectedEvent.value}${genre}Rounds`,
        JSON.stringify(smokeList.list)
      )
    }
    if (state.champion) {
      genreChampions.value = { ...genreChampions.value, [genre]: state.champion }
    }
  }
}
```

- [ ] **Step 4: Verify the frontend builds without errors**

From `BES-frontend/`:
```bash
npm run build
```
Expected: no TypeScript/lint errors, `dist/` emitted successfully.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: restore smoke state on genre switch-back; capture champion from phase WS"
```

---

## Task 6: End-to-end verification

- [ ] **Step 1: Start the full stack**

From repo root:
```bash
docker-compose up --build --no-cache
```
Wait for all three services to be healthy (backend logs `Started BESApplication`).

- [ ] **Step 2: Verify smoke list persistence across genre switches**

1. Open BattleControl at `http://localhost` as Admin/Organiser
2. Select an event with a 7-to-smoke genre and a second genre
3. Add participants to the smoke queue; drag them into slots
4. Switch to the second genre — confirm no "battle in progress" warning fires if smoke was at `IDLE`
5. Switch back to the smoke genre — confirm the queue is still populated (names + scores = 0)

- [ ] **Step 3: Verify champion detection and phase transition**

1. In the smoke genre, start a round, get score repeatedly until one battler reaches 7
2. Confirm Chart.vue (`/battle/chart`) shows the champion overlay immediately
3. Confirm BattleControl shows `DECIDED` state with the champion name in the glowing panel
4. Confirm the genre-switch guard no longer fires a warning (genre can be switched freely)

- [ ] **Step 4: Verify switch-back restores full state**

1. With a smoke genre in `DECIDED` (champion crowned), switch to another genre
2. Switch back — confirm the smoke queue shows the correct scores and queue order
3. Confirm BattleControl shows `DECIDED` and the champion name without any manual steps
4. Open a new incognito browser tab (simulates empty localStorage) and repeat steps 1-3 — state should restore from backend, not localStorage

- [ ] **Step 5: Verify regular bracket is untouched**

1. Switch to a Top16/Top32 genre
2. Run through a bracket match: seed, start round, vote, get score
3. Confirm phase transitions (LOCKED → VOTING → REVEALED) work as before
4. Confirm `lockChampion` / `revealChampionForGenre` still work for Top2 final

---

## Self-Review Notes

- All four spec requirements covered: V30 migration (Task 1), model field (Task 2), persist/restore/reset (Task 3), champion detection (Task 4), frontend WS handler + restore branch (Task 5)
- `getSmokeList` import added in Task 5a before it is used in Task 5c — no forward reference issue
- `resetToDefaults()` fix in Task 3c prevents stale smoke list leaking into new genres — not in the spec but caught during review
- `restoreAndBroadcastGenreBattle` has a guard `if (saved) { return }` that exits early when localStorage is populated — the smoke branch only runs when localStorage is absent (correct behaviour)
- Regular bracket `setScoreService()` and `setBattlerPairService()` are not touched in any task
