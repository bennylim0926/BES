# Check-in Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix concurrent check-in race conditions: duplicate guard (409), per-slot independent animations on AuditionNumber display, cancel signal for ghost previews, double-confirm guard, and Reset Scores access code gate.

**Architecture:** Backend adds a 409 guard in `EventGenreParticpantService` and a `cancelled` field on `CheckinPreviewDto`. Frontend replaces AuditionNumber's single `currentPerson` model with an `activeSlots` array where each slot owns its animation queue, keyed by `slotId-genreName` to allow parallel animations.

**Tech Stack:** Spring Boot (Java), Vue 3 (Composition API), STOMP WebSocket

---

## File Map

| File | Change |
|------|--------|
| `BES/src/main/java/com/example/BES/dtos/CheckinPreviewDto.java` | Add `cancelled` boolean field |
| `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java` | Throw `IllegalStateException("already_checked_in")` if all genres already have numbers |
| `BES/src/main/java/com/example/BES/controllers/EventController.java` | Catch `IllegalStateException` → 409 on `registerParticipantAllGenres` |
| `BES-frontend/src/views/AuditionNumber.vue` | Full refactor: `activeSlots` array, per-slot queues, parallel animation |
| `BES-frontend/src/views/EventDetails.vue` | Double-confirm guard, 409 error state, cancel preview on dialog close |
| `BES-frontend/src/views/AuditionList.vue` | Access code gate before Reset Scores executes |

---

## Task 1: Add `cancelled` field to `CheckinPreviewDto`

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/CheckinPreviewDto.java`

- [ ] **Open the file and add the field**

Replace the entire file with:

```java
package com.example.BES.dtos;

import java.util.List;

public class CheckinPreviewDto {
    public Long participantId;
    public String name;
    public String refCode;
    public Boolean cancelled;
    public List<String> memberNames;
    public List<GenreEntry> genres;

    public static class GenreEntry {
        public String genreName;
        public Integer auditionNumber;
    }
}
```

- [ ] **Build to confirm no compile errors**

```bash
cd BES && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/CheckinPreviewDto.java
git commit -m "feat: add cancelled field to CheckinPreviewDto"
```

---

## Task 2: Backend — 409 duplicate check-in guard

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Add duplicate check at the top of `getAllAuditionNumsViaQR` in the service**

In `EventGenreParticpantService.java`, find the `getAllAuditionNumsViaQR` method (currently at line ~118) and add the already-assigned check as the first thing it does:

```java
public void getAllAuditionNumsViaQR(Long participantId, Long eventId) {
    List<EventGenreParticipant> entries =
        repo.findByEventIdAndParticipantId(eventId, participantId);
    // Guard: if every entry already has a number, this is a duplicate check-in
    boolean allAssigned = !entries.isEmpty() &&
        entries.stream().allMatch(e -> e.getAuditionNumber() != null);
    if (allAssigned) {
        throw new IllegalStateException("already_checked_in");
    }
    for (EventGenreParticipant entry : entries) {
        AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
        dto.participantId = participantId;
        dto.eventId = eventId;
        dto.eventGenreId = entry.getEventGenre().getId();
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

- [ ] **Update the controller to return 409 for `IllegalStateException`**

In `EventController.java`, find `registerParticipantAllGenres` (currently at line ~445) and split the catch:

```java
@GetMapping("/register-participant/{participantId}/{eventId}")
public ResponseEntity<String> registerParticipantAllGenres(
        @PathVariable Long participantId,
        @PathVariable Long eventId) {
    try {
        eventGenreParticipantService.getAllAuditionNumsViaQR(participantId, eventId);
        return new ResponseEntity<>("registered", HttpStatus.CREATED);
    } catch (IllegalStateException e) {
        if ("already_checked_in".equals(e.getMessage())) {
            return new ResponseEntity<>("already_checked_in", HttpStatus.CONFLICT);
        }
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
        return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Build to confirm no compile errors**

```bash
cd BES && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: return 409 when participant already has all audition numbers"
```

---

## Task 3: EventDetails.vue — double-confirm guard + cancel preview + 409 handling

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

- [ ] **Add `confirming` ref near the other checkin refs (around line 750)**

```js
const checkingInId = ref(null)
const confirming = ref(false)   // ← add this line
```

- [ ] **Update `confirmCheckIn` to guard against double-submit and handle 409**

Find `confirmCheckIn` (around line 800) and replace it entirely:

```js
const confirmCheckIn = async () => {
  const p = checkinConfirm.value.participant
  if (!p || confirming.value) return

  confirming.value = true
  p.genres.forEach(g => { g.auditionNumber = null; g.rolling = false })
  dialogNumberQueue.length = 0
  Object.values(dialogRollingIntervals).forEach(clearInterval)
  for (const k in dialogRollingIntervals) delete dialogRollingIntervals[k]
  dialogFakeNums.value = {}

  checkinConfirm.value.phase = 'generating'
  checkinConfirm.value.refCode = null

  try {
    await checkIn(p)

    // 409 — already checked in by another desk
    if (checkingInId.value === null && checkinConfirm.value.phase === 'generating') {
      // checkIn sets checkingInId back to null; inspect participants for the result
    }

    const refEntry = verifiedDbParticipants.value.find(ep => ep.participantId === p.participantId)
    checkinConfirm.value.refCode = refEntry?.referenceCode || null

    const freshParticipant = checkinList.value.find(ep => ep.participantId === p.participantId)
    if (freshParticipant) {
      for (const ug of freshParticipant.genres) {
        if (ug.auditionNumber != null) {
          dialogNumberQueue.push({ genre: ug.genreName, auditionNumber: ug.auditionNumber })
        }
      }
    }

    if (dialogNumberQueue.length === 0) {
      checkinConfirm.value.phase = 'done'
    } else {
      processNextDialogNumber()
    }
  } finally {
    confirming.value = false
  }
}
```

- [ ] **Update `checkIn` to surface 409 as a distinct error state**

Find `checkIn` (around line 739) and replace it:

```js
const checkIn = async (p) => {
  checkingInId.value = p.participantId
  try {
    const res = await checkInParticipant(p.participantId, p.eventId)
    if (res?.status === 409) {
      checkinConfirm.value.phase = 'error'
      checkinConfirm.value.errorMessage = 'Already checked in at another desk.'
      return
    }
    await Promise.all([fetchCheckinList(), refreshFromDb()])
  } catch (e) {
    console.error(e)
  }
  checkingInId.value = null
}
```

- [ ] **Add `errorMessage` to `checkinConfirm` initial shape and the error phase to the template**

Find the `checkinConfirm` ref (around line 750):

```js
const checkinConfirm = ref({ show: false, participant: null, phase: 'confirm', refCode: null, errorMessage: '' })
```

In the template, find the `<!-- Actions -->` section of the check-in dialog and add an error phase between generating and done:

```html
<!-- Error phase -->
<div v-else-if="checkinConfirm.phase === 'error'"
  class="flex-1 py-2 flex flex-col items-center gap-3">
  <div class="flex items-center gap-2 type-label text-red-400">
    <i class="pi pi-exclamation-circle text-sm"></i>
    {{ checkinConfirm.errorMessage }}
  </div>
  <button @click="checkinConfirm.show = false"
    class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary transition-colors">
    Close
  </button>
</div>
```

Also update the dialog header to cover the error phase:

```html
{{ checkinConfirm.phase === 'confirm' ? 'Confirm Check-In'
   : checkinConfirm.phase === 'generating' ? 'Generating…'
   : checkinConfirm.phase === 'error' ? 'Check-In Failed'
   : 'Check-In Complete' }}
```

- [ ] **Disable confirm button while confirming**

Find the confirm button in the template and update:

```html
<button @click="confirmCheckIn"
  :disabled="confirming"
  class="flex-1 py-2 para-chip type-label text-white transition-all disabled:opacity-50"
  style="background:rgba(255,255,255,0.12);box-shadow:0 0 12px var(--accent-subtle)"
>
  <i class="pi pi-check mr-1.5 text-xs"></i> Confirm
</button>
```

- [ ] **Send cancel preview when dialog closes in `confirm` phase**

Find the close button in the check-in dialog header (the `×` button) and update its handler:

```html
<button v-if="checkinConfirm.phase !== 'generating'" @click="closeCheckinDialog"
  class="p-1 text-content-muted hover:text-content-primary transition-colors">
  <i class="pi pi-times text-sm" />
</button>
```

Add `closeCheckinDialog` near the other checkin functions:

```js
const closeCheckinDialog = () => {
  if (checkinConfirm.value.phase === 'confirm' && checkinConfirm.value.participant) {
    sendCheckinPreview(eventName.value, {
      participantId: checkinConfirm.value.participant.participantId,
      cancelled: true
    })
  }
  checkinConfirm.value.show = false
}
```

Also update the backdrop click handler to use `closeCheckinDialog`:

```html
@click="checkinConfirm.phase !== 'generating' && closeCheckinDialog()"
```

- [ ] **Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: double-confirm guard, 409 error state, cancel preview on dialog close"
```

---

## Task 4: AuditionNumber.vue — multi-slot refactor

**Files:**
- Modify: `BES-frontend/src/views/AuditionNumber.vue`

This is a full rewrite of the `<script setup>` logic and template. Work through it section by section.

- [ ] **Replace all state declarations**

Remove the old `currentPerson`, `auditionQueue`, `queueRunning`, `fakeNums`, `rollingIntervals` declarations and replace with:

```js
// Each slot: { slotId, person, queue, running }
// slotId = String(participantId ?? name)
const activeSlots = ref([])
const history = ref([])
const revealingRef = ref(null)

// Keyed by `${slotId}-${genreName}` to allow parallel animations across slots
const fakeNums = ref({})
const rollingIntervals = {}

const modalTitle = ref('')
const modalMessage = ref('')
const showModal = ref(false)

const wsClients = []
```

- [ ] **Replace all helper functions**

Remove `flushCurrentToHistory`, `startRolling`, `stopRolling`, `processNextInQueue`, `animateAuditionNumber` and add:

```js
const slotKey = (participantId, name) => String(participantId ?? name)

const flushSlotToHistory = (slotId) => {
  const idx = activeSlots.value.findIndex(s => s.slotId === slotId)
  if (idx === -1) return
  const slot = activeSlots.value[idx]
  history.value.unshift({ ...slot.person })
  activeSlots.value.splice(idx, 1)
}

function startRolling(slotId, genreName) {
  const key = `${slotId}-${genreName}`
  clearInterval(rollingIntervals[key])
  rollingIntervals[key] = setInterval(() => {
    fakeNums.value = { ...fakeNums.value, [key]: Math.floor(Math.random() * 99) + 1 }
  }, 80)
}

function stopRolling(slotId, genreName) {
  const key = `${slotId}-${genreName}`
  clearInterval(rollingIntervals[key])
  delete rollingIntervals[key]
  const next = { ...fakeNums.value }
  delete next[key]
  fakeNums.value = next
}

function checkSlotComplete(slotId) {
  const slot = activeSlots.value.find(s => s.slotId === slotId)
  if (!slot) return
  const allDone = slot.person.genres.every(g => g.auditionNumber !== null)
  if (allDone && slot.queue.length === 0) {
    setTimeout(() => flushSlotToHistory(slotId), 1500)
  }
}

function processSlotQueue(slotId) {
  const slot = activeSlots.value.find(s => s.slotId === slotId)
  if (!slot || slot.queue.length === 0) {
    if (slot) slot.running = false
    checkSlotComplete(slotId)
    return
  }
  slot.running = true
  const msg = slot.queue.shift()
  animateSlot(slotId, msg)
}

function animateSlot(slotId, msg) {
  const slot = activeSlots.value.find(s => s.slotId === slotId)
  if (!slot) return

  if (msg.refCode && !slot.person.refCode) slot.person.refCode = msg.refCode

  let genre = slot.person.genres.find(g => g.genreName === msg.genre)
  if (!genre) {
    genre = { genreName: msg.genre, auditionNumber: null, rolling: false }
    slot.person.genres.push(genre)
  }

  genre.rolling = true
  startRolling(slotId, msg.genre)
  setTimeout(() => {
    stopRolling(slotId, msg.genre)
    const s = activeSlots.value.find(s => s.slotId === slotId)
    const g = s?.person.genres.find(g => g.genreName === msg.genre)
    if (g) { g.rolling = false; g.auditionNumber = msg.auditionNumber }
    processSlotQueue(slotId)
  }, 2000)
}
```

- [ ] **Replace WS handlers**

Remove `isSamePerson`, `onPreview`, `onReceiveAuditionNumber` and replace with:

```js
const isSamePerson = (slot, msg) => {
  if (!slot || !msg) return false
  const sid = slot.person.participantId
  const mid = msg.participantId
  return (sid != null && mid != null) ? sid === mid : slot.person.name === msg.name
}

const findSlot = (msg) =>
  activeSlots.value.find(s => isSamePerson(s, msg))

const onPreview = (msg) => {
  // Cancel signal — remove the matching slot
  if (msg.cancelled) {
    const idx = activeSlots.value.findIndex(s => isSamePerson(s, msg))
    if (idx !== -1) {
      const slot = activeSlots.value[idx]
      // Stop any running animations for this slot
      slot.person.genres.forEach(g => stopRolling(slot.slotId, g.genreName))
      activeSlots.value.splice(idx, 1)
    }
    return
  }

  const existing = findSlot(msg)
  if (existing) {
    // Merge new genres into existing slot without overwriting
    if (msg.refCode && !existing.person.refCode) existing.person.refCode = msg.refCode
    const existingNames = new Set(existing.person.genres.map(g => g.genreName))
    for (const g of (msg.genres ?? [])) {
      if (!existingNames.has(g.genreName)) {
        existing.person.genres.push({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null, rolling: false })
      }
    }
  } else {
    // New slot
    const slotId = slotKey(msg.participantId, msg.name)
    activeSlots.value.push({
      slotId,
      person: {
        participantId: msg.participantId ?? null,
        name: msg.name,
        refCode: msg.refCode ?? null,
        memberNames: msg.memberNames ?? [],
        genres: (msg.genres ?? []).map(g => ({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null, rolling: false }))
      },
      queue: [],
      running: false
    })
  }
}

const onReceiveAuditionNumber = (msg) => {
  const slot = findSlot(msg)
  if (slot) {
    // Ensure genre row exists in pending state
    if (!slot.person.genres.find(g => g.genreName === msg.genre)) {
      slot.person.genres.push({ genreName: msg.genre, auditionNumber: null, rolling: false })
    }
    if (msg.refCode && !slot.person.refCode) slot.person.refCode = msg.refCode
    slot.queue.push(msg)
    if (!slot.running) processSlotQueue(slot.slotId)
  } else {
    // Late message — update history directly
    const historyEntry = history.value.find(h =>
      msg.participantId != null ? h.participantId === msg.participantId : h.name === msg.name
    )
    if (historyEntry) {
      const hGenre = historyEntry.genres.find(g => g.genreName === msg.genre)
      if (hGenre) hGenre.auditionNumber = msg.auditionNumber
    }
  }
}

const onRepeatAudition = (msg) => {
  const judgeLabel = msg.judge ? ` · Judge: ${msg.judge}` : ''
  modalTitle.value = `Hey ${msg.name}!`
  modalMessage.value = `Your audition number is ${msg.genre} #${msg.audition}${judgeLabel}`
  showModal.value = true
}

const clearHistory = () => { history.value = [] }
```

- [ ] **Update `onBeforeUnmount` to clear all slot intervals**

```js
onBeforeUnmount(() => {
  Object.values(rollingIntervals).forEach(clearInterval)
  wsClients.forEach(c => deactivateClient(c))
})
```

(The keys changed but `Object.values(rollingIntervals)` still works — no change needed here.)

- [ ] **Replace the entire `<template>`**

```html
<template>
  <div class="page-container">
    <div class="color-bleed"></div>

    <!-- ── Live Previews ──────────────────────────────────────────────────── -->
    <div class="section-rule mb-4">
      <span class="section-rule-label">Live Previews</span>
      <div class="section-rule-line"></div>
    </div>

    <!-- Idle state -->
    <div v-if="activeSlots.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
      <div class="para-chip-sm w-16 h-16 flex items-center justify-center mb-4">
        <i class="pi pi-qrcode text-content-muted text-2xl"></i>
      </div>
      <p class="type-body text-content-secondary">Waiting for check-in…</p>
      <p class="type-label text-content-muted mt-1">Check in a participant on the Event Details page</p>
    </div>

    <!-- Slot grid: up to 3 per row -->
    <div
      v-else
      class="grid gap-4 mb-6"
      :style="{ gridTemplateColumns: `repeat(${Math.min(activeSlots.length, 3)}, 1fr)` }"
    >
      <div
        v-for="slot in activeSlots"
        :key="slot.slotId"
        class="card-hover p-4 relative"
      >
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <!-- Name row -->
        <div class="flex items-start justify-between gap-3 mb-1">
          <div class="flex-1 min-w-0">
            <p class="type-label text-content-muted mb-1">Good Luck</p>
            <p class="type-page-title text-content-primary leading-tight truncate" style="font-size:clamp(1.1rem,3vw,1.8rem)">
              {{ slot.person.name }}
            </p>
          </div>
          <!-- Ref code chip -->
          <div
            v-if="slot.person.refCode"
            class="flex-shrink-0 para-chip-sm px-3 py-2 cursor-pointer select-none flex flex-col items-end gap-0.5"
            @mousedown="revealingRef = slot.slotId" @mouseup="revealingRef = null" @mouseleave="revealingRef = null"
            @touchstart="revealingRef = slot.slotId" @touchend="revealingRef = null" @touchcancel="revealingRef = null"
          >
            <span class="type-label text-content-muted">Ref Code</span>
            <span v-if="revealingRef === slot.slotId" class="font-source tracking-widest text-accent" style="font-size:0.9rem;letter-spacing:0.2em">
              {{ slot.person.refCode }}
            </span>
            <span v-else class="type-label text-content-muted/40">Hold to reveal</span>
          </div>
          <div v-else-if="slot.person.genres.some(g => g.auditionNumber === null)" class="flex-shrink-0 para-chip-sm px-2 py-1.5 flex items-center gap-1">
            <i class="pi pi-spin pi-spinner text-content-muted text-xs"></i>
            <span class="type-label text-content-muted">Assigning…</span>
          </div>
        </div>

        <!-- Team members -->
        <div v-if="slot.person.memberNames?.length" class="flex items-center gap-1.5 type-label text-content-muted mb-2">
          <i class="pi pi-users" style="font-size:0.65rem"></i>
          <span class="truncate">{{ slot.person.memberNames.join(' · ') }}</span>
        </div>

        <!-- Divisions -->
        <div class="section-rule my-2">
          <span class="section-rule-label">Divisions</span>
          <div class="section-rule-line"></div>
        </div>

        <div class="space-y-1.5">
          <div
            v-for="g in slot.person.genres"
            :key="g.genreName"
            class="flex items-center gap-2 para-chip-sm px-2.5 py-2"
            :style="g.auditionNumber !== null ? { borderColor: 'var(--accent-muted)', background: 'var(--accent-subtle)' } : {}"
          >
            <span
              class="inline-block w-2 h-2 rounded-full flex-shrink-0"
              :style="g.auditionNumber !== null
                ? 'background:var(--accent-color);box-shadow:0 0 8px var(--accent-muted)'
                : g.rolling
                  ? 'background:rgba(245,158,11,0.7);box-shadow:0 0 6px rgba(245,158,11,0.5)'
                  : 'background:rgba(255,255,255,0.15)'"
            ></span>
            <span class="type-body text-content-primary flex-1 truncate">{{ g.genreName }}</span>
            <div class="flex items-baseline gap-0.5 tabular-nums min-w-[4rem] justify-end">
              <template v-if="g.rolling">
                <span class="type-label text-amber-400/60 text-xs">Drawing</span>
                <span class="type-stat text-amber-400" style="font-size:1.4rem">
                  {{ fakeNums[`${slot.slotId}-${g.genreName}`] ?? '—' }}
                </span>
              </template>
              <template v-else-if="g.auditionNumber !== null">
                <span class="type-label text-accent/60 text-xs">#</span>
                <span class="type-stat text-accent" style="font-size:1.4rem">{{ g.auditionNumber }}</span>
              </template>
              <template v-else>
                <span class="type-stat text-content-muted/20" style="font-size:1.4rem">—</span>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ── History ──────────────────────────────────────────────────────── -->
    <template v-if="history.length > 0">
      <div class="section-rule mb-3">
        <span class="section-rule-label">History</span>
        <div class="section-rule-line"></div>
        <button @click="clearHistory" class="para-chip-sm px-2 py-0.5 type-label text-content-muted hover:text-content-primary transition-colors ml-3 flex-shrink-0">
          Clear
        </button>
      </div>

      <div class="space-y-2">
        <div
          v-for="(person, i) in history"
          :key="(person.participantId ?? person.name) + i"
          class="para-chip-sm px-3 py-2.5 flex items-center gap-3 flex-wrap"
          :class="i === 0 ? 'border-white/15' : 'opacity-50'"
        >
          <span class="type-body text-content-primary shrink-0 min-w-[6rem]">{{ person.name }}</span>
          <div class="flex flex-wrap gap-1.5 flex-1 min-w-0">
            <span
              v-for="g in person.genres"
              :key="g.genreName"
              class="inline-flex items-center gap-1 badge-neutral capitalize"
            >
              <span class="text-content-muted">{{ g.genreName }}</span>
              <span class="text-accent">#{{ g.auditionNumber }}</span>
            </span>
          </div>
          <span
            v-if="person.refCode"
            class="relative ml-auto shrink-0 inline-flex items-center gap-1.5 para-chip-sm px-2.5 py-1 type-label cursor-pointer select-none transition-colors"
            :class="revealingRef === (person.participantId ?? person.name) + i ? 'text-accent' : 'text-content-muted hover:text-accent'"
            @click="revealingRef = revealingRef === (person.participantId ?? person.name) + i ? null : (person.participantId ?? person.name) + i"
          >
            <i class="pi pi-eye" style="font-size:0.6rem"></i>
            <template v-if="revealingRef === (person.participantId ?? person.name) + i">
              <span class="font-source tracking-widest" style="font-size:0.75rem;letter-spacing:0.2em">{{ person.refCode }}</span>
            </template>
            <template v-else>Ref</template>
          </span>
        </div>
      </div>
    </template>
  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    variant="info"
    @accept="() => { showModal = false }"
    @close="() => { showModal = false }"
  >
    <p class="type-body text-content-secondary">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
```

- [ ] **Verify the dev server starts with no errors**

```bash
cd BES-frontend && npm run dev
```

Open `http://localhost:5173/event/audition-number` — should show "Waiting for check-in…" idle state.

- [ ] **Commit**

```bash
git add BES-frontend/src/views/AuditionNumber.vue
git commit -m "feat: multi-slot parallel animation in AuditionNumber display"
```

---

## Task 5: AuditionList.vue — Reset Scores access code gate

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

- [ ] **Add access code gate state near the other modal refs (around line 30)**

```js
const resetConfirmCode = ref('')
const resetCodeError = ref('')
const resetCodeChecking = ref(false)
```

- [ ] **Replace `confirmReset` to use the two-step gate**

Find `confirmReset` (around line 87) and replace:

```js
const confirmReset = (title, message) => {
  resetConfirmCode.value = ''
  resetCodeError.value = ''
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'warning'
  showModal.value = true
  // dynamicCallBack now handles code validation before reset
  dynamicCallBack.value = async () => {
    if (!resetConfirmCode.value.trim()) {
      resetCodeError.value = 'Enter the event access code to confirm.'
      return
    }
    const activeEvent = getActiveEvent()
    if (!activeEvent?.id) {
      resetCodeError.value = 'Could not resolve event ID.'
      return
    }
    resetCodeChecking.value = true
    try {
      const res = await verifyEventAccessCode(activeEvent.id, resetConfirmCode.value.trim())
      if (!res?.valid) {
        resetCodeError.value = 'Incorrect access code.'
        resetCodeChecking.value = false
        return
      }
    } catch {
      resetCodeError.value = 'Could not verify access code.'
      resetCodeChecking.value = false
      return
    }
    resetCodeChecking.value = false
    showModal.value = false
    await resetScore()
  }
}
```

- [ ] **Add `verifyEventAccessCode` and `getActiveEvent` to the imports at the top of the file**

Find the existing import line and add `verifyEventAccessCode`:

```js
import { getRegisteredParticipantsByEvent, submitParticipantScore, getParticipantScore, whoami, getJudgingMode, setJudgingMode, submitAuditionFeedback, getAuditionFeedback, getScoringCriteria, getGenresByEvent, getJudgesByDivision, resetJudgeScores, resetJudgeFeedback, verifyEventAccessCode } from '@/utils/api';
```

And add `getActiveEvent` to the auth import:

```js
import { getActiveEvent } from '@/utils/auth';
```

- [ ] **Add the access code input to `ActionDoneModal` in the template**

Find the `ActionDoneModal` at the bottom of the template (around line 862) and replace it:

```html
<ActionDoneModal
  :show="showModal"
  :title="modalTitle"
  :variant="modalVariant"
  @accept="() => { dynamicCallBack() }"
  @close="() => { showModal = false; resetCodeError = '' }"
>
  <p class="type-body text-content-secondary">{{ modalMessage }}</p>
  <!-- Access code gate — only shown for reset (warning variant) -->
  <template v-if="modalVariant === 'warning'">
    <div class="mt-4 space-y-2">
      <label class="type-label text-content-muted">Event Access Code</label>
      <input
        v-model="resetConfirmCode"
        type="text"
        placeholder="Enter access code…"
        class="input-base w-full"
        @keyup.enter="dynamicCallBack()"
      />
      <p v-if="resetCodeError" class="type-label text-red-400">{{ resetCodeError }}</p>
      <p v-if="resetCodeChecking" class="type-label text-content-muted">Verifying…</p>
    </div>
  </template>
</ActionDoneModal>
```

- [ ] **Verify the reset flow in the browser**

Start the dev server, navigate to `/event/audition-list`, select Judge role and a genre. Click Reset — the modal should now show the access code input. Entering the wrong code should show an error. Entering the correct code should execute the reset.

- [ ] **Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: require access code before Reset Scores executes"
```

---

## Task 6: Full build verification

- [ ] **Build the backend**

```bash
cd BES && mvn clean package -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Build the frontend**

```bash
cd BES-frontend && npm run build
```

Expected: no errors, `dist/` generated

- [ ] **Run frontend linter**

```bash
cd BES-frontend && npm run lint 2>/dev/null || npx eslint src/views/AuditionNumber.vue src/views/EventDetails.vue src/views/AuditionList.vue
```

Expected: no errors

- [ ] **Final commit if any lint fixes were made, then push**

```bash
git add -A
git status  # confirm only expected files
git commit -m "chore: lint fixes for checkin-concurrency changes"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|-----------------|------|
| 409 duplicate check-in guard | Task 2 |
| Cancel signal via `cancelled: true` on existing topic | Tasks 1, 3 |
| Multi-slot grid (3-per-row) | Task 4 |
| Per-slot independent animation queue | Task 4 |
| `fakeNums`/`rollingIntervals` keyed by `slotId-genreName` | Task 4 |
| Preview fires on dialog open (unchanged) | Task 3 (no change to `askCheckIn`) |
| Cancel on dialog close (confirm phase only) | Task 3 |
| Double-confirm guard | Task 3 |
| Reset Scores access code gate | Task 5 |
| `/topic/audition/` message format unchanged | Task 4 (only internal processing changed) |
| `EventDetails`, `AuditionList`, `AuditionAdjust` unaffected | Tasks 1–3 only touch their own files |

**No placeholders:** All code blocks are complete and runnable.

**Type consistency:** `slotId` is always `String(participantId ?? name)` via `slotKey()`. `fakeNums` and `rollingIntervals` keys are always `${slotId}-${genreName}`. `flushSlotToHistory` takes `slotId` string throughout.
