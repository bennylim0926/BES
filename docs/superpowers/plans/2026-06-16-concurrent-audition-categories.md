# Concurrent Audition Categories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scope audition display state and WebSocket topics to individual categories so multiple emcees can run concurrent auditions without interfering with each other.

**Architecture:** Backend state store key changes from `eventName` to `eventName:categoryName`; WebSocket topic changes from `/topic/audition/{event}/display` to `/topic/audition/{event}/{category}/display`. `AuditionDisplay.vue` subscribes to a specific category's channel (via URL param or operator panel). `AuditionList.vue` locks emcees to their chosen category once they enter the session.

**Tech Stack:** Spring Boot (Java), Vue 3 (Composition API), STOMP WebSocket, Pinia

---

## File Map

| File | Change |
|------|--------|
| `BES/src/main/java/com/example/BES/services/EventAuditionDisplayService.java` | Composite state key + category-scoped WS topic |
| `BES/src/main/java/com/example/BES/controllers/EventAuditionDisplayController.java` | Add `?category` param to GET, validate categoryName on POST |
| `BES-frontend/src/utils/websocket.js` | Return subscription object from `subscribeToChannel` so callers can unsubscribe |
| `BES-frontend/src/utils/api.js` | Thread `categoryName` through both audition display functions |
| `BES-frontend/src/components/EmceeRoundView.vue` | Pass categoryName to GET on mount; fix background color |
| `BES-frontend/src/views/AuditionList.vue` | Lock emcee category; add "Change Category" button |
| `BES-frontend/src/views/AuditionDisplay.vue` | Category subscription; operator panel dropdown; fix background color |

---

## Task 1: websocket.js — Return Subscription from `subscribeToChannel`

**Files:**
- Modify: `BES-frontend/src/utils/websocket.js`

`subscribeToChannel` currently returns nothing. `AuditionDisplay.vue` needs to call `.unsubscribe()` when switching categories. This task makes the function return a subscription handle.

- [ ] **Step 1: Update `subscribeToChannel` to return an unsubscribe handle**

Open `BES-frontend/src/utils/websocket.js`. Replace the `subscribeToChannel` export with:

```js
export const subscribeToChannel = (client, topic, callback) => {
  let storedSub = null
  const doSubscribe = () => {
    storedSub = client.subscribe(topic, (msg) => {
      callback(JSON.parse(msg.body))
    })
  }
  if (!client.connected) {
    const prev = client.onConnect
    client.onConnect = () => {
      if (prev) prev()
      doSubscribe()
    }
  } else {
    doSubscribe()
  }
  if (!client.active) client.activate()
  return { unsubscribe: () => { if (storedSub) storedSub.unsubscribe() } }
}
```

All existing callers that ignore the return value continue to work unchanged. Only `AuditionDisplay.vue` will use the returned handle.

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/websocket.js
git commit -m "feat: return unsubscribe handle from subscribeToChannel"
```

---

## Task 2: Backend — Category-Scoped State Store

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventAuditionDisplayService.java`

- [ ] **Step 1: Replace the service with category-scoped implementation**

Replace the entire contents of `EventAuditionDisplayService.java` with:

```java
package com.example.BES.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private String stateKey(String eventName, String categoryName) {
        return eventName + ":" + categoryName;
    }

    public void updateState(String eventName, AuditionDisplayStateDto dto) {
        String key = stateKey(eventName, dto.categoryName);
        stateStore.put(key, dto);
        String encodedCategory = URLEncoder.encode(dto.categoryName, StandardCharsets.UTF_8);
        messagingTemplate.convertAndSend(
            "/topic/audition/" + eventName + "/" + encodedCategory + "/display", dto);
    }

    public AuditionDisplayStateDto getState(String eventName, String categoryName) {
        return stateStore.get(stateKey(eventName, categoryName));
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
cd BES && mvn clean package -DskipTests -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventAuditionDisplayService.java
git commit -m "feat: scope audition display state store to event+category"
```

---

## Task 3: Backend — Update Controller GET + POST Validation

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventAuditionDisplayController.java`

- [ ] **Step 1: Replace controller with category-aware version**

Replace the entire contents of `EventAuditionDisplayController.java` with:

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

    @PostMapping
    public ResponseEntity<?> updateDisplayState(@RequestBody AuditionDisplayStateDto dto) {
        if (dto == null || dto.eventName == null || dto.eventName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventName is required"));
        }
        if (dto.categoryName == null || dto.categoryName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "categoryName is required"));
        }
        displayService.updateState(dto.eventName, dto);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @GetMapping
    public ResponseEntity<?> getDisplayState(
            @RequestParam String event,
            @RequestParam String category) {
        if (category == null || category.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "category is required"));
        }
        AuditionDisplayStateDto state = displayService.getState(event, category);
        if (state == null) {
            return ResponseEntity.ok(Map.of(
                "standby", true,
                "eventName", event,
                "categoryName", category
            ));
        }
        return ResponseEntity.ok(state);
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
cd BES && mvn clean package -DskipTests -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventAuditionDisplayController.java
git commit -m "feat: add category param to audition display GET; validate categoryName on POST"
```

---

## Task 4: Frontend — Update api.js

**Files:**
- Modify: `BES-frontend/src/utils/api.js` (lines 1588–1615)

- [ ] **Step 1: Update `postAuditionDisplayState` to include category in query string**

In `api.js`, replace lines 1588–1602:

```js
export const postAuditionDisplayState = async (state) => {
  try {
    const category = encodeURIComponent(state.categoryName || '')
    return await fetch(`${domain}/api/v1/event/audition-display?event=${encodeURIComponent(state.eventName)}&category=${category}`, {
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
```

- [ ] **Step 2: Update `getAuditionDisplayState` to accept and pass categoryName**

In `api.js`, replace lines 1604–1615:

```js
export const getAuditionDisplayState = async (eventName, categoryName) => {
  try {
    const url = `${domain}/api/v1/event/audition-display?event=${encodeURIComponent(eventName)}&category=${encodeURIComponent(categoryName || '')}`
    const res = await fetch(url, { credentials: 'include' })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.error(e)
    return null
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: thread categoryName through audition display API functions"
```

---

## Task 5: Frontend — EmceeRoundView.vue

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue`

- [ ] **Step 1: Pass `categoryName` to `getAuditionDisplayState` on mount**

In `EmceeRoundView.vue`, replace line 113:

```js
  const state = await getAuditionDisplayState(props.eventName)
```

with:

```js
  const state = await getAuditionDisplayState(props.eventName, props.categoryName)
```

- [ ] **Step 2: Fix background color**

In `EmceeRoundView.vue` template (line 184), replace:

```html
  <div class="emcee-root w-full flex flex-col h-full touch-manipulation" style="background: #060818; overflow: hidden;">
```

with:

```html
  <div class="emcee-root w-full flex flex-col h-full touch-manipulation" style="background: #111111; overflow: hidden;">
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue
git commit -m "feat: pass categoryName to audition display GET on mount; fix background color"
```

---

## Task 6: Frontend — AuditionList.vue Emcee Category Lock

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

The goal: once an emcee selects a category, hide the category toggle buttons so they can't switch mid-session. Add a "Change Category" button in the context bar so they can intentionally reset.

- [ ] **Step 1: Hide category toggle buttons for locked emcees**

In `AuditionList.vue`, find the category button section (around line 807):

```html
          <div v-if="isAdmin || isOrganiser || isEmcee" class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-1">
```

Replace with:

```html
          <div v-if="isAdmin || isOrganiser || (isEmcee && !selectedCategory)" class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-1">
```

This hides the category buttons for emcees once a category is selected, preventing accidental switches.

- [ ] **Step 2: Add "Change Category" button to emcee context bar**

In the context bar section (around line 722), find the closing `</div>` of the left-side breadcrumb block. After `<span v-if="!isJudgeSession" class="type-label">{{ selectedRole }}</span>` (around line 720), add:

```html
        <template v-if="isEmcee && selectedCategory">
          <span class="text-content-muted opacity-30">·</span>
          <button
            class="type-label text-content-muted hover:text-content-primary transition-colors"
            @click="selectedCategory = ''"
          >‹ CHANGE CATEGORY</button>
        </template>
```

- [ ] **Step 3: Remove emcee-specific modal warning in `switchCategory`**

In `AuditionList.vue`, find the `switchCategory` function (around line 112). Remove the `else if (isEmcee.value)` branch entirely — emcees can no longer switch via the filter panel, so the warning is unreachable:

```js
const switchCategory = (g) => {
  if (g === selectedCategory.value) return
  const hasUnsaved = filteredParticipantsForJudge.value.some(p => p.score > 0 && !p.submitted)
  if (hasUnsaved) {
    modalTitle.value = `Switch to ${g}?`
    modalMessage.value = `You have unsaved scores for ${selectedCategory.value}. They're cached and won't be lost, but submit them first to be safe.`
    modalVariant.value = 'warning'
    showModal.value = true
    dynamicCallBack.value = () => { showModal.value = false; selectedCategory.value = g }
  } else {
    selectedCategory.value = g
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: lock emcee to selected category; add Change Category button"
```

---

## Task 7: Frontend — AuditionDisplay.vue Category Subscription

**Files:**
- Modify: `BES-frontend/src/views/AuditionDisplay.vue`

This is the largest change. The display must dynamically subscribe to a category-specific WebSocket topic, support URL param init for OBS, and add a category dropdown to the operator panel.

- [ ] **Step 1: Replace the `<script setup>` block**

Replace the entire `<script setup>` block (lines 1–111) with:

```vue
<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAuditionDisplayState, getCategoriesByEvent, updateCategoryNumberColor } from '@/utils/api'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'
import { useAuthStore } from '@/utils/auth'

const route = useRoute()
const authStore = useAuthStore()
const eventName = ref(route.query.event || '')
const state = ref(null)
const client = ref(null)
let currentSubscription = null

// ── Category selection — drives which WS topic we listen to ──────────────────
const selectedCategory = ref(route.query.category || '')

// ── Operator overlay: visible only to logged-in operators ─────────────────────
const isOperator = computed(() => !!authStore.isAuthenticated && !!authStore.user)
const showOperatorPanel = ref(false)
const eventCategories = ref([])

const activeCategoryEntry = computed(() => {
  if (!selectedCategory.value) return null
  return eventCategories.value.find(g => g.name === selectedCategory.value) ?? null
})

async function loadCategoriesForOperator() {
  if (!isOperator.value || !eventName.value) return
  eventCategories.value = await getCategoriesByEvent(eventName.value) ?? []
}

async function saveOperatorNumberColor(color) {
  const entry = activeCategoryEntry.value
  if (!entry) return
  const next = color || null
  await updateCategoryNumberColor(eventName.value, entry.eventCategoryId, next)
  entry.numberColor = next
  if (state.value) state.value = { ...state.value, numberColor: next }
}

// ── Local timer ticker ────────────────────────────────────────────────────────
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

// ── State application ─────────────────────────────────────────────────────────
function applyState(newState) {
  state.value = newState
  if (newState.timerRunning && newState.timerStartedAt && newState.timerDuration) {
    startLocalTimer(newState.timerStartedAt, newState.timerDuration)
  } else {
    stopLocalTimer()
    displayTimeLeft.value = newState.timerDuration ?? 0
  }
}

// ── Computed display values ───────────────────────────────────────────────────
const isStandby     = computed(() => !selectedCategory.value || !state.value || state.value.standby)
const mode          = computed(() => state.value?.mode ?? 'SOLO')
const categoryName  = computed(() => state.value?.categoryName ?? '')
const eventLabel    = computed(() => state.value?.eventName ?? eventName.value ?? '')
const roundLabel    = computed(() => {
  if (!state.value || !state.value.totalRounds) return ''
  return `ROUND ${state.value.currentRound} / ${state.value.totalRounds}`
})
const categoryRoundLabel = computed(() => state.value?.roundLabel || 'Preliminary Round')
const numberColor   = computed(() => state.value?.numberColor ?? null)
const currentSlots  = computed(() => state.value?.currentSlots ?? [])
const nextSlots     = computed(() => state.value?.nextSlots ?? [])
const isNearEnd     = computed(() => displayTimeLeft.value <= 10 && displayTimeLeft.value > 0 && state.value?.timerRunning)
const isFinished    = computed(() => displayTimeLeft.value <= 0 && state.value?.timerDuration > 0)
const timerLabel    = computed(() => {
  if (!state.value?.timerDuration) return ''
  return String(displayTimeLeft.value)
})

// ── Category subscription watcher ─────────────────────────────────────────────
watch(selectedCategory, async (newCat) => {
  // Tear down previous subscription
  if (currentSubscription) { currentSubscription.unsubscribe(); currentSubscription = null }
  stopLocalTimer()
  state.value = null

  if (!newCat || !eventName.value) return

  // Fetch initial state for the new category
  const initial = await getAuditionDisplayState(eventName.value, newCat)
  if (initial) applyState(initial)

  // Subscribe to category-specific topic
  const encodedCat = encodeURIComponent(newCat)
  currentSubscription = subscribeToChannel(
    client.value,
    `/topic/audition/${eventName.value}/${encodedCat}/display`,
    applyState
  )

  // Update URL so operator can copy it into OBS browser source
  const url = new URL(window.location.href)
  url.searchParams.set('category', newCat)
  history.pushState({}, '', url)
})

// ── Lifecycle ─────────────────────────────────────────────────────────────────
onMounted(async () => {
  if (!eventName.value) return

  client.value = createClient()

  // If category already in URL (OBS path or returning operator), subscribe immediately
  if (selectedCategory.value) {
    const initial = await getAuditionDisplayState(eventName.value, selectedCategory.value)
    if (initial) applyState(initial)
    const encodedCat = encodeURIComponent(selectedCategory.value)
    currentSubscription = subscribeToChannel(
      client.value,
      `/topic/audition/${eventName.value}/${encodedCat}/display`,
      applyState
    )
  }

  await loadCategoriesForOperator()
})

onUnmounted(() => {
  stopLocalTimer()
  if (currentSubscription) currentSubscription.unsubscribe()
  if (client.value) deactivateClient(client.value)
})
</script>
```

- [ ] **Step 2: Update the operator panel template to add the category dropdown**

In the `<template>`, find the operator panel `<div v-if="showOperatorPanel" class="op-panel">` block (around line 203). Replace the entire `op-panel` div with:

```html
        <div v-if="showOperatorPanel" class="op-panel">
          <div class="op-panel-header">
            <span class="op-panel-title">Display Settings</span>
            <span class="op-panel-category">{{ selectedCategory || '—' }}</span>
          </div>

          <!-- Category selector -->
          <div class="op-row" style="margin-bottom:12px">
            <span class="op-row-label">Monitoring Category</span>
            <select
              v-model="selectedCategory"
              class="op-select"
            >
              <option value="">— Select —</option>
              <option v-for="cat in eventCategories" :key="cat.eventCategoryId" :value="cat.name">
                {{ cat.name }}
              </option>
            </select>
          </div>

          <!-- Number color -->
          <div class="op-row">
            <span class="op-row-label">Audition Number Color</span>
            <div class="op-row-controls">
              <input
                type="color"
                :disabled="!activeCategoryEntry"
                :value="state?.numberColor || '#ffffff'"
                @change="saveOperatorNumberColor($event.target.value)"
                class="op-color-input"
                title="Pick a color for this category's audition number"
              />
              <span class="op-row-value">{{ state?.numberColor || 'Default' }}</span>
              <button
                v-if="state?.numberColor"
                class="op-reset"
                @click="saveOperatorNumberColor(null)"
                title="Reset to default"
              ><i class="pi pi-times"></i></button>
            </div>
          </div>
          <p v-if="!activeCategoryEntry" class="op-warn">
            Select a category above to enable controls.
          </p>
        </div>
```

- [ ] **Step 3: Update the standby screen message when no category is selected**

Find the standby container (around line 122):

```html
    <div v-if="isStandby" class="standby-container">
      <div class="corner-bar-tl"></div>
      <div class="corner-bar-bl"></div>
      <span class="type-label text-content-muted" style="font-size:14px;letter-spacing:0.22em">{{ eventLabel }}</span>
      <span class="type-stat text-accent" style="font-size: clamp(48px,8vw,80px);margin-top:12px">STANDBY</span>
      <span class="type-label text-content-muted" style="margin-top:8px">AWAITING AUDITION START</span>
    </div>
```

Replace with:

```html
    <div v-if="isStandby" class="standby-container">
      <div class="corner-bar-tl"></div>
      <div class="corner-bar-bl"></div>
      <span class="type-label text-content-muted" style="font-size:14px;letter-spacing:0.22em">{{ eventLabel }}</span>
      <span class="type-stat text-accent" style="font-size: clamp(48px,8vw,80px);margin-top:12px">STANDBY</span>
      <span class="type-label text-content-muted" style="margin-top:8px">
        {{ selectedCategory ? 'AWAITING AUDITION START' : 'SELECT A CATEGORY TO BEGIN' }}
      </span>
    </div>
```

- [ ] **Step 4: Fix background color**

In the `<style scoped>` block, find `.display-root`:

```css
.display-root {
  position: fixed;
  inset: 0;
  background: #060818;
```

Change `background: #060818` to `background: #111111`.

- [ ] **Step 5: Add `.op-select` style to the scoped CSS**

At the end of the `<style scoped>` block (before the closing `</style>`), add:

```css
.op-select {
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.85);
  font-family: 'Oswald', sans-serif;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  padding: 4px 8px;
  cursor: pointer;
  min-width: 120px;
}
.op-select option {
  background: #1a1a1a;
}
```

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/AuditionDisplay.vue
git commit -m "feat: category-scoped subscription in AuditionDisplay; operator panel category selector; fix background color"
```

---

## Task 8: End-to-End Verification

- [ ] **Step 1: Start the dev stack**

```bash
# Terminal 1 — backend
cd BES && mvn spring-boot:run

# Terminal 2 — frontend
cd BES-frontend && npm run dev
```

- [ ] **Step 2: Verify single-category flow still works**

1. Log in as Admin/Organiser, navigate to `/event/audition-list`
2. Select role "Emcee" — category buttons appear in filter panel
3. Select a category (e.g. "Hip-Hop") — category buttons disappear, `‹ CHANGE CATEGORY` appears in context bar
4. EmceeRoundView loads showing Hip-Hop participants
5. Open `/audition/display?event=<EventName>` in another tab — standby shows "SELECT A CATEGORY TO BEGIN"
6. Log in on the display tab, open operator panel (cog), pick "Hip-Hop" from dropdown — URL updates to `?event=...&category=Hip-Hop`
7. Back in AuditionList, click NEXT round — display updates showing the new round
8. Start the timer — display shows countdown; timer counts down independently

- [ ] **Step 3: Verify concurrent two-category flow**

1. Open two incognito tabs, log in as Emcee in each
2. Tab A: select "Hip-Hop" → EmceeRoundView for Hip-Hop
3. Tab B: select "Breaking" → EmceeRoundView for Breaking
4. Open `/audition/display?event=<EventName>&category=Hip-Hop` (no login needed — URL param)
5. Open `/audition/display?event=<EventName>&category=Breaking` (no login needed)
6. Advance rounds and start timers independently in Tab A and Tab B
7. Verify Hip-Hop display only shows Hip-Hop state; Breaking display only shows Breaking state
8. Verify timers run independently with zero crosstalk

- [ ] **Step 4: Verify timer recovery after page refresh**

1. As emcee in Hip-Hop, start a 60-second timer
2. Refresh the EmceeRoundView page
3. Timer should resume at the correct remaining time (not reset to 60)

- [ ] **Step 5: Verify "Change Category" flow**

1. As emcee in Hip-Hop, click `‹ CHANGE CATEGORY` in context bar
2. `selectedCategory` clears → filter panel reappears with category buttons
3. Pick "Breaking" → EmceeRoundView loads for Breaking
4. Breaking display updates; Hip-Hop display stays on its last state (standby if no other emcee)
