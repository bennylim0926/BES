# Participant Management Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework `UpdateEventDetails.vue` into a full participant management page — expandable rows grouped by participant, genre-chip filter + name search, format-aware edit modal, and two-level delete (genre or entire participant).

**Architecture:** Bottom-up — repo query additions → service methods → controller endpoints → api.js → ConfirmModal component → UpdateEventDetails.vue rewrite. All destructive actions are transactional in the service layer.

**Tech Stack:** Spring Boot / JPA (backend), Vue 3 Composition API / scoped CSS (frontend).

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `BES/src/main/java/com/example/BES/respositories/EventParticipantRepo.java` | MODIFY | Add `findByParticipant` query |
| `BES/src/main/java/com/example/BES/dtos/UpdateParticipantDto.java` | CREATE | Request DTO for name + member update |
| `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java` | MODIFY | Add `deleteParticipantFromEvent` + `updateParticipant` methods; inject Score/Feedback/TeamMember repos |
| `BES/src/main/java/com/example/BES/controllers/EventController.java` | MODIFY | Add `DELETE /participant/{participantId}/{eventId}` + `PUT /participant/{participantId}/{eventId}` |
| `BES-frontend/src/utils/api.js` | MODIFY | Add `deleteParticipantFromEvent`, `updateParticipant` |
| `BES-frontend/src/components/ConfirmModal.vue` | CREATE | Reusable confirm/cancel dialog |
| `BES-frontend/src/views/UpdateEventDetails.vue` | REWRITE | Full page: filter bar, expandable table, edit modal |

---

## Task 1: Repo — add `findByParticipant` to EventParticipantRepo

**Files:**
- Modify: `BES/src/main/java/com/example/BES/respositories/EventParticipantRepo.java`

- [ ] **Step 1: Add the query method**

Open `BES/src/main/java/com/example/BES/respositories/EventParticipantRepo.java`. After the existing `findByEventAndPaymentVerifiedFalse` line, add:

```java
List<EventParticipant> findByParticipant(Participant participant);
```

The full interface should now read:

```java
public interface EventParticipantRepo extends JpaRepository<EventParticipant, Long> {
    Optional<EventParticipant> findByEventAndParticipant(Event event_id, Participant participant_id);
    List<EventParticipant> findByEvent(Event event_id);
    List<EventParticipant> findByEventAndPaymentVerifiedFalse(Event event);
    Optional<EventParticipant> findByReferenceCode(String referenceCode);
    List<EventParticipant> findByParticipant(Participant participant);
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
mvn clean package -DskipTests -f BES/pom.xml
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES/src/main/java/com/example/BES/respositories/EventParticipantRepo.java
git -C /Users/bennylim/Documents/BES commit -m "feat: add findByParticipant query to EventParticipantRepo"
```

---

## Task 2: DTO — UpdateParticipantDto

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/UpdateParticipantDto.java`

- [ ] **Step 1: Create the DTO**

```java
package com.example.BES.dtos;

import java.util.List;

public class UpdateParticipantDto {
    public String name;
    public List<String> memberNames;
}
```

- [ ] **Step 2: Build to verify**

```bash
mvn clean package -DskipTests -f BES/pom.xml
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES/src/main/java/com/example/BES/dtos/UpdateParticipantDto.java
git -C /Users/bennylim/Documents/BES commit -m "feat: add UpdateParticipantDto"
```

---

## Task 3: Service — deleteParticipantFromEvent + updateParticipant

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`

### Step 1: Inject missing repos

- [ ] Open `EventGenreParticpantService.java`. Find the existing `@Autowired` block (around line 45). Add these three injections **after** the existing ones:

```java
@Autowired
ScoreRepo scoreRepo;

@Autowired
AuditionFeedbackRepository auditionFeedbackRepository;

@Autowired
EventParticipantTeamMemberRepo eventParticipantTeamMemberRepo;
```

- [ ] Add the missing imports at the top of the file (after existing imports):

```java
import com.example.BES.models.EventParticipantTeamMember;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.ScoreRepo;
import com.example.BES.dtos.UpdateParticipantDto;
```

### Step 2: Add deleteParticipantFromEvent

- [ ] Add this method at the end of the service class, **before** the closing `}`:

```java
@Transactional
public void deleteParticipantFromEvent(long participantId, long eventId) {
    Event event = eventRepo.findById(eventId).orElse(null);
    Participant participant = participantRepo.findById(participantId).orElse(null);
    if (event == null || participant == null) return;

    // 1. Delete Score + AuditionFeedback for every EGP of this participant in this event
    List<EventGenreParticipant> egps = repo.findByEventIdAndParticipantId(eventId, participantId);
    for (EventGenreParticipant egp : egps) {
        scoreRepo.deleteAll(scoreRepo.findByEventGenreParticipant(egp));
        auditionFeedbackRepository.deleteAll(auditionFeedbackRepository.findByEventGenreParticipant(egp));
    }

    // 2. Delete EventGenreParticipant records (releases audition numbers — rows simply removed)
    repo.deleteAll(egps);

    // 3. Delete EventParticipant + its team members
    EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);
    if (ep != null) {
        eventParticipantTeamMemberRepo.deleteByEventParticipant(ep);
        eventParticipantRepo.delete(ep);
    }

    // 4. Delete Participant only if no other EventParticipant references it
    List<EventParticipant> remaining = eventParticipantRepo.findByParticipant(participant);
    if (remaining.isEmpty()) {
        participantRepo.delete(participant);
    }
}
```

### Step 3: Add updateParticipant

- [ ] Add this method directly after `deleteParticipantFromEvent`, still before the closing `}`:

```java
@Transactional
public void updateParticipant(long participantId, long eventId, UpdateParticipantDto dto) {
    if (dto.name == null || dto.name.isBlank())
        throw new IllegalArgumentException("Name must not be empty");

    Event event = eventRepo.findById(eventId).orElse(null);
    Participant participant = participantRepo.findById(participantId).orElse(null);
    if (event == null || participant == null)
        throw new RuntimeException("Event or Participant not found");

    EventParticipant ep = eventParticipantRepo.findByEventAndParticipant(event, participant).orElse(null);

    boolean isTeam = ep != null && ep.getTeamName() != null && !ep.getTeamName().isBlank();

    // Duplicate check: no other participant in this event shares this name (case-insensitive)
    String trimmedName = dto.name.trim();
    if (isTeam) {
        boolean duplicate = eventParticipantRepo.findByEvent(event).stream()
            .filter(e -> !e.getParticipant().getParticipantId().equals(participantId))
            .anyMatch(e -> trimmedName.equalsIgnoreCase(e.getTeamName()));
        if (duplicate)
            throw new IllegalStateException("A team with this name already exists in this event");
    } else {
        boolean duplicate = eventParticipantRepo.findByEvent(event).stream()
            .filter(e -> !e.getParticipant().getParticipantId().equals(participantId))
            .anyMatch(e -> trimmedName.equalsIgnoreCase(e.getParticipant().getParticipantName()));
        if (duplicate)
            throw new IllegalStateException("A participant with this name already exists in this event");
    }

    if (isTeam) {
        // Update team name
        ep.setTeamName(trimmedName);
        ep.setDisplayName(trimmedName);
        eventParticipantRepo.save(ep);
        // Replace team members
        eventParticipantTeamMemberRepo.deleteByEventParticipant(ep);
        if (dto.memberNames != null) {
            List<EventParticipantTeamMember> newMembers = dto.memberNames.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(m -> new EventParticipantTeamMember(ep, m.trim()))
                .collect(java.util.stream.Collectors.toList());
            eventParticipantTeamMemberRepo.saveAll(newMembers);
        }
    } else {
        // Update solo name
        participant.setParticipantName(trimmedName);
        participantRepo.save(participant);
        if (ep != null) {
            ep.setDisplayName(trimmedName);
            eventParticipantRepo.save(ep);
        }
    }

    // Sync displayName on all EGP rows for this participant in this event
    List<EventGenreParticipant> egps = repo.findByEventIdAndParticipantId(eventId, participantId);
    for (EventGenreParticipant egp : egps) {
        egp.setDisplayName(trimmedName);
    }
    repo.saveAll(egps);
}
```

- [ ] **Step 4: Build to verify**

```bash
mvn clean package -DskipTests -f BES/pom.xml
```

Expected: `BUILD SUCCESS`. Fix any import or compile errors before continuing.

- [ ] **Step 5: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java
git -C /Users/bennylim/Documents/BES commit -m "feat: add deleteParticipantFromEvent and updateParticipant service methods"
```

---

## Task 4: Controller — two new endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Step 1: Add missing imports** (if not already present)

Find the import block in `EventController.java`. Add if missing:

```java
import com.example.BES.dtos.UpdateParticipantDto;
import org.springframework.web.bind.annotation.PutMapping;
```

- [ ] **Step 2: Add the DELETE endpoint**

Find the existing `@DeleteMapping("participant-genre/{participantId}/{eventId}/{eventGenreId}")` endpoint (around line 620). **After** its closing brace, add:

```java
@Operation(summary = "Delete Participant From Event", description = "Removes a participant from an event entirely — all genres, scores, feedback, team members")
@DeleteMapping("/participant/{participantId}/{eventId}")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> deleteParticipantFromEvent(
        @PathVariable Long participantId,
        @PathVariable Long eventId) {
    try {
        eventGenreParticipantService.deleteParticipantFromEvent(participantId, eventId);
        return ResponseEntity.ok(gson.toJson("Participant deleted"));
    } catch (Exception e) {
        log.error("Error deleting participant", e);
        return new ResponseEntity<>(gson.toJson("Error deleting participant"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

- [ ] **Step 3: Add the PUT endpoint**

Directly after the DELETE endpoint above, add:

```java
@Operation(summary = "Update Participant", description = "Updates participant name (solo) or team name + members (team)")
@PutMapping("/participant/{participantId}/{eventId}")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> updateParticipant(
        @PathVariable Long participantId,
        @PathVariable Long eventId,
        @RequestBody UpdateParticipantDto dto) {
    try {
        eventGenreParticipantService.updateParticipant(participantId, eventId, dto);
        return ResponseEntity.ok(gson.toJson("Participant updated"));
    } catch (IllegalArgumentException | IllegalStateException e) {
        return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.UNPROCESSABLE_ENTITY);
    } catch (Exception e) {
        log.error("Error updating participant", e);
        return new ResponseEntity<>(gson.toJson("Error updating participant"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

- [ ] **Step 4: Build and run backend tests**

```bash
mvn clean package -DskipTests -f BES/pom.xml
```

Expected: `BUILD SUCCESS`

```bash
mvn test -f BES/pom.xml
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES/src/main/java/com/example/BES/controllers/EventController.java
git -C /Users/bennylim/Documents/BES commit -m "feat: add DELETE and PUT participant endpoints to EventController"
```

---

## Task 5: Frontend API functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add the two functions**

Find `export const removeParticipantGenre` (around line 908). **After** its closing brace, add:

```js
export const deleteParticipantFromEvent = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant/${participantId}/${eventId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const updateParticipant = async (participantId, eventId, { name, memberNames = [] }) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant/${participantId}/${eventId}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, memberNames })
    })
  } catch (e) {
    console.log(e)
  }
}
```

- [ ] **Step 2: Lint check**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES-frontend/src/utils/api.js
git -C /Users/bennylim/Documents/BES commit -m "feat: add deleteParticipantFromEvent and updateParticipant API functions"
```

---

## Task 6: ConfirmModal.vue component

**Files:**
- Create: `BES-frontend/src/components/ConfirmModal.vue`

- [ ] **Step 1: Create the component**

```vue
<script setup>
defineProps({
  show:         { type: Boolean, default: false },
  title:        { type: String,  default: 'Confirm' },
  message:      { type: String,  default: '' },
  confirmLabel: { type: String,  default: 'Confirm' },
  variant:      { type: String,  default: 'danger' }  // 'danger' | 'warning'
})
const emit = defineEmits(['confirm', 'cancel'])
</script>

<template>
  <Teleport to="body">
    <div v-if="show" class="confirm-backdrop" @click.self="emit('cancel')">
      <div class="confirm-modal" role="dialog" aria-modal="true">
        <div class="confirm-header">
          <span class="type-label">{{ title }}</span>
        </div>
        <div class="confirm-body">
          <p class="type-body text-content-secondary">{{ message }}</p>
        </div>
        <div class="confirm-footer">
          <button class="btn-ghost" @click="emit('cancel')">Cancel</button>
          <button
            :class="variant === 'danger' ? 'btn-danger' : 'btn-warning'"
            @click="emit('confirm')"
          >{{ confirmLabel }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm-backdrop {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  padding: 16px;
}
.confirm-modal {
  background: var(--color-surface-800, #1a1a1a);
  border: 1px solid rgba(255,255,255,0.1);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  width: 100%; max-width: 440px;
  padding: 24px;
  display: flex; flex-direction: column; gap: 16px;
}
.confirm-header { border-bottom: 1px solid rgba(255,255,255,0.07); padding-bottom: 12px; }
.confirm-footer { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; }
.btn-ghost {
  background: none; border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.55); padding: 6px 16px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
.btn-danger {
  background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4);
  color: #f87171; padding: 6px 16px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
.btn-warning {
  background: rgba(245,158,11,0.15); border: 1px solid rgba(245,158,11,0.4);
  color: #fbbf24; padding: 6px 16px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
</style>
```

- [ ] **Step 2: Lint check**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES-frontend/src/components/ConfirmModal.vue
git -C /Users/bennylim/Documents/BES commit -m "feat: add reusable ConfirmModal component"
```

---

## Task 7: UpdateEventDetails.vue — full rewrite

**Files:**
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue`

This is a complete replacement of the file's content. Read the existing file first to note the `allJudges` fetch pattern, which is reused.

- [ ] **Step 1: Write the new component**

Replace the entire file with:

```vue
<script setup>
import { onMounted, ref, computed } from 'vue'
import { useAuthStore } from '@/utils/auth'
import CreateParticipantForm from '@/components/CreateParticipantForm.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import ActionDoneModal from './ActionDoneModal.vue'
import {
  getRegisteredParticipantsByEvent,
  deleteParticipantFromEvent,
  removeParticipantGenre,
  updateParticipant,
  updateParticipantsJudge
} from '@/utils/api'

const authStore   = useAuthStore()
const selectedEvent = computed(() => authStore.activeEvent?.name || localStorage.getItem('selectedEvent') || '')

// ── raw + grouped data ────────────────────────────────────────────────────
const allJudges   = ref([])
const rawRows     = ref([])   // GetEventGenreParticipantDto[]
const participants = computed(() => groupParticipants(rawRows.value))

// ── filter state ──────────────────────────────────────────────────────────
const search         = ref('')
const activeGenre    = ref('All')

const genreList = computed(() => {
  const names = [...new Set(rawRows.value.map(r => r.genreName))].sort()
  return ['All', ...names]
})

const genreCount = (genre) =>
  genre === 'All'
    ? participants.value.length
    : participants.value.filter(p => p.genres.some(g => g.genreName === genre)).length

const filtered = computed(() => {
  let list = participants.value
  if (activeGenre.value !== 'All')
    list = list.filter(p => p.genres.some(g => g.genreName === activeGenre.value))
  if (search.value.trim())
    list = list.filter(p => p.name.toLowerCase().includes(search.value.trim().toLowerCase()))
  return list
})

// ── expand state ──────────────────────────────────────────────────────────
const expanded = ref(new Set())
const toggle   = (id) => expanded.value.has(id) ? expanded.value.delete(id) : expanded.value.add(id)

// ── edit modal ────────────────────────────────────────────────────────────
const editTarget  = ref(null)   // { participantId, eventId, name, isTeam, memberNames, slotCount }
const editName    = ref('')
const editMembers = ref([])     // string[] fixed length
const editError   = ref('')
const showEdit    = ref(false)

const openEdit = (p) => {
  const slotCount = parseFormatSize(p.format)
  const isTeam    = slotCount >= 2
  editTarget.value  = { ...p, isTeam, slotCount }
  editName.value    = p.name
  editMembers.value = isTeam
    ? Array.from({ length: slotCount }, (_, i) => (p.memberNames[i] ?? ''))
    : []
  editError.value   = ''
  showEdit.value    = true
}

const validateEdit = () => {
  if (!editName.value.trim()) return 'Name is required'
  if (editTarget.value.isTeam) {
    for (let i = 0; i < editMembers.value.length; i++) {
      if (!editMembers.value[i].trim()) return `Member ${i + 1} is required`
    }
  }
  return ''
}

const submitEdit = async () => {
  const err = validateEdit()
  if (err) { editError.value = err; return }

  const res = await updateParticipant(
    editTarget.value.participantId,
    editTarget.value.eventId,
    { name: editName.value.trim(), memberNames: editTarget.value.isTeam ? editMembers.value.map(m => m.trim()) : [] }
  )
  if (!res) { editError.value = 'Network error'; return }
  if (res.status === 422) {
    const body = await res.json().catch(() => null)
    editError.value = body || 'Duplicate name'
    return
  }
  if (!res.ok) { editError.value = 'Save failed'; return }
  showEdit.value = false
  await reload()
  openToast('Participant updated')
}

// ── confirm delete ────────────────────────────────────────────────────────
const confirmState = ref({ show: false, title: '', message: '', onConfirm: null })

const confirmDeleteParticipant = (p) => {
  const genreList = p.genres.map(g => g.genreName).join(', ')
  confirmState.value = {
    show: true,
    title: 'Delete Participant',
    message: `Remove "${p.name}" from ${selectedEvent.value}? This will remove them from all genres (${genreList}) and release all audition numbers. This cannot be undone.`,
    onConfirm: async () => {
      await deleteParticipantFromEvent(p.participantId, p.eventId)
      confirmState.value.show = false
      await reload()
      openToast('Participant removed')
    }
  }
}

const confirmRemoveGenre = (p, genre) => {
  confirmState.value = {
    show: true,
    title: 'Remove from Genre',
    message: `Remove "${p.name}" from ${genre.genreName}? Their audition number will be released.`,
    onConfirm: async () => {
      await removeParticipantGenre(p.participantId, p.eventId, genre.eventGenreId)
      confirmState.value.show = false
      await reload()
      openToast('Genre removed')
    }
  }
}

// ── judge assignment ──────────────────────────────────────────────────────
const assignJudge = async (p, genre, judgeName) => {
  await updateParticipantsJudge(p.eventId, [
    { eventName: selectedEvent.value, participantName: p.name, genreName: genre.genreName, judgeName }
  ])
  await reload()
}

// ── toast ─────────────────────────────────────────────────────────────────
const toast        = ref('')
const showToast    = ref(false)
let   toastTimer   = null
const openToast = (msg) => {
  toast.value = msg; showToast.value = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { showToast.value = false }, 2500)
}

// ── add participant ───────────────────────────────────────────────────────
const showCreate = ref(false)

// ── data helpers ──────────────────────────────────────────────────────────
function isTeamFormat(fmt) {
  if (!fmt) return false
  return /^\d+v\d+$/i.test(fmt) && fmt.toLowerCase() !== '1v1'
}

function parseFormatSize(fmt) {
  if (!fmt) return 0
  const m = fmt.match(/^(\d+)v\d+$/i)
  return m ? parseInt(m[1]) : 0
}

function groupParticipants(rows) {
  const map = new Map()
  for (const row of rows) {
    if (!map.has(row.participantId)) {
      map.set(row.participantId, {
        participantId: row.participantId,
        eventId:       row.eventId,
        name:          row.participantName,
        format:        row.format,
        memberNames:   row.memberNames || [],
        genres: []
      })
    }
    map.get(row.participantId).genres.push({
      genreName:    row.genreName,
      eventGenreId: row.eventGenreId,
      judgeName:    row.judgeName || '',
      auditionNumber: row.auditionNumber
    })
  }
  return Array.from(map.values())
}

const reload = async () => {
  if (!selectedEvent.value) return
  const res = await getRegisteredParticipantsByEvent(selectedEvent.value)
  rawRows.value = Array.isArray(res) ? res : []
}

const fetchJudges = async () => {
  try {
    const res = await fetch('/api/v1/event/judges', { credentials: 'include' })
    if (!res.ok) return
    const data = await res.json()
    allJudges.value = Object.values(data).map(j => j.judgeName)
  } catch { /* silent */ }
}

onMounted(async () => {
  await reload()
  await fetchJudges()
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10">

      <!-- Page header -->
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
        <div>
          <div class="type-page-title mb-1">Participants</div>
          <p class="type-label text-content-muted">Manage entries, edit names, assign judges</p>
        </div>
        <button
          @click="showCreate = true"
          class="bg-accent para-chip type-label text-surface-900 px-4 py-2 flex items-center gap-2"
        >
          <i class="pi pi-plus text-xs"></i> Add Participant
        </button>
      </div>

      <!-- Filter bar -->
      <div class="para-chip p-4 mb-6">
        <input
          v-model="search"
          class="search-input"
          placeholder="Search name..."
        />
        <div class="genre-chips">
          <button
            v-for="genre in genreList"
            :key="genre"
            :class="['genre-chip', activeGenre === genre && 'active']"
            @click="activeGenre = genre"
          >
            {{ genre }}
            <span class="count-badge">{{ genreCount(genre) }}</span>
          </button>
        </div>
        <div class="flex items-center gap-2 mt-3 pt-3 border-t border-surface-600/30">
          <i class="pi pi-users text-content-muted text-sm"></i>
          <span class="type-label text-content-muted">
            Showing <span class="text-accent">{{ filtered.length }}</span>
            of <span class="text-accent">{{ participants.length }}</span> participants
          </span>
        </div>
      </div>

      <!-- Table -->
      <div class="section-rule mb-4">
        <span class="section-rule-label">Participants</span>
        <div class="section-rule-line"></div>
      </div>

      <div v-if="filtered.length === 0" class="empty-state">
        <i class="pi pi-users text-2xl text-content-muted mb-2"></i>
        <p class="type-body text-content-secondary">No participants found</p>
      </div>

      <div v-else class="participant-table">
        <div class="pt-header">
          <div class="pt-col-expand"></div>
          <div class="pt-col-name">Name</div>
          <div class="pt-col-format">Format</div>
          <div class="pt-col-genres">Genres</div>
          <div class="pt-col-actions"></div>
        </div>

        <template v-for="p in filtered" :key="p.participantId">
          <!-- Participant row -->
          <div class="pt-row">
            <button class="expand-btn" @click="toggle(p.participantId)">
              {{ expanded.has(p.participantId) ? '▾' : '▸' }}
            </button>
            <div class="pt-col-name">
              <span class="participant-name">{{ p.name }}</span>
            </div>
            <div class="pt-col-format">
              <span :class="['format-badge', isTeamFormat(p.format) ? 'team' : 'solo']">
                {{ p.format || 'Solo' }}
              </span>
            </div>
            <div class="pt-col-genres">
              <span
                v-for="g in p.genres"
                :key="g.genreName"
                class="genre-pill"
              >{{ g.genreName }}</span>
            </div>
            <div class="pt-col-actions">
              <button class="btn-action" @click="openEdit(p)">
                <i class="pi pi-pencil"></i> Edit
              </button>
              <button class="btn-action danger" @click="confirmDeleteParticipant(p)">
                <i class="pi pi-trash"></i> Delete
              </button>
            </div>
          </div>

          <!-- Genre sub-rows -->
          <template v-if="expanded.has(p.participantId)">
            <div
              v-for="genre in p.genres"
              :key="genre.genreName"
              class="pt-subrow"
            >
              <span class="subrow-indent">↳</span>
              <span class="genre-pill">{{ genre.genreName }}</span>
              <span class="subrow-label">Judge:</span>
              <select
                class="judge-select"
                :value="genre.judgeName"
                @change="assignJudge(p, genre, $event.target.value)"
              >
                <option value="">—</option>
                <option v-for="j in allJudges" :key="j" :value="j">{{ j }}</option>
              </select>
              <span v-if="genre.auditionNumber" class="audition-num">#{{ genre.auditionNumber }}</span>
              <button
                class="btn-remove-genre"
                @click="confirmRemoveGenre(p, genre)"
              >Remove Genre</button>
            </div>
          </template>
        </template>
      </div>

    </div><!-- end relative z-10 -->
  </div>

  <!-- Edit modal -->
  <Teleport to="body">
    <div v-if="showEdit" class="modal-backdrop" @click.self="showEdit = false">
      <div class="edit-modal">
        <div class="modal-header">
          <span class="type-label">{{ editTarget?.isTeam ? 'Edit Team' : 'Edit Participant' }}</span>
          <button class="modal-close" @click="showEdit = false">✕</button>
        </div>

        <div class="modal-body">
          <div class="field">
            <label class="field-label">
              {{ editTarget?.isTeam ? 'Team Name' : 'Name' }}
              <span class="required">*</span>
            </label>
            <input
              v-model="editName"
              class="field-input"
              :placeholder="editTarget?.isTeam ? 'Team name' : 'Participant name'"
            />
          </div>

          <template v-if="editTarget?.isTeam">
            <div class="members-label">Members</div>
            <div
              v-for="(_, i) in editMembers"
              :key="i"
              class="field"
            >
              <label class="field-label">
                Member {{ i + 1 }} <span class="required">*</span>
              </label>
              <input
                v-model="editMembers[i]"
                class="field-input"
                :placeholder="`Member ${i + 1}`"
              />
            </div>
          </template>

          <p v-if="editError" class="field-error">{{ editError }}</p>
        </div>

        <div class="modal-footer">
          <button class="btn-ghost" @click="showEdit = false">Cancel</button>
          <button class="btn-primary" @click="submitEdit">Save</button>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Confirm modal -->
  <ConfirmModal
    :show="confirmState.show"
    :title="confirmState.title"
    :message="confirmState.message"
    confirm-label="Confirm"
    variant="danger"
    @confirm="confirmState.onConfirm && confirmState.onConfirm()"
    @cancel="confirmState.show = false"
  />

  <!-- Add participant -->
  <CreateParticipantForm
    :event="selectedEvent"
    :show="showCreate"
    title="New participant entry"
    @createNewEntry="showCreate = false; reload()"
    @close="showCreate = false"
  />

  <!-- Toast -->
  <Transition name="toast">
    <div v-if="showToast" class="toast">{{ toast }}</div>
  </Transition>
</template>

<style scoped>
/* ── Filter bar ───────────────────────────────────────── */
.search-input {
  width: 100%; max-width: 280px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.8);
  padding: 7px 12px; font-size: 12px;
  margin-bottom: 12px;
}
.search-input::placeholder { color: rgba(255,255,255,0.25); }
.genre-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.genre-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 12px; font-size: 10px; letter-spacing: 0.1em; text-transform: uppercase;
  border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);
  color: rgba(255,255,255,0.5); cursor: pointer;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  transition: all 0.15s;
}
.genre-chip.active {
  background: rgba(255,255,255,0.12); border-color: rgba(255,255,255,0.35);
  color: rgba(255,255,255,0.9);
}
.count-badge {
  background: rgba(255,255,255,0.1); color: rgba(255,255,255,0.5);
  font-size: 9px; padding: 0 4px; border-radius: 2px;
}

/* ── Table ────────────────────────────────────────────── */
.participant-table { display: flex; flex-direction: column; gap: 2px; }
.pt-header {
  display: grid;
  grid-template-columns: 32px 1fr auto auto auto;
  gap: 8px; align-items: center;
  padding: 6px 12px;
  font-size: 9px; letter-spacing: 0.15em; text-transform: uppercase;
  color: rgba(255,255,255,0.3);
  border-bottom: 1px solid rgba(255,255,255,0.07);
}
.pt-row {
  display: grid;
  grid-template-columns: 32px 1fr auto auto auto;
  gap: 8px; align-items: center;
  padding: 10px 12px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  transition: background 0.15s;
}
.pt-row:hover { background: rgba(255,255,255,0.05); }
.pt-subrow {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 8px 12px 8px 44px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.04);
  border-top: none; font-size: 12px;
}
.expand-btn {
  background: none; border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.45); font-size: 10px;
  width: 24px; height: 24px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.participant-name {
  font-size: 13px; letter-spacing: 0.06em; text-transform: uppercase;
  color: rgba(255,255,255,0.85);
}
.format-badge {
  font-size: 9px; letter-spacing: 0.12em; text-transform: uppercase;
  padding: 2px 8px;
}
.format-badge.solo {
  background: rgba(99,102,241,0.12); border: 1px solid rgba(99,102,241,0.3);
  color: #a5b4fc;
}
.format-badge.team {
  background: rgba(20,184,166,0.12); border: 1px solid rgba(20,184,166,0.3);
  color: #5eead4;
}
.genre-pill {
  display: inline-block; padding: 1px 8px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  font-size: 9px; text-transform: uppercase; letter-spacing: 0.1em;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
}
.pt-col-actions { display: flex; gap: 4px; flex-shrink: 0; }
.btn-action {
  background: none; border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.5); font-size: 10px; letter-spacing: 0.08em;
  padding: 4px 10px; cursor: pointer; display: flex; align-items: center; gap: 4px;
  text-transform: uppercase;
}
.btn-action:hover { border-color: rgba(255,255,255,0.25); color: rgba(255,255,255,0.8); }
.btn-action.danger { border-color: rgba(239,68,68,0.2); color: rgba(248,113,113,0.6); }
.btn-action.danger:hover { border-color: rgba(239,68,68,0.5); color: #f87171; }
.subrow-indent { color: rgba(255,255,255,0.25); }
.subrow-label { font-size: 11px; color: rgba(255,255,255,0.3); }
.judge-select {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.7); font-size: 11px; padding: 3px 6px;
}
.audition-num { font-size: 10px; color: rgba(255,255,255,0.3); letter-spacing: 0.08em; }
.btn-remove-genre {
  margin-left: auto; background: none;
  border: 1px solid rgba(239,68,68,0.2); color: rgba(248,113,113,0.55);
  font-size: 10px; letter-spacing: 0.08em; text-transform: uppercase;
  padding: 3px 8px; cursor: pointer;
}
.btn-remove-genre:hover { border-color: rgba(239,68,68,0.45); color: #f87171; }

/* ── Empty state ──────────────────────────────────────── */
.empty-state {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 60px 0; text-align: center;
}

/* ── Edit modal ───────────────────────────────────────── */
.modal-backdrop {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center; padding: 16px;
}
.edit-modal {
  background: var(--color-surface-800, #1a1a1a);
  border: 1px solid rgba(255,255,255,0.1);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  width: 100%; max-width: 480px;
  display: flex; flex-direction: column;
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid rgba(255,255,255,0.07);
}
.modal-close {
  background: none; border: none; color: rgba(255,255,255,0.4);
  font-size: 13px; cursor: pointer;
}
.modal-body { padding: 20px; display: flex; flex-direction: column; gap: 12px; }
.modal-footer {
  display: flex; gap: 8px; justify-content: flex-end;
  padding: 12px 20px; border-top: 1px solid rgba(255,255,255,0.07);
}
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label {
  font-size: 10px; letter-spacing: 0.15em; text-transform: uppercase;
  color: rgba(255,255,255,0.4);
}
.required { color: #f87171; margin-left: 2px; }
.field-input {
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.85); padding: 8px 10px; font-size: 13px;
}
.field-input:focus { outline: none; border-color: rgba(255,255,255,0.3); }
.members-label {
  font-size: 10px; letter-spacing: 0.18em; text-transform: uppercase;
  color: rgba(255,255,255,0.25); padding-top: 4px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
.field-error { font-size: 11px; color: #f87171; margin-top: -4px; }
.btn-ghost {
  background: none; border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.55); padding: 7px 18px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
.btn-primary {
  background: rgba(255,255,255,0.9); color: #111; border: none;
  padding: 7px 18px; font-size: 11px; letter-spacing: 0.1em;
  text-transform: uppercase; cursor: pointer;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}

/* ── Toast ────────────────────────────────────────────── */
.toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.85); padding: 10px 20px; font-size: 12px;
  letter-spacing: 0.1em; text-transform: uppercase; z-index: 300;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}
.toast-enter-active, .toast-leave-active { transition: opacity 0.2s, transform 0.2s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(8px); }

/* ── Mobile ───────────────────────────────────────────── */
@media (max-width: 640px) {
  .pt-header { display: none; }
  .pt-row {
    grid-template-columns: 32px 1fr;
    grid-template-rows: auto auto;
  }
  .pt-col-format, .pt-col-genres { grid-column: 2; }
  .pt-col-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .pt-subrow { padding-left: 12px; }
}
</style>
```

- [ ] **Step 2: Verify the import for `updateParticipantsJudge` exists in api.js**

```bash
grep -n "updateParticipantsJudge\|participants-judge" /Users/bennylim/Documents/BES/BES-frontend/src/utils/api.js | head -5
```

If the function is named differently (e.g., `updateParticipantJudge`), update the import name in the component to match.

- [ ] **Step 3: Frontend build check**

```bash
npm run build
```

Expected: no errors (chunk size warning is OK).

- [ ] **Step 4: Lint check**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git -C /Users/bennylim/Documents/BES add BES-frontend/src/views/UpdateEventDetails.vue
git -C /Users/bennylim/Documents/BES commit -m "feat: rewrite UpdateEventDetails — expandable table, edit modal, two-level delete"
```

---

## Task 8: Local Docker verify

- [ ] **Step 1: Rebuild backend and frontend**

```bash
docker compose stop frontend backend
docker compose rm -f frontend backend
docker compose build --no-cache frontend backend
docker compose up -d
```

- [ ] **Step 2: Verify all containers running**

```bash
docker compose ps
```

Expected: `bes_backend`, `bes_frontend`, `bes-postgres-1` all `Up`.

- [ ] **Step 3: Smoke test in browser**

Open `http://localhost/event/update-event-details` and verify:
- Genre chips appear, search input filters by name
- Expanding a row shows genre sub-rows with judge selects
- Edit modal opens with correct fields (solo: name only; team: team name + N member fields)
- Delete participant shows confirm dialog before deleting
- Remove Genre on sub-row shows confirm dialog before removing
- Judge assignment change auto-saves

- [ ] **Step 4: Commit if any fixes were needed**

```bash
git -C /Users/bennylim/Documents/BES add -A
git -C /Users/bennylim/Documents/BES commit -m "fix: post-docker verification fixes"
```

---

## Self-Review

**Spec coverage:**
- ✅ Filter by genre (chips) + name search
- ✅ One row per participant, genres joined as pills
- ✅ Expandable sub-rows per genre with judge + Remove Genre
- ✅ Edit modal — solo (name only) / team (name + N fixed member slots)
- ✅ Format-driven member slot count (`parseFormatSize`)
- ✅ Duplicate check for name / team name (not members)
- ✅ Empty field validation
- ✅ Delete participant — cascade (Score → AuditionFeedback → EGP → TeamMember → EventParticipant → Participant if orphan)
- ✅ Remove genre — uses existing `removeParticipantFromGenre` service
- ✅ Confirm prompts on all destructive actions
- ✅ Mobile responsive (single-column grid on ≤640px)
- ✅ Modal max-width 480px (not full-screen)
- ✅ Member names informational — no uniqueness check

**Type consistency:** `updateParticipant(participantId, eventId, { name, memberNames })` matches api.js and service. `deleteParticipantFromEvent(participantId, eventId)` consistent throughout.

**Placeholder scan:** None found.
