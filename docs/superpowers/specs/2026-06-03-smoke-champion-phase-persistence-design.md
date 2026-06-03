# 7-to-Smoke Champion Phase & State Persistence

**Date:** 2026-06-03
**Branch:** `feat/smoke-champion-phase-persistence`
**Issue:** #55

## Problem

Two related gaps in the 7-to-smoke format:

1. **No phase transition when a champion is crowned.** When a battler reaches 7 smokes, the backend never moves to `DECIDED`. The genre-switch guard checks `phase !== 'DECIDED'`, so switching genres while a smoke session is in progress always triggers the "battle in progress" warning even after a winner exists.

2. **State not restored on genre switch-back.** The smoke battlers list (names + scores + queue order) is held in-memory on the backend but never persisted to `battle_genre_state`. On genre switch-back, BattleControl restores `rounds.value` from localStorage only — if localStorage is empty (new device, cleared storage, page reload), the queue shows blank.

## Scope Constraint

7-to-smoke uses a different code path from standard bracket formats (Top32/Top16). All changes are isolated to smoke-specific paths. `setScoreService()`, `setBattlerPairService()`, and the regular bracket flow are untouched.

## What Is Not Changing

- `Chart.vue` — already detects champion live (`score >= 7` in `updateList`) and restores from `getSmokeList()` on mount. No changes needed; it benefits automatically from backend persistence.
- `BattleOverlay.vue`, `BracketVisualization.vue` — not touched.
- The champion announcement flow — Chart.vue shows the overlay immediately when a battler hits 7. There is no "Reveal Champion" button for smoke; DECIDED is purely a state signal.
- Regular bracket champion flow (`lockChampion`, `revealChampionForGenre`) — untouched.

## Design

### Backend

#### V30 migration
```sql
ALTER TABLE battle_genre_state ADD COLUMN smoke_list_json TEXT;
```

#### `BattleGenreState.java`
Add field:
```java
@Column(name = "smoke_list_json", columnDefinition = "TEXT")
private String smokeListJson;
```

#### `BattleService.persistActiveState()`
Serialize `battlers` alongside existing fields:
```java
s.setSmokeListJson(objectMapper.writeValueAsString(battlers));
```

#### `BattleService.loadGenreStateIntoMemory()`
Restore `battlers` from `smokeListJson`; fall back to empty list if null:
```java
if (s.getSmokeListJson() != null) {
    battlers = objectMapper.readValue(s.getSmokeListJson(), new TypeReference<List<Battler>>(){});
} else {
    battlers = new ArrayList<>();
}
```

#### `BattleService.setSmokeBattlersService()`
After updating `battlers` and broadcasting to `/topic/battle/smoke`, check for a champion:
```java
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
```

The method already broadcasts the smoke list; this adds a conditional phase broadcast only when 7 smokes are reached. `persistActiveState()` is called to save both the champion name and the DECIDED phase.

### Frontend — BattleControl.vue

#### Phase WS handler (line ~1752)
Extend to capture champion name when DECIDED arrives:
```js
battlePhase.value = msg.phase
if (msg.phase === 'DECIDED' && msg.champion && selectedGenre.value) {
    genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: msg.champion }
}
```
The DECIDED panel (`{{ genreChampions[selectedGenre] ?? ... }}`) then displays the smoke winner's name immediately.

#### `restoreAndBroadcastGenreBattle(genre)`
Add a smoke branch alongside the existing standard bracket branch:
```js
} else if (isSmoke.value) {
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
```
This restores queue order and scores from the persisted backend list when localStorage is absent. The existing genre watcher defensive check (line 1551) already forces DECIDED if `genreChampions[newVal]` is populated.

## Data Flow Summary

### Live (smoke session in progress)
1. Organiser clicks Get Score → BattleControl calls `update7toSmokeMatch(winner)` → `updateSmokeList(rounds.value)` → `POST /api/v1/battle/smoke`
2. Backend `setSmokeBattlersService()`: updates `battlers`, broadcasts to `/topic/battle/smoke`
3. **If any battler.score >= 7**: sets `champion`, `battlePhase=DECIDED`, broadcasts `{ phase, genre, champion }`, persists
4. BattleControl phase WS handler: sets `battlePhase.value = 'DECIDED'`, sets `genreChampions[genre] = champion`
5. Chart.vue `updateList()`: detects score >= 7, shows champion overlay

### Genre switch-back
1. Genre watcher fires `setActiveGenre()` → backend calls `loadGenreStateIntoMemory()` which now restores `battlers` from `smoke_list_json`
2. `restoreAndBroadcastGenreBattle(genre)` smoke branch: calls `getSmokeList()`, populates `rounds.value` with correct scores + queue order
3. If `state.battlePhase === 'DECIDED'` and `state.champion`: sets `battlePhase.value = 'DECIDED'`, sets `genreChampions[genre]`
4. Genre watcher defensive check (line 1551): if `genreChampions[newVal]` present and phase != DECIDED, forces DECIDED

### Chart.vue on mount / page reload
1. `getSmokeList()` returns the persisted `battlers` (with correct scores)
2. `smokeParticipants.value` populated with correct bar heights
3. If any battler had score >= 7, `showChampion` fires immediately

## Files Changed

| File | Change |
|------|--------|
| `BES/src/main/resources/db/migration/V30__add_smoke_list_to_battle_genre_state.sql` | New migration |
| `BES/src/main/java/com/example/BES/models/BattleGenreState.java` | Add `smokeListJson` field |
| `BES/src/main/java/com/example/BES/services/BattleService.java` | persist/restore battlers, champion detection in setSmokeBattlersService |
| `BES-frontend/src/views/BattleControl.vue` | Phase WS handler + restoreAndBroadcastGenreBattle smoke branch |
