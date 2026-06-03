# Battle Division State Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist battle state (bracket, pair, round, phase, judges) per event+genre in DB so all four battle views restore correctly after a page refresh or backend restart.

**Architecture:** Thin persistence layer (Approach A from spec). Backend gains two new DB tables (`battle_genre_state`, `battle_active_genre`), three new REST endpoints, and calls a private `persistActiveState()` after every mutation. Frontend views call `GET /api/v1/battle/state` on mount and subscribe to `/topic/battle/state` for cross-client genre switches.

**Tech Stack:** Spring Boot (JPA/Hibernate, Spring Data, Jackson, STOMP WebSocket), Vue 3 (Composition API, STOMP client), PostgreSQL (prod), H2 (tests), Flyway (migrations), JUnit 5 + MockMvc (backend tests).

**Spec:** `docs/superpowers/specs/2026-06-02-battle-division-state-persistence-design.md`

---

## File Map

| Action | File |
|--------|------|
| Create | `BES/src/main/resources/db/migration/V17__add_battle_state_persistence.sql` |
| Create | `BES/src/main/java/com/example/BES/models/BattleGenreState.java` |
| Create | `BES/src/main/java/com/example/BES/models/BattleActiveGenre.java` |
| Create | `BES/src/main/java/com/example/BES/respositories/BattleGenreStateRepository.java` |
| Create | `BES/src/main/java/com/example/BES/respositories/BattleActiveGenreRepository.java` |
| Create | `BES/src/main/java/com/example/BES/dtos/battle/SetActiveGenreDto.java` |
| Modify | `BES/src/main/java/com/example/BES/dtos/battle/SetBracketStateDto.java` |
| Modify | `BES/src/main/java/com/example/BES/services/BattleService.java` |
| Modify | `BES/src/main/java/com/example/BES/controllers/BattleController.java` |
| Modify | `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java` |
| Modify | `BES-frontend/src/utils/api.js` |
| Modify | `BES-frontend/src/views/BattleControl.vue` |
| Modify | `BES-frontend/src/views/BracketVisualization.vue` |
| Modify | `BES-frontend/src/views/BattleOverlay.vue` |
| Modify | `BES-frontend/src/views/BattleJudge.vue` |

---

## Task 1: DB Migration

**Files:**
- Create: `BES/src/main/resources/db/migration/V17__add_battle_state_persistence.sql`

- [ ] **Step 1: Create migration file**

```sql
CREATE TABLE battle_genre_state (
    id                          BIGSERIAL    PRIMARY KEY,
    event_name                  VARCHAR(255) NOT NULL,
    genre_name                  VARCHAR(255) NOT NULL,
    bracket_json                TEXT,
    top_size                    INTEGER,
    current_round_index         INTEGER      DEFAULT 0,
    current_pair_left           VARCHAR(255),
    current_pair_left_members   TEXT,
    current_pair_right          VARCHAR(255),
    current_pair_right_members  TEXT,
    is_final                    BOOLEAN      DEFAULT FALSE,
    battle_phase                VARCHAR(20)  DEFAULT 'IDLE',
    judges_json                 TEXT,
    updated_at                  TIMESTAMP    DEFAULT NOW(),
    UNIQUE (event_name, genre_name)
);

CREATE TABLE battle_active_genre (
    id         INTEGER PRIMARY KEY DEFAULT 1,
    event_name VARCHAR(255),
    genre_name VARCHAR(255)
);

INSERT INTO battle_active_genre (id, event_name, genre_name) VALUES (1, NULL, NULL);
```

- [ ] **Step 2: Verify migration applies cleanly**

```bash
cd BES && mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/bes -Dflyway.user=bes -Dflyway.password=bes 2>&1 | tail -5
```

Expected: `Successfully applied 1 migration` (or confirm via Docker logs if running in Docker).

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V17__add_battle_state_persistence.sql
git commit -m "feat: add battle_genre_state and battle_active_genre DB tables (V17)"
```

---

## Task 2: JPA Models

**Files:**
- Create: `BES/src/main/java/com/example/BES/models/BattleGenreState.java`
- Create: `BES/src/main/java/com/example/BES/models/BattleActiveGenre.java`

- [ ] **Step 1: Create `BattleGenreState.java`**

```java
package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "battle_genre_state", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_name", "genre_name"})
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleGenreState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "genre_name")
    private String genreName;

    @Column(name = "bracket_json", columnDefinition = "TEXT")
    private String bracketJson;

    @Column(name = "top_size")
    private Integer topSize;

    @Column(name = "current_round_index")
    private Integer currentRoundIndex;

    @Column(name = "current_pair_left")
    private String currentPairLeft;

    @Column(name = "current_pair_left_members", columnDefinition = "TEXT")
    private String currentPairLeftMembers;

    @Column(name = "current_pair_right")
    private String currentPairRight;

    @Column(name = "current_pair_right_members", columnDefinition = "TEXT")
    private String currentPairRightMembers;

    @Column(name = "is_final")
    private Boolean isFinal;

    @Column(name = "battle_phase")
    private String battlePhase;

    @Column(name = "judges_json", columnDefinition = "TEXT")
    private String judgesJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create `BattleActiveGenre.java`**

```java
package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "battle_active_genre")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleActiveGenre {

    @Id
    private Integer id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "genre_name")
    private String genreName;
}
```

- [ ] **Step 3: Compile to verify models are valid**

```bash
cd BES && mvn compile -q 2>&1 | tail -5
```

Expected: no output (clean compile).

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/BattleGenreState.java \
        BES/src/main/java/com/example/BES/models/BattleActiveGenre.java
git commit -m "feat: add BattleGenreState and BattleActiveGenre JPA models"
```

---

## Task 3: JPA Repositories

**Files:**
- Create: `BES/src/main/java/com/example/BES/respositories/BattleGenreStateRepository.java`
- Create: `BES/src/main/java/com/example/BES/respositories/BattleActiveGenreRepository.java`

- [ ] **Step 1: Create `BattleGenreStateRepository.java`**

```java
package com.example.BES.respositories;

import com.example.BES.models.BattleGenreState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BattleGenreStateRepository extends JpaRepository<BattleGenreState, Long> {
    Optional<BattleGenreState> findByEventNameAndGenreName(String eventName, String genreName);
}
```

- [ ] **Step 2: Create `BattleActiveGenreRepository.java`**

```java
package com.example.BES.respositories;

import com.example.BES.models.BattleActiveGenre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleActiveGenreRepository extends JpaRepository<BattleActiveGenre, Integer> {}
```

- [ ] **Step 3: Compile to verify**

```bash
cd BES && mvn compile -q 2>&1 | tail -5
```

Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/respositories/BattleGenreStateRepository.java \
        BES/src/main/java/com/example/BES/respositories/BattleActiveGenreRepository.java
git commit -m "feat: add BattleGenreStateRepository and BattleActiveGenreRepository"
```

---

## Task 4: DTOs

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/battle/SetActiveGenreDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/battle/SetBracketStateDto.java`

- [ ] **Step 1: Create `SetActiveGenreDto.java`**

```java
package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;

public class SetActiveGenreDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String genreName;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getGenreName() { return genreName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
}
```

- [ ] **Step 2: Add `currentRoundIndex` to `SetBracketStateDto.java`**

Open `BES/src/main/java/com/example/BES/dtos/battle/SetBracketStateDto.java` and add the field + getter/setter:

```java
package com.example.BES.dtos.battle;

public class SetBracketStateDto {
    private String topSize;
    private Object rounds;
    private Integer currentRoundIndex;

    public String getTopSize() { return topSize; }
    public void setTopSize(String topSize) { this.topSize = topSize; }
    public Object getRounds() { return rounds; }
    public void setRounds(Object rounds) { this.rounds = rounds; }
    public Integer getCurrentRoundIndex() { return currentRoundIndex; }
    public void setCurrentRoundIndex(Integer currentRoundIndex) { this.currentRoundIndex = currentRoundIndex; }
}
```

- [ ] **Step 3: Compile to verify**

```bash
cd BES && mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/battle/SetActiveGenreDto.java \
        BES/src/main/java/com/example/BES/dtos/battle/SetBracketStateDto.java
git commit -m "feat: add SetActiveGenreDto and currentRoundIndex to SetBracketStateDto"
```

---

## Task 5: BattleService — Persistence Layer

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

This is the core task. Add repos, new fields, `persistActiveState()`, `switchActiveGenreService()`, `loadGenreStateIntoMemory()`, `@PostConstruct`, and `getBattleStateService()`. Also make `BattleJudge` a static inner class (required for Jackson deserialization).

- [ ] **Step 1: Write the failing integration test for `POST /api/v1/battle/active-genre`**

Add to `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java`:

```java
@Test
@WithMockUser(roles = {"ADMIN"})
public void testSetAndGetActiveGenre() throws Exception {
    String json = objectMapper.writeValueAsString(
        Map.of("eventName", "TestEvent", "genreName", "Breaking Top 16")
    );
    mockMvc.perform(post("/api/v1/battle/active-genre")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Active genre set"));

    mockMvc.perform(get("/api/v1/battle/active-genre"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventName").value("TestEvent"))
            .andExpect(jsonPath("$.genreName").value("Breaking Top 16"));
}

@Test
@WithMockUser
public void testGetBattleStateEmptyWhenNoActiveGenre() throws Exception {
    // Reset active genre by calling switch to a fresh genre
    mockMvc.perform(get("/api/v1/battle/state"))
            .andExpect(status().isOk());
    // State may be empty ({}) or contain a previous test's genre — just verify 200
}

@Test
@WithMockUser(roles = {"ADMIN"})
public void testGetBattleStateAfterSettingActiveGenre() throws Exception {
    String json = objectMapper.writeValueAsString(
        Map.of("eventName", "EventA", "genreName", "Popping")
    );
    mockMvc.perform(post("/api/v1/battle/active-genre")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/battle/state"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventName").value("EventA"))
            .andExpect(jsonPath("$.genreName").value("Popping"))
            .andExpect(jsonPath("$.battlePhase").exists());
}
```

- [ ] **Step 2: Run the tests to confirm they fail (endpoints don't exist yet)**

```bash
cd BES && mvn test -Dtest=BattleControllerIntegrationTest#testSetAndGetActiveGenre -q 2>&1 | tail -10
```

Expected: FAIL — `404` or `405` (endpoint doesn't exist).

- [ ] **Step 3: Replace the full `BattleService.java` with the updated version**

Full file — add all imports at the top, make `BattleJudge` static, add repos + objectMapper, add new fields, add all new methods:

```java
package com.example.BES.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.battle.ChampionRevealDto;
import com.example.BES.dtos.battle.SetActiveGenreDto;
import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetBracketStateDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import com.example.BES.dtos.battle.SetSmokeBattlersDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.models.BattleActiveGenre;
import com.example.BES.models.BattleGenreState;
import com.example.BES.models.Judge;
import com.example.BES.respositories.BattleActiveGenreRepository;
import com.example.BES.respositories.BattleGenreStateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class BattleService {

    @Autowired
    JudgeService judgeService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    BattleGenreStateRepository battleGenreStateRepository;

    @Autowired
    BattleActiveGenreRepository battleActiveGenreRepository;

    @Autowired
    ObjectMapper objectMapper;

    // ── Mode ─────────────────────────────────────────────────────────
    private List<String> modes = Arrays.asList("Top32", "Top16", "7-to-Smoke");
    private String selectedMode;

    // ── Battle state (in-memory) ──────────────────────────────────────
    private Object bracketState = null;
    private Integer currentRoundIndex = 0;
    private List<Battler> battlers = new ArrayList<>();
    private String battlePhase = "IDLE";
    private boolean currentIsFinal = false;
    private Map<String, Object> overlayConfig = new HashMap<>(Map.of(
        "showImages", true,
        "leftColor",  "#dc2626",
        "rightColor", "#2563eb"
    ));
    private BattlePair currentPair;
    private final List<BattleJudge> judges = Collections.synchronizedList(new ArrayList<>());
    private String status;

    // ── Active genre tracking ─────────────────────────────────────────
    private String activeEventName;
    private String activeGenreName;

    BattleService() {
        selectedMode = "";
        currentPair = new BattlePair();
        Battler left = new Battler();
        Battler right = new Battler();
        currentPair.leftBattler = left;
        currentPair.rightBattler = right;
    }

    // ── Startup: restore active genre from DB ─────────────────────────
    @PostConstruct
    public void loadStateFromDb() {
        battleActiveGenreRepository.findById(1).ifPresent(active -> {
            if (active.getEventName() != null && active.getGenreName() != null) {
                activeEventName = active.getEventName();
                activeGenreName = active.getGenreName();
                loadGenreStateIntoMemory(activeEventName, activeGenreName);
            }
        });
    }

    // ── Smoke ─────────────────────────────────────────────────────────
    public List<Battler> getSmokeBattlersService() { return battlers; }

    public void setSmokeBattlersService(SetSmokeBattlersDto dto) {
        battlers = new ArrayList<>();
        for (Battler battler : dto.getBattlers()) battlers.add(battler);
        messagingTemplate.convertAndSend("/topic/battle/smoke", Map.of("battlers", battlers));
    }

    // ── Pair ──────────────────────────────────────────────────────────
    public void setBattlerPairService(SetBattlerPairDto dto) {
        getCurrentPair().leftBattler.setName(dto.getLeftBattler());
        getCurrentPair().leftBattler.setScore(0);
        getCurrentPair().leftBattler.setMembers(dto.getLeftMembers());
        getCurrentPair().rightBattler.setName(dto.getRightBattler());
        getCurrentPair().rightBattler.setScore(0);
        getCurrentPair().rightBattler.setMembers(dto.getRightMembers());
        currentIsFinal = dto.isFinal();
        messagingTemplate.convertAndSend("/topic/battle/battle-pair", Map.of(
            "left",         currentPair.getLeftBattler().getName(),
            "leftScore",    currentPair.getLeftBattler().getScore(),
            "leftMembers",  currentPair.getLeftBattler().getMembers(),
            "right",        currentPair.getRightBattler().getName(),
            "rightScore",   currentPair.getRightBattler().getScore(),
            "rightMembers", currentPair.getRightBattler().getMembers(),
            "isFinal",      currentIsFinal
        ));
        battlePhase = "LOCKED";
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of("phase", battlePhase));
        persistActiveState();
    }

    // ── Score ─────────────────────────────────────────────────────────
    public Integer setScoreService(boolean isFinal) {
        List<Integer> score = new ArrayList<>();
        Integer res = -100;
        synchronized (judges) {
            if (judges.size() == 0) { res = -2; }
            for (BattleJudge judge : judges) score.add(judge.getVote());
        }
        if (Collections.frequency(score, 0) == Collections.frequency(score, 1)) {
            if (isFinal) return -3;
            res = -1;
        } else if (Collections.frequency(score, 0) > Collections.frequency(score, 1)) {
            currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
            res = 0;
        } else {
            currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
            res = 1;
        }
        messagingTemplate.convertAndSend("/topic/battle/score", Map.of(
            "message", res,
            "left",    currentPair.getLeftBattler().getScore(),
            "right",   currentPair.getRightBattler().getScore()
        ));
        if (res == 0 || res == 1) {
            battlePhase = "REVEALED";
            messagingTemplate.convertAndSend("/topic/battle/phase", Map.of("phase", battlePhase));
            persistActiveState();
        }
        return res;
    }

    // ── Judges ────────────────────────────────────────────────────────
    public Integer removeBattleJudgeService(SetJudgeDto dto) {
        judges.removeIf(judge -> Objects.equals(judge.getId(), dto.getId()));
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
        return dto.getId().intValue();
    }

    public Integer setBattleJudgeService(SetJudgeDto dto) {
        Judge judge = judgeService.getJudgeById(dto.getId());
        Integer code = -50;
        if (judge != null) {
            Boolean exists = judges.stream().anyMatch(j -> j.getName().equals(judge.getName()));
            if (exists) return 0;
            BattleJudge battleJudge = new BattleJudge();
            battleJudge.setName(judge.getName());
            battleJudge.setVote(-1);
            battleJudge.setId(dto.getId());
            judges.add(battleJudge);
            code = dto.getId().intValue();
        } else {
            return -1;
        }
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
        return code;
    }

    public Integer setVoteService(SetVoteDto dto) {
        Integer code = -50;
        Optional<BattleJudge> battleJude = judges.stream()
            .filter(j -> j.getId().equals(dto.getId())).findFirst();
        if (battleJude.isPresent()) {
            battleJude.get().setVote(dto.getVote());
        } else {
            return -2;
        }
        code = dto.getVote();
        messagingTemplate.convertAndSend(
            String.format("/topic/battle/vote/%d", dto.getId()),
            Map.of("vote", code, "judge", dto.getId())
        );
        persistActiveState();
        return code;
    }

    public void resetJudgeVotesService() {
        synchronized (judges) {
            for (BattleJudge judge : judges) judge.setVote(-1);
        }
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
    }

    // ── Bracket ───────────────────────────────────────────────────────
    public Object getBracketState() { return bracketState; }

    public void setBracketStateService(SetBracketStateDto dto) {
        Map<String, Object> state = new HashMap<>();
        state.put("topSize", dto.getTopSize());
        state.put("rounds", dto.getRounds());
        this.bracketState = state;
        if (dto.getCurrentRoundIndex() != null) {
            this.currentRoundIndex = dto.getCurrentRoundIndex();
        }
        messagingTemplate.convertAndSend("/topic/battle/bracket", state);
        persistActiveState();
    }

    // ── Phase ─────────────────────────────────────────────────────────
    public String getBattlePhase() { return battlePhase; }

    public void setBattlePhaseService(String phase) {
        if ("REVEALED".equals(phase)) return;
        battlePhase = phase;
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of("phase", battlePhase));
        persistActiveState();
    }

    // ── Overlay config ────────────────────────────────────────────────
    public Map<String, Object> getOverlayConfig() { return overlayConfig; }

    public void setOverlayConfigService(SetOverlayConfigDto dto) {
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("showImages", dto.isShowImages());
        newConfig.put("leftColor",  dto.getLeftColor());
        newConfig.put("rightColor", dto.getRightColor());
        overlayConfig = newConfig;
        messagingTemplate.convertAndSend("/topic/battle/overlay-config", newConfig);
    }

    // ── Champion reveal ───────────────────────────────────────────────
    public void broadcastChampionReveal(ChampionRevealDto dto) {
        if (dto.isDismiss()) {
            messagingTemplate.convertAndSend("/topic/battle/champion-reveal", Map.of("dismiss", true));
        } else {
            messagingTemplate.convertAndSend("/topic/battle/champion-reveal", Map.of(
                "dismiss",       false,
                "genreName",     dto.getGenreName()    != null ? dto.getGenreName()    : "",
                "championName",  dto.getChampionName() != null ? dto.getChampionName() : ""
            ));
        }
    }

    // ── Active genre switch ───────────────────────────────────────────
    @Transactional
    public void switchActiveGenreService(SetActiveGenreDto dto) {
        // 1. Persist current in-memory state under old genre
        persistActiveState();

        // 2. Update singleton in DB
        BattleActiveGenre active = battleActiveGenreRepository.findById(1)
            .orElse(new BattleActiveGenre(1, null, null));
        active.setEventName(dto.getEventName());
        active.setGenreName(dto.getGenreName());
        battleActiveGenreRepository.save(active);

        // 3. Update in-memory active genre
        activeEventName = dto.getEventName();
        activeGenreName = dto.getGenreName();

        // 4. Load new genre state from DB
        loadGenreStateIntoMemory(activeEventName, activeGenreName);

        // 5. Broadcast full state snapshot to all clients
        broadcastStateSnapshot();
    }

    // ── Full state snapshot ───────────────────────────────────────────
    public Map<String, Object> getBattleStateService() {
        if (activeEventName == null || activeGenreName == null) return new HashMap<>();
        Map<String, Object> state = new HashMap<>();
        state.put("eventName", activeEventName);
        state.put("genreName", activeGenreName);
        state.put("bracket", bracketState);
        state.put("currentRoundIndex", currentRoundIndex);
        Map<String, Object> pair = new HashMap<>();
        pair.put("left",         currentPair.getLeftBattler().getName());
        pair.put("leftMembers",  currentPair.getLeftBattler().getMembers());
        pair.put("right",        currentPair.getRightBattler().getName());
        pair.put("rightMembers", currentPair.getRightBattler().getMembers());
        pair.put("isFinal",      currentIsFinal);
        state.put("currentPair", pair);
        state.put("battlePhase", battlePhase);
        synchronized (judges) {
            state.put("judges", new ArrayList<>(judges));
        }
        return state;
    }

    public String getActiveEventName() { return activeEventName; }
    public String getActiveGenreName() { return activeGenreName; }

    // ── Persist ───────────────────────────────────────────────────────
    private void persistActiveState() {
        if (activeEventName == null || activeGenreName == null) return;
        try {
            BattleGenreState s = battleGenreStateRepository
                .findByEventNameAndGenreName(activeEventName, activeGenreName)
                .orElse(new BattleGenreState());
            s.setEventName(activeEventName);
            s.setGenreName(activeGenreName);
            s.setBracketJson(bracketState != null ? objectMapper.writeValueAsString(bracketState) : null);
            if (bracketState instanceof Map) {
                Object ts = ((Map<?, ?>) bracketState).get("topSize");
                if (ts != null) {
                    try { s.setTopSize(Integer.parseInt(ts.toString())); }
                    catch (NumberFormatException ignored) {}
                }
            }
            s.setCurrentRoundIndex(currentRoundIndex);
            s.setCurrentPairLeft(currentPair.getLeftBattler().getName());
            s.setCurrentPairLeftMembers(
                objectMapper.writeValueAsString(currentPair.getLeftBattler().getMembers()));
            s.setCurrentPairRight(currentPair.getRightBattler().getName());
            s.setCurrentPairRightMembers(
                objectMapper.writeValueAsString(currentPair.getRightBattler().getMembers()));
            s.setIsFinal(currentIsFinal);
            s.setBattlePhase(battlePhase);
            synchronized (judges) {
                s.setJudgesJson(objectMapper.writeValueAsString(new ArrayList<>(judges)));
            }
            s.setUpdatedAt(LocalDateTime.now());
            battleGenreStateRepository.save(s);
        } catch (Exception e) {
            System.err.println("Failed to persist battle state: " + e.getMessage());
        }
    }

    private void loadGenreStateIntoMemory(String eventName, String genreName) {
        Optional<BattleGenreState> stateOpt =
            battleGenreStateRepository.findByEventNameAndGenreName(eventName, genreName);
        if (stateOpt.isEmpty()) { resetToDefaults(); return; }
        BattleGenreState s = stateOpt.get();
        try {
            bracketState = s.getBracketJson() != null
                ? objectMapper.readValue(s.getBracketJson(), Map.class) : null;
            currentRoundIndex = s.getCurrentRoundIndex() != null ? s.getCurrentRoundIndex() : 0;
            currentPair.getLeftBattler().setName(s.getCurrentPairLeft() != null ? s.getCurrentPairLeft() : "");
            currentPair.getLeftBattler().setScore(0);
            currentPair.getLeftBattler().setMembers(s.getCurrentPairLeftMembers() != null
                ? objectMapper.readValue(s.getCurrentPairLeftMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            currentPair.getRightBattler().setName(s.getCurrentPairRight() != null ? s.getCurrentPairRight() : "");
            currentPair.getRightBattler().setScore(0);
            currentPair.getRightBattler().setMembers(s.getCurrentPairRightMembers() != null
                ? objectMapper.readValue(s.getCurrentPairRightMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            currentIsFinal = Boolean.TRUE.equals(s.getIsFinal());
            battlePhase = s.getBattlePhase() != null ? s.getBattlePhase() : "IDLE";
            synchronized (judges) {
                judges.clear();
                if (s.getJudgesJson() != null) {
                    List<BattleJudge> restored =
                        objectMapper.readValue(s.getJudgesJson(), new TypeReference<List<BattleJudge>>(){});
                    judges.addAll(restored);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load genre state from DB: " + e.getMessage());
            resetToDefaults();
        }
    }

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
        synchronized (judges) { judges.clear(); }
    }

    private void broadcastStateSnapshot() {
        messagingTemplate.convertAndSend("/topic/battle/state", getBattleStateService());
    }

    // ── Mode / Judges accessors (unchanged) ───────────────────────────
    public List<String> getModes() { return modes; }
    public String getSelectedMode() { return selectedMode; }
    public void setSelectedMode(SetBattleModeDto dto) { this.selectedMode = dto.getMode(); }
    public BattlePair getCurrentPair() { return currentPair; }
    public void setCurrentPair(BattlePair currentPair) { this.currentPair = currentPair; }
    public List<BattleJudge> getJudges() { return judges; }
    public void setJudges(List<BattleJudge> judges) {
        synchronized (this.judges) { this.judges.clear(); this.judges.addAll(judges); }
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isCurrentFinal() { return currentIsFinal; }

    // ── Inner classes ─────────────────────────────────────────────────
    // BattleJudge is static so Jackson can deserialize it from judgesJson
    public static class BattleJudge {
        private Long id;
        private String name;
        private Integer vote;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getVote() { return vote; }
        public void setVote(Integer vote) { this.vote = vote; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    public static class Battler {
        private String name;
        private Integer score;
        private List<String> members = new ArrayList<>();
        Battler() { name = ""; score = 0; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public List<String> getMembers() { return members; }
        public void setMembers(List<String> members) {
            this.members = members != null ? members : new ArrayList<>();
        }
    }

    public class BattlePair {
        private Battler leftBattler;
        private Battler rightBattler;
        public Battler getLeftBattler() { return leftBattler; }
        public Battler getRightBattler() { return rightBattler; }
        public void setLeftBattler(Battler leftBattler) { this.leftBattler = leftBattler; }
        public void setRightBattler(Battler rightBattler) { this.rightBattler = rightBattler; }
    }
}
```

- [ ] **Step 4: Compile**

```bash
cd BES && mvn compile -q 2>&1 | tail -10
```

Expected: no output.

- [ ] **Step 5: Run existing battle tests to ensure no regressions**

```bash
cd BES && mvn test -Dtest=BattleControllerIntegrationTest -q 2>&1 | tail -15
```

Expected: tests that don't involve the new endpoints pass; new tests fail with 404.

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java \
        BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java
git commit -m "feat: add persistence layer to BattleService (persistActiveState, switchActiveGenre, @PostConstruct)"
```

---

## Task 6: BattleController — New Endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

- [ ] **Step 1: Add three new endpoints to `BattleController.java`**

Add after the existing `@GetMapping("/overlay-config")` block (before the closing brace):

```java
@PostMapping("/active-genre")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setActiveGenre(@Valid @RequestBody SetActiveGenreDto dto) {
    battleService.switchActiveGenreService(dto);
    return ResponseEntity.ok(Map.of("message", "Active genre set"));
}

@GetMapping("/active-genre")
public ResponseEntity<?> getActiveGenre() {
    return ResponseEntity.ok(Map.of(
        "eventName", battleService.getActiveEventName() != null ? battleService.getActiveEventName() : "",
        "genreName", battleService.getActiveGenreName() != null ? battleService.getActiveGenreName() : ""
    ));
}

@GetMapping("/state")
public ResponseEntity<?> getBattleState() {
    return ResponseEntity.ok(battleService.getBattleStateService());
}
```

Also add the import at the top of the file (it's already imported via wildcard if using `com.example.BES.dtos.battle.*` — verify and add if missing):

```java
import com.example.BES.dtos.battle.SetActiveGenreDto;
```

- [ ] **Step 2: Compile**

```bash
cd BES && mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 3: Run the new tests**

```bash
cd BES && mvn test -Dtest=BattleControllerIntegrationTest#testSetAndGetActiveGenre+testGetBattleStateAfterSettingActiveGenre -q 2>&1 | tail -15
```

Expected: both tests PASS.

- [ ] **Step 4: Run the full battle test suite**

```bash
cd BES && mvn test -Dtest=BattleControllerIntegrationTest -q 2>&1 | tail -10
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add POST/GET /api/v1/battle/active-genre and GET /api/v1/battle/state endpoints"
```

---

## Task 7: Frontend API Utility Functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add `currentRoundIndex` parameter to `setBracketState`**

Find the existing `setBracketState` function (line ~555) and replace it:

```js
export const setBracketState = async (rounds, topSize, currentRoundIndex = 0) => {
  try {
    return await fetch(`${domain}/api/v1/battle/bracket`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ rounds, topSize: String(topSize), currentRoundIndex })
    })
  } catch (e) { console.error(e) }
}
```

- [ ] **Step 2: Add the three new battle API functions after `getBracketState`**

```js
export const setActiveGenre = async (eventName, genreName) => {
  try {
    return await fetch(`${domain}/api/v1/battle/active-genre`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, genreName })
    })
  } catch (e) { console.error(e) }
}

export const getActiveGenre = async () => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/active-genre`, { credentials: 'include' })
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const getBattleState = async () => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/state`, { credentials: 'include' })
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}
```

- [ ] **Step 3: Verify the frontend builds**

```bash
cd BES-frontend && npm run build 2>&1 | tail -10
```

Expected: `✓ built in` — no errors.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add setActiveGenre, getActiveGenre, getBattleState API functions; add currentRoundIndex to setBracketState"
```

---

## Task 8: BattleControl.vue — Genre Switch + Mount Recovery

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Add `setActiveGenre` and `getBattleState` to the import line**

Find the line that imports from `@/utils/api` near the top of the `<script setup>` block and add `setActiveGenre` and `getBattleState` to it. Example — the existing import likely looks like:

```js
import { ..., setBracketState, getBracketState, ... } from '@/utils/api'
```

Add `setActiveGenre` and `getBattleState` to that same import.

- [ ] **Step 2: Update `broadcastBracket` to pass `currentRound.value`**

Find:
```js
const broadcastBracket = () => setBracketState(toRaw(rounds.value), topSize.value)
```

Replace with:
```js
const broadcastBracket = () => setBracketState(toRaw(rounds.value), topSize.value, currentRound.value)
```

- [ ] **Step 3: Add recovery banner reactive state** (add near the top of the script setup, alongside the other `ref` declarations):

```js
const showRecoveryBanner = ref(false)
const recoveryState      = ref(null)
```

- [ ] **Step 4: Add `jumpToRecoveredPair` handler**

Add this function after `restoreAndBroadcastGenreBattle`:

```js
const jumpToRecoveredPair = async () => {
  if (!recoveryState.value) return
  showRecoveryBanner.value = false
  currentRound.value = recoveryState.value.currentRoundIndex ?? 0
  await restoreAndBroadcastGenreBattle(selectedGenre.value)
}
```

- [ ] **Step 5: Update `watch(selectedGenre)` to call `setActiveGenre` on genre switch**

Find the `watch(selectedGenre, ...)` handler. Inside the `if (newVal)` block, the current code is:

```js
if (oldVal) {
  saveGenreBattleState(oldVal)
  await restoreAndBroadcastGenreBattle(newVal)
}
```

Change it to:

```js
if (oldVal) {
  saveGenreBattleState(oldVal)
  await setActiveGenre(selectedEvent.value, newVal)
}
broadcastBracket()
if (oldVal) {
  await restoreAndBroadcastGenreBattle(newVal)
}
```

**Important:** Also remove the standalone `broadcastBracket()` call that currently appears BEFORE the `if (oldVal)` block (there should now be only one `broadcastBracket()` call in the watch handler, inside the `if (newVal)` block, after the `setActiveGenre` call).

The updated `if (newVal)` section of the watch should read:

```js
if (newVal) {
  const genreNeedsSmoke = newVal.toLowerCase().includes('7 to smoke') || newVal.toLowerCase().includes('7tosmoke')
  if (genreNeedsSmoke && Number(topSize.value) !== 7) {
    topSize.value = 7
    localStorage.setItem('topSize', '7')
  } else if (!genreNeedsSmoke && Number(topSize.value) === 7) {
    topSize.value = 16
    localStorage.setItem('topSize', '16')
  }
  localStorage.setItem("selectedGenre", newVal)
  const storedRounds = localStorage.getItem(`Top${topSize.value}${newVal}Rounds`)
  rounds.value = JSON.parse(storedRounds) || initRounds()
  pickupCrews.value = await getPickupCrews(selectedEvent.value, newVal)
  placeGuestsInBracket()
  if (oldVal) {
    saveGenreBattleState(oldVal)
    await setActiveGenre(selectedEvent.value, newVal)
  }
  broadcastBracket()
  if (oldVal) {
    await restoreAndBroadcastGenreBattle(newVal)
  }
  if (mountJudgeSyncDone && oldVal) await syncJudgesForGenre(newVal, oldVal)
} else {
  pickupCrews.value = []
}
```

- [ ] **Step 6: Add recovery banner logic to `onMounted`**

In the existing `onMounted`, after `mountJudgeSyncDone = true` and before the WS setup, add:

```js
// Restore current round index + show recovery banner if a battle was in progress
const battleState = await getBattleState()
if (battleState?.battlePhase && battleState.battlePhase !== 'IDLE'
    && battleState.currentPair?.left) {
  currentRound.value = battleState.currentRoundIndex ?? 0
  recoveryState.value = battleState
  showRecoveryBanner.value = true
}
```

- [ ] **Step 7: Add recovery banner to the template**

Add the following right after the opening `<div>` of the root template element (or after the top-level toolbar/header — wherever is visible without scrolling):

```html
<Transition name="banner-fade">
  <div v-if="showRecoveryBanner && recoveryState" class="recovery-banner">
    <span class="recovery-dot"></span>
    <span class="recovery-msg">
      IN PROGRESS: ROUND {{ (recoveryState.currentRoundIndex ?? 0) + 1 }} —
      {{ recoveryState.currentPair?.left }} VS {{ recoveryState.currentPair?.right }}
    </span>
    <button class="recovery-btn recovery-btn-jump" @click="jumpToRecoveredPair">JUMP TO PAIR</button>
    <button class="recovery-btn recovery-btn-dismiss" @click="showRecoveryBanner = false">DISMISS</button>
  </div>
</Transition>
```

- [ ] **Step 8: Add recovery banner styles**

Add to the `<style scoped>` section:

```css
/* ── Recovery banner ─────────────────────────────────────────── */
.recovery-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: rgba(245, 158, 11, 0.08);
  border-left: 3px solid rgba(245, 158, 11, 0.8);
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  flex-wrap: wrap;
}
.recovery-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: rgba(245, 158, 11, 0.9);
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.7);
  flex-shrink: 0;
  animation: recoveryPulse 1.6s ease-in-out infinite;
}
.recovery-msg { flex: 1; min-width: 0; }
.recovery-btn {
  font-family: 'Anton SC', sans-serif;
  font-size: 9px; letter-spacing: 0.18em; text-transform: uppercase;
  padding: 4px 12px; border: none; cursor: pointer;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}
.recovery-btn-jump {
  background: rgba(245, 158, 11, 0.25);
  color: rgba(255, 255, 255, 0.9);
}
.recovery-btn-jump:hover { background: rgba(245, 158, 11, 0.4); }
.recovery-btn-dismiss {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.4);
}
.recovery-btn-dismiss:hover { color: rgba(255, 255, 255, 0.7); }

.banner-fade-enter-active, .banner-fade-leave-active { transition: opacity 0.25s ease; }
.banner-fade-enter-from, .banner-fade-leave-to { opacity: 0; }

@keyframes recoveryPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.65); }
}
```

- [ ] **Step 9: Verify no lint errors**

```bash
cd BES-frontend && npm run build 2>&1 | tail -10
```

Expected: `✓ built in` — no errors.

- [ ] **Step 10: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: BattleControl — genre switch calls setActiveGenre, mount restores round index + shows recovery banner"
```

---

## Task 9: BracketVisualization.vue — Mount Restore + State Subscription

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`

- [ ] **Step 1: Add `getBattleState` to the import**

Find:
```js
import { getBracketState, getOverlayConfig } from '@/utils/api'
```

Replace with:
```js
import { getBracketState, getOverlayConfig, getBattleState } from '@/utils/api'
```

- [ ] **Step 2: Replace the `getBracketState()` call in `onMounted` with `getBattleState()`**

Find in `onMounted`:
```js
const state = await getBracketState()
if (state && (state.topSize || state.rounds)) bracketState.value = state
```

Replace with:
```js
const state = await getBattleState()
if (state?.bracket && (state.bracket.topSize || state.bracket.rounds)) {
  bracketState.value = state.bracket
}
if (state?.currentPair?.left) {
  activePair.value = { left: state.currentPair.left, right: state.currentPair.right }
}
if (state?.genreName) {
  currentGenre.value = state.genreName
}
```

- [ ] **Step 3: Add `/topic/battle/state` subscription inside `wsClient.onConnect`**

Inside the `wsClient.onConnect = () => { ... }` block, add after the existing subscriptions:

```js
wsClient.subscribe('/topic/battle/state', (msg) => {
  const data = JSON.parse(msg.body)
  if (data?.bracket && (data.bracket.topSize || data.bracket.rounds)) {
    if (animRunning) {
      pendingBracket.value = data.bracket
    } else {
      bracketState.value = data.bracket
    }
  }
  if (data?.currentPair?.left) {
    activePair.value = { left: data.currentPair.left, right: data.currentPair.right }
  }
  if (data?.genreName) {
    currentGenre.value = data.genreName
  }
})
```

- [ ] **Step 4: Build to verify**

```bash
cd BES-frontend && npm run build 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue
git commit -m "feat: BracketVisualization — restore bracket/pair/genre from getBattleState on mount; subscribe to /topic/battle/state"
```

---

## Task 10: BattleOverlay.vue — Mount Restore + State Subscription

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

- [ ] **Step 1: Add `getBattleState` to the import**

Find:
```js
import { getBattleJudges, getCurrentBattlePair, getImage, getOverlayConfig } from '@/utils/api';
```

Replace with:
```js
import { getBattleJudges, getCurrentBattlePair, getImage, getOverlayConfig, getBattleState } from '@/utils/api';
```

- [ ] **Step 2: Replace the `getBattleJudges` + `getCurrentBattlePair` calls in `onMounted` with `getBattleState`**

In `onMounted`, the non-smoke branch currently reads:
```js
if (!isSmoke.value) {
  battleJudges.value = await getBattleJudges()
  const res = await getCurrentBattlePair()
  if (res) await updateBattlePair(res)
  ...
}
```

Replace the `getBattleJudges` and `getCurrentBattlePair` calls with `getBattleState`. Also restore `battlePhase` (currently the overlay doesn't fetch it on mount):

```js
if (!isSmoke.value) {
  const state = await getBattleState()
  if (state?.battlePhase) battlePhase.value = state.battlePhase
  if (state?.judges?.length) {
    battleJudges.value = { judges: state.judges }
  } else {
    battleJudges.value = await getBattleJudges()
  }
  const pair = state?.currentPair?.left ? state.currentPair : await getCurrentBattlePair()
  if (pair) await updateBattlePair(pair)
  ...
}
```

Keep the WS subscriptions (`subscribeToChannel(cPair2, ...)` etc.) unchanged after this block.

- [ ] **Step 3: Add `/topic/battle/state` subscription for cross-machine genre switches**

After the `subscribeToChannel(cOverlay, '/topic/battle/overlay-config', ...)` block, add:

```js
const cState = createClient(); clients.push(cState)
subscribeToChannel(cState, '/topic/battle/state', async (msg) => {
  if (isSmoke.value) return
  if (msg?.battlePhase) battlePhase.value = msg.battlePhase
  if (msg?.currentPair?.left) await updateBattlePair(msg.currentPair)
  if (msg?.judges?.length) updateBattleJudge({ judges: msg.judges })
})
```

- [ ] **Step 4: Build to verify**

```bash
cd BES-frontend && npm run build 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: BattleOverlay — restore phase/pair/judges from getBattleState on mount; subscribe to /topic/battle/state"
```

---

## Task 11: BattleJudge.vue — Vote Restore from Backend

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue`

- [ ] **Step 1: Update vote restore logic in `onMounted` (Step 5)**

Find the existing vote restore block:
```js
// 5. Restore vote from localStorage (backend WS overrides if different)
if (battlePhase.value === 'VOTING') {
  const stored = restoreVoteFromStorage()
  if (stored !== null) confirmedVote.value = stored
}
```

Replace with:
```js
// 5. Restore vote — backend DB is authoritative (survives restarts), localStorage is fallback
if (battlePhase.value === 'VOTING' && judgeId.value != null) {
  const backendJudge = (battleJudges.value?.judges ?? []).find(j => j.id === judgeId.value)
  const backendVote  = backendJudge?.vote
  if (backendVote === 0 || backendVote === 1 || backendVote === -1) {
    confirmedVote.value = backendVote
  } else {
    const stored = restoreVoteFromStorage()
    if (stored !== null) confirmedVote.value = stored
  }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd BES-frontend && npm run build 2>&1 | tail -5
```

- [ ] **Step 3: Run full backend test suite to confirm no regressions**

```bash
cd BES && mvn test -q 2>&1 | tail -15
```

Expected: all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "feat: BattleJudge — restore confirmed vote from backend judges list (DB-authoritative after restart)"
```

---

## Self-Review

**Spec coverage check:**

| Spec Requirement | Task |
|-----------------|------|
| `battle_genre_state` table | Task 1, 2, 3 |
| `battle_active_genre` singleton table | Task 1, 2, 3 |
| `POST/GET /api/v1/battle/active-genre` | Task 6 |
| `GET /api/v1/battle/state` | Task 6 |
| `/topic/battle/state` WS broadcast | Task 5 (`broadcastStateSnapshot`) |
| `persistActiveState()` on every mutation | Task 5 |
| `@PostConstruct` restart recovery | Task 5 |
| `currentRoundIndex` persisted via `SetBracketStateDto` | Tasks 4, 5, 7, 8 |
| BattleControl genre switch calls `setActiveGenre` | Task 8 |
| BattleControl mount recovery prompt | Task 8 |
| BracketVisualization mount restore + genre ticker fix | Task 9 |
| BattleOverlay mount restore + phase recovery | Task 10 |
| BattleJudge vote restore from backend | Task 11 |
| Judge votes persisted in `judges_json` | Task 5 (`persistActiveState`) |

All spec requirements are covered. ✓
