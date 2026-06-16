# Genre → Category Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename "genre" → "category" everywhere (DB tables, columns, Java classes, frontend variables, UI labels) and flatten the data model by dropping the global `genre` table and `event_genre_link` junction table, so organisers manage categories freely without admin pre-configuration.

**Architecture:** Bottom-up rename — DB migration first, then Java models/repos/DTOs/services/controllers, then frontend. Pure rename + drop of unused tables; no behavior changes. The `event_genre` table becomes `event_category`; child tables rename accordingly; `pickup_crew` FK migrates from global `genre.genre_id` to `event_category.id`.

**Tech Stack:** Spring Boot + JPA/Lombok (backend), Vue 3 (frontend), PostgreSQL + Flyway (DB), Maven + Vite.

---

## File Structure

### DB (new migration)
- **Create:** `BES/src/main/resources/db/migration/V41__rename_genre_to_category.sql`

### Backend — Delete
- `models/Genre.java`
- `respositories/GenreRepo.java`
- `services/GenreService.java`
- `dtos/GetGenreDto.java`
- `dtos/LinkGenresDto.java`
- `dtos/admin/AddGenreDto.java`
- `dtos/admin/DeleteGenreDto.java`
- `dtos/admin/UpdateGenreDto.java`

### Backend — Rename (file + class + all references)
| Old | New |
|-----|-----|
| `models/EventGenre.java` | `models/EventCategory.java` |
| `models/EventGenreBattleGuest.java` | `models/EventCategoryBattleGuest.java` |
| `models/EventGenreParticipant.java` | `models/EventCategoryParticipant.java` |
| `models/EventGenreParticipantId.java` | `models/EventCategoryParticipantId.java` |
| `models/EventGenreParticipantMember.java` | `models/EventCategoryParticipantMember.java` |
| `models/BattleGenreState.java` | `models/BattleCategoryState.java` |
| `models/BattleActiveGenre.java` | `models/BattleActiveCategory.java` |
| `respositories/EventGenreRepo.java` | `respositories/EventCategoryRepo.java` |
| `respositories/EventGenreParticpantRepo.java` | `respositories/EventCategoryParticipantRepo.java` |
| `respositories/EventGenreParticipantMemberRepo.java` | `respositories/EventCategoryParticipantMemberRepo.java` |
| `respositories/EventGenreBattleGuestRepo.java` | `respositories/EventCategoryBattleGuestRepo.java` |
| `respositories/BattleGenreStateRepository.java` | `respositories/BattleCategoryStateRepository.java` |
| `respositories/BattleActiveGenreRepository.java` | `respositories/BattleActiveCategoryRepository.java` |
| `services/EventGenreService.java` | `services/EventCategoryService.java` |
| `services/EventGenreParticpantService.java` | `services/EventCategoryParticipantService.java` |
| `dtos/AddGenreToEventDto.java` | `dtos/AddCategoryToEventDto.java` |
| `dtos/AddParticipantToEventGenreDto.java` | `dtos/AddParticipantToEventCategoryDto.java` |
| `dtos/GetEventGenreParticipantDto.java` | `dtos/GetEventCategoryParticipantDto.java` |
| `dtos/GetEventDivisionDto.java` | `dtos/GetEventCategoryDto.java` |
| `dtos/UpdateParticipantGenreDto.java` | `dtos/UpdateParticipantCategoryDto.java` |
| `dtos/battle/SetActiveGenreDto.java` | `dtos/battle/SetActiveCategoryDto.java` |

### Backend — Modify
- `models/Event.java` — remove `linkedGenres` field
- `models/PickupCrew.java` — `genre` (→ `Genre`) becomes `eventCategory` (→ `EventCategory`)
- `controllers/EventController.java` — rename endpoints, remove linked-genre + global genre endpoints
- `controllers/AdminController.java` — remove genre CRUD section
- `controllers/BattleController.java` — `genreName` → `categoryName` in all payloads
- `services/BattleService.java` — `activeGenreName` → `activeCategoryName`, all `genreName` params
- `services/PickupCrewService.java` — use `EventCategoryRepo` instead of `GenreRepo`

### Frontend — Modify
- `utils/api.js`
- `utils/adminApi.js` (remove genre CRUD exports)
- `utils/auth.js` (localStorage key rename)
- `views/EventDetails.vue`
- `views/AdminPage.vue`
- `views/BattleControl.vue`
- `views/AuditionList.vue`
- `views/Score.vue`
- `views/UpdateEventDetails.vue`
- `views/AuditionDisplay.vue`
- `views/AuditionNumber.vue`
- `views/AuditionAdjust.vue`
- `views/BattleJudge.vue`
- `views/BattleOverlay.vue`
- `views/BracketVisualization.vue`
- `views/CrewFormation.vue`
- `views/JudgeSessionView.vue`
- `views/MainMenu.vue`
- `views/Results.vue`
- `components/BracketEditor.vue`
- `components/CreateParticipantForm.vue`
- `components/EmceeRoundView.vue`
- `components/LiveMatchPanel.vue`
- `components/ScoringCriteriaModal.vue`
- `components/ScoringCriteriaPanel.vue`
- `components/UpdateScoreForm.vue`
- `utils/__tests__/adminApi.test.js`
- `utils/__tests__/api.test.js`
- `utils/__tests__/CreateParticipantForm.test.js`
- `utils/__tests__/liveMatchPanel.test.js`

---

## Implementation Tasks

### Task 1: DB Migration

**Files:**
- Create: `BES/src/main/resources/db/migration/V41__rename_genre_to_category.sql`

- [ ] **Step 1: Write the migration file**

```sql
-- V41: Rename genre → category throughout the schema
-- Drop event_genre_link (admin-assigned genre links, no longer needed)
-- Drop global genre table (organisers manage categories freely)
-- Migrate pickup_crew FK from genre.genre_id → event_category.id

-- 1. Rename the main table first (FK references auto-update in PostgreSQL)
ALTER TABLE event_genre RENAME TO event_category;

-- 2. Rename child tables
ALTER TABLE event_genre_participant       RENAME TO event_category_participant;
ALTER TABLE event_genre_participant_member RENAME TO event_category_participant_member;
ALTER TABLE event_genre_battle_guest      RENAME TO event_category_battle_guest;
ALTER TABLE event_genre_judge             RENAME TO event_category_judge;

-- 3. Rename the event_genre_id FK columns in child tables to event_category_id
ALTER TABLE event_category_participant        RENAME COLUMN event_genre_id TO event_category_id;
ALTER TABLE event_category_participant_member RENAME COLUMN event_genre_id TO event_category_id;
ALTER TABLE event_category_battle_guest       RENAME COLUMN event_genre_id TO event_category_id;
ALTER TABLE event_category_judge              RENAME COLUMN event_genre_id TO event_category_id;
ALTER TABLE scoring_criteria                  RENAME COLUMN event_genre_id TO event_category_id;
ALTER TABLE score                             RENAME COLUMN event_genre_id TO event_category_id;
ALTER TABLE audition_feedback                 RENAME COLUMN event_genre_id TO event_category_id;

-- 4. Migrate pickup_crew: change genre_id (global genre FK) → event_category_id (event_category FK)
--    Use the still-present event_category.genre_id column to do the backfill before dropping it.
ALTER TABLE pickup_crew RENAME COLUMN genre_id TO event_category_id;
UPDATE pickup_crew pc
SET event_category_id = ec.id
FROM event_category ec
WHERE pc.event_id = ec.event_id
  AND ec.genre_id = pc.event_category_id;

ALTER TABLE pickup_crew
    DROP CONSTRAINT pickup_crew_genre_id_fkey;
ALTER TABLE pickup_crew
    ADD CONSTRAINT pickup_crew_event_category_id_fkey
    FOREIGN KEY (event_category_id) REFERENCES event_category(id);

-- 5. Drop genre_id column from event_category (no longer needed after backfill)
ALTER TABLE event_category DROP CONSTRAINT fk5nearhompurfhv6qrt792v4pj;
ALTER TABLE event_category DROP COLUMN genre_id;

-- 6. Drop event_genre_link (admin-assigned genre links, superseded by organiser-managed categories)
DROP TABLE IF EXISTS event_genre_link;

-- 7. Drop global genre table (no remaining FKs after steps 4–5)
DROP TABLE IF EXISTS genre;

-- 8. Rename battle tables and their genre_name columns → category_name
ALTER TABLE battle_genre_state  RENAME TO battle_category_state;
ALTER TABLE battle_category_state RENAME COLUMN genre_name TO category_name;

ALTER TABLE battle_active_genre RENAME TO battle_active_category;
ALTER TABLE battle_active_category RENAME COLUMN genre_name TO category_name;
```

- [ ] **Step 2: Verify file exists**

Run from `BES/`: `ls src/main/resources/db/migration/V41__rename_genre_to_category.sql`
Expected: file listed

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V41__rename_genre_to_category.sql
git commit -m "db: rename genre→category tables and columns, drop genre + event_genre_link tables"
```

---

### Task 2: Backend Models — Rename and Update

**Files:**
- Rename `models/EventGenre.java` → `models/EventCategory.java`
- Rename `models/EventGenreBattleGuest.java` → `models/EventCategoryBattleGuest.java`
- Rename `models/EventGenreParticipant.java` → `models/EventCategoryParticipant.java`
- Rename `models/EventGenreParticipantId.java` → `models/EventCategoryParticipantId.java`
- Rename `models/EventGenreParticipantMember.java` → `models/EventCategoryParticipantMember.java`
- Rename `models/BattleGenreState.java` → `models/BattleCategoryState.java`
- Rename `models/BattleActiveGenre.java` → `models/BattleActiveCategory.java`
- Modify `models/Event.java`
- Modify `models/PickupCrew.java`
- Delete `models/Genre.java`

- [ ] **Step 1: Create `EventCategory.java`**

Create `BES/src/main/java/com/example/BES/models/EventCategory.java`:

```java
package com.example.BES.models;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "event_category")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "format")
    private String format;

    @Column(name = "round_label")
    private String roundLabel;

    @Column(name = "number_color")
    private String numberColor;

    @Column(name = "sheet_aliases")
    private String sheetAliases;

    @Column(name = "solo_allowed", nullable = false)
    private boolean soloAllowed = true;

    @ToString.Exclude
    @OneToMany(mappedBy = "eventCategory")
    private List<EventCategoryParticipant> participants;

    @ManyToMany
    @JoinTable(
        name = "event_category_judge",
        joinColumns = @JoinColumn(name = "event_category_id"),
        inverseJoinColumns = @JoinColumn(name = "judge_id")
    )
    private List<Judge> judges = new ArrayList<>();
}
```

Delete `models/EventGenre.java`.

- [ ] **Step 2: Create `EventCategoryParticipantId.java`**

Create `BES/src/main/java/com/example/BES/models/EventCategoryParticipantId.java`:

Read the old `EventGenreParticipantId.java` first to copy the structure, then create:

```java
package com.example.BES.models;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventCategoryParticipantId implements Serializable {
    private Long eventId;
    private Long eventCategoryId;
    private Long participantId;
}
```

Delete `models/EventGenreParticipantId.java`.

- [ ] **Step 3: Create `EventCategoryParticipant.java`**

Read `models/EventGenreParticipant.java`, then create `models/EventCategoryParticipant.java` replacing:
- Class name: `EventGenreParticipant` → `EventCategoryParticipant`
- `@Table(name = "event_genre_participant")` → `@Table(name = "event_category_participant")`
- `@EmbeddedId EventGenreParticipantId id` → `@EmbeddedId EventCategoryParticipantId id`
- `@ManyToOne EventGenre eventGenre` → `@ManyToOne EventCategory eventCategory` (column: `event_category_id`)
- All remaining field/method references from genre → category

Delete `models/EventGenreParticipant.java`.

- [ ] **Step 4: Create `EventCategoryParticipantMember.java`**

Read `models/EventGenreParticipantMember.java`, then create `models/EventCategoryParticipantMember.java` replacing:
- Class name
- `@Table(name = "event_genre_participant_member")` → `@Table(name = "event_category_participant_member")`
- FK column `event_genre_id` → `event_category_id`
- Reference to `EventCategoryParticipant` composite key

Delete `models/EventGenreParticipantMember.java`.

- [ ] **Step 5: Create `EventCategoryBattleGuest.java`**

Read `models/EventGenreBattleGuest.java`, then create `models/EventCategoryBattleGuest.java` replacing:
- Class name
- `@Table(name = "event_genre_battle_guest")` → `@Table(name = "event_category_battle_guest")`
- `@ManyToOne EventGenre eventGenre` → `@ManyToOne EventCategory eventCategory` (column: `event_category_id`)

Delete `models/EventGenreBattleGuest.java`.

- [ ] **Step 6: Create `BattleCategoryState.java`**

Read `models/BattleGenreState.java`, then create `models/BattleCategoryState.java`:

```java
package com.example.BES.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "battle_category_state", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_name", "category_name"})
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleCategoryState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "category_name")
    private String categoryName;

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

    @Column(name = "champion")
    private String champion;

    @Column(name = "smoke_list_json", columnDefinition = "TEXT")
    private String smokeListJson;

    @Column(name = "resolved_participants_json", columnDefinition = "TEXT")
    private String resolvedParticipantsJson;

    @Column(name = "format_timer_json", columnDefinition = "TEXT")
    private String formatTimerJson;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

Delete `models/BattleGenreState.java`.

- [ ] **Step 7: Create `BattleActiveCategory.java`**

Create `models/BattleActiveCategory.java`:

```java
package com.example.BES.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "battle_active_category")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BattleActiveCategory {
    @Id
    private Integer id;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "category_name")
    private String categoryName;
}
```

Delete `models/BattleActiveGenre.java`.

- [ ] **Step 8: Update `Event.java` — remove linkedGenres**

Read `models/Event.java`. Remove the `linkedGenres` field and its `@ManyToMany` / `@JoinTable` annotation (the `event_genre_link` table is being dropped). Remove any import of `Genre`.

- [ ] **Step 9: Update `PickupCrew.java`**

Read `models/PickupCrew.java`. Replace the `genre` field:

```java
// REMOVE this:
@ManyToOne
@JoinColumn(name = "genre_id", nullable = false)
private Genre genre;

// REPLACE WITH:
@ManyToOne
@JoinColumn(name = "event_category_id", nullable = false)
private EventCategory eventCategory;
```

Remove `import com.example.BES.models.Genre;`. Add `import com.example.BES.models.EventCategory;`.

- [ ] **Step 10: Delete `Genre.java`**

```bash
rm BES/src/main/java/com/example/BES/models/Genre.java
```

- [ ] **Step 11: Verify compilation**

Run from `BES/`: `mvn clean compile -DskipTests 2>&1 | tail -20`
Expected: BUILD SUCCESS (will fail until repos/services/controllers are updated — fix compilation errors in subsequent tasks)

- [ ] **Step 12: Commit models**

```bash
git add BES/src/main/java/com/example/BES/models/
git commit -m "refactor: rename EventGenre→EventCategory and related models, drop Genre model"
```

---

### Task 3: Backend Repositories

**Files:** Create new repo files, delete old ones.

- [ ] **Step 1: Create `EventCategoryRepo.java`**

Create `respositories/EventCategoryRepo.java`:

```java
package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;

@Repository
public interface EventCategoryRepo extends JpaRepository<EventCategory, Long> {
    Optional<EventCategory> findByEventAndName(Event event, String name);
    List<EventCategory> findByEvent(Event event);
}
```

Delete `respositories/EventGenreRepo.java`.

- [ ] **Step 2: Create `EventCategoryParticipantRepo.java`**

Read `respositories/EventGenreParticpantRepo.java`. Create `respositories/EventCategoryParticipantRepo.java` with the same methods but importing `EventCategoryParticipant` and `EventCategoryParticipantId` instead.

Delete `respositories/EventGenreParticpantRepo.java`.

- [ ] **Step 3: Create `EventCategoryParticipantMemberRepo.java`**

Read `respositories/EventGenreParticipantMemberRepo.java`. Create `respositories/EventCategoryParticipantMemberRepo.java` with the same methods but importing the renamed models.

Delete `respositories/EventGenreParticipantMemberRepo.java`.

- [ ] **Step 4: Create `EventCategoryBattleGuestRepo.java`**

Read `respositories/EventGenreBattleGuestRepo.java`. Create `respositories/EventCategoryBattleGuestRepo.java` with the same methods but importing `EventCategoryBattleGuest`.

Delete `respositories/EventGenreBattleGuestRepo.java`.

- [ ] **Step 5: Create `BattleCategoryStateRepository.java`**

Read `respositories/BattleGenreStateRepository.java`. Create `respositories/BattleCategoryStateRepository.java`:
- Import `BattleCategoryState` instead of `BattleGenreState`
- Rename method `findByEventNameAndGenreName` → `findByEventNameAndCategoryName`

Delete `respositories/BattleGenreStateRepository.java`.

- [ ] **Step 6: Create `BattleActiveCategoryRepository.java`**

Read `respositories/BattleActiveGenreRepository.java`. Create `respositories/BattleActiveCategoryRepository.java` importing `BattleActiveCategory`.

Delete `respositories/BattleActiveGenreRepository.java`.

- [ ] **Step 7: Update `PickupCrewRepo.java`**

Read `respositories/PickupCrewRepo.java`. Replace:
- `findByEventAndGenre(Event event, Genre genre)` → `findByEventAndEventCategory(Event event, EventCategory eventCategory)`
- `countMemberInEventGenre(...)` → `countMemberInEventCategory(...)`

Update imports accordingly.

- [ ] **Step 8: Delete `GenreRepo.java`**

```bash
rm BES/src/main/java/com/example/BES/respositories/GenreRepo.java
```

- [ ] **Step 9: Commit repositories**

```bash
git add BES/src/main/java/com/example/BES/respositories/
git commit -m "refactor: rename genre repositories to category repositories"
```

---

### Task 4: Backend DTOs

- [ ] **Step 1: Create `GetEventCategoryDto.java`**

Create `dtos/GetEventCategoryDto.java`:

```java
package com.example.BES.dtos;

public class GetEventCategoryDto {
    public Long id;
    public String name;
    public String format;
    public String roundLabel;
    public String numberColor;
    public String sheetAliases;
    public boolean soloAllowed = true;
    public long participantCount;
}
```

Delete `dtos/GetEventDivisionDto.java`.

- [ ] **Step 2: Create `AddCategoryToEventDto.java`**

Create `dtos/AddCategoryToEventDto.java`:

```java
package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AddCategoryToEventDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @NotEmpty
    public List<Category> categories;

    public static class Category {
        @NotBlank @Size(max = 255)
        public String name;
        public String format;
    }
}
```

Delete `dtos/AddGenreToEventDto.java`.

- [ ] **Step 3: Create `SetActiveCategoryDto.java`**

Create `dtos/battle/SetActiveCategoryDto.java`:

```java
package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;

public class SetActiveCategoryDto {
    @NotBlank
    private String eventName;

    @NotBlank
    private String categoryName;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
}
```

Delete `dtos/battle/SetActiveGenreDto.java`.

- [ ] **Step 4: Rename participant-related DTOs**

Read `dtos/AddParticipantToEventGenreDto.java`. Create `dtos/AddParticipantToEventCategoryDto.java` replacing `genre` → `category` in field names and class name. Delete the old file.

Read `dtos/GetEventGenreParticipantDto.java`. Create `dtos/GetEventCategoryParticipantDto.java` replacing `genre` → `category` in field names. Delete the old file.

Read `dtos/UpdateParticipantGenreDto.java`. Create `dtos/UpdateParticipantCategoryDto.java` replacing `genre` → `category` in field names. Delete the old file.

- [ ] **Step 5: Update `CreatePickupCrewDto.java`**

Read `dtos/CreatePickupCrewDto.java`. Rename the field:
- `public String genreName;` → `public String categoryName;`

- [ ] **Step 6: Delete unused DTOs**

```bash
rm BES/src/main/java/com/example/BES/dtos/GetGenreDto.java
rm BES/src/main/java/com/example/BES/dtos/LinkGenresDto.java
rm BES/src/main/java/com/example/BES/dtos/admin/AddGenreDto.java
rm BES/src/main/java/com/example/BES/dtos/admin/DeleteGenreDto.java
rm BES/src/main/java/com/example/BES/dtos/admin/UpdateGenreDto.java
```

- [ ] **Step 7: Commit DTOs**

```bash
git add BES/src/main/java/com/example/BES/dtos/
git commit -m "refactor: rename genre DTOs to category DTOs, remove global genre DTOs"
```

---

### Task 5: Backend Services

- [ ] **Step 1: Create `EventCategoryService.java`**

Read `services/EventGenreService.java`. Create `services/EventCategoryService.java` with these changes:
- Class: `EventGenreService` → `EventCategoryService`
- Inject `EventCategoryRepo eventCategoryRepo` (drop `GenreRepo`)
- Method: `getGenresByEventService` → `getCategoriesByEventService`, return `List<GetEventCategoryDto>`
- In the body, remove `dto.genreId = ...` (field no longer exists), populate `GetEventCategoryDto` fields
- Method: `addGenreToEventService(AddGenreToEventDto)` → `addCategoryToEventService(AddCategoryToEventDto)` — remove all `genre` lookup, iterate `dto.categories`, create `EventCategory` directly (no `Genre` FK)
- Remove `linkGenresToEvent`, `getLinkedGenresService`, `unlinkGenreFromEvent`, `ensureGenreLinked` — these methods become dead code with no callers
- All other methods (`updateEventGenreFormat`, `renameDivision`, `updateAliases`, `updateSoloAllowed`, `updateRoundLabel`, `updateNumberColor`, `deleteDivision`) stay the same logic, just use `EventCategory` and `EventCategoryRepo`

Delete `services/EventGenreService.java`.

- [ ] **Step 2: Delete `GenreService.java`**

```bash
rm BES/src/main/java/com/example/BES/services/GenreService.java
```

- [ ] **Step 3: Create `EventCategoryParticipantService.java`**

Read `services/EventGenreParticpantService.java`. Create `services/EventCategoryParticipantService.java`:
- Replace all `EventGenreParticipant` → `EventCategoryParticipant`
- Replace `EventCategoryParticipantId` (composite key)
- Replace `EventCategoryParticipantRepo`
- Replace `eventGenreId` field names → `eventCategoryId`
- Rename DTO imports to the new category DTOs

Delete `services/EventGenreParticpantService.java`.

- [ ] **Step 4: Update `BattleService.java` — rename genre→category**

Read `BES/src/main/java/com/example/BES/services/BattleService.java`.

Apply these replacements throughout the file:
- Import `BattleCategoryState` (drop `BattleGenreState`)
- Import `BattleActiveCategory` (drop `BattleActiveGenre`)
- Import `BattleCategoryStateRepository` (drop `BattleGenreStateRepository`)
- Import `BattleActiveCategoryRepository` (drop `BattleActiveGenreRepository`)
- Import `EventCategoryRepo` (drop `EventGenreRepo`)
- Import `SetActiveCategoryDto` (drop `SetActiveGenreDto`)
- Field: `BattleGenreStateRepository battleGenreStateRepository` → `BattleCategoryStateRepository battleCategoryStateRepository`
- Field: `BattleActiveGenreRepository battleActiveGenreRepository` → `BattleActiveCategoryRepository battleActiveCategoryRepository`
- Field: `EventGenreRepo eventGenreRepo` → `EventCategoryRepo eventCategoryRepo`
- In internal state class `S`: `String activeGenreName` → `String activeCategoryName`
- All uses of `s.activeGenreName` → `s.activeCategoryName`
- All `battleGenreStateRepository.*` → `battleCategoryStateRepository.*`
- All `battleActiveGenreRepository.*` → `battleActiveCategoryRepository.*`
- All `getGenreName()` / `setGenreName()` on state objects → `getCategoryName()` / `setCategoryName()`
- All `findByEventNameAndGenreName(...)` → `findByEventNameAndCategoryName(...)`
- All `new BattleGenreState()` → `new BattleCategoryState()`
- All `new BattleActiveGenre(...)` → `new BattleActiveCategory(...)`
- JSON response keys: `"genre"` → `"category"`, `"genreName"` → `"categoryName"` (all Map.of entries)
- Method `switchActiveGenreService(SetActiveGenreDto)` → `switchActiveCategoryService(SetActiveCategoryDto)`
- Method `getGenreStateFromDbService(eventName, genreName)` → `getCategoryStateFromDbService(eventName, categoryName)`
- All `eventGenreRepo.findByEventAndName(...)` → `eventCategoryRepo.findByEventAndName(...)`

- [ ] **Step 5: Update `PickupCrewService.java`**

Read `services/PickupCrewService.java`.

Replace:
- Import `EventCategory` (drop `Genre`)
- Import `EventCategoryRepo` (drop `GenreRepo`)
- Import `EventCategoryParticipant`, `EventCategoryParticipantId` (drop old versions)
- Import `EventCategoryRepo` (drop `EventGenreRepo`)
- `@Autowired GenreRepo genreRepo` → `@Autowired EventCategoryRepo eventCategoryRepo`
- `@Autowired EventGenreRepo eventGenreRepo` — keep (now named `eventCategoryRepo`, see above)
- In `getCrewsForEventGenre(String eventName, String genreName)` → `getCrewsForEventCategory(String eventName, String categoryName)`:
  - Remove `Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);`
  - Replace with: `EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, categoryName).orElse(null);`
  - Replace `crewRepo.findByEventAndGenre(event, genre)` → `crewRepo.findByEventAndEventCategory(event, eventCategory)`
  - Replace `new EventGenreParticipantId(...)` → `new EventCategoryParticipantId(...)`
- In `createCrew(CreatePickupCrewDto dto)`:
  - Replace `dto.genreName` → `dto.categoryName`
  - Remove `Genre genre = genreRepo.findByGenreName(...)` lookup
  - Replace with: `EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, dto.categoryName).orElse(null); if (event == null || eventCategory == null) throw new RuntimeException("Event or category not found");`
  - Replace `crew.setGenre(genre)` → `crew.setEventCategory(eventCategory)`
  - Replace `crewRepo.countMemberInEventGenre(event, genre, ...)` → `crewRepo.countMemberInEventCategory(event, eventCategory, ...)`

- [ ] **Step 6: Compile check**

Run from `BES/`: `mvn clean compile -DskipTests 2>&1 | grep -E "ERROR|BUILD"`
Expected: `BUILD SUCCESS`

Fix any remaining compile errors (import mismatches, field name mismatches).

- [ ] **Step 7: Commit services**

```bash
git add BES/src/main/java/com/example/BES/services/
git commit -m "refactor: rename genre services to category services, update BattleService and PickupCrewService"
```

---

### Task 6: Backend Controllers

- [ ] **Step 1: Update `EventController.java`**

Read `BES/src/main/java/com/example/BES/controllers/EventController.java`.

Changes:
- Replace `EventGenreService eventGenreService` → `EventCategoryService eventCategoryService`
- Replace `EventGenreParticpantService eventGenreParticipantService` → `EventCategoryParticipantService eventCategoryParticipantService`
- Remove `GenreService genreService` and its `@Autowired` — this entire service is being deleted
- Remove the `getAllGenres` endpoint (`@GetMapping("/genre")`)
- Remove the `linkGenresToEvent` endpoint (`@PostMapping("/{eventName}/linked-genres")`)
- Remove the `getLinkedGenres` endpoint (`@GetMapping("/{eventName}/linked-genres")`)
- Remove the `unlinkGenreFromEvent` endpoint (`@DeleteMapping("/{eventName}/linked-genres/{genreId}")`)
- Rename `getGenresByEvent` → `getCategoriesByEvent`, path `/{eventName}/genres` → `/{eventName}/categories`
- Rename `assignGenreToEvent` → `addCategoryToEvent`, path `/genre` → `/category`, DTO `AddGenreToEventDto` → `AddCategoryToEventDto`
- Rename format endpoint path: `/{eventName}/genres/{eventGenreId}/format` → `/{eventName}/categories/{categoryId}/format`
- Rename round-label endpoint path similarly
- Rename number-color endpoint path similarly
- All DTO imports updated to category versions
- Any `genreName` parameter remaining → `categoryName`

- [ ] **Step 2: Update `AdminController.java`**

Read `BES/src/main/java/com/example/BES/controllers/AdminController.java`.

Remove:
- `@Autowired GenreService genreService`
- The `createGenre` endpoint (`@PostMapping("/genre")`)
- The `updateGenre` endpoint (`@PostMapping("/update-genre")`)
- The `deleteGenre` endpoint (`@DeleteMapping("/genre")`)
- All genre-related imports (`AddGenreDto`, `DeleteGenreDto`, `UpdateGenreDto`, `GetGenreDto`, `Genre`, `GenreService`)

- [ ] **Step 3: Update `BattleController.java`**

Read `BES/src/main/java/com/example/BES/controllers/BattleController.java`.

Changes:
- Import `SetActiveCategoryDto` (drop `SetActiveGenreDto`)
- Method `setActiveGenre(@Valid @RequestBody SetActiveGenreDto dto)` → `setActiveCategory(@Valid @RequestBody SetActiveCategoryDto dto)`
- Call `battleService.switchActiveCategoryService(dto)` (drop `switchActiveGenreService`)
- Any other `genreName` parameter references → `categoryName`
- Endpoint for `genre-state` → `category-state`
- Method call `battleService.getGenreStateFromDbService(...)` → `battleService.getCategoryStateFromDbService(...)`

- [ ] **Step 4: Update `ResultsController.java` and other controllers**

Run: `grep -rn "genre\|Genre" BES/src/main/java/com/example/BES/controllers/ | grep -v "BattleController\|EventController\|AdminController"`

Fix any remaining genre references in other controllers.

- [ ] **Step 5: Compile check**

Run from `BES/`: `mvn clean compile -DskipTests 2>&1 | grep -E "ERROR|BUILD"`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit controllers**

```bash
git add BES/src/main/java/com/example/BES/controllers/
git commit -m "refactor: rename genre endpoints to category endpoints, remove global genre admin endpoints"
```

---

### Task 7: Backend Full Test Run

- [ ] **Step 1: Run all backend tests**

Run from `BES/`: `mvn clean test 2>&1 | tail -30`
Expected: Tests pass or only pre-existing failures unrelated to this rename.

- [ ] **Step 2: Fix any test compilation errors**

Run: `grep -rn "genre\|Genre\|EventGenre\|GenreRepo\|GenreService" BES/src/test/ 2>/dev/null`
Update any test files that import or reference old genre classes.

- [ ] **Step 3: Commit test fixes**

```bash
git add BES/src/test/
git commit -m "refactor: update backend tests to use category naming"
```

---

### Task 8: Frontend — `api.js`

**File:** `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Rename genre API functions**

Read `BES-frontend/src/utils/api.js`. Apply these renames:

| Old function | New function | URL change |
|---|---|---|
| `fetchAllGenres` | DELETE (global genres gone) | `GET /event/genre` dropped |
| `linkGenreToEvent` | `addCategoryToEvent` | `POST /event/category` |
| `linkGenresToEvent` | DELETE | `POST /{name}/linked-genres` dropped |
| `getLinkedGenres` | DELETE | `GET /{name}/linked-genres` dropped |
| `unlinkGenreFromEvent` | DELETE | `DELETE /{name}/linked-genres/{id}` dropped |
| `getGenresByEvent` | `getCategoriesByEvent` | `/genres` → `/categories` |
| `setActiveGenre` | `setActiveCategory` | body: `genreName` → `categoryName` |
| `getGenreStateFromDb` | `getCategoryStateFromDb` | `?genre=` → `?category=` |
| `removeParticipantGenre` | `removeParticipantCategory` | path unchanged, param rename |
| `addGenreToParticipant` | `addCategoryToParticipant` | body: `genreName` → `categoryName` |
| `submitParticipantScore` | — | rename param `genreName` → `categoryName` in body |
| `resetJudgeScores` | — | rename param `genreName` → `categoryName` |
| `resetJudgeFeedback` | — | rename param `genreName` → `categoryName` |
| `revealChampion` | — | body: `genreName` → `categoryName` |
| `submitAuditionFeedback` | — | body: `genreName` → `categoryName` |
| `getAuditionFeedback` | — | param: `genreName` → `categoryName` |
| `getParticipantFeedback` | — | param: `genreName` → `categoryName` |
| `getScoringCriteria` | — | param: `genre=` → `category=` |
| `getScoringCriteriaStrict` | — | param: `genre=` → `category=` |
| `deleteAllCriteriaForGenre` | `deleteAllCriteriaForCategory` | param: `genre=` → `category=` |
| `getPickupCrews` | — | path unchanged, param rename |
| `createPickupCrew` | — | body: `genreName` → `categoryName` |
| `updateEventGenreFormat` | `updateEventCategoryFormat` | path: `/genres/{id}/format` → `/categories/{id}/format` |
| `addWalkinToSystem` | — | body: `genre` → `category` |

Also rename the path segment variable names in request bodies: any `genreName:` → `categoryName:`.

URL path segment `/genres/` → `/categories/` where it appears.

- [ ] **Step 2: Verify no remaining genre references**

Run: `grep -n "genre\|Genre" BES-frontend/src/utils/api.js`
Expected: zero matches (or only in comments explaining the rename)

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "refactor: rename genre API functions and request params to category"
```

---

### Task 9: Frontend — `adminApi.js` and `auth.js`

- [ ] **Step 1: Remove genre functions from `adminApi.js`**

Read `BES-frontend/src/utils/adminApi.js`.

Delete these exports entirely:
- `addGenre`
- `deleteGenre`
- `updateGenre`

These called `POST /admin/genre`, `DELETE /admin/genre`, `POST /admin/update-genre` which are all being removed from the backend.

- [ ] **Step 2: Update `auth.js`**

Read `BES-frontend/src/utils/auth.js`.

Replace: `localStorage.removeItem('selectedGenre')` → `localStorage.removeItem('selectedCategory')`

Check for any other `genre` refs and rename them.

- [ ] **Step 3: Update `dropdown.js`**

Read `BES-frontend/src/utils/dropdown.js`.

Apply any `genre` → `category` renames in state management if present.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/adminApi.js BES-frontend/src/utils/auth.js BES-frontend/src/utils/dropdown.js
git commit -m "refactor: remove genre admin functions from adminApi, rename genre refs in auth/dropdown"
```

---

### Task 10: Frontend — `EventDetails.vue`

**File:** `BES-frontend/src/views/EventDetails.vue` (~296 genre refs — the largest single-file change)

- [ ] **Step 1: Update imports and API calls**

Read `BES-frontend/src/views/EventDetails.vue`.

Replace all API function calls:
- `fetchAllGenres()` → DELETE this call and any state that stored global genres for the picker
- `linkGenreToEvent(...)` → `addCategoryToEvent(...)`
- `getLinkedGenres(...)` → DELETE (no more linked genres concept)
- `unlinkGenreFromEvent(...)` → DELETE
- `linkGenresToEvent(...)` → DELETE
- `getGenresByEvent(...)` → `getCategoriesByEvent(...)`
- `updateEventGenreFormat(...)` → `updateEventCategoryFormat(...)`

- [ ] **Step 2: Rename reactive state variables**

Apply renames for all `ref`/`reactive` variables:
- `genres` → `categories`
- `linkedGenres` → DELETE
- `selectedGenre` → `selectedCategory`
- `genreList` → `categoryList`
- Any other `*genre*` variable → `*category*`

- [ ] **Step 3: Remove linked-genre UI section**

Remove the "Linked Genres" accordion/section from the template — this was the admin-selected genre grouping. The "FROM YOUR SHEET" pill section and the flat category list remain.

- [ ] **Step 4: Update template labels**

Replace in template:
- UI text "Genre" / "Genres" → "Category" / "Categories"
- "GENRES & CATEGORIES" section header → "CATEGORIES"
- Tooltip/description text referencing genres → categories

- [ ] **Step 5: Update DTO field references**

Replace `eventGenreId` → `id` (the field rename in `GetEventCategoryDto`). If the code accesses `category.eventGenreId`, change it to `category.id`.

- [ ] **Step 6: Verify no remaining genre references**

Run: `grep -n "genre\|Genre" BES-frontend/src/views/EventDetails.vue`
Expected: zero matches

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "refactor: rename genre→category throughout EventDetails.vue"
```

---

### Task 11: Frontend — `BattleControl.vue` and battle views

**Files:** `BattleControl.vue`, `BattleOverlay.vue`, `BracketVisualization.vue`, `BattleJudge.vue`

- [ ] **Step 1: Update `BattleControl.vue`** (~218 genre refs)

Read `BES-frontend/src/views/BattleControl.vue`.

Replace:
- All API calls: `setActiveGenre(...)` → `setActiveCategory(...)`, `getGenreStateFromDb(...)` → `getCategoryStateFromDb(...)`
- All reactive variables: `activeGenreName` → `activeCategoryName`, `selectedGenre` → `selectedCategory`, `genreName` → `categoryName`
- All object property accesses: `state.genre` → `state.category`, `state.genreName` → `state.categoryName`
- WebSocket payload reads: `.genre` → `.category`, `.genreName` → `.categoryName`
- Template labels: "Genre" → "Category", "Genres" → "Categories"

- [ ] **Step 2: Update `LiveMatchPanel.vue`**

Read `BES-frontend/src/components/LiveMatchPanel.vue`.

Apply the same pattern: `genreName` → `categoryName` in props, emits, and template.

- [ ] **Step 3: Update `BattleOverlay.vue`**

Read `BES-frontend/src/views/BattleOverlay.vue`.

Replace WebSocket state reads: `state.genre` → `state.category`, `state.genreName` → `state.categoryName`. Update any template display of genre name.

- [ ] **Step 4: Update `BracketVisualization.vue`**

Read `BES-frontend/src/views/BracketVisualization.vue`.

Apply `genreName` → `categoryName` in state reads and display.

- [ ] **Step 5: Update `BattleJudge.vue`**

Read `BES-frontend/src/views/BattleJudge.vue`.

Apply same pattern.

- [ ] **Step 6: Update `BracketEditor.vue`**

Read `BES-frontend/src/components/BracketEditor.vue`.

Apply `genre` → `category` renames in props/emits.

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue BES-frontend/src/views/BattleOverlay.vue BES-frontend/src/views/BracketVisualization.vue BES-frontend/src/views/BattleJudge.vue BES-frontend/src/components/LiveMatchPanel.vue BES-frontend/src/components/BracketEditor.vue
git commit -m "refactor: rename genre→category in battle views and components"
```

---

### Task 12: Frontend — Remaining Views and Components

- [ ] **Step 1: Update `AdminPage.vue`** (~41 genre refs)

Read `BES-frontend/src/views/AdminPage.vue`.

Remove the global "Genres" management section entirely (the table that listed genres with add/edit/delete buttons, and called `addGenre`, `updateGenre`, `deleteGenre` from adminApi). These operations no longer exist.

Rename any remaining `genre` variable/label to `category`.

- [ ] **Step 2: Update `AuditionList.vue`**

Read `BES-frontend/src/views/AuditionList.vue`.

Apply `genreName` → `categoryName` in API calls, reactive state, and template labels.

- [ ] **Step 3: Update `Score.vue`**

Read `BES-frontend/src/views/Score.vue`.

Apply `genreName` → `categoryName` in API calls and state.

- [ ] **Step 4: Update `UpdateEventDetails.vue`**

Read `BES-frontend/src/views/UpdateEventDetails.vue`.

Apply `genreName` → `categoryName` in API calls, state, and labels.

- [ ] **Step 5: Update `CrewFormation.vue`**

Read `BES-frontend/src/views/CrewFormation.vue`.

Apply:
- `selectedGenre` → `selectedCategory`
- `uniqueGenres` → `uniqueCategories`
- `genreName` → `categoryName` in API calls
- Template label "Genre" → "Category"

- [ ] **Step 6: Update `AuditionDisplay.vue`**

Read and apply `genre` → `category` renames.

- [ ] **Step 7: Update `AuditionNumber.vue`**

Read and apply `genre` → `category` renames.

- [ ] **Step 8: Update `AuditionAdjust.vue`**

Read and apply `genre` → `category` renames.

- [ ] **Step 9: Update remaining components**

Apply `genre` → `category` renames in:
- `components/CreateParticipantForm.vue`
- `components/EmceeRoundView.vue`
- `components/ScoringCriteriaModal.vue`
- `components/ScoringCriteriaPanel.vue`
- `components/UpdateScoreForm.vue`

For each: read the file, grep for genre refs, apply renames in JS logic and template labels.

- [ ] **Step 10: Update remaining views**

Apply `genre` → `category` renames in:
- `views/JudgeSessionView.vue`
- `views/MainMenu.vue`
- `views/Results.vue`

- [ ] **Step 11: Commit**

```bash
git add BES-frontend/src/views/ BES-frontend/src/components/
git commit -m "refactor: rename genre→category in remaining views and components"
```

---

### Task 13: Frontend Tests

- [ ] **Step 1: Update test files**

Read each test file that references genre and apply renames:
- `utils/__tests__/adminApi.test.js` — remove/update tests for `addGenre`, `deleteGenre`, `updateGenre`
- `utils/__tests__/api.test.js` — rename genre API function tests
- `utils/__tests__/CreateParticipantForm.test.js` — rename genre prop/event references
- `utils/__tests__/liveMatchPanel.test.js` — rename genreName → categoryName in test payloads

- [ ] **Step 2: Run frontend tests**

Run from `BES-frontend/`: `npm test 2>&1 | tail -30`
Expected: All tests pass or only pre-existing failures.

- [ ] **Step 3: Build check**

Run from `BES-frontend/`: `npm run build 2>&1 | tail -20`
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/__tests__/
git commit -m "refactor: update frontend tests to use category naming"
```

---

### Task 14: Full Validation

- [ ] **Step 1: Verify no remaining genre references in source**

Run: `grep -rn "genre\|Genre" BES/src/main/java/ | grep -v "\.class" | grep -v "BattleGenreState\|BattleActiveGenre\|EventGenre" | wc -l`

Expected: 0 (if any appear, fix them)

Run: `grep -rn "genre\|Genre" BES-frontend/src/ | grep -v "__tests__" | wc -l`

Expected: 0 (if any appear, fix them)

- [ ] **Step 2: Backend full test run**

Run from `BES/`: `mvn clean test 2>&1 | tail -30`
Expected: All tests pass.

- [ ] **Step 3: Build backend JAR**

Run from `BES/`: `mvn clean package -DskipTests 2>&1 | tail -10`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Start Docker and verify migration runs**

Run from root: `docker-compose down && docker-compose up --build --no-cache`

Wait for startup (~30 seconds), then check:
```bash
docker-compose logs backend 2>&1 | grep -i "flyway\|error\|V41"
```
Expected: `Successfully applied 1 migration to schema` for V41, no errors.

- [ ] **Step 5: Verify DB schema**

```bash
docker exec bes-postgres-1 psql -U bes_admin -d bes_db -c "\dt *category*"
```
Expected: `battle_category_state`, `battle_active_category`, `event_category`, `event_category_participant`, `event_category_participant_member`, `event_category_battle_guest`, `event_category_judge`

```bash
docker exec bes-postgres-1 psql -U bes_admin -d bes_db -c "\dt *genre*"
```
Expected: 0 rows (all genre tables gone)

- [ ] **Step 6: Smoke test in browser**

Navigate to `http://localhost`:
1. Log in as Organiser → go to EventDetails → verify Categories section shows correctly
2. Add a new category manually (no sheet) → verify it saves
3. Open a sheet-connected event → verify "FROM YOUR SHEET" pills still appear
4. Click a pill to add as category → verify it works
5. Go to BattleControl → verify category selector still functions
6. Check AdminPage → verify Genres section is gone

- [ ] **Step 7: Final commit**

```bash
git status
git add .
git commit -m "refactor: complete genre→category rename — all layers verified"
```

---

## Self-Review

**Spec coverage:**
- ✅ Rename `event_genre` → `event_category` (Task 1, 2, 3)
- ✅ Drop global `genre` table (Task 1)
- ✅ Drop `event_genre_link` table (Task 1)
- ✅ Drop `genre_id` FK from `event_category` (Task 1)
- ✅ Migrate `pickup_crew` FK to `event_category` (Task 1, Task 5)
- ✅ Rename battle state tables + `genre_name` → `category_name` columns (Task 1, 2, 5, 6)
- ✅ Remove GenreService / GenreRepo (Task 3, 5)
- ✅ Remove admin genre CRUD endpoints (Task 6)
- ✅ Remove linked-genres endpoints (Task 6)
- ✅ Frontend: rename all API functions and params (Task 8)
- ✅ Frontend: remove global genre management from AdminPage (Task 12)
- ✅ Frontend: rename all view/component genre refs (Tasks 10–12)
- ✅ Full validation (Task 14)

**No placeholders:** All steps include exact code, commands, and expected output.

**Type consistency:** `GetEventCategoryDto.id` is used throughout (Tasks 4, 5, 10). `SetActiveCategoryDto.categoryName` used in Tasks 4, 5, 6, 8, 11.
