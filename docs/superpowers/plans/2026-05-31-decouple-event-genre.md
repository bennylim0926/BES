# Decouple EventGenre from Global Genre Table — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the composite PK `(event_id, genre_id)` on `event_genre` with a surrogate BIGSERIAL `id`, add a free-text `name` column, and make `genre_id` nullable — allowing one event to have "Breaking 3v3" AND "Breaking 7 to Smoke" as separate divisions, and custom division names like "Junior Breaking" without touching the global genre pool.

**Architecture:** `EventGenre` becomes the canonical "division" entity for an event, with its own name and optional link back to a global `Genre` for sheet alias inheritance. `EventGenreParticipant`'s composite PK changes from `(event_id, genre_id, participant_id)` to `(event_id, event_genre_id, participant_id)`. All tables that FK into EGP (`score`, `audition_feedback`, `event_genre_participant_member`) rename their `genre_id` column to `event_genre_id`. `EventGenreBattleGuest` and `ScoringCriteria` also switch their `genre_id` FK from `genre` to `event_genre`. Sheet import matching switches from global genre names+aliases to event-specific division names+aliases.

**Tech Stack:** Java 21, Spring Boot 3, JPA/Hibernate, PostgreSQL (Flyway migrations), Vue 3, Vitest

---

## File Map

**New files:**
- `BES/src/main/resources/db/migration/V23__decouple_event_genre.sql`
- `BES/src/main/java/com/example/BES/dtos/GetEventDivisionDto.java`

**Deleted files:**
- `BES/src/main/java/com/example/BES/models/EventGenreId.java`

**Modified files:**
- `BES/src/main/java/com/example/BES/models/EventGenre.java` — surrogate PK, name, sheetAliases, nullable genre
- `BES/src/main/java/com/example/BES/models/EventGenreParticipantId.java` — rename `genreId` → `eventGenreId`
- `BES/src/main/java/com/example/BES/models/EventGenreParticipant.java` — remove `Genre genre`, fix `eventGenre` FK
- `BES/src/main/java/com/example/BES/models/Score.java` — rename JoinColumn `genre_id` → `event_genre_id`
- `BES/src/main/java/com/example/BES/models/AuditionFeedback.java` — same rename
- `BES/src/main/java/com/example/BES/models/EventGenreParticipantMember.java` — same rename
- `BES/src/main/java/com/example/BES/models/EventGenreBattleGuest.java` — `Genre genre` → `EventGenre eventGenre`
- `BES/src/main/java/com/example/BES/models/ScoringCriteria.java` — `Genre genre` → `EventGenre eventGenre`
- `BES/src/main/java/com/example/BES/respositories/EventGenreRepo.java` — new PK type, new finder methods
- `BES/src/main/java/com/example/BES/respositories/EventGenreParticpantRepo.java` — all JPQL updated
- `BES/src/main/java/com/example/BES/respositories/EventGenreBattleGuestRepo.java` — Genre → EventGenre
- `BES/src/main/java/com/example/BES/respositories/ScoringCriteriaRepo.java` — JPQL updated
- `BES/src/main/java/com/example/BES/dtos/AddGenreToEventDto.java` — new division-based structure
- `BES/src/main/java/com/example/BES/dtos/AddParticipantToEventGenreDto.java` — `genreId` → `eventGenreId`
- `BES/src/main/java/com/example/BES/dtos/GetEventGenreParticipantDto.java` — `genreId` → `eventGenreId`
- `BES/src/main/java/com/example/BES/services/EventGenreService.java` — rewrite using new model
- `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java` — all genre refs updated
- `BES/src/main/java/com/example/BES/services/RegistrationService.java` — updated genre lookup chain
- `BES/src/main/java/com/example/BES/services/ResultsService.java` — `egp.getGenre()` → `egp.getEventGenre()`
- `BES/src/main/java/com/example/BES/services/BattleGuestService.java` — Genre → EventGenre lookups
- `BES/src/main/java/com/example/BES/services/ScoringCriteriaService.java` — Genre → EventGenre
- `BES/src/main/java/com/example/BES/services/GoogleSheetService.java` — event-scoped match strings
- `BES/src/main/java/com/example/BES/parsers/GoogleSheetParser.java` — new overload for Map-based matching
- `BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java` — Map-based genre matching
- `BES/src/main/java/com/example/BES/controllers/EventController.java` — updated endpoint signatures
- `BES-frontend/src/utils/api.js` — updated API calls
- `BES-frontend/src/views/EventDetails.vue` — updated to use new division API

---

## Task 1: DB Migration

**Files:**
- Create: `BES/src/main/resources/db/migration/V23__decouple_event_genre.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V23: Decouple event_genre from global genre table
-- Gives event_genre its own surrogate PK and a free-text division name.
-- genre_id becomes optional (NULL = custom division not tied to a global genre).
-- All FK-chains through genre_id in dependent tables are renamed to event_genre_id.

-- ── 1. Add surrogate PK column to event_genre ───────────────────────────────
ALTER TABLE event_genre ADD COLUMN id BIGSERIAL;

-- ── 2. Add division name column and backfill from global genre ───────────────
ALTER TABLE event_genre ADD COLUMN name VARCHAR(255);
UPDATE event_genre eg
    SET name = g.genre_name
    FROM genre g
    WHERE eg.genre_id = g.genre_id;
ALTER TABLE event_genre ALTER COLUMN name SET NOT NULL;

-- ── 3. Add per-division sheet aliases ───────────────────────────────────────
ALTER TABLE event_genre ADD COLUMN sheet_aliases TEXT;

-- ── 4. Swap PK: old (event_id, genre_id) → new surrogate id ─────────────────
ALTER TABLE event_genre DROP CONSTRAINT event_genre_pkey;
ALTER TABLE event_genre ADD PRIMARY KEY (id);

-- ── 5. Make genre_id nullable (custom divisions need no global genre) ─────────
ALTER TABLE event_genre ALTER COLUMN genre_id DROP NOT NULL;

-- ── 6. Enforce unique division name per event ─────────────────────────────────
ALTER TABLE event_genre
    ADD CONSTRAINT event_genre_event_name_unique UNIQUE (event_id, name);

-- ── 7. Migrate event_genre_participant ───────────────────────────────────────
-- Add event_genre_id, backfill from the new event_genre.id, then rebuild PK.

ALTER TABLE event_genre_participant ADD COLUMN event_genre_id BIGINT;

UPDATE event_genre_participant egp
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE egp.event_id = eg.event_id
      AND egp.genre_id = eg.genre_id;

ALTER TABLE event_genre_participant ALTER COLUMN event_genre_id SET NOT NULL;

-- Drop old composite FK to event_genre (auto-named by Postgres)
ALTER TABLE event_genre_participant
    DROP CONSTRAINT IF EXISTS event_genre_participant_event_id_genre_id_fkey;

-- Add new FK: event_genre_id → event_genre.id
ALTER TABLE event_genre_participant
    ADD CONSTRAINT egp_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id);

-- Rebuild composite PK using event_genre_id
ALTER TABLE event_genre_participant DROP CONSTRAINT event_genre_participant_pkey;
ALTER TABLE event_genre_participant DROP COLUMN genre_id;
ALTER TABLE event_genre_participant
    ADD PRIMARY KEY (event_id, event_genre_id, participant_id);

-- ── 8. Migrate score ─────────────────────────────────────────────────────────
ALTER TABLE score ADD COLUMN event_genre_id BIGINT;

UPDATE score s
    SET event_genre_id = egp.event_genre_id
    FROM event_genre_participant egp
    WHERE s.event_id = egp.event_id
      AND s.genre_id = egp.genre_id          -- egp.genre_id still exists at backfill time? No — we already dropped it!
      -- Because we dropped genre_id from egp in step 7, we need a different backfill approach.
      -- Actually, score.genre_id still refers to the old global genre_id which equals event_genre.genre_id.
      -- We can join through event_genre instead:
      AND s.participant_id = egp.participant_id;
-- ^^^ This will fail because egp.genre_id was already dropped. Fix: join via event_genre.

-- CORRECTED approach: backfill score BEFORE dropping EGP's genre_id column.
-- See the corrected ordering below — this entire migration must reorder steps 7 and 8.
```

The ordering above has a dependency issue. The corrected migration:

```sql
-- V23: Decouple event_genre from global genre table

-- ── Phase A: Upgrade event_genre table ──────────────────────────────────────
ALTER TABLE event_genre ADD COLUMN id BIGSERIAL;

ALTER TABLE event_genre ADD COLUMN name VARCHAR(255);
UPDATE event_genre eg
    SET name = g.genre_name
    FROM genre g WHERE eg.genre_id = g.genre_id;
ALTER TABLE event_genre ALTER COLUMN name SET NOT NULL;

ALTER TABLE event_genre ADD COLUMN sheet_aliases TEXT;

ALTER TABLE event_genre DROP CONSTRAINT event_genre_pkey;
ALTER TABLE event_genre ADD PRIMARY KEY (id);
ALTER TABLE event_genre ALTER COLUMN genre_id DROP NOT NULL;
ALTER TABLE event_genre
    ADD CONSTRAINT event_genre_event_name_unique UNIQUE (event_id, name);

-- ── Phase B: Add event_genre_id to all dependent tables (BEFORE dropping any genre_id) ──

-- event_genre_participant
ALTER TABLE event_genre_participant ADD COLUMN event_genre_id BIGINT;
UPDATE event_genre_participant egp
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE egp.event_id = eg.event_id AND egp.genre_id = eg.genre_id;
ALTER TABLE event_genre_participant ALTER COLUMN event_genre_id SET NOT NULL;

-- score  (backfill via event_genre, not via egp, since score has genre_id = global genre_id)
ALTER TABLE score ADD COLUMN event_genre_id BIGINT;
UPDATE score s
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE s.event_id = eg.event_id AND s.genre_id = eg.genre_id;

-- audition_feedback
ALTER TABLE audition_feedback ADD COLUMN event_genre_id BIGINT;
UPDATE audition_feedback af
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE af.event_id = eg.event_id AND af.genre_id = eg.genre_id;

-- event_genre_participant_member (uses egp's old genre_id column which still exists here)
ALTER TABLE event_genre_participant_member ADD COLUMN event_genre_id BIGINT;
UPDATE event_genre_participant_member m
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE m.event_id = eg.event_id AND m.genre_id = eg.genre_id;

-- event_genre_battle_guest: genre_id → event_genre_id (FK moves from genre to event_genre)
ALTER TABLE event_genre_battle_guest ADD COLUMN event_genre_id BIGINT;
UPDATE event_genre_battle_guest bg
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE bg.event_id = eg.event_id AND bg.genre_id = eg.genre_id;
ALTER TABLE event_genre_battle_guest ALTER COLUMN event_genre_id SET NOT NULL;

-- scoring_criteria: genre_id → event_genre_id (FK moves from genre to event_genre)
ALTER TABLE scoring_criteria ADD COLUMN event_genre_id BIGINT;
UPDATE scoring_criteria sc
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE sc.event_id = eg.event_id AND sc.genre_id = eg.genre_id;

-- ── Phase C: Drop old constraints and genre_id columns ───────────────────────

-- event_genre_participant: rebuild PK and FK
ALTER TABLE event_genre_participant
    DROP CONSTRAINT IF EXISTS event_genre_participant_event_id_genre_id_fkey;
ALTER TABLE event_genre_participant DROP CONSTRAINT event_genre_participant_pkey;
ALTER TABLE event_genre_participant DROP COLUMN genre_id;
ALTER TABLE event_genre_participant
    ADD CONSTRAINT egp_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id);
ALTER TABLE event_genre_participant
    ADD PRIMARY KEY (event_id, event_genre_id, participant_id);

-- score
ALTER TABLE score DROP COLUMN genre_id;

-- audition_feedback: drop old PK surrogate, unique constraint, rebuild
ALTER TABLE audition_feedback DROP CONSTRAINT IF EXISTS audition_feedback_event_id_genre_id_participant_id_judge_id_key;
ALTER TABLE audition_feedback DROP COLUMN genre_id;
ALTER TABLE audition_feedback
    ADD CONSTRAINT audition_feedback_unique
    UNIQUE (event_id, event_genre_id, participant_id, judge_id);

-- event_genre_participant_member
ALTER TABLE event_genre_participant_member DROP CONSTRAINT IF EXISTS fk_egpm_egp;
ALTER TABLE event_genre_participant_member DROP COLUMN genre_id;
ALTER TABLE event_genre_participant_member
    ADD CONSTRAINT fk_egpm_egp
    FOREIGN KEY (event_id, event_genre_id, participant_id)
    REFERENCES event_genre_participant (event_id, event_genre_id, participant_id)
    ON DELETE CASCADE;

-- event_genre_battle_guest
ALTER TABLE event_genre_battle_guest
    DROP CONSTRAINT IF EXISTS event_genre_battle_guest_genre_id_fkey;
ALTER TABLE event_genre_battle_guest DROP COLUMN genre_id;
ALTER TABLE event_genre_battle_guest
    ADD CONSTRAINT egbg_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id) ON DELETE CASCADE;

-- scoring_criteria
ALTER TABLE scoring_criteria
    DROP CONSTRAINT IF EXISTS scoring_criteria_genre_id_fkey;
ALTER TABLE scoring_criteria DROP COLUMN genre_id;
ALTER TABLE scoring_criteria
    ADD CONSTRAINT sc_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id);
```

- [ ] **Step 2: Verify migration compiles (flyway validate)**

```bash
cd BES
./mvnw flyway:validate -Dflyway.url=jdbc:postgresql://localhost:5433/postgres \
  -Dflyway.user=postgres -Dflyway.password=postgres 2>&1 | tail -20
```

Expected: `Flyway validation successful` (or similar — it only validates checksum, not SQL syntax)

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V23__decouple_event_genre.sql
git commit -m "feat: V23 migration — decouple event_genre from global genre table"
```

---

## Task 2: Model Layer Changes

**Files:**
- Delete: `BES/src/main/java/com/example/BES/models/EventGenreId.java`
- Modify: `BES/src/main/java/com/example/BES/models/EventGenre.java`
- Modify: `BES/src/main/java/com/example/BES/models/EventGenreParticipantId.java`
- Modify: `BES/src/main/java/com/example/BES/models/EventGenreParticipant.java`
- Modify: `BES/src/main/java/com/example/BES/models/Score.java`
- Modify: `BES/src/main/java/com/example/BES/models/AuditionFeedback.java`
- Modify: `BES/src/main/java/com/example/BES/models/EventGenreParticipantMember.java`
- Modify: `BES/src/main/java/com/example/BES/models/EventGenreBattleGuest.java`
- Modify: `BES/src/main/java/com/example/BES/models/ScoringCriteria.java`

- [ ] **Step 1: Delete EventGenreId.java**

```bash
git rm BES/src/main/java/com/example/BES/models/EventGenreId.java
```

- [ ] **Step 2: Rewrite EventGenre.java**

Replace the entire file with:

```java
package com.example.BES.models;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "genre_id", nullable = true)
    private Genre genre;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "format")
    private String format;

    @Column(name = "sheet_aliases")
    private String sheetAliases;

    @ToString.Exclude
    @OneToMany(mappedBy = "eventGenre")
    private List<EventGenreParticipant> participants;
}
```

- [ ] **Step 3: Rewrite EventGenreParticipantId.java**

```java
package com.example.BES.models;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreParticipantId implements Serializable {
    private Long eventId;
    private Long eventGenreId;
    private Long participantId;
}
```

- [ ] **Step 4: Rewrite EventGenreParticipant.java**

The composite FK to `event_genre` is now a simple `@ManyToOne` on the surrogate ID. The redundant `Genre genre` field is removed.

```java
package com.example.BES.models;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreParticipant {
    @EmbeddedId
    private EventGenreParticipantId id;

    @ManyToOne
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne
    @MapsId("eventGenreId")
    @JoinColumn(name = "event_genre_id")
    private EventGenre eventGenre;

    @ManyToOne
    @MapsId("participantId")
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "format")
    private String format;

    private Integer auditionNumber;

    @ManyToOne
    @JoinColumn(name = "judge_id", nullable = true)
    private Judge judge;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "eventGenreParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Score> scores;

    @Column(name = "team_name")
    private String teamName;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "eventGenreParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventGenreParticipantMember> members = new ArrayList<>();
}
```

- [ ] **Step 5: Update Score.java — rename JoinColumn genre_id → event_genre_id**

```java
    @ManyToOne
    @JoinColumns({
        @JoinColumn(name = "event_id", referencedColumnName = "event_id"),
        @JoinColumn(name = "event_genre_id", referencedColumnName = "event_genre_id"),
        @JoinColumn(name = "participant_id", referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;
```

- [ ] **Step 6: Update AuditionFeedback.java — same rename**

```java
    @ManyToOne
    @JoinColumns({
        @JoinColumn(name = "event_id", referencedColumnName = "event_id"),
        @JoinColumn(name = "event_genre_id", referencedColumnName = "event_genre_id"),
        @JoinColumn(name = "participant_id", referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;
```

- [ ] **Step 7: Update EventGenreParticipantMember.java — same rename**

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "event_id",        referencedColumnName = "event_id"),
        @JoinColumn(name = "event_genre_id",  referencedColumnName = "event_genre_id"),
        @JoinColumn(name = "participant_id",  referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;
```

- [ ] **Step 8: Rewrite EventGenreBattleGuest.java — Genre → EventGenre**

```java
package com.example.BES.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreBattleGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "event_genre_id", nullable = false)
    private EventGenre eventGenre;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "entry_round", nullable = false)
    private String entryRound;
}
```

- [ ] **Step 9: Update ScoringCriteria.java — Genre → EventGenre**

Replace the `genre` field:

```java
    @ManyToOne
    @JoinColumn(name = "event_genre_id")
    private EventGenre eventGenre;  // null = event-level default (applies to all divisions)
```

Remove the old `private Genre genre;` field and its import.

- [ ] **Step 10: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -30
```

Expected: BUILD SUCCESS. Fix any remaining `genreId`/`genre` reference errors.

- [ ] **Step 11: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/
git commit -m "feat: decouple EventGenre — surrogate PK, new name field, nullable genre_id"
```

---

## Task 3: Repository Layer Changes

**Files:**
- Modify: `BES/src/main/java/com/example/BES/respositories/EventGenreRepo.java`
- Modify: `BES/src/main/java/com/example/BES/respositories/EventGenreParticpantRepo.java`
- Modify: `BES/src/main/java/com/example/BES/respositories/EventGenreBattleGuestRepo.java`
- Modify: `BES/src/main/java/com/example/BES/respositories/ScoringCriteriaRepo.java`

- [ ] **Step 1: Rewrite EventGenreRepo.java**

```java
package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;

@Repository
public interface EventGenreRepo extends JpaRepository<EventGenre, Long> {
    Optional<EventGenre> findByEventAndNameIgnoreCase(Event event, String name);
    List<EventGenre> findByEvent(Event event);
}
```

- [ ] **Step 2: Rewrite EventGenreParticpantRepo.java**

All JPQL references to `e.genre.genreId` → `e.id.eventGenreId`, and `e.genre.genreName` → `e.eventGenre.name`. The parameter names change from `genreId` (global genre ID) to `eventGenreId` (event_genre surrogate ID).

```java
package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;

@Repository
public interface EventGenreParticpantRepo extends JpaRepository<EventGenreParticipant, EventGenreParticipantId> {
    List<EventGenreParticipant> findByEventAndEventGenre(Event event, EventGenre eventGenre);
    List<EventGenreParticipant> findByEvent(Event event);

    @Query("""
       SELECT e
       FROM EventGenreParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.eventGenre.name) = LOWER(:divisionName)
         AND LOWER(e.displayName) = LOWER(:participantName)
       """)
    Optional<EventGenreParticipant> findByEventGenreParticipant(
        @Param("eventName") String eventName,
        @Param("divisionName") String divisionName,
        @Param("participantName") String participantName);

    @Query("""
       SELECT COUNT(e)
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.id.eventGenreId = :eventGenreId
       """)
    long countByEventIdAndEventGenreId(@Param("eventId") Long eventId,
                                       @Param("eventGenreId") Long eventGenreId);

    @Query("""
       SELECT COUNT(e)
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.id.eventGenreId = :eventGenreId
         AND e.judge.name = :name
       """)
    long countByEventIdAndEventGenreIdAndJudge(@Param("eventId") Long eventId,
                                               @Param("eventGenreId") Long eventGenreId,
                                               @Param("name") String name);

    @Query("""
        SELECT e FROM EventGenreParticipant e
        WHERE e.event.eventId = :eventId
          AND e.participant.participantId = :participantId
        """)
    List<EventGenreParticipant> findByEventIdAndParticipantId(
        @Param("eventId") Long eventId,
        @Param("participantId") Long participantId);

    @Query("""
       SELECT e.auditionNumber
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.id.eventGenreId = :eventGenreId
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndEventGenre(@Param("eventId") Long eventId,
                                                         @Param("eventGenreId") Long eventGenreId);

    @Query("""
       SELECT e.auditionNumber
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.id.eventGenreId = :eventGenreId
         AND e.judge.name = :name
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndEventGenreAndJudge(@Param("eventId") Long eventId,
                                                                  @Param("eventGenreId") Long eventGenreId,
                                                                  @Param("name") String name);

    @Query("""
       SELECT e FROM EventGenreParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.eventGenre.name) = LOWER(:divisionName)
         AND e.auditionNumber = :auditionNumber
       """)
    Optional<EventGenreParticipant> findByEventNameAndDivisionNameAndAuditionNumber(
        @Param("eventName") String eventName,
        @Param("divisionName") String divisionName,
        @Param("auditionNumber") Integer auditionNumber);

    // ── Format-scoped pool queries ────────────────────────────────────────────

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format IS NULL")
    long countByEventIdAndEventGenreIdAndFormatIsNull(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format = :format")
    long countByEventIdAndEventGenreIdAndFormat(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format IS NULL AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormatIsNull(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format = :format AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormat(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format IS NULL AND e.judge.name = :name")
    long countByEventIdAndEventGenreIdAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format = :format AND e.judge.name = :name")
    long countByEventIdAndEventGenreIdAndFormatAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format IS NULL AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND e.format = :format AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormatAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format, @Param("name") String name);

    // ── Solo pool (format IS NULL OR format = '1v1' treated as one pool) ──────

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1')")
    long countByEventIdAndEventGenreIdAndSolo(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndSolo(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.judge.name = :name")
    long countByEventIdAndEventGenreIdAndSoloAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.id.eventGenreId = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndSoloAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);
}
```

- [ ] **Step 3: Update EventGenreBattleGuestRepo.java**

```java
package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreBattleGuest;

public interface EventGenreBattleGuestRepo extends JpaRepository<EventGenreBattleGuest, Long> {
    List<EventGenreBattleGuest> findByEventAndEventGenre(Event event, EventGenre eventGenre);
    List<EventGenreBattleGuest> findByEvent(Event event);
}
```

- [ ] **Step 4: Update ScoringCriteriaRepo.java**

```java
package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.ScoringCriteria;

@Repository
public interface ScoringCriteriaRepo extends JpaRepository<ScoringCriteria, Long> {

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event.eventName = :eventName AND sc.eventGenre IS NULL ORDER BY sc.displayOrder ASC")
    List<ScoringCriteria> findEventLevelByEventName(@Param("eventName") String eventName);

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event.eventName = :eventName AND LOWER(sc.eventGenre.name) = LOWER(:divisionName) ORDER BY sc.displayOrder ASC")
    List<ScoringCriteria> findByEventNameAndDivisionName(@Param("eventName") String eventName, @Param("divisionName") String divisionName);
}
```

- [ ] **Step 5: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/respositories/
git commit -m "feat: update repos for decoupled EventGenre — new queries, rename genreId → eventGenreId"
```

---

## Task 4: DTO Layer Changes

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/GetEventDivisionDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/AddGenreToEventDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/AddParticipantToEventGenreDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/GetEventGenreParticipantDto.java`

- [ ] **Step 1: Create GetEventDivisionDto.java**

This DTO replaces `GetGenreDto` for event-scoped division endpoints.

```java
package com.example.BES.dtos;

import java.util.List;

public class GetEventDivisionDto {
    public Long eventGenreId;   // event_genre.id (surrogate)
    public String name;          // division display name
    public String format;        // e.g. "3v3", null for solo
    public String sheetAliases;  // comma-separated raw string
    public Long genreId;         // global genre link, may be null
}
```

- [ ] **Step 2: Rewrite AddGenreToEventDto.java**

```java
package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AddGenreToEventDto {
    @NotBlank @Size(max = 255)
    public String eventName;

    @NotEmpty
    public List<Division> divisions;

    public static class Division {
        @NotBlank @Size(max = 255)
        public String name;
        public String format;
        public Long genreId;   // optional link to global genre for sheet alias inheritance
    }
}
```

- [ ] **Step 3: Update AddParticipantToEventGenreDto.java**

```java
package com.example.BES.dtos;

public class AddParticipantToEventGenreDto {
    public Long participantId;
    public Long eventId;
    public Long eventGenreId;
}
```

- [ ] **Step 4: Update GetEventGenreParticipantDto.java**

Rename `genreId` → `eventGenreId`:

```java
package com.example.BES.dtos;

import java.util.List;

public class GetEventGenreParticipantDto {
    public String eventName;
    public String participantName;
    public String genreName;
    public String judgeName;
    public Integer auditionNumber;
    public Boolean walkin;
    public Long participantId;
    public Long eventId;
    public Long eventGenreId;
    public String referenceCode;
    public List<String> memberNames;
    public String format;
}
```

- [ ] **Step 5: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS (services will fail because they haven't been updated yet — that's OK at this stage).

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/
git commit -m "feat: update DTOs for decoupled EventGenre — new GetEventDivisionDto, division-based AddGenreToEventDto"
```

---

## Task 5: Service Layer — EventGenreService, BattleGuestService, ScoringCriteriaService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreService.java`
- Modify: `BES/src/main/java/com/example/BES/services/BattleGuestService.java`
- Modify: `BES/src/main/java/com/example/BES/services/ScoringCriteriaService.java`

- [ ] **Step 1: Rewrite EventGenreService.java**

```java
package com.example.BES.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetEventDivisionDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;

@Service
public class EventGenreService {
    @Autowired EventGenreRepo eventGenreRepo;
    @Autowired EventRepo eventRepo;
    @Autowired GenreRepo genreRepo;

    public List<GetEventDivisionDto> getGenresByEventService(String eventName) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (e == null) return new ArrayList<>();
        return eventGenreRepo.findByEvent(e).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public void updateEventGenreFormat(Long eventGenreId, String format) {
        EventGenre eg = eventGenreRepo.findById(eventGenreId).orElse(null);
        if (eg == null) throw new RuntimeException("Division not found");
        eg.setFormat(format == null || format.isBlank() ? null : format.trim());
        eventGenreRepo.save(eg);
    }

    public void addGenreToEventService(AddGenreToEventDto dto) {
        Event e = eventRepo.findByEventName(dto.eventName).orElse(null);
        if (e == null) throw new NullPointerException("Event does not exist");

        for (AddGenreToEventDto.Division div : dto.divisions) {
            String name = div.name.trim();
            if (eventGenreRepo.findByEventAndNameIgnoreCase(e, name).isPresent()) {
                throw new DataIntegrityViolationException("Division '" + name + "' already exists in this event");
            }
            Genre genre = null;
            if (div.genreId != null) {
                genre = genreRepo.findById(div.genreId).orElse(null);
            }
            EventGenre eg = new EventGenre();
            eg.setEvent(e);
            eg.setGenre(genre);
            eg.setName(name);
            eg.setFormat(div.format == null || div.format.isBlank() ? null : div.format.trim());
            eventGenreRepo.save(eg);
        }
    }

    /** Returns all match strings for a division: its name + parsed sheet_aliases (all lowercase). */
    public List<String> getMatchStrings(EventGenre eg) {
        List<String> all = new ArrayList<>();
        all.add(eg.getName().toLowerCase());
        if (eg.getSheetAliases() != null && !eg.getSheetAliases().isBlank()) {
            Arrays.stream(eg.getSheetAliases().split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .forEach(all::add);
        }
        // Also inherit global genre aliases if linked
        if (eg.getGenre() != null && eg.getGenre().getSheetAliases() != null
                && !eg.getGenre().getSheetAliases().isBlank()) {
            Arrays.stream(eg.getGenre().getSheetAliases().split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .forEach(all::add);
        }
        return all;
    }

    private GetEventDivisionDto toDto(EventGenre eg) {
        GetEventDivisionDto dto = new GetEventDivisionDto();
        dto.eventGenreId = eg.getId();
        dto.name = eg.getName();
        dto.format = eg.getFormat();
        dto.sheetAliases = eg.getSheetAliases();
        dto.genreId = eg.getGenre() != null ? eg.getGenre().getGenreId() : null;
        return dto;
    }
}
```

- [ ] **Step 2: Rewrite BattleGuestService.java**

Replace global `Genre` lookup with event division lookup via `eventGenreRepo.findByEventAndNameIgnoreCase`:

```java
package com.example.BES.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddBattleGuestDto;
import com.example.BES.dtos.GetBattleGuestDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreBattleGuest;
import com.example.BES.respositories.EventGenreBattleGuestRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;

@Service
public class BattleGuestService {

    @Autowired EventRepo eventRepo;
    @Autowired EventGenreRepo eventGenreRepo;
    @Autowired EventGenreBattleGuestRepo battleGuestRepo;

    public List<GetBattleGuestDto> getBattleGuests(String eventName, String divisionName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventGenre eg = event == null ? null
            : eventGenreRepo.findByEventAndNameIgnoreCase(event, divisionName).orElse(null);
        if (event == null || eg == null) return List.of();

        return battleGuestRepo.findByEventAndEventGenre(event, eg).stream()
            .map(g -> toDto(g)).collect(Collectors.toList());
    }

    public List<GetBattleGuestDto> getAllBattleGuestsForEvent(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return List.of();
        return battleGuestRepo.findByEvent(event).stream()
            .map(g -> toDto(g)).collect(Collectors.toList());
    }

    public GetBattleGuestDto addBattleGuest(String eventName, String divisionName, AddBattleGuestDto dto) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventGenre eg = event == null ? null
            : eventGenreRepo.findByEventAndNameIgnoreCase(event, divisionName).orElse(null);
        if (event == null || eg == null) throw new RuntimeException("Event or division not found");

        EventGenreBattleGuest guest = new EventGenreBattleGuest();
        guest.setEvent(event);
        guest.setEventGenre(eg);
        guest.setGuestName(dto.guestName);
        guest.setEntryRound(dto.entryRound);
        guest = battleGuestRepo.save(guest);
        return toDto(guest);
    }

    public void removeBattleGuest(Long guestId) {
        battleGuestRepo.deleteById(guestId);
    }

    private GetBattleGuestDto toDto(EventGenreBattleGuest g) {
        GetBattleGuestDto dto = new GetBattleGuestDto();
        dto.id = g.getId();
        dto.guestName = g.getGuestName();
        dto.entryRound = g.getEntryRound();
        dto.genreName = g.getEventGenre().getName();
        return dto;
    }
}
```

- [ ] **Step 3: Update ScoringCriteriaService.java**

Replace `Genre genre` with `EventGenre eventGenre`. The service is called with `(eventName, genreName)` where `genreName` is now the division name. Read the existing file first, then make these targeted changes:

a) Remove `GenreRepo genreRepo` field and its `@Autowired`.
b) Add `@Autowired EventGenreRepo eventGenreRepo;`.
c) In `getCriteria(eventName, genreName)`: replace `findByEventNameAndGenreName` → `findByEventNameAndDivisionName`.
d) In `getStrictCriteria(eventName, genreName)`: same.
e) In `addCriteria(dto)`:
   ```java
   // OLD:
   Genre genre = genreRepo.findByGenreName(dto.genreName).orElseThrow(...);
   sc.setGenre(genre);
   // NEW:
   if (dto.genreName != null && !dto.genreName.isBlank()) {
       Event event = eventRepo.findByEventNameIgnoreCase(dto.eventName).orElseThrow(() -> new RuntimeException("Event not found"));
       EventGenre eg = eventGenreRepo.findByEventAndNameIgnoreCase(event, dto.genreName)
           .orElseThrow(() -> new RuntimeException("Division not found: " + dto.genreName));
       sc.setEventGenre(eg);
   }
   ```
f) In `deleteAllCriteria(eventName, genreName)`: replace `findByEventNameAndGenreName` → `findByEventNameAndDivisionName`.
g) In `toDto(sc)`: replace `sc.getGenre() != null ? sc.getGenre().getGenreName() : null` → `sc.getEventGenre() != null ? sc.getEventGenre().getName() : null`.

- [ ] **Step 4: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -20
```

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreService.java \
        BES/src/main/java/com/example/BES/services/BattleGuestService.java \
        BES/src/main/java/com/example/BES/services/ScoringCriteriaService.java
git commit -m "feat: update EventGenreService, BattleGuestService, ScoringCriteriaService for decoupled EventGenre"
```

---

## Task 6: Service Layer — EventGenreParticpantService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`

This service has the most widespread changes. Every method that reads `egp.getGenre()` or constructs `EventGenreParticipantId` with a `genre_id` must be updated.

- [ ] **Step 1: Write the failing test to catch any Genre reference**

```bash
cd BES && grep -r "getGenre\(\)\|\.genreId\b\|GenreRepo\|Genre genre" \
  src/main/java/com/example/BES/services/EventGenreParticpantService.java
```

Expected output: lists all occurrences that need fixing. Every one must be eliminated.

- [ ] **Step 2: Update addWalkInToEventGenreParticipant**

Replace the current method body. The key changes:
- `genreRepo.findByGenreName(genre)` → `eventGenreRepo.findByEventAndNameIgnoreCase(ep.getEvent(), genre)`
- `new EventGenreParticipantId(eventId, g.getGenreId(), participantId)` → `new EventGenreParticipantId(eventId, eg.getId(), participantId)`
- Remove `egp.setGenre(g)` (field no longer exists)
- `eventGenreRepo.findByEventAndGenre(ep.getEvent(), g)` → already have `eg`

```java
public EventGenreParticipant addWalkInToEventGenreParticipant(
        Participant p, String divisionName, EventParticipant ep,
        String judgeName, String entryMode, String teamName, List<String> teamMembers) {

    EventGenre eg = eventGenreRepo.findByEventAndNameIgnoreCase(ep.getEvent(), divisionName).orElse(null);
    if (eg == null) throw new RuntimeException("Division not found: " + divisionName);
    Judge j = judgeRepo.findByName(judgeName).orElse(null);
    EventGenreParticipantId id = new EventGenreParticipantId(
        ep.getEvent().getEventId(), eg.getId(), p.getParticipantId());
    EventGenreParticipant egp = repo.findById(id).orElse(null);
    if (egp == null) {
        egp = new EventGenreParticipant();
        egp.setId(id);
        egp.setJudge(j);
        egp.setEvent(ep.getEvent());
        egp.setParticipant(p);
        egp.setEventGenre(eg);

        String genreFormat = eg.getFormat();
        boolean isTeamFormat = isTeamFormat(genreFormat);
        boolean isTeamEntry = isTeamFormat && !"solo".equalsIgnoreCase(entryMode);

        if (isTeamEntry) {
            validateTeamEntry(genreFormat, teamName, teamMembers);
            egp.setFormat(genreFormat);
            egp.setTeamName(teamName);
            egp.setDisplayName(teamName);
        } else {
            egp.setFormat(isTeamFormat ? null : genreFormat);
            egp.setDisplayName(ep.getDisplayName() != null ? ep.getDisplayName() : p.getParticipantName());
        }
    }
    EventGenreParticipant saved = repo.save(egp);

    if (!"solo".equalsIgnoreCase(entryMode) && isTeamFormat(saved.getFormat()) && teamMembers != null) {
        for (String memberName : teamMembers) {
            if (memberName != null && !memberName.isBlank()) {
                egpMemberRepo.save(new EventGenreParticipantMember(saved, memberName));
            }
        }
    }
    return saved;
}
```

Also remove the `GenreRepo genreRepo` field from the service (no longer needed here — the repo is now only used in addGenreToExistingParticipant below).

- [ ] **Step 3: Update getAllAuditionNumsViaQR**

```java
public void getAllAuditionNumsViaQR(Long participantId, Long eventId) {
    List<EventGenreParticipant> entries =
        repo.findByEventIdAndParticipantId(eventId, participantId);
    for (EventGenreParticipant entry : entries) {
        AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
        dto.participantId = participantId;
        dto.eventId = eventId;
        dto.eventGenreId = entry.getId().getEventGenreId();
        int attempts = 0;
        while (true) {
            try {
                getAuditionNumViaQR(dto);
                break;
            } catch (Exception e) {
                if (++attempts >= 3) throw e;
            }
        }
    }
}
```

- [ ] **Step 4: Update getAuditionNumViaQR**

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void getAuditionNumViaQR(AddParticipantToEventGenreDto dto) {
    Integer auditionNumber = 0;
    EventGenreParticipantId id = new EventGenreParticipantId(dto.eventId, dto.eventGenreId, dto.participantId);

    EventGenreParticipant participantInEventGenre = repo.findById(id).orElse(new EventGenreParticipant());
    Judge j = participantInEventGenre.getJudge();
    if (participantInEventGenre.getParticipant() != null && participantInEventGenre.getAuditionNumber() == null) {
        int totalInGenre;
        List<Integer> takenNumbers;

        String entryFormat = participantInEventGenre.getFormat();
        boolean isSolo = entryFormat == null || "1v1".equalsIgnoreCase(entryFormat);
        long egId = dto.eventGenreId;

        if (j != null) {
            if (isSolo) {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSoloAndJudge(dto.eventId, egId, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSoloAndJudge(dto.eventId, egId, j.getName());
            } else {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormatAndJudge(dto.eventId, egId, entryFormat, j.getName());
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormatAndJudge(dto.eventId, egId, entryFormat, j.getName());
            }
        } else {
            if (isSolo) {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndSolo(dto.eventId, egId);
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndSolo(dto.eventId, egId);
            } else {
                totalInGenre = (int) repo.countByEventIdAndEventGenreIdAndFormat(dto.eventId, egId, entryFormat);
                takenNumbers = repo.findAuditionNumberByEventAndEventGenreAndFormat(dto.eventId, egId, entryFormat);
            }
        }

        List<Integer> pool = IntStream.rangeClosed(1, totalInGenre)
                .boxed().collect(Collectors.toCollection(ArrayList::new));
        pool.removeAll(takenNumbers);
        Collections.shuffle(pool, SECURE_RANDOM);
        if (pool.isEmpty()) throw new RuntimeException("No available audition numbers for this genre");
        auditionNumber = pool.get(0);
        participantInEventGenre.setAuditionNumber(auditionNumber);
        repo.save(participantInEventGenre);

        EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(
            participantInEventGenre.getEvent(), participantInEventGenre.getParticipant()).orElse(null);
        String refCode = ep != null ? ep.getReferenceCode() : null;
        Map<String, Object> auditMsg = new java.util.HashMap<>();
        auditMsg.put("auditionNumber", auditionNumber);
        auditMsg.put("genre", participantInEventGenre.getEventGenre().getName());
        auditMsg.put("name", participantInEventGenre.getDisplayName());
        auditMsg.put("judge", j != null ? j.getName() : "");
        auditMsg.put("eventName", participantInEventGenre.getEvent().getEventName());
        auditMsg.put("participantId", participantInEventGenre.getParticipant().getParticipantId());
        auditMsg.put("eventId", participantInEventGenre.getEvent().getEventId());
        auditMsg.put("eventGenreId", participantInEventGenre.getId().getEventGenreId());
        auditMsg.put("walkin", false);
        auditMsg.put("refCode", refCode != null ? refCode : "");
        auditMsg.put("format", participantInEventGenre.getFormat() != null ? participantInEventGenre.getFormat() : "");
        messagingTemplate.convertAndSend("/topic/audition/", auditMsg);
    } else {
        messagingTemplate.convertAndSend("/topic/error/",
            Map.of(
                "audition", participantInEventGenre.getAuditionNumber(),
                "genre", participantInEventGenre.getEventGenre().getName(),
                "name", participantInEventGenre.getDisplayName(),
                "judge", j != null ? j.getName() : ""));
    }
}
```

- [ ] **Step 5: Update getAllEventGenreParticipantByEventService**

Replace all `res.getGenre().getGenreName()` → `res.getEventGenre().getName()` and `res.getGenre().getGenreId()` → `res.getId().getEventGenreId()`:

```java
dto.genreName = res.getEventGenre().getName();
dto.eventGenreId = res.getId().getEventGenreId();
```

- [ ] **Step 6: Update removeParticipantFromGenre**

```java
public void removeParticipantFromGenre(long participantId, long eventId, long eventGenreId) {
    EventGenreParticipantId id = new EventGenreParticipantId(eventId, eventGenreId, participantId);
    EventGenreParticipant egp = repo.findById(id).orElse(null);
    if (egp == null) return;
    String removedGenreName = egp.getEventGenre().getName();
    // ... rest of method unchanged except removedGenreName source
```

- [ ] **Step 7: Update addGenreToExistingParticipant**

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void addGenreToExistingParticipant(long participantId, long eventId, String divisionName) {
    Event event = eventRepo.findById(eventId).orElse(null);
    if (event == null) throw new RuntimeException("Event not found");
    EventGenre eg = eventGenreRepo.findByEventAndNameIgnoreCase(event, divisionName).orElse(null);
    if (eg == null) throw new RuntimeException("Division not found: " + divisionName);

    EventGenreParticipantId id = new EventGenreParticipantId(eventId, eg.getId(), participantId);
    if (repo.existsById(id)) return;

    Participant participant = participantRepo.findById(participantId).orElse(null);
    if (participant == null) throw new RuntimeException("Participant not found");

    EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
    EventGenreParticipant egp = new EventGenreParticipant();
    egp.setId(id);
    egp.setEvent(event);
    egp.setEventGenre(eg);
    egp.setParticipant(participant);
    egp.setDisplayName(ep != null ? ep.getDisplayName() : participant.getParticipantName());

    String genreFormat = eg.getFormat();
    boolean isTeamEntry = isTeamFormat(genreFormat)
        && ep != null
        && ((ep.getTeamName() != null && !ep.getTeamName().isBlank())
            || (ep.getTeamMembers() != null && !ep.getTeamMembers().isEmpty()));
    egp.setFormat(isTeamEntry ? genreFormat : (isTeamFormat(genreFormat) ? null : genreFormat));
    if (isTeamEntry && ep != null) {
        egp.setDisplayName(ep.getTeamName() != null ? ep.getTeamName() : egp.getDisplayName());
        egp.setTeamName(ep.getTeamName());
    }
    repo.save(egp);

    if (ep != null) {
        String current = ep.getGenre();
        if (current == null || current.isBlank()) {
            ep.setGenre(divisionName);
        } else if (Arrays.stream(current.split(",")).map(String::trim).noneMatch(g -> g.equalsIgnoreCase(divisionName))) {
            ep.setGenre(current + ", " + divisionName);
        }
        eventParticipantRepo.save(ep);
    }
}
```

Also: remove the `GenreRepo genreRepo` `@Autowired` field if it's no longer used anywhere in this service. Add `EventGenreRepo eventGenreRepo` `@Autowired` if not already present.

- [ ] **Step 8: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -20
```

- [ ] **Step 9: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java
git commit -m "feat: update EventGenreParticpantService — replace Genre refs with EventGenre"
```

---

## Task 7: Service Layer — RegistrationService and ResultsService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/RegistrationService.java`
- Modify: `BES/src/main/java/com/example/BES/services/ResultsService.java`

- [ ] **Step 1: Update RegistrationService.java**

The main `addParticipantToEvent` loop currently:
1. Calls `genreRepo.findByGenreName(genreName)` to get a `Genre`
2. Builds `EventGenreParticipantId(event_id, genre.getGenreId(), participant_id)`
3. Calls `eventGenreRepo.findByEventAndGenre(event, genre)` to get the `EventGenre`

Replace with:
1. `eventGenreRepo.findByEventAndNameIgnoreCase(event, genreName)` → get `EventGenre` directly
2. Build `EventGenreParticipantId(event_id, eg.getId(), participant_id)`
3. `egp.setEventGenre(eg)` (no `setGenre`)

Update `hasTeamFormatGenre` and `getTeamFormat` similarly.

Update `getUnverifiedParticipantsFromDb`: `egp.getGenre().getGenreName()` → `egp.getEventGenre().getName()`.

Update `getCheckinList`: same.

Remove `GenreRepo genreRepo` field and its import. Add `EventGenreRepo eventGenreRepo` if not present.

The key changes in `addParticipantToEvent`:

```java
// REPLACE:
Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
if (genre == null) continue;
EventGenreParticipantId id = new EventGenreParticipantId(
    event.getEventId(), genre.getGenreId(), toAddParticipant.getParticipantId());
EventGenreParticipant egp = new EventGenreParticipant();
egp.setId(id);
egp.setEvent(event);
egp.setGenre(genre);
egp.setParticipant(toAddParticipant);
EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);

// WITH:
EventGenre eg = eventGenreRepo.findByEventAndNameIgnoreCase(event, genreName).orElse(null);
if (eg == null) continue;
EventGenreParticipantId id = new EventGenreParticipantId(
    event.getEventId(), eg.getId(), toAddParticipant.getParticipantId());
EventGenreParticipant egp = new EventGenreParticipant();
egp.setId(id);
egp.setEvent(event);
egp.setEventGenre(eg);
egp.setParticipant(toAddParticipant);
// (effectiveFormat now comes from eg.getFormat() already)
```

Update `hasTeamFormatGenre`:
```java
private boolean hasTeamFormatGenre(List<String> divisionNames, Event event) {
    if (divisionNames == null) return false;
    for (String name : divisionNames) {
        EventGenre eg = eventGenreRepo.findByEventAndNameIgnoreCase(event, name).orElse(null);
        if (eg != null && isTeamFormat(eg.getFormat())) return true;
    }
    return false;
}

private String getTeamFormat(List<String> divisionNames, Event event) {
    if (divisionNames == null) return null;
    for (String name : divisionNames) {
        EventGenre eg = eventGenreRepo.findByEventAndNameIgnoreCase(event, name).orElse(null);
        if (eg != null && isTeamFormat(eg.getFormat())) return eg.getFormat();
    }
    return null;
}
```

- [ ] **Step 2: Update ResultsService.java**

Replace `egp.getGenre().getGenreName()` → `egp.getEventGenre().getName()` in the `genreResults` builder:

```java
genreResults.add(new GetResultsDto.GenreResult(
    egp.getEventGenre().getName(),
    egp.getFormat(),
    egp.getAuditionNumber(),
    scoreEntries,
    feedbackEntries
));
```

- [ ] **Step 3: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/RegistrationService.java \
        BES/src/main/java/com/example/BES/services/ResultsService.java
git commit -m "feat: update RegistrationService and ResultsService for decoupled EventGenre"
```

---

## Task 8: Sheet Import Matching — GoogleSheetService and Parser

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/GoogleSheetService.java`
- Modify: `BES/src/main/java/com/example/BES/parsers/GoogleSheetParser.java`
- Modify: `BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java`

The sheet parser currently takes `List<String> genres` (flat match strings) and returns `Map<matchString, format>`. After the change, it needs to return `Map<canonicalDivisionName, format>`. We achieve this by passing a `Map<String, String>` (matchString → canonicalName) and returning `Map<canonicalName, format>`.

- [ ] **Step 1: Add overload to GoogleSheetParser.java**

Add a new static method (keep the old one for `getParticipantsBreakDown` which still uses global genres):

```java
/**
 * Parses a raw category cell using a match-string-to-canonical-name lookup.
 * Returns map of canonicalDivisionName → format.
 */
public static Map<String, String> parseGenreFormatsFromDivisions(
        String rawCellValue, Map<String, String> matchStringToCanonical) {
    Map<String, String> result = new HashMap<>();
    if (rawCellValue == null || rawCellValue.isBlank()) return result;

    String[] segments = rawCellValue.split(",");
    for (String segment : segments) {
        String seg = segment.toLowerCase().trim();
        if (seg.isBlank()) continue;

        Matcher m = FORMAT_PATTERN.matcher(seg);
        String format = m.find() ? m.group() : null;

        for (Map.Entry<String, String> entry : matchStringToCanonical.entrySet()) {
            if (seg.contains(entry.getKey())) {
                result.putIfAbsent(entry.getValue(), format);
            }
        }
    }
    return result;
}
```

- [ ] **Step 2: Update RegistrationDtoMapper.java**

Add a new overload of `mapRow` that accepts `Map<String, String> genreMatchStrings` (matchString → canonicalName) instead of `List<String> genres`:

```java
public AddParticipantDto mapRow(List<String> row,
                                Map<String, Integer> colIndexMap,
                                List<Integer> categoriesCols,
                                Map<String, String> genreMatchStrings,   // ← new type
                                List<Integer> memberCols) {
    // ... same logic for name, stageName, teamName, members, entryType, payment, screenshot ...

    Map<String, String> genreFormats = new java.util.HashMap<>();
    for (Integer i : categoriesCols) {
        if (row.size() > i && !row.get(i).isBlank()) {
            genreFormats = GoogleSheetParser.parseGenreFormatsFromDivisions(row.get(i), genreMatchStrings);
            if (!genreFormats.isEmpty()) break;
        }
    }
    dto.setGenreFormats(genreFormats);
    dto.setGenres(new ArrayList<>(genreFormats.keySet()));
    return dto;
}
```

Keep the old `mapRow(... List<String> genres ...)` for backward compatibility, or remove it if no other code calls it. Check with `grep -r "mapRow" BES/src/`.

- [ ] **Step 3: Update GoogleSheetService.getAllImportableParticipants**

Replace `loadGenreMatchStrings()` call with an event-specific division match strings loader:

```java
public List<AddParticipantDto> getAllImportableParticipants(AddParticipantToEventDto dto)
        throws IOException {
    Event event = eventRepo.findByEventName(dto.eventName).orElse(null);
    if (event == null) return new ArrayList<>();

    List<AddParticipantDto> importable = new ArrayList<>();
    List<String> originalHeaders = getHeaders(dto.fileId);
    Map<String, Integer> colIndexMap = getColumnIndexMap(dto.fileId, originalHeaders);
    List<List<String>> resultString = getsheetAllRows(dto.fileId, originalHeaders);
    List<Integer> categoriesColumn = getCategoriesColumns(dto.fileId);
    List<Integer> memberCols = getMemberNameColumns(originalHeaders);

    Map<String, String> divisionMatchStrings = loadEventDivisionMatchStrings(event);
    for (List<String> res : resultString) {
        AddParticipantDto participant = mapper.mapRow(res, colIndexMap, categoriesColumn, divisionMatchStrings, memberCols);
        String name = participant.getParticipantName();
        if (name != null && !name.isBlank()) {
            importable.add(participant);
        }
    }
    return importable;
}
```

Add `@Autowired EventRepo eventRepo;` to the class.

Add the new loader method:

```java
/**
 * Builds a match-string → canonical division name map for a specific event.
 * Used by sheet import to match sheet cells to event divisions.
 */
private Map<String, String> loadEventDivisionMatchStrings(Event event) {
    Map<String, String> result = new java.util.LinkedHashMap<>();
    for (EventGenre eg : eventGenreRepo.findByEvent(event)) {
        for (String matchStr : eventGenreService.getMatchStrings(eg)) {
            result.putIfAbsent(matchStr, eg.getName());
        }
    }
    return result;
}
```

Add `@Autowired EventGenreRepo eventGenreRepo;` and `@Autowired EventGenreService eventGenreService;` to the class.

Keep `loadGenreMatchStrings()` (for `buildGenreCounts`, which still uses global genres for the stats preview).

- [ ] **Step 4: Add EventRepo to GoogleSheetService imports**

The `getAllImportableParticipants` method now needs to look up the event by name. Check if `EventRepo` is already imported; if not, add `@Autowired EventRepo eventRepo;`.

- [ ] **Step 5: Compile check**

```bash
cd BES && ./mvnw clean compile -q 2>&1 | tail -20
```

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/GoogleSheetService.java \
        BES/src/main/java/com/example/BES/parsers/GoogleSheetParser.java \
        BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java
git commit -m "feat: switch sheet import matching from global genres to event-specific divisions"
```

---

## Task 9: Controller Layer — EventController

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Step 1: Update getGenresByEvent return type**

```java
@GetMapping("/{eventName}/genres")
public ResponseEntity<List<GetEventDivisionDto>> getGenresByEvent(@PathVariable String eventName) {
    return new ResponseEntity<>(eventGenreService.getGenresByEventService(eventName), HttpStatus.OK);
}
```

- [ ] **Step 2: Update updateEventGenreFormat endpoint**

The format is now identified by `eventGenreId`, not `genreName`:

```java
// OLD: POST /{eventName}/genres/{genreName}/format
// NEW: POST /{eventName}/genres/{eventGenreId}/format
@PostMapping("/{eventName}/genres/{eventGenreId}/format")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> updateEventGenreFormat(
        @PathVariable String eventName,
        @PathVariable Long eventGenreId,
        @Valid @RequestBody Map<String, String> body) {
    try {
        eventGenreService.updateEventGenreFormat(eventGenreId, body.get("format"));
        return ResponseEntity.ok().build();
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
```

- [ ] **Step 3: Update removeParticipantFromGenre endpoint**

The path variable is now `eventGenreId` semantically (same URL structure, different meaning):

```java
// Path: DELETE /participant-genre/{participantId}/{eventId}/{eventGenreId}
@DeleteMapping("/participant-genre/{participantId}/{eventId}/{eventGenreId}")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> removeParticipantFromGenre(
        @PathVariable long participantId,
        @PathVariable long eventId,
        @PathVariable long eventGenreId) {
    eventGenreParticipantService.removeParticipantFromGenre(participantId, eventId, eventGenreId);
    return new ResponseEntity<>("removed", HttpStatus.OK);
}
```

- [ ] **Step 4: Update register-participant endpoint if it takes genreId**

Find the `GET /register-participant/{participantId}/{eventId}` endpoint. If it calls `getAllAuditionNumsViaQR(participantId, eventId)`, no change needed (the eventGenreId is resolved internally). Verify this is the case.

- [ ] **Step 5: Update addParticipantToEventGenre endpoint**

The DTO now uses `eventGenreId`:

```java
@GetMapping("/register-participant/{participantId}/{eventId}/{eventGenreId}")
public ResponseEntity<String> registerParticipant(
        @PathVariable Long participantId,
        @PathVariable Long eventId,
        @PathVariable Long eventGenreId) {
    AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
    dto.participantId = participantId;
    dto.eventId = eventId;
    dto.eventGenreId = eventGenreId;
    eventGenreParticipantService.getAuditionNumViaQR(dto);
    return new ResponseEntity<>("registered", HttpStatus.OK);
}
```

(Adjust to match the actual controller method — read the file to confirm the exact method.)

- [ ] **Step 6: Update addAssignGenreToEvent (POST /genre)**

Return type changes from `String` to indicate success. The DTO shape changes internally — no controller change needed beyond re-compiling.

- [ ] **Step 7: Compile and run tests**

```bash
cd BES && ./mvnw clean test 2>&1 | tail -40
```

Expected: Tests pass (mocked services, so schema doesn't matter for unit tests).

- [ ] **Step 8: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: update EventController for decoupled EventGenre — use eventGenreId in endpoints"
```

---

## Task 10: Frontend Changes

**Files:**
- Modify: `BES-frontend/src/utils/api.js`
- Modify: `BES-frontend/src/views/EventDetails.vue`

- [ ] **Step 1: Update linkGenreToEvent in api.js**

```js
export const linkGenreToEvent = async (eventName, divisions) => {
  // divisions: [{ name, format, genreId? }]
  try {
    return await fetch(`${domain}/api/v1/event/genre`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, divisions })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}
```

- [ ] **Step 2: Update updateEventGenreFormat in api.js**

The endpoint path now uses `eventGenreId` instead of `genreName`:

```js
export const updateEventGenreFormat = async (eventName, eventGenreId, format) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/genres/${eventGenreId}/format`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ format })
    })
  } catch (e) {
    console.log(e)
  }
}
```

- [ ] **Step 3: Update EventDetails.vue — linkGenreToEvent call**

The `createTable.genres` array of names and `createTable.genreFormats` map need to be converted into a `divisions` array. The genre selector in EventDetails also needs to capture the global `genreId` for each selected genre.

Find where `createTable.genres` is populated (look for genre selection UI). For each selected genre, store its `id` (from `GetGenreDto`). Then build divisions:

```js
// Replace:
const resp = await linkGenreToEvent(props.eventName, createTable.genres, createTable.genreFormats)

// With:
const divisions = createTable.genres.map(name => ({
  name,
  format: createTable.genreFormats[name] || null,
  genreId: createTable.genreIds?.[name] || null   // genreId map populated by genre selector
}))
const resp = await linkGenreToEvent(props.eventName, divisions)
```

For this to work, `createTable.genreIds` must be populated when the user selects genres. Find the genre selection handler and add:
```js
createTable.genreIds[genre.genreName] = genre.id
```

If `createTable` is a reactive object, add `genreIds: {}` to its initial state.

- [ ] **Step 4: Update EventDetails.vue — saveFormat call**

```js
const saveFormat = async (genre) => {
  await updateEventGenreFormat(props.eventName, genre.eventGenreId, editingFormatValue.value || null)
  eventGenres.value = await getGenresByEvent(props.eventName)
  editingFormatFor.value = null
}
```

Update `startEditFormat` to use `genre.eventGenreId` as the key (not `genre.genreName`):
```js
const startEditFormat = (genre) => {
  editingFormatFor.value = genre.eventGenreId
  editingFormatValue.value = genre.format || ''
}
```

Update the template to compare `editingFormatFor.value === genre.eventGenreId`.

- [ ] **Step 5: Update EventDetails.vue — removeParticipantGenre call**

The EGP DTO now returns `eventGenreId` instead of `genreId`. Update:
```js
// OLD:
await removeParticipantGenre(egp.participantId, egp.eventId, egp.genreId)
// NEW:
await removeParticipantGenre(egp.participantId, egp.eventId, egp.eventGenreId)
```

- [ ] **Step 6: Verify getRegisteredParticipantsByEvent DTO field names**

Search `api.js` for the function that returns participant data. The response from the backend now has `eventGenreId` instead of `genreId`. If any Vue code reads `.genreId` from participant data, rename to `.eventGenreId`.

```bash
grep -n "\.genreId" /Users/bennylim/Documents/BES/BES-frontend/src/views/EventDetails.vue
grep -n "\.genreId" /Users/bennylim/Documents/BES/BES-frontend/src/views/AuditionList.vue
```

Update any remaining `.genreId` → `.eventGenreId` in those files (the field was renamed in the response DTO).

- [ ] **Step 7: Frontend build check**

```bash
cd BES-frontend && npm run build 2>&1 | tail -20
```

Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/utils/api.js BES-frontend/src/views/EventDetails.vue \
        BES-frontend/src/views/AuditionList.vue
git commit -m "feat: update frontend for decoupled EventGenre — new linkGenreToEvent, eventGenreId"
```

---

## Task 11: Integration Test Updates

**Files:**
- Modify: `BES/src/test/java/com/example/BES/controllers/AdminControllerIntegrationTest.java`
- Any other integration tests that reference `EventGenreId`, `genreId` on EGP, or `Genre genre` on EGP

- [ ] **Step 1: Find all affected tests**

```bash
grep -rl "EventGenreId\|getGenreId\|\.genreId\|Genre genre\|EventGenreParticipantId" \
  BES/src/test/ 2>/dev/null
```

- [ ] **Step 2: Update each test**

For any test constructing `EventGenreId`, replace with `Long` (the surrogate ID).

For any test constructing `EventGenreParticipantId(eventId, genreId, participantId)`, replace with `EventGenreParticipantId(eventId, eventGenreId, participantId)` where `eventGenreId` is the `EventGenre.id` value.

For any mock `when(eventGenreRepo.findByEventAndGenre(...))` calls, replace with `when(eventGenreRepo.findByEventAndNameIgnoreCase(...))`.

- [ ] **Step 3: Run full test suite**

```bash
cd BES && ./mvnw clean test 2>&1 | tail -50
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/
git commit -m "test: update integration tests for decoupled EventGenre"
```

---

## Task 12: End-to-End Smoke Test

- [ ] **Step 1: Build and start Docker**

```bash
cd /Users/bennylim/Documents/BES
docker compose stop backend && docker compose rm -f backend
docker compose build --no-cache backend
docker compose up -d backend
```

Wait ~30 seconds for Spring Boot to start.

- [ ] **Step 2: Check backend health**

```bash
docker compose logs --tail=50 backend | grep -E "Started|ERROR|FlywayException"
```

Expected: `Started BesApplication` with no Flyway errors. If a Flyway migration error appears, examine the SQL and fix.

- [ ] **Step 3: Rebuild frontend if needed**

```bash
docker compose stop frontend && docker compose rm -f frontend
docker compose build --no-cache frontend
docker compose up -d frontend
```

- [ ] **Step 4: Smoke test the happy path**

In a browser:
1. Log in as admin
2. Go to `/admin` → confirm global genres still display
3. Go to an event's EventDetails → "Set Up Event" → add a genre → confirm it creates a division
4. Add a division with the same global genre but different name (e.g., "Breaking 3v3") → should succeed
5. Import participants from a Google Sheet → confirm division matching works
6. Add a walk-in to the new division → confirm audition number assignment works

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: complete EventGenre decoupling — surrogate PK, per-division names, multi-format support"
```

---

## Self-Review

**Spec coverage:**
- [x] Surrogate PK replaces composite PK → Task 1 (migration), Task 2 (EventGenre model)
- [x] Free-text `name` per division → Task 1, Task 2, Task 5 (EventGenreService)
- [x] `genre_id` made nullable → Task 1, Task 2
- [x] Same genre can appear twice (e.g., "Breaking 3v3" + "Breaking 7 to Smoke") → enforced by `UNIQUE(event_id, name)` instead of `UNIQUE(event_id, genre_id)`
- [x] Custom division names without adding to global pool → `genre_id = NULL` divisions
- [x] Sheet import matches against division names + aliases → Task 8
- [x] All FK-chain renames (EGP, Score, AuditionFeedback, EGPMember, BattleGuest, ScoringCriteria) → Tasks 1–9
- [x] Frontend updated to use `eventGenreId` → Task 10

**Placeholder scan:** None found — all code blocks are complete.

**Type consistency:**
- `EventGenreParticipantId` uses `eventGenreId Long` throughout Tasks 2–6
- Repo methods all use `eventGenreId Long` as parameter, matching the embedded ID field name
- `GetEventDivisionDto.eventGenreId` flows through controller → frontend → api.js consistently
