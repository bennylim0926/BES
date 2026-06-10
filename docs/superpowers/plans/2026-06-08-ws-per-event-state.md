# Per-Event WS State — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `BattleService`'s single in-memory state slot with a per-event `ConcurrentHashMap`, and namespace all WS topic broadcasts with the event name, so two simultaneous battle events don't corrupt each other's state or cross-contaminate each other's overlays.

**Architecture:** All mutable battle state (phase, pair, bracket, judges, battlers, timers) is extracted into an `EventBattleState` inner class. A `ConcurrentHashMap<String, EventBattleState>` keyed by event name replaces the current field cluster. Service methods accept an explicit `eventName` param; controllers extract it from DTOs (optional field — falls back to `activeEventName` for backwards compat). WS topics are namespaced as `/topic/battle/{eventName}/...`. The `GET /battle/state` endpoint gains an optional `?event=` query param.

**Tech Stack:** Spring Boot · Spring WebSocket (STOMP) · Jackson · JUnit 5 + Mockito

**Branch:** `feat/ws-per-event-state`  
**Closes:** #98, #99

---

## File Map

| File | Action | Summary |
|------|---------|---------|
| `BES/src/main/java/com/example/BES/services/BattleService.java` | Modify | Extract EventBattleState; add state map; update all methods |
| `BES/src/main/java/com/example/BES/dtos/battle/SetBattlerPairDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetBattlePhaseDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetBracketStateDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetJudgeDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/UpdateJudgeWeightageDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetVoteDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetOverlayConfigDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetSmokeBattlersDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/SetBattleScoreDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/dtos/battle/ChampionRevealDto.java` | Modify | Add optional `eventName` field |
| `BES/src/main/java/com/example/BES/controllers/BattleController.java` | Modify | Extract eventName from DTOs; add `?event=` to GET /state; add `?event=` to parameterless DELETE/POST |
| `BES/src/test/java/com/example/BES/services/BattleServiceTest.java` | Modify | Update all calls to pass eventName; add multi-event isolation test |

---

## Task 1 — Write failing multi-event isolation test (RED)

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`

- [ ] **Step 1.1: Add the failing isolation test to `BattleServiceTest`**

Add this test at the bottom of the class. It calls `setBattlerPairService(String eventName, SetBattlerPairDto dto)` which doesn't exist yet — so it will fail to compile.

```java
@Test
void twoEvents_statesAreIsolated() {
    SetBattlerPairDto dtoA = mock(SetBattlerPairDto.class);
    when(dtoA.getLeftBattler()).thenReturn("Alice");
    when(dtoA.getRightBattler()).thenReturn("Bob");
    lenient().when(dtoA.getLeftMembers()).thenReturn(new java.util.ArrayList<>());
    lenient().when(dtoA.getRightMembers()).thenReturn(new java.util.ArrayList<>());

    SetBattlerPairDto dtoB = mock(SetBattlerPairDto.class);
    when(dtoB.getLeftBattler()).thenReturn("Charlie");
    when(dtoB.getRightBattler()).thenReturn("Dave");
    lenient().when(dtoB.getLeftMembers()).thenReturn(new java.util.ArrayList<>());
    lenient().when(dtoB.getRightMembers()).thenReturn(new java.util.ArrayList<>());

    service.setBattlerPairService("EventAlpha", dtoA);
    service.setBattlerPairService("EventBeta", dtoB);

    assertThat(service.getCurrentPair("EventAlpha").getLeftBattler().getName()).isEqualTo("Alice");
    assertThat(service.getCurrentPair("EventBeta").getLeftBattler().getName()).isEqualTo("Charlie");
    assertThat(service.getBattlePhase("EventAlpha")).isEqualTo("LOCKED");
    assertThat(service.getBattlePhase("EventBeta")).isEqualTo("LOCKED");
}
```

- [ ] **Step 1.2: Confirm compile failure**

```bash
cd BES && mvn test -Dtest=BattleServiceTest -pl . 2>&1 | grep -E "ERROR|FAIL|cannot find"
```

Expected: compilation error — `setBattlerPairService(String, SetBattlerPairDto)` does not exist.

---

## Task 2 — Make `BattlePair` a static inner class

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

`BattlePair` is currently a non-static inner class (it implicitly holds a reference to `BattleService`). It must be static to be used inside the static `EventBattleState` class.

- [ ] **Step 2.1: Change `BattlePair` to static**

In `BattleService.java`, locate:
```java
public class BattlePair {
```
Change to:
```java
public static class BattlePair {
```

- [ ] **Step 2.2: Verify the existing tests still compile and pass**

```bash
cd BES && mvn test -Dtest=BattleServiceTest -pl . 2>&1 | tail -20
```

Expected: All existing tests pass (the new isolation test still fails — that's fine).

- [ ] **Step 2.3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "refactor: make BattlePair static to support EventBattleState extraction"
```

---

## Task 3 — Create `EventBattleState` inner class + state map

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

- [ ] **Step 3.1: Add `EventBattleState` static inner class to `BattleService`**

Add this class just before the existing `BattleJudge` inner class (around line 669):

```java
public static class EventBattleState {
    Object bracketState = null;
    Integer currentRoundIndex = 0;
    List<Battler> battlers = new ArrayList<>();
    String battlePhase = "IDLE";
    boolean currentIsFinal = false;
    BattlePair currentPair;
    final List<BattleJudge> judges = Collections.synchronizedList(new ArrayList<>());
    String activeGenreName;
    String genreFormat;
    String champion = null;
    Map<String, Object> lastTimerPayload = null;
    long timerLastUpdated = 0;
    Map<String, Object> lastFormatTimerPayload = null;
    long formatTimerLastUpdated = 0;
    Map<String, Object> overlayConfig = new HashMap<>(Map.of(
        "showImages", true,
        "leftColor",  "#dc2626",
        "rightColor", "#2563eb"
    ));

    EventBattleState() {
        currentPair = new BattlePair();
        currentPair.setLeftBattler(new Battler());
        currentPair.setRightBattler(new Battler());
    }
}
```

- [ ] **Step 3.2: Add state map + helper methods to `BattleService`**

Replace the block of top-level mutable fields (lines 68–85 approximately):

```java
// OLD — remove these lines:
private Object bracketState = null;
private Integer currentRoundIndex = 0;
private List<Battler> battlers = new ArrayList<>();
private String battlePhase = "IDLE";
private boolean currentIsFinal = false;
private Map<String, Object> overlayConfig = new HashMap<>(Map.of(...));
private BattlePair currentPair;
private final List<BattleJudge> judges = ...;
private String status;
private String activeEventName;
private String activeGenreName;
private String genreFormat;
private String champion = null;
private Map<String, Object> lastTimerPayload = null;
private long timerLastUpdated = 0;
private Map<String, Object> lastFormatTimerPayload = null;
private long formatTimerLastUpdated = 0;
```

Replace with:

```java
// Per-event state map — keyed by eventName
private final ConcurrentHashMap<String, EventBattleState> eventStates = new ConcurrentHashMap<>();

// Global pointer — tracks the most recently activated event (used as fallback when
// incoming requests don't specify an event name)
private String activeEventName;
private String status;
```

Add these two helper methods right after the field declarations (before `BattleService()` constructor):

```java
/** Returns (or creates) the state bucket for the given event. Never returns null. */
private EventBattleState stateFor(String eventName) {
    return eventStates.computeIfAbsent(
        eventName != null ? eventName : "", k -> new EventBattleState());
}

/**
 * Resolves the event name to use for an operation.
 * Explicit param wins; falls back to activeEventName; last resort is empty string.
 */
private String resolveEvent(String explicit) {
    if (explicit != null && !explicit.isBlank()) return explicit;
    return activeEventName != null ? activeEventName : "";
}
```

- [ ] **Step 3.3: Remove or update the constructor**

The old constructor initialised `currentPair`. Remove it entirely — `EventBattleState` now handles its own initialisation:

```java
// DELETE this constructor:
BattleService() {
    selectedMode = "";
    currentPair = new BattlePair();
    Battler left = new Battler();
    Battler right = new Battler();
    currentPair.leftBattler = left;
    currentPair.rightBattler = right;
}
```

Also remove `private List<String> modes = Arrays.asList("Top32", "Top16", "7-to-Smoke");` and `private String selectedMode;` — these are not per-event and can stay as-is (leave `modes` and `selectedMode` untouched as top-level fields).

Add `ConcurrentHashMap` to imports:
```java
import java.util.concurrent.ConcurrentHashMap;
```

- [ ] **Step 3.4: Verify it compiles (tests will fail — that's expected)**

```bash
cd BES && mvn compile -pl . 2>&1 | grep -E "ERROR|BUILD"
```

Expected: `BUILD SUCCESS` (no compile errors yet — the service methods still reference the old fields but we'll fix those in Task 4).

---

## Task 4 — Refactor all `BattleService` methods to use state map

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

Each method below follows the same pattern:
1. Add `String eventName` parameter
2. Replace direct field access with `EventBattleState s = stateFor(eventName);`
3. Read/write via `s.fieldName`
4. Pass `eventName` to WS topic: `/topic/battle/{eventName}/...`
5. Pass `eventName` to `persistActiveState(eventName)`

The template for every method:
```java
// Before
void someMethod(SomeDto dto) {
    battlePhase = "X";
    messagingTemplate.convertAndSend("/topic/battle/phase", payload);
    persistActiveState();
}

// After
void someMethod(String eventName, SomeDto dto) {
    EventBattleState s = stateFor(eventName);
    s.battlePhase = "X";
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", payload);
    persistActiveState(eventName);
}
```

Apply to every method listed below. Complete implementations are shown.

- [ ] **Step 4.1: Refactor `setSmokeBattlersService`**

```java
public void setSmokeBattlersService(String eventName, SetSmokeBattlersDto dto) {
    EventBattleState s = stateFor(eventName);
    s.battlers = new ArrayList<>(dto.getBattlers());
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/smoke",
        Map.of("battlers", s.battlers));
    for (Battler battler : s.battlers) {
        if (battler.getScore() != null && battler.getScore() >= 7) {
            s.battlePhase = "DECIDED";
            s.champion = battler.getName();
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
                "phase",    s.battlePhase,
                "genre",    s.activeGenreName != null ? s.activeGenreName : "",
                "champion", s.champion
            ));
            break;
        }
    }
    persistActiveState(eventName);
}
```

- [ ] **Step 4.2: Refactor `setBattlerPairService`**

```java
public void setBattlerPairService(String eventName, SetBattlerPairDto dto) {
    EventBattleState s = stateFor(eventName);
    s.currentPair.getLeftBattler().setName(dto.getLeftBattler());
    s.currentPair.getLeftBattler().setScore(0);
    s.currentPair.getLeftBattler().setMembers(dto.getLeftMembers());
    s.currentPair.getRightBattler().setName(dto.getRightBattler());
    s.currentPair.getRightBattler().setScore(0);
    s.currentPair.getRightBattler().setMembers(dto.getRightMembers());
    s.currentIsFinal = dto.isFinal();
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/battle-pair", Map.of(
        "left",         s.currentPair.getLeftBattler().getName(),
        "leftScore",    s.currentPair.getLeftBattler().getScore(),
        "leftMembers",  s.currentPair.getLeftBattler().getMembers(),
        "right",        s.currentPair.getRightBattler().getName(),
        "rightScore",   s.currentPair.getRightBattler().getScore(),
        "rightMembers", s.currentPair.getRightBattler().getMembers(),
        "isFinal",      s.currentIsFinal
    ));
    s.battlePhase = "LOCKED";
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
        "phase", s.battlePhase,
        "genre", s.activeGenreName != null ? s.activeGenreName : ""
    ));
    persistActiveState(eventName);
}
```

- [ ] **Step 4.3: Refactor `clearBattlePairService`**

```java
public void clearBattlePairService(String eventName) {
    EventBattleState s = stateFor(eventName);
    s.currentPair.getLeftBattler().setName("");
    s.currentPair.getLeftBattler().setScore(0);
    s.currentPair.getLeftBattler().setMembers(new ArrayList<>());
    s.currentPair.getRightBattler().setName("");
    s.currentPair.getRightBattler().setScore(0);
    s.currentPair.getRightBattler().setMembers(new ArrayList<>());
    s.currentIsFinal = false;
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/battle-pair", Map.of(
        "left",         "",
        "leftScore",    0,
        "leftMembers",  new ArrayList<>(),
        "right",        "",
        "rightScore",   0,
        "rightMembers", new ArrayList<>(),
        "isFinal",      false
    ));
}
```

- [ ] **Step 4.4: Refactor `setScoreService`**

```java
public Integer setScoreService(String eventName, boolean isFinal) {
    EventBattleState s = stateFor(eventName);
    int leftWeight, rightWeight;
    synchronized (s.judges) {
        leftWeight  = s.judges.stream().filter(j -> j.getVote() == 0).mapToInt(BattleJudge::getWeightage).sum();
        rightWeight = s.judges.stream().filter(j -> j.getVote() == 1).mapToInt(BattleJudge::getWeightage).sum();
    }
    Integer res;
    if (leftWeight == rightWeight) {
        if (isFinal) return -3;
        res = -1;
    } else if (leftWeight > rightWeight) {
        s.currentPair.getLeftBattler().setScore(s.currentPair.getLeftBattler().getScore() + 1);
        res = 0;
    } else {
        s.currentPair.getRightBattler().setScore(s.currentPair.getRightBattler().getScore() + 1);
        res = 1;
    }
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/score", Map.of(
        "message", res,
        "left",    s.currentPair.getLeftBattler().getScore(),
        "right",   s.currentPair.getRightBattler().getScore()
    ));
    if (res == 0 || res == 1) {
        if (res == 0 && !s.battlers.isEmpty()) {
            s.battlers.get(0).setScore(s.battlers.get(0).getScore() + 1);
        } else if (res == 1 && s.battlers.size() > 1) {
            s.battlers.get(1).setScore(s.battlers.get(1).getScore() + 1);
        }
        s.battlePhase = "REVEALED";
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
            "phase", s.battlePhase,
            "genre", s.activeGenreName != null ? s.activeGenreName : ""
        ));
        persistActiveState(eventName);
    }
    return res;
}
```

- [ ] **Step 4.5: Refactor `removeBattleJudgeService`**

```java
public Integer removeBattleJudgeService(String eventName, SetJudgeDto dto) {
    EventBattleState s = stateFor(eventName);
    s.judges.removeIf(judge -> Objects.equals(judge.getId(), dto.getId()));
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
        Map.of("judges", s.judges));
    persistActiveState(eventName);
    return dto.getId().intValue();
}
```

- [ ] **Step 4.6: Refactor `updateJudgeWeightageService`**

```java
public void updateJudgeWeightageService(String eventName, UpdateJudgeWeightageDto dto) {
    EventBattleState s = stateFor(eventName);
    synchronized (s.judges) {
        s.judges.stream()
            .filter(j -> j.getId().equals(dto.getId()))
            .findFirst()
            .ifPresent(j -> j.setWeightage(Math.max(1, dto.getWeightage())));
    }
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
        Map.of("judges", s.judges));
    persistActiveState(eventName);
}
```

- [ ] **Step 4.7: Refactor `setBattleJudgeService`**

```java
public Integer setBattleJudgeService(String eventName, SetJudgeDto dto) {
    EventBattleState s = stateFor(eventName);
    Judge judge = judgeService.getJudgeById(dto.getId());
    if (judge == null) return -1;
    synchronized (s.judges) {
        boolean exists = s.judges.stream().anyMatch(j -> j.getName().equals(judge.getName()));
        if (exists) return 0;
        BattleJudge battleJudge = new BattleJudge();
        battleJudge.setName(judge.getName());
        battleJudge.setVote(-3);
        battleJudge.setId(dto.getId());
        battleJudge.setWeightage(Math.max(1, dto.getWeightage()));
        s.judges.add(battleJudge);
    }
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
        Map.of("judges", s.judges));
    persistActiveState(eventName);
    return dto.getId().intValue();
}
```

- [ ] **Step 4.8: Refactor `setVoteService`**

```java
public Integer setVoteService(String eventName, SetVoteDto dto) {
    EventBattleState s = stateFor(eventName);
    Optional<BattleJudge> battleJudge = s.judges.stream()
        .filter(j -> j.getId().equals(dto.getId())).findFirst();
    if (battleJudge.isEmpty()) return -2;
    battleJudge.get().setVote(dto.getVote());
    Integer code = dto.getVote();
    messagingTemplate.convertAndSend(
        String.format("/topic/battle/vote/%d", dto.getId()),
        Map.of("vote", code, "judge", dto.getId())
    );
    persistActiveState(eventName);
    return code;
}
```

Note: vote topics stay un-namespaced (`/topic/battle/vote/{judgeId}`) because they are judge-scoped, not event-scoped. The frontend's per-judge subscription already provides uniqueness.

- [ ] **Step 4.9: Refactor `resetJudgeVotesService`**

```java
public void resetJudgeVotesService(String eventName) {
    EventBattleState s = stateFor(eventName);
    synchronized (s.judges) {
        for (BattleJudge judge : s.judges) judge.setVote(-3);
    }
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
        Map.of("judges", s.judges));
    persistActiveState(eventName);
}
```

- [ ] **Step 4.10: Refactor `setBracketStateService`**

```java
public void setBracketStateService(String eventName, SetBracketStateDto dto) {
    EventBattleState s = stateFor(eventName);
    Map<String, Object> state = new HashMap<>();
    state.put("topSize", dto.getTopSize());
    state.put("rounds", dto.getRounds());
    s.bracketState = state;
    if (dto.getCurrentRoundIndex() != null) s.currentRoundIndex = dto.getCurrentRoundIndex();
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/bracket", state);
    persistActiveState(eventName);
    broadcastStateSnapshot(eventName);
}
```

- [ ] **Step 4.11: Refactor `setBattlePhaseService`**

```java
public void setBattlePhaseService(String eventName, String phase) {
    setBattlePhaseService(eventName, phase, null);
}

public void setBattlePhaseService(String eventName, String phase, String championName) {
    if ("REVEALED".equals(phase)) return;
    EventBattleState s = stateFor(eventName);
    if ("LOCKED".equals(phase)) {
        synchronized (s.judges) {
            if (s.judges.isEmpty()) {
                throw new IllegalArgumentException(
                    "Cannot start round: no judges assigned. Add at least one judge first.");
            }
        }
    }
    s.battlePhase = phase;
    if (championName != null) s.champion = championName;
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
        "phase",    s.battlePhase,
        "genre",    s.activeGenreName != null ? s.activeGenreName : "",
        "champion", s.champion != null ? s.champion : ""
    ));
    persistActiveState(eventName);
}
```

- [ ] **Step 4.12: Refactor `setOverlayConfigService`**

```java
public void setOverlayConfigService(String eventName, SetOverlayConfigDto dto) {
    EventBattleState s = stateFor(eventName);
    Map<String, Object> newConfig = new HashMap<>();
    newConfig.put("showImages", dto.isShowImages());
    newConfig.put("leftColor",  dto.getLeftColor());
    newConfig.put("rightColor", dto.getRightColor());
    s.overlayConfig = newConfig;
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/overlay-config", newConfig);
}
```

- [ ] **Step 4.13: Refactor `broadcastChampionReveal`**

```java
public void broadcastChampionReveal(String eventName, ChampionRevealDto dto) {
    EventBattleState s = stateFor(eventName);
    if (dto.isDismiss()) {
        s.champion = null;
        persistActiveState(eventName);
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/champion-reveal",
            Map.of("dismiss", true));
    } else {
        s.champion = dto.getChampionName() != null ? dto.getChampionName() : "";
        persistActiveState(eventName);
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/champion-reveal", Map.of(
            "dismiss",      false,
            "genreName",    dto.getGenreName()    != null ? dto.getGenreName()    : "",
            "championName", s.champion
        ));
    }
}
```

- [ ] **Step 4.14: Refactor `handleTimerPayload`**

```java
public void handleTimerPayload(String eventName, Map<String, Object> payload) {
    EventBattleState s = stateFor(eventName);
    s.lastTimerPayload = new HashMap<>(payload);
    s.timerLastUpdated = System.currentTimeMillis();
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/timer", payload);
}

public void rebroadcastTimer(String eventName, Object timerState) {
    if (timerState != null) {
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/timer", timerState);
    }
}
```

- [ ] **Step 4.15: Refactor `handleFormatTimerPayload`**

```java
public void handleFormatTimerPayload(String eventName, Map<String, Object> payload) {
    EventBattleState s = stateFor(eventName);
    s.lastFormatTimerPayload = new HashMap<>(payload);
    s.formatTimerLastUpdated = System.currentTimeMillis();
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/format-timer", payload);
    persistFormatTimer(eventName);
}

private void persistFormatTimer(String eventName) {
    EventBattleState s = stateFor(eventName);
    if (activeEventName == null || s.activeGenreName == null || s.lastFormatTimerPayload == null) return;
    try {
        BattleGenreState st = battleGenreStateRepository
            .findByEventNameAndGenreName(eventName, s.activeGenreName).orElse(null);
        if (st == null) return;
        st.setFormatTimerJson(objectMapper.writeValueAsString(s.lastFormatTimerPayload));
        st.setUpdatedAt(LocalDateTime.now());
        battleGenreStateRepository.save(st);
    } catch (Exception e) {
        System.err.println("Failed to persist format timer: " + e.getMessage());
    }
}
```

- [ ] **Step 4.16: Refactor `setResolvedParticipants`**

```java
@Transactional
public void setResolvedParticipants(String eventName, String genreName, List<String> participants) {
    try {
        BattleGenreState st = battleGenreStateRepository
            .findByEventNameAndGenreName(eventName, genreName)
            .orElse(new BattleGenreState());
        st.setEventName(eventName);
        st.setGenreName(genreName);
        st.setResolvedParticipantsJson(
            participants != null ? objectMapper.writeValueAsString(participants) : null);
        st.setUpdatedAt(LocalDateTime.now());
        battleGenreStateRepository.save(st);
        EventBattleState s = stateFor(eventName);
        if (genreName != null && genreName.equals(s.activeGenreName)) {
            broadcastStateSnapshot(eventName);
        }
    } catch (Exception e) {
        System.err.println("Failed to save resolved participants: " + e.getMessage());
    }
}
```

- [ ] **Step 4.17: Refactor `persistActiveState`**

```java
private void persistActiveState(String eventName) {
    EventBattleState s = stateFor(eventName);
    if (s.activeGenreName == null) return;
    try {
        BattleGenreState st = battleGenreStateRepository
            .findByEventNameAndGenreName(eventName, s.activeGenreName)
            .orElse(new BattleGenreState());
        st.setEventName(eventName);
        st.setGenreName(s.activeGenreName);
        st.setBracketJson(s.bracketState != null ? objectMapper.writeValueAsString(s.bracketState) : null);
        if (s.bracketState instanceof Map) {
            Object ts = ((Map<?, ?>) s.bracketState).get("topSize");
            if (ts != null) {
                try { st.setTopSize(Integer.parseInt(ts.toString())); }
                catch (NumberFormatException ignored) {}
            }
        }
        st.setCurrentRoundIndex(s.currentRoundIndex);
        st.setCurrentPairLeft(s.currentPair.getLeftBattler().getName());
        st.setCurrentPairLeftMembers(
            objectMapper.writeValueAsString(s.currentPair.getLeftBattler().getMembers()));
        st.setCurrentPairRight(s.currentPair.getRightBattler().getName());
        st.setCurrentPairRightMembers(
            objectMapper.writeValueAsString(s.currentPair.getRightBattler().getMembers()));
        st.setIsFinal(s.currentIsFinal);
        st.setBattlePhase(s.battlePhase);
        st.setChampion(s.champion);
        st.setSmokeListJson(objectMapper.writeValueAsString(new ArrayList<>(s.battlers)));
        synchronized (s.judges) {
            st.setJudgesJson(objectMapper.writeValueAsString(new ArrayList<>(s.judges)));
        }
        if (s.lastFormatTimerPayload != null) {
            st.setFormatTimerJson(objectMapper.writeValueAsString(s.lastFormatTimerPayload));
        }
        st.setUpdatedAt(LocalDateTime.now());
        battleGenreStateRepository.save(st);
    } catch (Exception e) {
        System.err.println("Failed to persist battle state: " + e.getMessage());
    }
}
```

- [ ] **Step 4.18: Refactor `loadGenreStateIntoMemory`**

```java
private void loadGenreStateIntoMemory(String eventName, String genreName) {
    EventBattleState s = stateFor(eventName);
    s.activeGenreName = genreName;
    s.genreFormat = null;
    if (eventName != null && genreName != null) {
        Event ev = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (ev != null) {
            eventGenreRepo.findByEventAndName(ev, genreName)
                .ifPresent(eg -> s.genreFormat = eg.getFormat());
        }
    }
    Optional<BattleGenreState> stateOpt =
        battleGenreStateRepository.findByEventNameAndGenreName(eventName, genreName);
    if (stateOpt.isEmpty()) { resetToDefaults(eventName); return; }
    BattleGenreState dbState = stateOpt.get();
    try {
        s.bracketState = dbState.getBracketJson() != null
            ? objectMapper.readValue(dbState.getBracketJson(), Map.class) : null;
        s.currentRoundIndex = dbState.getCurrentRoundIndex() != null ? dbState.getCurrentRoundIndex() : 0;
        s.currentPair.getLeftBattler().setName(
            dbState.getCurrentPairLeft() != null ? dbState.getCurrentPairLeft() : "");
        s.currentPair.getLeftBattler().setScore(0);
        s.currentPair.getLeftBattler().setMembers(dbState.getCurrentPairLeftMembers() != null
            ? objectMapper.readValue(dbState.getCurrentPairLeftMembers(), new TypeReference<List<String>>(){})
            : new ArrayList<>());
        s.currentPair.getRightBattler().setName(
            dbState.getCurrentPairRight() != null ? dbState.getCurrentPairRight() : "");
        s.currentPair.getRightBattler().setScore(0);
        s.currentPair.getRightBattler().setMembers(dbState.getCurrentPairRightMembers() != null
            ? objectMapper.readValue(dbState.getCurrentPairRightMembers(), new TypeReference<List<String>>(){})
            : new ArrayList<>());
        s.currentIsFinal = Boolean.TRUE.equals(dbState.getIsFinal());
        s.battlePhase = dbState.getBattlePhase() != null ? dbState.getBattlePhase() : "IDLE";
        s.champion = dbState.getChampion();
        s.battlers = dbState.getSmokeListJson() != null
            ? objectMapper.readValue(dbState.getSmokeListJson(), new TypeReference<List<Battler>>(){})
            : new ArrayList<>();
        synchronized (s.judges) {
            s.judges.clear();
            if (dbState.getJudgesJson() != null) {
                List<BattleJudge> restored =
                    objectMapper.readValue(dbState.getJudgesJson(), new TypeReference<List<BattleJudge>>(){});
                s.judges.addAll(restored);
            }
        }
        if (dbState.getFormatTimerJson() != null) {
            s.lastFormatTimerPayload = objectMapper.readValue(
                dbState.getFormatTimerJson(), new TypeReference<Map<String, Object>>(){});
            s.formatTimerLastUpdated = System.currentTimeMillis();
        } else {
            s.lastFormatTimerPayload = null;
        }
    } catch (Exception e) {
        System.err.println("Failed to load genre state from DB: " + e.getMessage());
        resetToDefaults(eventName);
    }
}
```

- [ ] **Step 4.19: Refactor `resetToDefaults`**

```java
private void resetToDefaults(String eventName) {
    EventBattleState s = stateFor(eventName);
    s.bracketState = null;
    s.currentRoundIndex = 0;
    s.currentPair.getLeftBattler().setName("");
    s.currentPair.getLeftBattler().setScore(0);
    s.currentPair.getLeftBattler().setMembers(new ArrayList<>());
    s.currentPair.getRightBattler().setName("");
    s.currentPair.getRightBattler().setScore(0);
    s.currentPair.getRightBattler().setMembers(new ArrayList<>());
    s.currentIsFinal = false;
    s.battlePhase = "IDLE";
    s.champion = null;
    s.battlers = new ArrayList<>();
    synchronized (s.judges) { s.judges.clear(); }
    s.lastFormatTimerPayload = null;
    s.formatTimerLastUpdated = 0;
}
```

- [ ] **Step 4.20: Refactor `switchActiveGenreService`**

```java
@Transactional
public void switchActiveGenreService(SetActiveGenreDto dto) {
    // Persist the old active event's state before switching
    if (activeEventName != null) persistActiveState(activeEventName);

    BattleActiveGenre active = battleActiveGenreRepository.findById(1)
        .orElse(new BattleActiveGenre(1, null, null));
    active.setEventName(dto.getEventName());
    active.setGenreName(dto.getGenreName());
    battleActiveGenreRepository.save(active);
    activeEventName = dto.getEventName();
    loadGenreStateIntoMemory(activeEventName, dto.getGenreName());
    broadcastStateSnapshot(activeEventName);

    EventBattleState s = stateFor(activeEventName);
    synchronized (s.judges) {
        messagingTemplate.convertAndSend("/topic/battle/" + activeEventName + "/judges",
            Map.of("judges", new ArrayList<>(s.judges)));
    }
    messagingTemplate.convertAndSend("/topic/battle/" + activeEventName + "/phase", Map.of(
        "phase", s.battlePhase,
        "genre", s.activeGenreName != null ? s.activeGenreName : ""
    ));
}
```

- [ ] **Step 4.21: Refactor `getBattleStateService`**

```java
public Map<String, Object> getBattleStateService(String eventName) {
    EventBattleState s = stateFor(eventName);
    if (s.activeGenreName == null) return new HashMap<>();
    Map<String, Object> state = new HashMap<>();
    state.put("eventName",      eventName);
    state.put("genreName",      s.activeGenreName);
    state.put("genreFormat",    s.genreFormat);
    state.put("bracket",        s.bracketState);
    state.put("currentRoundIndex", s.currentRoundIndex);
    Map<String, Object> pair = new HashMap<>();
    pair.put("left",         s.currentPair.getLeftBattler().getName());
    pair.put("leftMembers",  s.currentPair.getLeftBattler().getMembers());
    pair.put("right",        s.currentPair.getRightBattler().getName());
    pair.put("rightMembers", s.currentPair.getRightBattler().getMembers());
    pair.put("isFinal",      s.currentIsFinal);
    state.put("currentPair", pair);
    state.put("battlePhase", s.battlePhase);
    state.put("champion",    s.champion);
    String resolvedJson = null;
    if (s.activeGenreName != null) {
        var st = battleGenreStateRepository
            .findByEventNameAndGenreName(eventName, s.activeGenreName).orElse(null);
        if (st != null) resolvedJson = st.getResolvedParticipantsJson();
    }
    state.put("resolvedParticipants", resolvedJson != null ? resolvedJson : "");
    synchronized (s.judges) {
        state.put("judges", new ArrayList<>(s.judges));
    }
    if (!s.battlers.isEmpty()) state.put("smokeBattlers", new ArrayList<>(s.battlers));
    // Timer — recalculate elapsed
    if (s.lastTimerPayload != null) {
        Map<String, Object> timer = new HashMap<>(s.lastTimerPayload);
        if (Boolean.TRUE.equals(timer.get("running"))) {
            long elapsedSec = (System.currentTimeMillis() - s.timerLastUpdated) / 1000;
            int adjusted = Math.max(0, ((Number) timer.getOrDefault("timeLeft", 0)).intValue() - (int) elapsedSec);
            timer.put("timeLeft", adjusted);
            if (adjusted <= 0) { timer.put("running", false); timer.put("timeLeft", 0); }
        }
        state.put("timer", timer);
    }
    // Format timer — recalculate elapsed
    if (s.lastFormatTimerPayload != null) {
        Map<String, Object> ft = new HashMap<>(s.lastFormatTimerPayload);
        if (Boolean.TRUE.equals(ft.get("running"))) {
            long elapsedSec = (System.currentTimeMillis() - s.formatTimerLastUpdated) / 1000;
            int adjusted = Math.max(0, ((Number) ft.getOrDefault("timeLeft", 0)).intValue() - (int) elapsedSec);
            ft.put("timeLeft", adjusted);
            if (adjusted <= 0) { ft.put("running", false); ft.put("timeLeft", 0); ft.put("expired", true); }
        }
        state.put("formatTimer", ft);
    }
    return state;
}
```

- [ ] **Step 4.22: Refactor `broadcastStateSnapshot`**

```java
private void broadcastStateSnapshot(String eventName) {
    Map<String, Object> state = getBattleStateService(eventName);
    messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/state", state);
    if (state.containsKey("timer")) {
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/timer", state.get("timer"));
    }
    if (state.containsKey("formatTimer")) {
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/format-timer", state.get("formatTimer"));
    }
}
```

- [ ] **Step 4.23: Refactor `loadStateFromDb` (@PostConstruct)**

```java
@PostConstruct
public void loadStateFromDb() {
    try {
        battleActiveGenreRepository.findById(1).ifPresent(active -> {
            if (active.getEventName() != null && active.getGenreName() != null) {
                activeEventName = active.getEventName();
                loadGenreStateIntoMemory(activeEventName, active.getGenreName());
            }
        });
    } catch (Exception e) {
        System.err.println("Failed to load battle state from DB on startup: " + e.getMessage());
    }
}
```

- [ ] **Step 4.24: Update the public getter methods**

Replace the old getters that return single-slot fields with event-aware versions:

```java
public List<Battler> getSmokeBattlersService() {
    return stateFor(resolveEvent(null)).battlers;
}

public BattlePair getCurrentPair(String eventName) {
    return stateFor(eventName).currentPair;
}

/** Backwards-compat getter — uses activeEventName */
public BattlePair getCurrentPair() {
    return stateFor(resolveEvent(null)).currentPair;
}

public void setCurrentPair(String eventName, BattlePair pair) {
    stateFor(eventName).currentPair = pair;
}

public List<BattleJudge> getJudges() {
    return stateFor(resolveEvent(null)).judges;
}

public List<BattleJudge> getJudges(String eventName) {
    return stateFor(eventName).judges;
}

public void setJudges(List<BattleJudge> judges) {
    EventBattleState s = stateFor(resolveEvent(null));
    synchronized (s.judges) { s.judges.clear(); s.judges.addAll(judges); }
}

public String getBattlePhase() {
    return stateFor(resolveEvent(null)).battlePhase;
}

public String getBattlePhase(String eventName) {
    return stateFor(eventName).battlePhase;
}

public boolean isCurrentFinal() {
    return stateFor(resolveEvent(null)).currentIsFinal;
}

public Map<String, Object> getOverlayConfig() {
    return stateFor(resolveEvent(null)).overlayConfig;
}

public Map<String, Object> getOverlayConfig(String eventName) {
    return stateFor(eventName).overlayConfig;
}

public String getActiveEventName() { return activeEventName; }

public String getActiveGenreName() {
    return stateFor(resolveEvent(null)).activeGenreName;
}

public String getStatus() { return status; }
public void setStatus(String status) { this.status = status; }
public List<String> getModes() { return modes; }
public String getSelectedMode() { return selectedMode; }
public void setSelectedMode(SetBattleModeDto dto) { this.selectedMode = dto.getMode(); }
public Object getBracketState() { return stateFor(resolveEvent(null)).bracketState; }
```

- [ ] **Step 4.25: Verify compilation**

```bash
cd BES && mvn compile -pl . 2>&1 | grep -E "ERROR|BUILD"
```

Expected: `BUILD SUCCESS`

---

## Task 5 — Add `eventName` to all mutation DTOs

**Files:** All DTO files listed in the File Map

Pattern: add an optional (no `@NotNull`/`@NotBlank`) `String eventName` field with getter.

- [ ] **Step 5.1: `SetBattlerPairDto.java`** — add field + getter:

```java
private String eventName;
public String getEventName() { return eventName; }
```

- [ ] **Step 5.2: Apply same pattern to remaining DTOs**

Add `private String eventName; public String getEventName() { return eventName; }` to:
- `SetBattlePhaseDto.java`
- `SetBracketStateDto.java`
- `SetJudgeDto.java`
- `UpdateJudgeWeightageDto.java`
- `SetVoteDto.java`
- `SetOverlayConfigDto.java`
- `SetSmokeBattlersDto.java`
- `SetBattleScoreDto.java`
- `ChampionRevealDto.java`

- [ ] **Step 5.3: Verify compilation**

```bash
cd BES && mvn compile -pl . 2>&1 | grep -E "ERROR|BUILD"
```

Expected: `BUILD SUCCESS`

---

## Task 6 — Update `BattleController` to pass `eventName`

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

Add a private helper at the top of the class:

```java
private String resolveEvent(String dtoEventName) {
    return (dtoEventName != null && !dtoEventName.isBlank())
        ? dtoEventName
        : battleService.getActiveEventName() != null ? battleService.getActiveEventName() : "";
}
```

Update every controller method that calls a service mutator. Follow this pattern for each:

```java
// Before
public ResponseEntity<?> setBattlerPair(@Valid @RequestBody SetBattlerPairDto dto) {
    battleService.setBattlerPairService(dto);
    ...
}

// After
public ResponseEntity<?> setBattlerPair(@Valid @RequestBody SetBattlerPairDto dto) {
    battleService.setBattlerPairService(resolveEvent(dto.getEventName()), dto);
    ...
}
```

- [ ] **Step 6.1: Update `setBattlerPair`** — pass `resolveEvent(dto.getEventName()), dto`

- [ ] **Step 6.2: Update `clearBattlePair`** — add `@RequestParam(required=false) String event` and call `battleService.clearBattlePairService(resolveEvent(event))`

```java
@DeleteMapping("/battle-pair")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> clearBattlePair(@RequestParam(required = false) String event) {
    battleService.clearBattlePairService(resolveEvent(event));
    return ResponseEntity.ok(Map.of("message", "battle pair cleared"));
}
```

- [ ] **Step 6.3: Update `setBattleScore`** — pass `resolveEvent(dto != null ? dto.getEventName() : null)`

```java
@PostMapping("/score")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setBattleScore(@RequestBody(required = false) SetBattleScoreDto dto) {
    String eName = resolveEvent(dto != null ? dto.getEventName() : null);
    boolean isFinal = dto != null && dto.isFinal();
    Integer code = battleService.setScoreService(eName, isFinal);
    ...
    // response logic unchanged — only service call changes
    String eName2 = eName; // used in response map below
    if (code == 0) return ResponseEntity.ok(Map.of(
        "message", "Left side get one point",
        "winner",  0,
        "current score", battleService.getCurrentPair(eName2).getLeftBattler().getScore()
    ));
    if (code == 1) return ResponseEntity.ok(Map.of(
        "message", "Right side get one point",
        "winner",  1,
        "current score", battleService.getCurrentPair(eName2).getRightBattler().getScore()
    ));
    // other codes unchanged
}
```

- [ ] **Step 6.4: Update `revote`** — add `@RequestParam(required=false) String event`

```java
@PostMapping("/revote")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> revote(@RequestParam(required = false) String event) {
    battleService.resetJudgeVotesService(resolveEvent(event));
    return ResponseEntity.ok(Map.of("message", "Judge votes reset"));
}
```

- [ ] **Step 6.5: Update `championReveal`**

```java
battleService.broadcastChampionReveal(resolveEvent(dto.getEventName()), dto);
```

- [ ] **Step 6.6: Update `removeBattleJudge`**

```java
battleService.removeBattleJudgeService(resolveEvent(dto.getEventName()), dto)
```

- [ ] **Step 6.7: Update `setJudge`**

```java
Integer status = battleService.setBattleJudgeService(resolveEvent(dto.getEventName()), dto);
```

- [ ] **Step 6.8: Update `updateJudgeWeightage`**

```java
battleService.updateJudgeWeightageService(resolveEvent(dto.getEventName()), dto);
```

- [ ] **Step 6.9: Update `submitVote`**

```java
Integer vote = battleService.setVoteService(resolveEvent(dto.getEventName()), dto);
```

- [ ] **Step 6.10: Update `setSmokeList`**

```java
battleService.setSmokeBattlersService(resolveEvent(dto.getEventName()), dto);
```

- [ ] **Step 6.11: Update `setBattlePhase`**

```java
battleService.setBattlePhaseService(resolveEvent(dto.getEventName()), dto.getPhase(), dto.getChampion());
```

- [ ] **Step 6.12: Update `setBracketState`**

```java
battleService.setBracketStateService(resolveEvent(dto.getEventName()), dto);
```

- [ ] **Step 6.13: Update `setOverlayConfig`**

```java
battleService.setOverlayConfigService(resolveEvent(dto.getEventName()), dto);
return ResponseEntity.ok(battleService.getOverlayConfig(resolveEvent(dto.getEventName())));
```

- [ ] **Step 6.14: Update `updateTimer` and `updateFormatTimer`**

```java
@PostMapping("/timer")
public ResponseEntity<?> updateTimer(
        @RequestParam(required = false) String event,
        @RequestBody Map<String, Object> payload) {
    battleService.handleTimerPayload(resolveEvent(event), payload);
    return ResponseEntity.ok(Map.of("message", "Timer updated"));
}

@PostMapping("/format-timer")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> updateFormatTimer(
        @RequestParam(required = false) String event,
        @RequestBody Map<String, Object> payload) {
    battleService.handleFormatTimerPayload(resolveEvent(event), payload);
    return ResponseEntity.ok(Map.of("message", "Format timer updated"));
}
```

- [ ] **Step 6.15: Verify compilation**

```bash
cd BES && mvn compile -pl . 2>&1 | grep -E "ERROR|BUILD"
```

Expected: `BUILD SUCCESS`

---

## Task 7 — Add `?event=` param to `GET /battle/state` (closes #99)

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

- [ ] **Step 7.1: Update `getBattleState`**

```java
@GetMapping("/state")
public ResponseEntity<?> getBattleState(@RequestParam(required = false) String event) {
    String eName = resolveEvent(event);
    Map<String, Object> state = battleService.getBattleStateService(eName);
    if (state.containsKey("timer")) {
        battleService.rebroadcastTimer(eName, state.get("timer"));
    }
    return ResponseEntity.ok(state);
}
```

- [ ] **Step 7.2: Also update `getOverlayConfig` and `getAllBattleJudges` to accept optional `?event=`**

```java
@GetMapping("/overlay-config")
public ResponseEntity<?> getOverlayConfig(@RequestParam(required = false) String event) {
    return ResponseEntity.ok(battleService.getOverlayConfig(resolveEvent(event)));
}

@GetMapping("/judges")
public ResponseEntity<?> getAllBattleJudges(@RequestParam(required = false) String event) {
    return ResponseEntity.ok(Map.of("judges", battleService.getJudges(resolveEvent(event))));
}
```

---

## Task 8 — Update `BattleServiceTest` to pass `eventName`

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`

All existing tests call service methods without an event name. Since the new methods require one, use a constant:

```java
private static final String E = "TestEvent";
```

Update every test call to pass `E` as the first argument. The tests remain otherwise identical. Example:

```java
// Before
service.setBattlerPairService(dto);
assertThat(service.getBattlePhase()).isEqualTo("LOCKED");

// After
service.setBattlerPairService(E, dto);
assertThat(service.getBattlePhase(E)).isEqualTo("LOCKED");
```

Apply this pattern to every test. The specific changes for each test:

- [ ] **Step 8.1: Add constant at top of class**

```java
private static final String E = "TestEvent";
```

- [ ] **Step 8.2: Update `setBattlerPair_setsNamesAndTransitionsToLOCKED`**

```java
service.setBattlerPairService(E, dto);
assertThat(service.getCurrentPair(E).getLeftBattler().getName()).isEqualTo("Alice");
assertThat(service.getCurrentPair(E).getRightBattler().getName()).isEqualTo("Bob");
assertThat(service.getBattlePhase(E)).isEqualTo("LOCKED");
```

- [ ] **Step 8.3: Update `setBattlePhase_cannotManuallySetREVEALED`**

```java
service.setBattlePhaseService(E, "REVEALED");
assertThat(service.getBattlePhase(E)).isEqualTo("IDLE");
```

- [ ] **Step 8.4: Update `setBattlePhase_setsVOTING`**

```java
service.setBattlePhaseService(E, "VOTING");
assertThat(service.getBattlePhase(E)).isEqualTo("VOTING");
```

- [ ] **Step 8.5: Update `setScore_returnsMinusOneWhenNoJudges`**

```java
assertThat(service.setScoreService(E, false)).isEqualTo(-1);
```

- [ ] **Step 8.6: Update `setScore_finalTie_returnsMinusThreeToSignalBlock`**

```java
assertThat(service.setScoreService(E, true)).isEqualTo(-3);
verify(messagingTemplate, never()).convertAndSend(
    eq("/topic/battle/" + E + "/score"), any(Map.class));
```

- [ ] **Step 8.7: Update `setScore_nonFinalTie_returnsMinusOne`**

```java
assertThat(service.setScoreService(E, false)).isEqualTo(-1);
```

- [ ] **Step 8.8: Update `setScore_finalWinner_returnsZeroAndTransitionsToREVEALED`**

```java
service.setBattleJudgeService(E, jDto);
service.setVoteService(E, vDto);
assertThat(service.setScoreService(E, true)).isEqualTo(0);
assertThat(service.getBattlePhase(E)).isEqualTo("REVEALED");
```

- [ ] **Step 8.9: Update `setScore_leftWins_returnsZeroAndTransitionsToREVEALED`**

```java
service.setBattleJudgeService(E, jDto);
service.setVoteService(E, vDto);
Integer result = service.setScoreService(E, false);
assertThat(result).isEqualTo(0);
assertThat(service.getBattlePhase(E)).isEqualTo("REVEALED");
```

- [ ] **Step 8.10: Update `setScore_rightWins_returnsOne`** — same pattern, use `E`

- [ ] **Step 8.11: Update all remaining tests** — same pattern (add `E` as first arg, use `getBattlePhase(E)`, `getCurrentPair(E)`, `getJudges()`)

Affected tests:
- `setBattleJudge_returnsDuplicateCodeWhenAlreadyAdded`
- `setBattleJudge_returnsMinusOneWhenJudgeNotFound`
- `removeBattleJudge_removesFromList`
- `setVote_returnsMinusTwoWhenJudgeNotInList`
- `getOverlayConfig_returnsDefaults` — becomes `service.getOverlayConfig(E)`
- `setOverlayConfigService_updatesInMemoryState` — pass `E` to service call + getter
- `setOverlayConfigService_broadcastsToWebSocket` — topic becomes `/topic/battle/TestEvent/overlay-config`
- `resetJudgeVotes_setsAllVotesToMinusThreeAndBroadcasts` — topic becomes `/topic/battle/TestEvent/judges`
- `setBattlerPair_withIsFinalTrue_persistsFlag` — use `isCurrentFinal()` on state `E` (add `service.stateFor` or expose via `isCurrentFinal(String e)`)
- `setBattlerPair_broadcastsIsFinalInPayload` — topic becomes `/topic/battle/TestEvent/battle-pair`
- `broadcastChampionReveal_sendsToCorrectTopic` — topic becomes `/topic/battle/TestEvent/champion-reveal`
- `setScore_heavierJudgeWins_overrulesHeadcount` — all method calls get `E`
- `setScore_equalWeightedVotes_returnsTie` — all method calls get `E`
- `updateJudgeWeightage_setsNewWeightageAndBroadcasts` — topic becomes `/topic/battle/TestEvent/judges`

Note: for `isCurrentFinal()`, add a public getter that takes `String eventName`:
```java
public boolean isCurrentFinal(String eventName) { return stateFor(eventName).currentIsFinal; }
```

- [ ] **Step 8.12: Run all tests (GREEN)**

```bash
cd BES && mvn test -Dtest=BattleServiceTest -pl . 2>&1 | tail -30
```

Expected: All tests including `twoEvents_statesAreIsolated` PASS.

- [ ] **Step 8.13: Run full test suite**

```bash
cd BES && mvn test -pl . 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`, no test failures.

- [ ] **Step 8.14: Commit**

```bash
git add BES/src/main/java/com/example/BES/ BES/src/test/java/com/example/BES/services/BattleServiceTest.java
git commit -m "feat: per-event WS state isolation + namespaced topics (#98, #99)"
```

---

## Task 9 — Manual end-to-end verification

This is an operational smoke test run by the developer with the app running locally.

- [ ] **Step 9.1: Start the backend**

```bash
cd BES && mvn spring-boot:run
```

- [ ] **Step 9.2: Verify single-event flow still works**

1. Open `http://localhost:5173/battle/control`
2. Set an active event + genre
3. Set a battle pair
4. Open `http://localhost:5050/api/v1/battle/state` in browser
5. Confirm `currentPair.left` and `currentPair.right` are set correctly

- [ ] **Step 9.3: Verify namespaced WS topics**

1. Open browser DevTools → Network → WS tab
2. Set a battle pair in BattleControl
3. In the WS inspector, find the incoming message from the server
4. Confirm the topic in the message frame is `/topic/battle/{yourEventName}/battle-pair` not `/topic/battle/battle-pair`

- [ ] **Step 9.4: Verify `GET /battle/state?event=` works**

```bash
curl "http://localhost:5050/api/v1/battle/state?event=YourEventName"
```

Confirm response contains `"eventName": "YourEventName"`.

---

## Self-Review Checklist

- [x] All `convertAndSend` calls use namespaced topics
- [x] All service mutators accept `String eventName`
- [x] DTOs have optional `eventName` (no validation — nullable)
- [x] Controller falls back to `activeEventName` when DTO doesn't include event
- [x] `GET /battle/state?event=` implemented
- [x] `GET /overlay-config?event=` and `GET /judges?event=` updated
- [x] `DELETE /battle-pair` and `POST /revote` accept `?event=` query param
- [x] `@PostConstruct` loads last-active event's state into the map
- [x] `persistActiveState` scoped to event name (no longer uses `activeEventName` as fallback)
- [x] `resetToDefaults` scoped to event name
- [x] Existing BattleServiceTest updated + new isolation test passes
- [x] Vote topic (`/topic/battle/vote/{judgeId}`) intentionally NOT namespaced (judge-scoped)
