# Audition Display Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public `/audition/display` OBS browser source that mirrors the emcee's current audition round + timer in real time via WebSocket.

**Architecture:** EmceeRoundView POSTs round + timer state to a new backend REST endpoint on round change and timer start/stop. The backend stores state in-memory (ConcurrentHashMap keyed by eventName) and broadcasts via STOMP to `/topic/audition/{eventName}/display`. AuditionDisplay.vue fetches initial state via GET on mount (OBS refresh survival), then subscribes to the WebSocket topic for live updates. Timer survives page refresh by storing `timerStartedAt` + `timerDuration` + `timerRunning`; the display reconstructs `timeLeft` client-side.

**Tech Stack:** Java 17 (Spring Boot), Vue 3, STOMP/WebSocket

---

### Task 1: Create DTO — `AuditionDisplayStateDto.java`

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/AuditionDisplayStateDto.java`

- [ ] **Step 1: Write the DTO**

This is the single DTO used for both the POST request body and the GET response. It holds round info, slot arrays, and timer metadata.

```java
package com.example.BES.dtos;

import java.util.List;

public class AuditionDisplayStateDto {

    public String eventName;
    public String genreName;
    public String mode;          // "SOLO" or "PAIR"
    public int currentRound;
    public int totalRounds;
    public List<Slot> currentSlots;
    public List<Slot> nextSlots;

    // Timer fields — null/0 when timer is not running
    public Long timerStartedAt;  // epoch ms
    public Integer timerDuration; // seconds
    public Boolean timerRunning;

    // Default constructor for Jackson
    public AuditionDisplayStateDto() {}

    public static class Slot {
        public int auditionNumber;
        public String participantName;
        public List<String> memberNames;
        public boolean placeholder;

        public Slot() {}
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/AuditionDisplayStateDto.java
git commit -m "feat: add AuditionDisplayStateDto for audition display screen

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Create Service — `EventAuditionDisplayService.java`

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/EventAuditionDisplayService.java`

- [ ] **Step 1: Write the service**

In-memory state store with broadcast. Follows the `BattleService` pattern: `ConcurrentHashMap`, `SimpMessagingTemplate.convertAndSend`.

```java
package com.example.BES.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AuditionDisplayStateDto;

@Service
public class EventAuditionDisplayService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, AuditionDisplayStateDto> stateStore = new ConcurrentHashMap<>();

    /**
     * Save state in memory and broadcast to all display clients for this event.
     */
    public void updateState(String eventName, AuditionDisplayStateDto dto) {
        dto.eventName = eventName;
        stateStore.put(eventName, dto);
        messagingTemplate.convertAndSend(
            "/topic/audition/" + eventName + "/display", dto);
    }

    /**
     * Return the current display state for an event, or null if none published yet.
     */
    public AuditionDisplayStateDto getState(String eventName) {
        return stateStore.get(eventName);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventAuditionDisplayService.java
git commit -m "feat: add EventAuditionDisplayService for in-memory display state

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Create Controller — `EventAuditionDisplayController.java`

**Files:**
- Create: `BES/src/main/java/com/example/BES/controllers/EventAuditionDisplayController.java`

- [ ] **Step 1: Write the controller**

Public endpoints (no auth — like battle overlay). POST for emcee updates, GET for display page refresh.

```java
package com.example.BES.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AuditionDisplayStateDto;
import com.example.BES.services.EventAuditionDisplayService;

import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/event/audition-display")
public class EventAuditionDisplayController {

    @Autowired
    private EventAuditionDisplayService displayService;

    /**
     * Called by EmceeRoundView on round change and timer start/stop.
     * Stores state and broadcasts to display clients.
     */
    @PostMapping
    public ResponseEntity<?> updateDisplayState(@RequestBody AuditionDisplayStateDto dto) {
        if (dto == null || dto.eventName == null || dto.eventName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventName is required"));
        }
        displayService.updateState(dto.eventName, dto);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    /**
     * Called by AuditionDisplay.vue on mount / OBS refresh.
     * Returns the latest published state for this event.
     */
    @GetMapping
    public ResponseEntity<?> getDisplayState(@RequestParam String event) {
        AuditionDisplayStateDto state = displayService.getState(event);
        if (state == null) {
            return ResponseEntity.ok(Map.of(
                "standby", true,
                "eventName", event
            ));
        }
        return ResponseEntity.ok(state);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventAuditionDisplayController.java
git commit -m "feat: add EventAuditionDisplayController with POST/GET endpoints

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Update SecurityConfig — permitAll for display endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/config/SecurityConfig.java:42-49`

- [ ] **Step 1: Add permitAll rule**

Add a new `.requestMatchers(...)` line BEFORE the `.anyRequest().authenticated()` catch-all. The display endpoints must be public so OBS browser sources can access them without auth.

```java
// In SecurityConfig.java filterChain() method, add this line:
.requestMatchers("/api/v1/event/audition-display/**").permitAll()
```

The full auth block should read:

```java
.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/battle/**").permitAll()
                .requestMatchers("/api/v1/event/audition-display/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/results").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/config/app").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/config/SecurityConfig.java
git commit -m "feat: permitAll for audition-display endpoints

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Add API functions — `api.js`

**Files:**
- Modify: `BES-frontend/src/utils/api.js` (append at end)

- [ ] **Step 1: Add `postAuditionDisplayState` and `getAuditionDisplayState`**

Append these two functions at the end of `api.js` (after line 1596):

```js
export const postAuditionDisplayState = async (state) => {
  try {
    return await fetch(`${domain}/api/v1/event/audition-display`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(state)
    })
  } catch (e) {
    console.error(e)
  }
}

export const getAuditionDisplayState = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/audition-display?event=${encodeURIComponent(eventName)}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.error(e)
    return null
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add postAuditionDisplayState and getAuditionDisplayState API functions

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Modify Timer.vue — add emits and `resumeTimer`

**Files:**
- Modify: `BES-frontend/src/components/Timer.vue`

- [ ] **Step 1: Add emits declaration**

Add to the `<script setup>` block, after the existing `import` lines and before `const selectedTime = ref(0)`:

```js
const emit = defineEmits(['started', 'stopped', 'tick'])
```

- [ ] **Step 2: Emit events on start, stop, and each tick**

Modify `startTimer()` to emit `started`:

```js
function startTimer(seconds) {
  if (selectedTime.value === seconds && timer) {
    reset()
    return
  }
  if (timer) clearInterval(timer)
  selectedTime.value = seconds
  timeLeft.value = 0
  timer = setInterval(() => {
    if (timeLeft.value < seconds) {
      timeLeft.value++
      // Emit tick with current state
      const remaining = selectedTime.value - timeLeft.value
      emit('tick', { remaining, total: selectedTime.value, running: true })
    } else {
      clearInterval(timer)
      timer = null
      emit('stopped')
      emit('tick', { remaining: 0, total: selectedTime.value, running: false })
    }
  }, 1000)
  emit('started', { duration: seconds, startedAt: Date.now() })
}
```

Modify `reset()` to emit `stopped`:

```js
function reset() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  selectedTime.value = 0
  timeLeft.value = 0
  emit('stopped')
}
```

Modify `stop()` to emit `stopped`:

```js
function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
    emit('stopped')
  }
}
```

- [ ] **Step 3: Add `resumeTimer` method and expose it**

Add this function before `defineExpose`:

```js
/**
 * Resume a countdown timer from a specific remaining time.
 * Called by EmceeRoundView on mount when recovering timer state after refresh.
 * @param {number} remainingSeconds - seconds left on the clock
 * @param {number} totalDuration - original total duration in seconds
 */
function resumeTimer(remainingSeconds, totalDuration) {
  if (timer) clearInterval(timer)
  if (remainingSeconds <= 0) {
    selectedTime.value = totalDuration
    timeLeft.value = totalDuration
    emit('stopped')
    return
  }
  selectedTime.value = totalDuration
  timeLeft.value = totalDuration - remainingSeconds
  timer = setInterval(() => {
    if (timeLeft.value < totalDuration) {
      timeLeft.value++
      const remaining = selectedTime.value - timeLeft.value
      emit('tick', { remaining, total: selectedTime.value, running: true })
    } else {
      clearInterval(timer)
      timer = null
      emit('stopped')
      emit('tick', { remaining: 0, total: selectedTime.value, running: false })
    }
  }, 1000)
  emit('started', { duration: totalDuration, startedAt: Date.now() - ((totalDuration - remainingSeconds) * 1000) })
}
```

Add `resumeTimer` to `defineExpose`:

```js
defineExpose({ reset, stop, resumeTimer })
```

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/components/Timer.vue
git commit -m "feat: add started/stopped/tick emits and resumeTimer to Timer.vue

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Modify EmceeRoundView.vue — publish state to backend

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue`

- [ ] **Step 1: Add new props and imports**

Add `eventName` and `genreName` to the existing `defineProps`:

```js
const props = defineProps({
  participants: { type: Array, required: true },
  mode:         { type: String, default: 'SOLO' },
  eventName:    { type: String, default: '' },
  genreName:    { type: String, default: '' },
});
```

Add the import for the API function at the top:

```js
import { ref, computed, watch, onMounted } from 'vue';
import Timer from './Timer.vue';
import { postAuditionDisplayState, getAuditionDisplayState } from '@/utils/api';
```

- [ ] **Step 2: Add `buildStatePayload` helper and `publishState` function**

Add these functions after `defineExpose({ resetTimer })` but before the `</script>`:

```js
function buildStatePayload(timerState = {}) {
  const current = currentRoundSlots.value.map(slot => ({
    auditionNumber: slot.auditionNumber,
    participantName: slot._placeholder ? null : slot.participantName,
    memberNames: slot.memberNames ?? [],
    placeholder: !!slot._placeholder
  }))

  const nextRoundIdx = currentRound.value // next round is currentRound (1-indexed → 0-indexed next)
  const nextSlotsRaw = rounds.value[nextRoundIdx] ?? []
  const next = nextSlotsRaw.map(slot => ({
    auditionNumber: slot.auditionNumber,
    participantName: slot._placeholder ? null : slot.participantName,
    memberNames: slot.memberNames ?? [],
    placeholder: !!slot._placeholder
  }))

  return {
    eventName: props.eventName,
    genreName: props.genreName,
    mode: props.mode,
    currentRound: currentRound.value,
    totalRounds: totalRounds.value,
    currentSlots: current,
    nextSlots: next,
    timerStartedAt: timerState.startedAt ?? null,
    timerDuration: timerState.duration ?? null,
    timerRunning: timerState.running ?? false
  }
}

function publishState(timerState = {}) {
  if (!props.eventName) return
  postAuditionDisplayState(buildStatePayload(timerState))
}
```

- [ ] **Step 3: Watch `currentRound` and publish on change**

Add a watcher after the `cardStyle` computed:

```js
// Publish state to display whenever the round changes (include timer state if running)
watch(currentRound, () => {
  publishState(lastTimerState.value)
})
```

- [ ] **Step 4: Handle timer events from Timer.vue**

Store the last timer state so it can be included in round-change publishes. Add before the `</script>`:

```js
const lastTimerState = ref({})

function onTimerStarted(detail) {
  lastTimerState.value = { startedAt: detail.startedAt, duration: detail.duration, running: true }
  publishState(lastTimerState.value)
}

function onTimerStopped() {
  lastTimerState.value = { startedAt: null, duration: null, running: false }
  publishState(lastTimerState.value)
}

function onTimerTick(detail) {
  lastTimerState.value = { startedAt: null, duration: detail.total, running: detail.running }
  // Don't POST on every tick — just update local state for round-change publishes
}
```

- [ ] **Step 5: Add `@started`/`@stopped`/`@tick` listeners on the Timer component**

In the template, update the `<Timer>` line:

```html
<Timer ref="timerRef" @started="onTimerStarted" @stopped="onTimerStopped" @tick="onTimerTick" />
```

- [ ] **Step 6: On mount, fetch current state and resume timer if running**

Add an `onMounted` block after the existing `defineExpose`:

```js
onMounted(async () => {
  if (!props.eventName) return
  const state = await getAuditionDisplayState(props.eventName)
  if (!state || state.standby) return
  // Restore round if state has a different round (unlikely, but defensive)
  // Timer recovery: if timer was running, resume it
  if (state.timerRunning && state.timerStartedAt && state.timerDuration) {
    const elapsed = Math.floor((Date.now() - state.timerStartedAt) / 1000)
    const remaining = Math.max(0, state.timerDuration - elapsed)
    if (remaining > 0) {
      // Wait for nextTick so Timer ref is mounted
      await new Promise(r => setTimeout(r, 100))
      timerRef.value?.resumeTimer(remaining, state.timerDuration)
    }
  }
})
```

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue
git commit -m "feat: publish audition display state from EmceeRoundView on round change and timer events

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: Modify AuditionList.vue — pass eventName and genreName

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

- [ ] **Step 1: Pass new props to EmceeRoundView**

In the template (around line 925), update the `<EmceeRoundView>` usage:

```html
<EmceeRoundView
  ref="emceeRoundRef"
  :key="selectedGenre"
  :participants="filteredParticipantsForEmceeView"
  :mode="judgingMode"
  :eventName="selectedEvent"
  :genreName="selectedGenre"
/>
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: pass eventName and genreName to EmceeRoundView

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: Create AuditionDisplay.vue — public display page

**Files:**
- Create: `BES-frontend/src/views/AuditionDisplay.vue`

- [ ] **Step 1: Write the full component**

Full-screen cinematic display following the project's design system. Anton SC typography, dark background, parallelogram shapes.

```vue
<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAuditionDisplayState } from '@/utils/api'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'

const route = useRoute()
const eventName = ref(route.query.event || '')
const state = ref(null)
const client = ref(null)

// ── Local timer ticker (reconstructed from backend timerStartedAt + timerDuration) ──
const displayTimeLeft = ref(0)
let timerInterval = null

function startLocalTimer(startedAt, duration) {
  stopLocalTimer()
  const tick = () => {
    const elapsed = Math.floor((Date.now() - startedAt) / 1000)
    displayTimeLeft.value = Math.max(0, duration - elapsed)
    if (displayTimeLeft.value <= 0) stopLocalTimer()
  }
  tick()
  timerInterval = setInterval(tick, 250)
}

function stopLocalTimer() {
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
}

// ── Computed ──────────────────────────────────────────────────────────────────
const isStandby  = computed(() => !state.value || state.value.standby)
const mode       = computed(() => state.value?.mode ?? 'SOLO')
const genreName  = computed(() => state.value?.genreName ?? '')
const eventLabel = computed(() => state.value?.eventName ?? eventName.value ?? '')
const roundLabel = computed(() => {
  if (!state.value || !state.value.totalRounds) return ''
  return `ROUND ${state.value.currentRound} / ${state.value.totalRounds}`
})
const currentSlots = computed(() => state.value?.currentSlots ?? [])
const nextSlots    = computed(() => state.value?.nextSlots ?? [])

const isNearEnd  = computed(() => displayTimeLeft.value <= 10 && displayTimeLeft.value > 0 && state.value?.timerRunning)
const isFinished = computed(() => displayTimeLeft.value <= 0 && state.value?.timerDuration > 0)
const timerLabel = computed(() => {
  if (!state.value?.timerDuration) return ''
  const t = displayTimeLeft.value
  if (t <= 0) return '0'
  return String(t)
})

// ── Lifecycle ─────────────────────────────────────────────────────────────────
function applyState(newState) {
  state.value = newState
  if (newState.timerRunning && newState.timerStartedAt && newState.timerDuration) {
    startLocalTimer(newState.timerStartedAt, newState.timerDuration)
  } else {
    stopLocalTimer()
    displayTimeLeft.value = newState.timerDuration ?? 0
  }
}

onMounted(async () => {
  if (!eventName.value) return

  const initial = await getAuditionDisplayState(eventName.value)
  if (initial) applyState(initial)

  client.value = createClient()
  subscribeToChannel(client.value, `/topic/audition/${eventName.value}/display`, (msg) => {
    applyState(msg)
  })
})

onUnmounted(() => {
  stopLocalTimer()
  if (client.value) deactivateClient(client.value)
})
</script>

<template>
  <div class="display-root">
    <!-- Scanlines overlay -->
    <div class="scanlines"></div>

    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <!-- STANDBY: no state published yet -->
    <div v-if="isStandby" class="standby-container">
      <div class="corner-bar-tl"></div>
      <div class="corner-bar-bl"></div>
      <span class="type-label text-content-muted" style="font-size:14px;letter-spacing:0.22em">{{ eventLabel }}</span>
      <span class="type-stat text-accent" style="font-size: clamp(48px,8vw,80px);margin-top:12px">STANDBY</span>
      <span class="type-label text-content-muted" style="margin-top:8px">AWAITING AUDITION START</span>
    </div>

    <!-- ACTIVE display -->
    <div v-else class="active-container">
      <!-- Top bar: event name + genre -->
      <div class="top-bar">
        <span class="type-label text-content-muted" style="letter-spacing:0.22em">{{ eventLabel }}</span>
        <span class="type-label text-accent" style="letter-spacing:0.12em">{{ genreName }}</span>
      </div>

      <!-- Main area: round counter, audition number, participant name, timer -->
      <div class="main-area">
        <!-- Round counter -->
        <div class="section-rule mb-3">
          <span class="section-rule-label type-label text-content-muted">{{ roundLabel }}</span>
        </div>

        <!-- Current slot(s) -->
        <div class="current-slots">
          <template v-for="(slot, sIdx) in currentSlots" :key="sIdx">
            <div v-if="slot.placeholder" class="slot-placeholder type-stat" style="font-size:clamp(50px,8vw,80px);color:rgba(245,158,11,0.3)">
              #{{ slot.auditionNumber }} — TBD
            </div>
            <div v-else class="slot-entry">
              <div class="type-stat audition-number">
                #{{ slot.auditionNumber }}
              </div>
              <div class="type-body participant-name">
                {{ slot.participantName }}
              </div>
              <div v-if="slot.memberNames?.length" class="type-label member-names">
                {{ slot.memberNames.join(' · ') }}
              </div>
            </div>
            <!-- PAIR separator -->
            <div v-if="mode === 'PAIR' && sIdx === 0 && currentSlots.length > 1" class="pair-sep">
              <span class="type-stat">&amp;</span>
            </div>
          </template>
        </div>

        <!-- Timer -->
        <div v-if="timerLabel" class="timer-display" :class="{ 'timer-near-end': isNearEnd, 'timer-finished': isFinished }">
          <div class="type-stat timer-number">{{ timerLabel }}</div>
        </div>
      </div>

      <!-- UP NEXT (secondary area) -->
      <div v-if="nextSlots.length > 0" class="up-next-area">
        <div class="section-rule mb-2">
          <span class="section-rule-label type-label text-content-muted">UP NEXT</span>
        </div>
        <div class="next-slots">
          <template v-for="(slot, sIdx) in nextSlots" :key="sIdx">
            <div v-if="slot.placeholder" class="type-label" style="opacity:0.3;font-size:16px">
              #{{ slot.auditionNumber }} — TBD
            </div>
            <div v-else class="next-slot-entry">
              <span class="type-stat" style="font-size:24px;opacity:0.5">#{{ slot.auditionNumber }}</span>
              <span class="type-body" style="font-size:20px;opacity:0.4;margin-left:8px">{{ slot.participantName }}</span>
              <span v-if="slot.memberNames?.length" class="type-label" style="opacity:0.3;font-size:12px;margin-left:8px">{{ slot.memberNames.join(' · ') }}</span>
            </div>
            <span v-if="mode === 'PAIR' && sIdx === 0 && nextSlots.length > 1" style="opacity:0.2;margin:0 8px">&amp;</span>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Root layout ──────────────────────────────────────────────────────────── */
.display-root {
  position: fixed;
  inset: 0;
  background: #060818;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-family: 'Anton SC', sans-serif;
  text-transform: uppercase;
}

/* ── Scanlines ────────────────────────────────────────────────────────────── */
.scanlines {
  pointer-events: none;
  position: fixed;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(0,0,0,0.03) 2px,
    rgba(0,0,0,0.03) 4px
  );
  z-index: 10;
}

/* ── Color bleed ──────────────────────────────────────────────────────────── */
.color-bleed {
  pointer-events: none;
  position: fixed;
  inset: 0;
  background:
    radial-gradient(ellipse at 0% 100%, var(--accent-subtle, rgba(255,255,255,0.015)) 0%, transparent 60%),
    radial-gradient(ellipse at 100% 100%, var(--accent-subtle, rgba(255,255,255,0.015)) 0%, transparent 60%);
  z-index: 0;
}

/* ── Standby ──────────────────────────────────────────────────────────────── */
.standby-container {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 64px;
  clip-path: polygon(10px 0%, 100% 0%, calc(100% - 10px) 100%, 0% 100%);
  border: 1px solid rgba(255,255,255,0.06);
}

/* ── Active layout ────────────────────────────────────────────────────────── */
.active-container {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 40px;
}

.top-bar {
  position: absolute;
  top: 40px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 16px;
  opacity: 0.5;
}

.main-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  justify-content: center;
}

/* ── Current slots ────────────────────────────────────────────────────────── */
.current-slots {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

.slot-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.audition-number {
  font-size: clamp(80px, 14vw, 120px);
  line-height: 1;
  letter-spacing: 0.02em;
  text-shadow: 2px 2px 0 var(--accent-muted, rgba(255,255,255,0.15));
  color: var(--accent-color, #ffffff);
}

.participant-name {
  font-size: clamp(28px, 5vw, 48px);
  letter-spacing: 0.05em;
  color: #ffffff;
  margin-top: 4px;
}

.member-names {
  font-size: 16px;
  letter-spacing: 0.04em;
  color: rgba(255,255,255,0.35);
  text-transform: none;
  margin-top: 4px;
}

.pair-sep {
  margin: 8px 0;
  color: rgba(255,255,255,0.25);
  font-size: 28px;
}

.slot-placeholder {
  opacity: 0.4;
}

/* ── Timer ────────────────────────────────────────────────────────────────── */
.timer-display {
  margin-top: 24px;
}

.timer-number {
  font-size: clamp(64px, 12vw, 100px);
  line-height: 1;
  letter-spacing: 0.02em;
  color: #ffffff;
  transition: color 0.3s ease;
}

.timer-near-end .timer-number {
  color: #ef4444;
  animation: pulse 0.5s ease-in-out infinite alternate;
}

.timer-finished .timer-number {
  color: rgba(255,255,255,0.2);
}

@keyframes pulse {
  from { opacity: 1; transform: scale(1); }
  to   { opacity: 0.7; transform: scale(1.03); }
}

/* ── UP NEXT ─────────────────────────────────────────────────────────────── */
.up-next-area {
  position: absolute;
  bottom: 60px;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.next-slots {
  display: flex;
  align-items: center;
}

.next-slot-entry {
  display: flex;
  align-items: baseline;
}

/* ── Section rule (global utility not available, define locally) ──────────── */
.section-rule {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 500px;
}
.section-rule::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(255,255,255,0.08);
}
.section-rule-label {
  flex-shrink: 0;
}

/* ── Corner bars ─────────────────────────────────────────────────────────── */
.corner-bar-tl, .corner-bar-bl {
  position: absolute;
  background: var(--accent-color, #ffffff);
  opacity: 0.4;
}
.corner-bar-tl {
  top: 0; left: 0;
  width: 2px; height: 20px;
}
.corner-bar-bl {
  bottom: 0; left: 0;
  width: 2px; height: 20px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/AuditionDisplay.vue
git commit -m "feat: add AuditionDisplay.vue — public OBS display page

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: Add route — `/audition/display`

**Files:**
- Modify: `BES-frontend/src/router/index.js`

- [ ] **Step 1: Import and add route**

Add the import at the top with the other view imports:

```js
import AuditionDisplay from "@/views/AuditionDisplay.vue";
```

Add the route definition in the `routes` array (alongside other public routes like BattleOverlay):

```js
{
    path: '/audition/display',
    name: 'AuditionDisplay',
    component: AuditionDisplay
},
```

Add `'AuditionDisplay'` to the `PUBLIC_ROUTES` array:

```js
const PUBLIC_ROUTES = ['Login', 'Forbidden', 'StreamOverlay', 'Smoke', 'Results', 'ResultsQR', 'BracketVisualization', 'TokenAuth', 'JudgeSession', 'EmceeSession', 'HelperSession', 'AuditionDisplay']
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/router/index.js
git commit -m "feat: add /audition/display public route

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 11: Update CLAUDE.md — routes table

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add the new route to the public routes table**

In the "Public routes" table, add this row:

```
| `/audition/display` | `AuditionDisplay` | Live audition round + timer OBS source |
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add /audition/display to public routes table

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---
