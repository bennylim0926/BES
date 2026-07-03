# Judge Assignment Mode — Backend Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-category `judgeAssignmentMode` flag, enforce it at check-in, expose participants grouped by judge, and extend the audition display state DTO to carry per-judge slots.

**Architecture:** Add a boolean column to `event_category`, wire it through model → DTO → service → controller. The existing per-judge pool logic in `assignAuditionNumber` / `getAuditionNumViaQR` is already correct; this plan adds the mode flag that gates check-in validation and surfaces the mode to clients. The `AuditionDisplayStateDto` gets a new `judgeSlots` map used by the OBS multi-panel (Plan 2).

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, JUnit 5 + MockMvc, H2 (test profile)

## Global Constraints

- Next Flyway migration is `V44` (last is `V43__drop_access_code_column.sql`)
- All new endpoints under `/api/v1/event/` following existing REST conventions
- `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ORGANISER')")` on write endpoints
- Tests use `@ActiveProfiles("test")` (H2 in-memory); run with `mvn test -Dtest=<ClassName>`
- Never edit existing migrations; always add new `V{n}__description.sql`
- Bottom-up order: migration → model → DTO → service → controller

---

### Task 1: DB Migration — add `judge_assignment_mode` to `event_category`

**Files:**
- Create: `BES/src/main/resources/db/migration/V44__add_judge_assignment_mode.sql`

**Interfaces:**
- Produces: `event_category.judge_assignment_mode` column (boolean, NOT NULL, DEFAULT false)

- [ ] **Step 1: Write the migration file**

```sql
ALTER TABLE event_category
    ADD COLUMN IF NOT EXISTS judge_assignment_mode BOOLEAN NOT NULL DEFAULT FALSE;
```

- [ ] **Step 2: Verify Flyway picks it up**

```bash
cd BES && mvn spring-boot:run -Dspring-boot.run.profiles=local 2>&1 | grep -E "V44|Flyway|migrate" | head -10
```
Expected: `Successfully applied 1 migration to schema "public"` (or similar Flyway success line)

Stop the server (Ctrl-C) after verification.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V44__add_judge_assignment_mode.sql
git commit -m "db: add judge_assignment_mode column to event_category (V44)"
```

---

### Task 2: EventCategory model + GetEventCategoryDto + service mapping

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/EventCategory.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/GetEventCategoryDto.java`
- Modify: `BES/src/main/java/com/example/BES/services/EventCategoryService.java` (mapping in `getCategoriesByEventService`)

**Interfaces:**
- Consumes: `event_category.judge_assignment_mode` column (Task 1)
- Produces: `EventCategory.judgeAssignmentMode` field; `GetEventCategoryDto.judgeAssignmentMode` boolean; `EventCategoryService.getCategoriesByEventService()` populates it

- [ ] **Step 1: Add field to model**

In `EventCategory.java`, add after the `soloAllowed` field:

```java
@Column(name = "judge_assignment_mode", nullable = false, columnDefinition = "boolean default false")
private boolean judgeAssignmentMode = false;
```

- [ ] **Step 2: Add field to DTO**

In `GetEventCategoryDto.java`, add at the end of the class:

```java
public boolean judgeAssignmentMode;
```

- [ ] **Step 3: Populate DTO in service**

In `EventCategoryService.getCategoriesByEventService()`, add this line after `dto.soloAllowed = ec.isSoloAllowed();`:

```java
dto.judgeAssignmentMode = ec.isJudgeAssignmentMode();
```

- [ ] **Step 4: Write the failing test**

Create `BES/src/test/java/com/example/BES/EventCategoryDtoTest.java`:

```java
package com.example.BES;

import com.example.BES.dtos.GetEventCategoryDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.services.EventCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventCategoryDtoTest {

    @Autowired EventCategoryService eventCategoryService;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo eventCategoryRepo;

    @Test
    void judgeAssignmentMode_is_exposed_in_dto() {
        Event event = new Event();
        event.setEventName("DtoTestEvent");
        eventRepo.save(event);

        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("Open");
        cat.setJudgeAssignmentMode(true);
        eventCategoryRepo.save(cat);

        List<GetEventCategoryDto> dtos = eventCategoryService.getCategoriesByEventService("DtoTestEvent");
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).judgeAssignmentMode).isTrue();
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

```bash
cd BES && mvn test -Dtest=EventCategoryDtoTest -q 2>&1 | tail -20
```
Expected: FAIL (field not mapped yet — you just added it, so actually it should pass. If it fails with compile error, check import).

- [ ] **Step 6: Run test to verify it passes**

```bash
cd BES && mvn test -Dtest=EventCategoryDtoTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/EventCategory.java \
        BES/src/main/java/com/example/BES/dtos/GetEventCategoryDto.java \
        BES/src/main/java/com/example/BES/services/EventCategoryService.java \
        BES/src/test/java/com/example/BES/EventCategoryDtoTest.java
git commit -m "feat: expose judgeAssignmentMode in EventCategory model and GetEventCategoryDto"
```

---

### Task 3: Toggle endpoint — `POST /api/v1/event/categories/{categoryId}/judge-assignment-mode`

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventCategoryService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

**Interfaces:**
- Consumes: `EventCategory.judgeAssignmentMode` (Task 2)
- Produces: `POST /api/v1/event/categories/{categoryId}/judge-assignment-mode` body `{"enabled": true|false}` → 200 OK or 404

- [ ] **Step 1: Write failing test**

Create `BES/src/test/java/com/example/BES/JudgeAssignmentModeToggleTest.java`:

```java
package com.example.BES;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JudgeAssignmentModeToggleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo categoryRepo;

    private Long categoryId;

    @BeforeEach
    void setup() {
        Event event = new Event();
        event.setEventName("ToggleTestEvent");
        eventRepo.save(event);
        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("HipHop");
        categoryId = categoryRepo.save(cat).getId();
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void toggleOn_sets_judgeAssignmentMode_true() throws Exception {
        mockMvc.perform(post("/api/v1/event/categories/" + categoryId + "/judge-assignment-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());

        assertThat(categoryRepo.findById(categoryId).get().isJudgeAssignmentMode()).isTrue();
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void toggleOff_sets_judgeAssignmentMode_false() throws Exception {
        EventCategory cat = categoryRepo.findById(categoryId).get();
        cat.setJudgeAssignmentMode(true);
        categoryRepo.save(cat);

        mockMvc.perform(post("/api/v1/event/categories/" + categoryId + "/judge-assignment-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());

        assertThat(categoryRepo.findById(categoryId).get().isJudgeAssignmentMode()).isFalse();
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cd BES && mvn test -Dtest=JudgeAssignmentModeToggleTest -q 2>&1 | tail -10
```
Expected: FAIL with 404 (endpoint doesn't exist yet)

- [ ] **Step 3: Add service method**

In `EventCategoryService.java`, add:

```java
public void setJudgeAssignmentMode(Long categoryId, boolean enabled) {
    EventCategory ec = eventCategoryRepo.findById(categoryId)
        .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));
    ec.setJudgeAssignmentMode(enabled);
    eventCategoryRepo.save(ec);
}
```

- [ ] **Step 4: Add controller endpoint**

In `EventController.java`, find the block of other category-update endpoints (around the `updateRoundLabel` / `updateNumberColor` group, approximately line 247–290) and add:

```java
@Operation(summary = "Set Judge Assignment Mode", description = "Enables or disables per-judge audition pools for a category")
@PostMapping("/categories/{categoryId}/judge-assignment-mode")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ORGANISER')")
public ResponseEntity<?> setJudgeAssignmentMode(
        @PathVariable Long categoryId,
        @RequestBody Map<String, Boolean> body) {
    try {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        eventCategoryService.setJudgeAssignmentMode(categoryId, enabled);
        return ResponseEntity.ok(Map.of("message", "ok", "enabled", enabled));
    } catch (RuntimeException e) {
        return ResponseEntity.notFound().build();
    }
}
```

Make sure `EventCategoryService` is already `@Autowired` in the controller (it is — check around line 100).

- [ ] **Step 5: Run test to verify it passes**

```bash
cd BES && mvn test -Dtest=JudgeAssignmentModeToggleTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventCategoryService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java \
        BES/src/test/java/com/example/BES/JudgeAssignmentModeToggleTest.java
git commit -m "feat: add POST /categories/{id}/judge-assignment-mode toggle endpoint"
```

---

### Task 4: Check-in validation — block participants without judge when mode is on

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventCategoryParticipantService.java` (method `getAllAuditionNumsViaQR`)
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java` (method `registerParticipantAllCategories`)

**Interfaces:**
- Consumes: `EventCategory.judgeAssignmentMode` (Task 2)
- Produces: `GET /register-participant/{participantId}/{eventId}` returns HTTP 422 with body `"no_judge_assigned"` when mode is on and participant has no judge in any category

- [ ] **Step 1: Write failing test**

Create `BES/src/test/java/com/example/BES/CheckinJudgeModeValidationTest.java`:

```java
package com.example.BES;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckinJudgeModeValidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo categoryRepo;
    @Autowired ParticipantRepo participantRepo;
    @Autowired EventParticipantRepo eventParticipantRepo;
    @Autowired EventCategoryParticipantRepo ecpRepo;

    @Test
    @WithMockUser(authorities = "ROLE_HELPER")
    void checkin_blocked_when_mode_on_and_no_judge_assigned() throws Exception {
        Event event = new Event();
        event.setEventName("CheckinTestEvent");
        eventRepo.save(event);

        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("Open");
        cat.setJudgeAssignmentMode(true);
        categoryRepo.save(cat);

        Participant p = new Participant();
        p.setName("TestDancer");
        participantRepo.save(p);

        EventParticipant ep = new EventParticipant();
        ep.setId(new EventParticipantId(event.getEventId(), p.getParticipantId()));
        ep.setEvent(event);
        ep.setParticipant(p);
        eventParticipantRepo.save(ep);

        EventCategoryParticipant ecp = new EventCategoryParticipant();
        ecp.setId(new EventCategoryParticipantId(event.getEventId(), cat.getId(), p.getParticipantId()));
        ecp.setEvent(event);
        ecp.setEventCategory(cat);
        ecp.setParticipant(p);
        // judge intentionally NOT set
        ecpRepo.save(ecp);

        mockMvc.perform(get("/api/v1/event/register-participant/"
                + p.getParticipantId() + "/" + event.getEventId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string("no_judge_assigned"));
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd BES && mvn test -Dtest=CheckinJudgeModeValidationTest -q 2>&1 | tail -10
```
Expected: FAIL with assertion error (currently returns 201 or 400, not 422)

- [ ] **Step 3: Add validation in service `getAllAuditionNumsViaQR`**

In `EventCategoryParticipantService.getAllAuditionNumsViaQR`, add this block **before** the `for` loop that calls `getAuditionNumViaQR`:

```java
// Validate: if any category has judgeAssignmentMode on, participant must have a judge assigned
for (EventCategoryParticipant entry : entries) {
    if (entry.getEventCategory().isJudgeAssignmentMode() && entry.getJudge() == null) {
        throw new IllegalStateException("no_judge_assigned");
    }
}
```

- [ ] **Step 4: Handle new exception in controller**

In `EventController.registerParticipantAllCategories`, the existing catch block handles `IllegalStateException`. Extend it:

```java
} catch (IllegalStateException e) {
    if ("already_checked_in".equals(e.getMessage())) {
        return new ResponseEntity<>("already_checked_in", HttpStatus.CONFLICT);
    }
    if ("no_judge_assigned".equals(e.getMessage())) {
        return new ResponseEntity<>("no_judge_assigned", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd BES && mvn test -Dtest=CheckinJudgeModeValidationTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventCategoryParticipantService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java \
        BES/src/test/java/com/example/BES/CheckinJudgeModeValidationTest.java
git commit -m "feat: block check-in when judge assignment mode is on and participant has no judge"
```

---

### Task 5: Participants-by-judge endpoint

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/JudgeParticipantSlotDto.java`
- Modify: `BES/src/main/java/com/example/BES/services/EventCategoryParticipantService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

**Interfaces:**
- Consumes: `EventCategoryParticipant` with `judge` and `auditionNumber` fields
- Produces: `GET /api/v1/event/{eventName}/categories/{categoryId}/participants-by-judge` →  
  `Map<String judgeName, List<JudgeParticipantSlotDto>>` sorted by `auditionNumber` per judge; unassigned participants keyed as `"__unassigned__"`

`JudgeParticipantSlotDto`:
```java
public class JudgeParticipantSlotDto {
    public Integer auditionNumber;
    public String participantName;
    public List<String> memberNames;
    public Long participantId;
}
```

- [ ] **Step 1: Write failing test**

Create `BES/src/test/java/com/example/BES/ParticipantsByJudgeTest.java`:

```java
package com.example.BES;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParticipantsByJudgeTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo categoryRepo;
    @Autowired ParticipantRepo participantRepo;
    @Autowired JudgeRepo judgeRepo;
    @Autowired EventCategoryParticipantRepo ecpRepo;

    @Test
    @WithMockUser(authorities = "ROLE_ORGANISER")
    void returns_participants_grouped_by_judge() throws Exception {
        Event event = new Event();
        event.setEventName("ByJudgeTestEvent");
        eventRepo.save(event);

        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("Open");
        categoryRepo.save(cat);

        Judge j1 = new Judge(); j1.setName("Jay"); judgeRepo.save(j1);
        Judge j2 = new Judge(); j2.setName("Kay"); judgeRepo.save(j2);

        Participant p1 = new Participant(); p1.setName("Alice"); participantRepo.save(p1);
        Participant p2 = new Participant(); p2.setName("Bob");   participantRepo.save(p2);
        Participant p3 = new Participant(); p3.setName("Carol"); participantRepo.save(p3);

        // p1 → j1 #2, p2 → j1 #1, p3 → j2 #1
        saveEcp(event, cat, p1, j1, 2);
        saveEcp(event, cat, p2, j1, 1);
        saveEcp(event, cat, p3, j2, 1);

        String body = mockMvc.perform(get("/api/v1/event/ByJudgeTestEvent/categories/" + cat.getId() + "/participants-by-judge"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, List<Map<String, Object>>> result = objectMapper.readValue(body, new TypeReference<>() {});
        assertThat(result).containsKeys("Jay", "Kay");
        // Jay's list should be sorted by auditionNumber: Bob (#1), Alice (#2)
        assertThat(result.get("Jay")).hasSize(2);
        assertThat(result.get("Jay").get(0).get("participantName")).isEqualTo("Bob");
        assertThat(result.get("Jay").get(1).get("participantName")).isEqualTo("Alice");
        assertThat(result.get("Kay")).hasSize(1);
        assertThat(result.get("Kay").get(0).get("participantName")).isEqualTo("Carol");
    }

    private void saveEcp(Event e, EventCategory cat, Participant p, Judge j, int num) {
        EventCategoryParticipant ecp = new EventCategoryParticipant();
        ecp.setId(new EventCategoryParticipantId(e.getEventId(), cat.getId(), p.getParticipantId()));
        ecp.setEvent(e);
        ecp.setEventCategory(cat);
        ecp.setParticipant(p);
        ecp.setDisplayName(p.getName());
        ecp.setJudge(j);
        ecp.setAuditionNumber(num);
        ecpRepo.save(ecp);
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd BES && mvn test -Dtest=ParticipantsByJudgeTest -q 2>&1 | tail -10
```
Expected: FAIL with 404

- [ ] **Step 3: Create DTO**

Create `BES/src/main/java/com/example/BES/dtos/JudgeParticipantSlotDto.java`:

```java
package com.example.BES.dtos;

import java.util.List;

public class JudgeParticipantSlotDto {
    public Integer auditionNumber;
    public String participantName;
    public List<String> memberNames;
    public Long participantId;
}
```

- [ ] **Step 4: Add service method**

In `EventCategoryParticipantService.java`, add:

```java
public Map<String, List<JudgeParticipantSlotDto>> getParticipantsByJudge(String eventName, Long categoryId) {
    List<EventCategoryParticipant> ecps = repo.findByEventNameAndEventCategoryId(eventName, categoryId);
    Map<String, List<JudgeParticipantSlotDto>> result = new java.util.LinkedHashMap<>();
    for (EventCategoryParticipant ecp : ecps) {
        String key = ecp.getJudge() != null ? ecp.getJudge().getName() : "__unassigned__";
        result.computeIfAbsent(key, k -> new java.util.ArrayList<>());
        JudgeParticipantSlotDto slot = new JudgeParticipantSlotDto();
        slot.auditionNumber = ecp.getAuditionNumber();
        slot.participantName = ecp.getDisplayName() != null ? ecp.getDisplayName()
            : ecp.getParticipant().getName();
        slot.participantId = ecp.getParticipant().getParticipantId();
        slot.memberNames = ecp.getMembers() == null ? List.of()
            : ecp.getMembers().stream().map(EventCategoryParticipantMember::getMemberName).toList();
        result.get(key).add(slot);
    }
    result.forEach((k, v) -> v.sort(java.util.Comparator.comparingInt(s -> s.auditionNumber != null ? s.auditionNumber : Integer.MAX_VALUE)));
    return result;
}
```

You'll also need to add the repo method `findByEventNameAndEventCategoryId`. Check `EventCategoryParticipantRepo` for an existing equivalent (look for `findByEvent_EventNameAndEventCategory_Id` or similar). If it exists, use it. If not, add to the repo interface:

```java
List<EventCategoryParticipant> findByEvent_EventNameIgnoreCaseAndEventCategory_Id(String eventName, Long categoryId);
```

Then call it in the service as `repo.findByEvent_EventNameIgnoreCaseAndEventCategory_Id(eventName, categoryId)`.

Also add the missing import in the service:
```java
import com.example.BES.dtos.JudgeParticipantSlotDto;
```

- [ ] **Step 5: Add controller endpoint**

In `EventController.java`, add near the other category endpoints:

```java
@Operation(summary = "Get Participants By Judge", description = "Returns participants grouped by assigned judge, sorted by audition number per judge. Unassigned participants keyed as __unassigned__.")
@GetMapping("/{eventName}/categories/{categoryId}/participants-by-judge")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE')")
public ResponseEntity<Map<String, List<JudgeParticipantSlotDto>>> getParticipantsByJudge(
        @PathVariable String eventName,
        @PathVariable Long categoryId) {
    return ResponseEntity.ok(eventCategoryParticipantService.getParticipantsByJudge(eventName, categoryId));
}
```

Add the import at the top of `EventController.java`:
```java
import com.example.BES.dtos.JudgeParticipantSlotDto;
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd BES && mvn test -Dtest=ParticipantsByJudgeTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/JudgeParticipantSlotDto.java \
        BES/src/main/java/com/example/BES/services/EventCategoryParticipantService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java \
        BES/src/main/java/com/example/BES/respositories/EventCategoryParticipantRepo.java \
        BES/src/test/java/com/example/BES/ParticipantsByJudgeTest.java
git commit -m "feat: add GET /categories/{id}/participants-by-judge endpoint"
```

---

### Task 6: Extend AuditionDisplayStateDto with judge-mode slots

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/AuditionDisplayStateDto.java`

**Interfaces:**
- Consumes: nothing new from prior tasks
- Produces: `AuditionDisplayStateDto.judgeMode` (boolean), `AuditionDisplayStateDto.judgeCurrentRound` (int), `AuditionDisplayStateDto.judgeSlots` (`Map<String judgeName, Slot>`) — frontend Plan 2 reads these to render the multi-panel OBS display

- [ ] **Step 1: Add fields to DTO**

In `AuditionDisplayStateDto.java`, add after the `baselineDuration` field:

```java
// Judge assignment mode fields — only populated when judgeMode = true
public boolean judgeMode;
public int judgeCurrentRound;
// Key = judge name, Value = the slot this judge is currently on (null if judge has no participant at this round)
public Map<String, Slot> judgeSlots;
```

Add the import:
```java
import java.util.Map;
```

- [ ] **Step 2: Verify build still compiles**

```bash
cd BES && mvn compile -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/AuditionDisplayStateDto.java
git commit -m "feat: add judgeMode/judgeCurrentRound/judgeSlots to AuditionDisplayStateDto"
```

---

### Task 7: Full backend build + all tests

- [ ] **Step 1: Run all backend tests**

```bash
cd BES && mvn test -q 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, all tests pass (new tests + existing suite)

- [ ] **Step 2: Build JAR**

```bash
cd BES && mvn clean package -DskipTests -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`, JAR in `target/`

- [ ] **Step 3: Commit if any fixes needed, then tag backend complete**

```bash
git commit -m "chore: backend judge assignment mode foundation complete" --allow-empty
```
