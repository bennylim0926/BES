# Access Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Grant Helper read-only Score access with tie-breaker, hide AuditionList UI from Organiser, and add Admin event deletion with full cascade cleanup.

**Architecture:** Three independent feature changes touching the same layers (backend controller/service, frontend router, navigation components, views). Event deletion is the largest piece — new backend endpoint + service method + frontend UI with two-step confirmation. All changes follow existing patterns: `@PreAuthorize` annotations on controllers, `allowedRoles` in router meta, role-gated tiles in EventPanel, and the existing API fetch pattern.

**Tech Stack:** Spring Boot (Java), Vue 3 (Composition API), PostgreSQL, Flyway (no migration needed — no schema changes)

## Global Constraints

- Score modification stays in AuditionList only — Score.vue is display-only for all roles
- Organiser keeps backend and route access to AuditionList — only UI surfaces hidden
- Event deletion must clean ALL related data in dependency order within @Transactional
- No JPA cascade configuration changes — manual cleanup for explicitness
- All new backend endpoints use `@PreAuthorize("hasRole('ADMIN')")`
- Frontend API calls follow existing pattern: `fetch()` with `credentials: 'include'`, `res.ok` check, `console.error` on catch

---

### Task 1: Add HELPER to backend Score read endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java:587`

**Interfaces:**
- Produces: `GET /api/v1/event/scores/{eventName}` now accessible to HELPER role

- [ ] **Step 1: Add HELPER to @PreAuthorize on GET scores**

Change line 587 from:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE', 'JUDGE')")
```
to:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE', 'JUDGE', 'HELPER')")
```

No other backend changes needed — the other Score-dependent read endpoints (`GET /{eventName}/criteria`, `GET /{eventName}/categories`, `GET /{eventName}/results-status`, `GET /{eventName}/feedback-tags`) are already public or already include HELPER.

- [ ] **Step 2: Verify build compiles**

Run: `cd BES && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: allow HELPER to read scores endpoint

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Add Helper to Score route in frontend router

**Files:**
- Modify: `BES-frontend/src/router/index.js:75`

**Interfaces:**
- Produces: `/event/score` route now allows `ROLE_HELPER`

- [ ] **Step 1: Add ROLE_HELPER to Score route allowedRoles**

Change line 75 from:
```js
meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_EMCEE', 'ROLE_ORGANISER'], requiresEvent: true }
```
to:
```js
meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_EMCEE', 'ROLE_ORGANISER', 'ROLE_HELPER'], requiresEvent: true }
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/router/index.js
git commit -m "feat: allow HELPER role to access Score route

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Add Helper to Score tile in EventPanel

**Files:**
- Modify: `BES-frontend/src/components/EventPanel.vue:32`

**Interfaces:**
- Produces: Score tile visible to Helper in the slide-over nav panel

- [ ] **Step 1: Add ROLE_HELPER to Score tile roles**

Change line 32 from:
```js
{ key: 'score',        icon: 'pi-chart-bar', label: 'Score',        roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE'] },
```
to:
```js
{ key: 'score',        icon: 'pi-chart-bar', label: 'Score',        roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE', 'ROLE_HELPER'] },
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/components/EventPanel.vue
git commit -m "feat: show Score tile to HELPER in EventPanel

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Show Scoreboard card to Helper in MainMenu

**Files:**
- Modify: `BES-frontend/src/views/MainMenu.vue:103-104`

**Interfaces:**
- Consumes: `role` computed from authStore (already defined)
- Produces: Scoreboard quick-action card visible to Helper

- [ ] **Step 1: Add ROLE_HELPER to Scoreboard card v-if guard**

Change lines 103-104 from:
```html
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE')"
          :to="{ name: 'Score' }"
```
to:
```html
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_HELPER')"
          :to="{ name: 'Score' }"
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/MainMenu.vue
git commit -m "feat: show Scoreboard card to HELPER on home screen

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Add Helper support in Score.vue

**Files:**
- Modify: `BES-frontend/src/views/Score.vue:23-24` (add isHelper computed)
- Modify: `BES-frontend/src/views/Score.vue:622` (results-release guard)
- Modify: `BES-frontend/src/views/Score.vue:762,768,786,792` (row action buttons)

**Interfaces:**
- Consumes: `userRole` computed from authStore (already defined)
- Produces: `isHelper` computed; tie-breaker panel accessible to Helper; row action buttons (breakdown, feedback, QR) stay Admin/Organiser-only

- [ ] **Step 1: Add isHelper computed**

After line 24 (`const isAdminOrOrganiser = ...`), add:
```js
const isHelper = computed(() => userRole.value === 'ROLE_HELPER')
```

- [ ] **Step 2: Update mode default for Helper**

Change line 32 from:
```js
return userRole.value === 'ROLE_EMCEE' ? 'broadcast' : 'control'
```
to:
```js
return (userRole.value === 'ROLE_EMCEE' || userRole.value === 'ROLE_HELPER') ? 'broadcast' : 'control'
```

- [ ] **Step 3: Keep results-release toggle Admin/Organiser-only (no change needed)**

Line 622 already uses `v-if="isAdminOrOrganiser && mode === 'control'"` — no change needed. Helper won't see it.

- [ ] **Step 4: Keep row action buttons Admin/Organiser-only (no change needed)**

Lines 762, 768, 786, 792 already use `v-if="isAdminOrOrganiser"` — no change needed. Helper won't see breakdown/feedback/QR buttons.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat: add isHelper to Score — defaults to broadcast, tie-breaker accessible

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Add Score link to HelperSessionView

**Files:**
- Modify: `BES-frontend/src/views/HelperSessionView.vue:123-144` (nav-list section)

**Interfaces:**
- Consumes: `router` (already imported), `authStore.activeEvent` (already available)
- Produces: Score navigation button in Helper session hub

- [ ] **Step 1: Add goToScore function**

After the `goToEventDetails` function (line 75), add:
```js
function goToScore() {
  if (authStore.activeEvent) {
    router.push({ name: 'Score' })
  }
}
```

- [ ] **Step 2: Add Score navigation button in template**

After the "Audition Screen" button (line 143) and before the closing `</div>` of `nav-list`, add:
```html
        <button
          @click="goToScore"
          class="nav-btn"
        >
          <i class="pi pi-chart-bar nav-btn-icon" aria-hidden="true"></i>
          <span class="nav-btn-label">Scoreboard</span>
        </button>
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/HelperSessionView.vue
git commit -m "feat: add Scoreboard link to HelperSessionView

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Hide AuditionList UI from Organiser

**Files:**
- Modify: `BES-frontend/src/components/EventPanel.vue:30` (audition tile roles)
- Modify: `BES-frontend/src/views/MainMenu.vue:80` (Audition card guard)

**Interfaces:**
- Produces: Audition List tile hidden from Organiser in EventPanel; Audition card hidden from Organiser in MainMenu. Router and backend unchanged.

- [ ] **Step 1: Remove ROLE_ORGANISER from audition tile in EventPanel**

Change line 30 from:
```js
{ key: 'audition',     icon: 'pi-list',      label: 'Audition',     roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE', 'ROLE_JUDGE'] },
```
to:
```js
{ key: 'audition',     icon: 'pi-list',      label: 'Audition',     roles: ['ROLE_ADMIN', 'ROLE_EMCEE', 'ROLE_JUDGE'] },
```

- [ ] **Step 2: Hide Audition card from Organiser in MainMenu**

Change line 80 from:
```html
          v-if="activeEvent && role !== 'ROLE_HELPER'"
```
to:
```html
          v-if="activeEvent && role !== 'ROLE_HELPER' && role !== 'ROLE_ORGANISER'"
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/components/EventPanel.vue BES-frontend/src/views/MainMenu.vue
git commit -m "feat: hide AuditionList UI from Organiser (nav surfaces only)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Backend — Event deletion service and controller

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventService.java` (add autowired repos + deleteEvent method)
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java` (add DELETE endpoint)
- Possibly modify: Individual repository files (add missing query methods where needed)

**Interfaces:**
- Consumes: Existing repository beans
- Produces: `EventService.deleteEvent(String eventName)` — deletes event and ALL related data within `@Transactional`
- Produces: `DELETE /api/v1/event/{eventName}` — Admin-only endpoint

**Repository method availability (verified from codebase):**

| Repository Bean | Has `findBy*` | Has `deleteBy*` | Notes |
|---|---|---|---|
| `EventCategoryRepo` | `findByEvent(Event)` | None | Need to add `deleteByEvent` or use `deleteAll` |
| `EventCategoryParticipantRepo` | `findByEventCategory(EventCategory)` | None | Need to add bulk delete or use `deleteAll` |
| `AuditionFeedbackRepository` | `findByEventCategoryParticipant(ECP)` | None | Need `@Modifying @Query` for bulk delete |
| `ScoreRepo` | `findByEventCategoryParticipant(ECP)` | `deleteByEventCategoryParticipantAndJudge(ECP, Judge)` only | Need `@Modifying @Query` to delete all scores for an ECP (regardless of judge) |
| `EventCategoryParticipantMemberRepo` | None | None | Need `deleteByEventCategoryParticipant` |
| `EventParticipantRepo` | `findByEvent(Event)` | None | Need `deleteByEvent` |
| `EventParticipantTeamMemberRepo` | None | `deleteByEventParticipant(EP)` | ✅ exists |
| `ScoringCriteriaRepo` | `findByEventAndEventCategory(Event, EC)` | None | Need `@Modifying @Query` to delete by event only and by event+category |
| `FeedbackTagRepository` | `findByEventEventId(Long)` | None | Use `deleteAll(findByEventEventId(...))` |
| `FeedbackTagGroupRepository` | `findByEventEventId(Long)` | None | Use `deleteAll(findByEventEventId(...))` |
| `EventCategoryBattleGuestRepo` | `findByEvent(Event)` | None | Need `deleteByEvent` |
| `PickupCrewRepo` | `findByEventAndEventCategory(Event, EC)` | None | Need `findByEvent` — add to repo; members cascade via `CascadeType.ALL, orphanRemoval=true` |
| `SessionTokenRepository` | `findByEvent_EventId(Long)` | None | Use `deleteAll(findByEvent_EventId(...))` |
| `BattleCategoryStateRepository` | `findByEventName(String)` | None | Use `deleteAll(findByEventName(...))` |
| `BattleActiveCategoryRepository` | None (empty interface) | None | Need `findByEventName` + `@Modifying @Query` to delete by event name |
| `EventEmailTemplateRepo` | `findByEvent_EventId(Long)` | None | Delete the single entity found |
| `JudgeRepo` | `findJudgesByEventId(Long)` | `deleteEventJudge(Long, Long)` (individual only) | Clear join tables by iterating judges |
| `AccountRepository` | `findByRole(String)` | — | Clear `organiser_event` via `Account.assignedEvents.removeIf()` |

**Strategy:** For repos lacking bulk-delete methods, use `deleteAll(findByXxx(...))` where the finder exists (simplest, still within `@Transactional`). For repos lacking both, add the minimal Spring Data derived method or `@Modifying @Query`.

- [ ] **Step 1: Add missing repository methods**

Add these methods to the respective repository files (only if they don't already exist):

**EventCategoryParticipantRepo.java** — add:
```java
void deleteByEventCategory(EventCategory eventCategory);
```

**EventCategoryParticipantMemberRepo.java** — add:
```java
void deleteByEventCategoryParticipant(EventCategoryParticipant ecp);
```

**AuditionFeedbackRepository.java** — add:
```java
@Modifying
@Transactional
void deleteByEventCategoryParticipant(EventCategoryParticipant ecp);
```

**ScoreRepo.java** — add:
```java
@Modifying
@Transactional
@Query("DELETE FROM Score s WHERE s.eventCategoryParticipant = :ecp")
void deleteAllByEventCategoryParticipant(@Param("ecp") EventCategoryParticipant ecp);
```

**EventParticipantRepo.java** — add:
```java
void deleteByEvent(Event event);
```

**ScoringCriteriaRepo.java** — add:
```java
@Modifying
@Transactional
@Query("DELETE FROM ScoringCriteria sc WHERE sc.event = :event")
void deleteByEvent(@Param("event") Event event);

@Modifying
@Transactional
@Query("DELETE FROM ScoringCriteria sc WHERE sc.eventCategory = :category")
void deleteByEventCategory(@Param("category") EventCategory category);
```

**EventCategoryBattleGuestRepo.java** — add:
```java
void deleteByEvent(Event event);
```

**PickupCrewRepo.java** — add:
```java
List<PickupCrew> findByEvent(Event event);
void deleteByEvent(Event event);
```

**EventCategoryRepo.java** — add:
```java
void deleteByEvent(Event event);
```

**BattleActiveCategoryRepository.java** — add:
```java
Optional<BattleActiveCategory> findByEventName(String eventName);

@Modifying
@Transactional
@Query("DELETE FROM BattleActiveCategory b WHERE b.eventName = :eventName")
void deleteByEventName(@Param("eventName") String eventName);
```

- [ ] **Step 2: Add autowired fields to EventService.java**

Add these imports (combine with existing):
```java
import com.example.BES.respositories.*;
import com.example.BES.models.*;
import java.util.List;
```

Add these `@Autowired` fields inside the class. Existing fields (`repo`, `accountRepository`, `emailTemplateService`, `messagingTemplate`) stay. Add the rest:

```java
@Autowired
private ScoreRepo scoreRepo;

@Autowired
private AuditionFeedbackRepository auditionFeedbackRepository;

@Autowired
private EventCategoryParticipantRepo eventCategoryParticipantRepo;

@Autowired
private EventCategoryParticipantMemberRepo eventCategoryParticipantMemberRepo;

@Autowired
private EventParticipantRepo eventParticipantRepo;

@Autowired
private EventParticipantTeamMemberRepo eventParticipantTeamMemberRepo;

@Autowired
private ScoringCriteriaRepo scoringCriteriaRepo;

@Autowired
private FeedbackTagRepository feedbackTagRepository;

@Autowired
private FeedbackTagGroupRepository feedbackTagGroupRepository;

@Autowired
private EventCategoryBattleGuestRepo eventCategoryBattleGuestRepo;

@Autowired
private PickupCrewRepo pickupCrewRepo;

@Autowired
private SessionTokenRepository sessionTokenRepository;

@Autowired
private BattleCategoryStateRepository battleCategoryStateRepository;

@Autowired
private BattleActiveCategoryRepository battleActiveCategoryRepository;

@Autowired
private EventEmailTemplateRepo eventEmailTemplateRepo;

@Autowired
private EventCategoryRepo eventCategoryRepo;

@Autowired
private JudgeRepo judgeRepo;
```

- [ ] **Step 3: Add deleteEvent method to EventService.java**

Add at end of class (before final `}`):

```java
@Transactional
public void deleteEvent(String eventName) {
    Event event = repo.findByEventName(eventName)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Long eventId = event.getEventId();

    // 1. Categories and their children
    List<EventCategory> categories = eventCategoryRepo.findByEvent(event);
    for (EventCategory cat : categories) {
        List<EventCategoryParticipant> ecps = eventCategoryParticipantRepo.findByEventCategory(cat);
        for (EventCategoryParticipant ecp : ecps) {
            // 1a. AuditionFeedback
            auditionFeedbackRepository.deleteByEventCategoryParticipant(ecp);
            // 1b. Scores (all judges)
            scoreRepo.deleteAllByEventCategoryParticipant(ecp);
            // 1c. Category participant members
            eventCategoryParticipantMemberRepo.deleteByEventCategoryParticipant(ecp);
        }
        // 1d. EventCategoryParticipants for this category
        eventCategoryParticipantRepo.deleteByEventCategory(cat);
    }

    // 2. EventParticipants and team members
    List<EventParticipant> participants = eventParticipantRepo.findByEvent(event);
    for (EventParticipant ep : participants) {
        eventParticipantTeamMemberRepo.deleteByEventParticipant(ep);
    }
    eventParticipantRepo.deleteByEvent(event);

    // 3. ScoringCriteria (event-level and category-level)
    scoringCriteriaRepo.deleteByEvent(event);
    for (EventCategory cat : categories) {
        scoringCriteriaRepo.deleteByEventCategory(cat);
    }

    // 4. Feedback tags and groups (event-scoped)
    feedbackTagRepository.deleteAll(feedbackTagRepository.findByEventEventId(eventId));
    feedbackTagGroupRepository.deleteAll(feedbackTagGroupRepository.findByEventEventId(eventId));

    // 5. Battle guests
    eventCategoryBattleGuestRepo.deleteByEvent(event);

    // 6. Pickup crews (members cascade via orphanRemoval)
    List<PickupCrew> crews = pickupCrewRepo.findByEvent(event);
    pickupCrewRepo.deleteAll(crews);  // cascade deletes members

    // 7. Session tokens
    sessionTokenRepository.deleteAll(sessionTokenRepository.findByEvent_EventId(eventId));

    // 8. Battle state
    battleCategoryStateRepository.deleteAll(battleCategoryStateRepository.findByEventName(eventName));

    // 8b. Battle active category
    battleActiveCategoryRepository.deleteByEventName(eventName);

    // 9. Clear event_judge join table
    List<Judge> eventJudges = judgeRepo.findJudgesByEventId(eventId);
    for (Judge judge : eventJudges) {
        judgeRepo.deleteEventJudge(eventId, judge.getJudgeId());
    }

    // 9b. Clear event_category_judge join table — clear each category's judges list
    for (EventCategory cat : categories) {
        cat.getJudges().clear();
        eventCategoryRepo.save(cat);
    }

    // 10. Email template
    eventEmailTemplateRepo.findByEvent_EventId(eventId)
        .ifPresent(eventEmailTemplateRepo::delete);

    // 11. EventCategories
    eventCategoryRepo.deleteByEvent(event);

    // 12. Clear organiser_event join table
    List<Account> organisers = accountRepository.findByRole("ORGANISER");
    for (Account org : organisers) {
        List<Event> assigned = org.getAssignedEvents();
        if (assigned != null && assigned.removeIf(e -> e.getEventId().equals(eventId))) {
            accountRepository.save(org);
        }
    }

    // 13. Finally
    repo.delete(event);
}
```

- [ ] **Step 4: Add DELETE endpoint to EventController.java**

Add this method to EventController.java (near the other DELETE endpoints):
```java
@Operation(summary = "Delete Event", description = "Permanently deletes an event and ALL associated data — participants, categories, scores, feedback, battle state, session tokens. Admin only.")
@DeleteMapping("/{eventName}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> deleteEvent(@PathVariable String eventName) {
    try {
        eventService.deleteEvent(eventName);
        return ResponseEntity.ok(Map.of("message", "Event '" + eventName + "' and all associated data deleted"));
    } catch (ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
    } catch (Exception e) {
        log.error("Error deleting event: {}", eventName, e);
        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete event: " + e.getMessage()));
    }
}
```

- [ ] **Step 5: Verify build compiles**

Run: `cd BES && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java \
        BES/src/main/java/com/example/BES/respositories/
git commit -m "feat: add Admin event deletion with full cascade cleanup

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Frontend — deleteEvent API function

**Files:**
- Modify: `BES-frontend/src/utils/api.js` (add deleteEvent function)

**Interfaces:**
- Produces: `deleteEvent(eventName)` — calls `DELETE /api/v1/event/{eventName}`

- [ ] **Step 1: Add deleteEvent function**

Add this function to `api.js` (near the other event functions, after `fetchAllEvents` around line 74):
```js
export const deleteEvent = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.error || `Delete failed (${res.status})`)
    }
    return await res.json()
  } catch (err) {
    console.error(err)
    throw err
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add deleteEvent API function

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Frontend — Delete button on EventCard with confirmation modal

**Files:**
- Modify: `BES-frontend/src/components/EventCard.vue` (add delete action + emit)
- Modify: `BES-frontend/src/views/Events.vue` (handle delete, confirmation modal, refresh list)

**Interfaces:**
- Consumes: `deleteEvent` from api.js, existing EventCard emit pattern, `expandedId` state in Events.vue
- Produces: Delete button on each EventCard (visible only to Admin); two-step confirmation modal in Events.vue

- [ ] **Step 1: Add delete action to EventCard.vue**

In `<script setup>`, add a new emit after the existing ones (line 8):
```js
const emit = defineEmits(['onDetails', 'onAudition', 'onParticipants', 'onScoreboard', 'onBattle', 'toggle', 'onDelete'])
```

Add a new action entry after "Battle" in the actions array (line 18):
```js
  { key: 'onDelete',      icon: 'pi-trash',     label: 'Delete'  },
```

Update the grid from `grid-cols-5` to `grid-cols-6` (line 59):
```html
        <div class="grid grid-cols-6">
```

- [ ] **Step 2: Add Delete button styling in EventCard.vue**

The delete action button should use red styling. Modify the button in the action panel grid. Currently all buttons use the same classes. Add a conditional class for the delete action. Change line 60-68 from:
```html
          <button
            v-for="action in actions"
            :key="action.key"
            @click.stop="emit(action.key)"
            class="flex flex-col items-center gap-1.5 py-3.5 text-content-muted
                   hover:text-accent hover:bg-[rgba(255,255,255,0.04)] transition-all duration-150"
          >
            <i class="pi text-base text-accent" :class="action.icon"></i>
            <span class="type-label">{{ action.label }}</span>
          </button>
```
to:
```html
          <button
            v-for="action in actions"
            :key="action.key"
            @click.stop="emit(action.key)"
            class="flex flex-col items-center gap-1.5 py-3.5 transition-all duration-150"
            :class="action.key === 'onDelete'
              ? 'text-content-muted hover:text-red-400 hover:bg-red-950/30'
              : 'text-content-muted hover:text-accent hover:bg-[rgba(255,255,255,0.04)]'"
          >
            <i class="pi text-base" :class="[action.icon, action.key === 'onDelete' ? '' : 'text-accent']"></i>
            <span class="type-label">{{ action.label }}</span>
          </button>
```

- [ ] **Step 3: Wire delete handler in Events.vue**

In `<script setup>`, add imports and state after existing refs:
```js
import { deleteEvent } from '@/utils/api'

// ... after existing refs (line 14):
const showDeleteModal = ref(false)
const eventToDelete = ref(null)
const deleteConfirmName = ref('')
const deleteError = ref('')
const deleting = ref(false)
```

Add handler functions:
```js
function openDeleteModal(event) {
  eventToDelete.value = event
  deleteConfirmName.value = ''
  deleteError.value = ''
  showDeleteModal.value = true
  expandedId.value = null  // close the action panel
}

function closeDeleteModal() {
  showDeleteModal.value = false
  eventToDelete.value = null
  deleteConfirmName.value = ''
  deleteError.value = ''
}

async function confirmDelete() {
  if (!eventToDelete.value || deleteConfirmName.value !== eventToDelete.value.folderName) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteEvent(eventToDelete.value.folderName)
    // Remove from local list
    events.value = events.value.filter(e => e.folderID !== eventToDelete.value.folderID)
    dbEvents.value = dbEvents.value.filter(e => e.name !== eventToDelete.value.folderName)
    closeDeleteModal()
  } catch (err) {
    deleteError.value = err.message || 'Failed to delete event'
  } finally {
    deleting.value = false
  }
}
```

Add the `@onDelete` handler to the EventCard in the template (line 94-105):
```html
      <EventCard
        v-for="event in filtered"
        :key="event.folderID"
        :buttonName="event.folderName"
        :expanded="expandedId === event.folderID"
        @toggle="toggleExpanded(event.folderID)"
        @onDetails="goToEventDetails(event.folderName, event.folderID)"
        @onAudition="activateAndGo(event.folderName, 'Audition List')"
        @onParticipants="activateAndGo(event.folderName, 'Update Event Details')"
        @onScoreboard="activateAndGo(event.folderName, 'Score')"
        @onBattle="activateAndGo(event.folderName, 'Battle Control')"
        @onDelete="openDeleteModal(event)"
      />
```

- [ ] **Step 4: Add confirmation modal template to Events.vue**

Add this modal at the end of the template, just before the closing `</div>` of the outer page-container div (after line 126, before the outermost `</div></div>`):

```html
    <!-- Delete Event Confirmation Modal -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showDeleteModal"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
          style="background: rgba(0,0,0,0.8)"
          @click.self="closeDeleteModal"
        >
          <div
            class="w-full max-w-md p-6 flex flex-col gap-5"
            style="background: #1a1a1a; border: 1px solid rgba(239,68,68,0.3); clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%)"
          >
            <!-- Header -->
            <div class="flex items-center gap-3">
              <div class="w-2.5 h-2.5 rounded-full bg-red-500 flex-shrink-0" style="box-shadow: 0 0 10px rgba(239,68,68,0.7)"></div>
              <h2 class="type-page-title text-red-400" style="font-size: 20px">DELETE EVENT</h2>
            </div>

            <!-- Warning -->
            <div class="px-4 py-3" style="border-left: 3px solid rgba(239,68,68,0.6); background: rgba(239,68,68,0.07)">
              <p class="type-label text-red-300/90 mb-1">This will permanently delete:</p>
              <p class="type-prose text-red-200/70">
                <strong class="type-name text-red-300">{{ eventToDelete?.folderName }}</strong>
                and ALL associated data — participants, categories, scores, feedback, battle state, and session tokens.
                This cannot be undone.
              </p>
            </div>

            <!-- Step 1: Initial warning -->
            <div v-if="deleteConfirmName === ''">
              <p class="type-prose text-content-muted mb-3">Type the event name to enable deletion.</p>
              <input
                v-model="deleteConfirmName"
                type="text"
                :placeholder="eventToDelete?.folderName"
                class="input-base w-full"
                autofocus
              />
            </div>

            <!-- Step 2: Name matched — ready to delete -->
            <div v-else-if="deleteConfirmName === eventToDelete?.folderName">
              <p class="type-label text-red-400 mb-3">✓ Name confirmed. Ready to delete.</p>
            </div>

            <!-- Error -->
            <div
              v-if="deleteError"
              class="px-3 py-2 type-label text-red-300"
              style="border-left: 3px solid rgba(239,68,68,0.5); background: rgba(239,68,68,0.08)"
            >
              {{ deleteError }}
            </div>

            <!-- Actions -->
            <div class="flex gap-3">
              <button
                @click="closeDeleteModal"
                class="flex-1 py-2.5 type-label border border-surface-600 text-content-muted hover:text-content-primary transition-colors"
                style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)"
                :disabled="deleting"
              >CANCEL</button>
              <button
                @click="confirmDelete"
                :disabled="deleteConfirmName !== eventToDelete?.folderName || deleting"
                class="flex-1 py-2.5 type-label font-bold transition-all"
                :class="deleteConfirmName === eventToDelete?.folderName && !deleting
                  ? 'bg-red-600 text-white hover:bg-red-500'
                  : 'bg-surface-700 text-content-muted cursor-not-allowed'"
                style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)"
              >
                <span v-if="deleting">DELETING…</span>
                <span v-else>DELETE EVENT</span>
              </button>
            </div>

            <!-- Tap outside hint -->
            <p class="type-prose-sm text-content-muted/50 text-center">Tap outside to cancel</p>
          </div>
        </div>
      </Transition>
    </Teleport>
```

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/components/EventCard.vue BES-frontend/src/views/Events.vue
git commit -m "feat: add Admin delete event button with two-step confirmation

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: Verify end-to-end

**Files:** (none — manual verification)

- [ ] **Step 1: Build backend**

Run: `cd BES && mvn clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: Build frontend**

Run: `cd BES-frontend && npm run build`
Expected: Build completes without errors

- [ ] **Step 3: Run backend tests**

Run: `cd BES && mvn test`
Expected: All existing tests pass

- [ ] **Step 4: Run frontend tests**

Run: `cd BES-frontend && npm test`
Expected: All existing tests pass

- [ ] **Step 5: Commit if any build fixes were needed**

```bash
git add -A
git commit -m "chore: build fixes for access management changes

Co-Authored-By: Claude <noreply@anthropic.com>"
```
