<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAuditionDisplayState, getCategoriesByEvent, updateCategoryNumberColor } from '@/utils/api'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'
import { useAuthStore } from '@/utils/auth'
import { getPositionLabel, getPositionDisplay } from '@/utils/auditionPairs'

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
    // Idle: fall back to the sticky baseline picked by the emcee so the
    // display never goes blank between rounds, on timer expiry, or after
    // an explicit Reset.
    stopLocalTimer()
    displayTimeLeft.value = newState.baselineDuration ?? newState.timerDuration ?? 0
  }
}

// ── Computed display values ───────────────────────────────────────────────────
const isStandby     = computed(() => !selectedCategory.value || !state.value || state.value.standby)
const mode          = computed(() => state.value?.mode ?? 'SOLO')
const categoryName  = computed(() => state.value?.categoryName ?? '')
const eventLabel    = computed(() => state.value?.eventName ?? eventName.value ?? '')
const categoryRoundLabel = computed(() => state.value?.roundLabel || 'Preliminary Round')
const numberColor   = computed(() => state.value?.numberColor ?? null)

const currentSlots  = computed(() => state.value?.currentSlots ?? [])
const nextSlots     = computed(() => state.value?.nextSlots ?? [])
const isNearEnd     = computed(() => displayTimeLeft.value <= 10 && displayTimeLeft.value > 0 && state.value?.timerRunning)
const isFinished    = computed(() => state.value?.timerRunning && displayTimeLeft.value <= 0 && state.value?.timerDuration > 0)
// Show the live countdown when running, otherwise the sticky baseline
// the emcee picked for this category. Only blank when neither is set.
const timerLabel    = computed(() => {
  if (state.value?.timerRunning && state.value?.timerDuration) return String(displayTimeLeft.value)
  if (state.value?.baselineDuration) return String(state.value.baselineDuration)
  return ''
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
  currentSubscription = subscribeToChannel(
    client.value,
    `/topic/audition/${eventName.value}/${newCat}/display`,
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
    currentSubscription = subscribeToChannel(
      client.value,
      `/topic/audition/${eventName.value}/${selectedCategory.value}/display`,
      applyState
    )
  }

  await loadCategoriesForOperator()
})

// Auth may not be ready at mount time — load categories once operator status resolves
watch(isOperator, (val) => {
  if (val && eventCategories.value.length === 0) loadCategoriesForOperator()
})

onUnmounted(() => {
  stopLocalTimer()
  if (currentSubscription) currentSubscription.unsubscribe()
  if (client.value) deactivateClient(client.value)
})
</script>

<template>
  <div class="display-root">
    <!-- Scanlines overlay -->
    <div class="scanlines"></div>

    <!-- STANDBY: no state published yet -->
    <div v-if="isStandby" class="standby-container">
      <div class="corner-bar-tl"></div>
      <div class="corner-bar-bl"></div>
      <span class="type-label text-content-muted" style="font-size:14px;letter-spacing:0.22em">{{ eventLabel }}</span>
      <span v-if="selectedCategory" class="type-label text-accent" style="font-size:18px;letter-spacing:0.18em;margin-top:4px">{{ selectedCategory }}</span>
      <span class="type-stat text-accent" style="font-size: clamp(48px,8vw,80px);margin-top:12px">STANDBY</span>
      <span class="type-label text-content-muted" style="margin-top:8px">
        {{ selectedCategory ? 'AWAITING AUDITION START' : 'SELECT A CATEGORY TO BEGIN' }}
      </span>
    </div>

    <!-- ACTIVE display -->
    <div v-else class="active-container" :style="currentSlots.length >= 3 ? { paddingTop: '18vh' } : {}">

      <!-- Main area: event/category header, round counter, number+name+timer -->
      <div class="main-area">
        <!-- Event name + category stacked above everything -->
        <div class="event-header">
          <span class="event-header-name">{{ eventLabel }}</span>
          <span class="event-header-category">{{ categoryName }}<template v-if="categoryRoundLabel"> &nbsp;|&nbsp; {{ categoryRoundLabel }}</template></span>
        </div>

        <!-- PAIR mode: stacked names left | timer right -->
        <div v-if="mode === 'PAIR'" class="pair-row">
          <!-- Left: both names stacked -->
          <div class="pair-names" :class="{ 'pair-names-threeway': currentSlots.length >= 3 }">
            <template v-for="(slot, sIdx) in currentSlots" :key="sIdx">
              <div class="pair-name-entry">
                <!-- Number + name stacked (left side) -->
                <div class="pair-entry-content">
                  <!-- Number + name on one row -->
                  <div class="pair-name-row">
                    <span class="type-stat audition-number" :style="numberColor ? { color: numberColor } : {}">#{{ slot.auditionNumber }}</span>
                    <span v-if="slot.placeholder" class="participant-name" style="opacity:0.3">TBD</span>
                    <span v-else class="type-body participant-name">{{ slot.participantName }}</span>
                  </div>
                  <!-- Member names + position label on same row -->
                  <div v-if="!slot.placeholder" class="pair-member-names" style="display:flex;align-items:center;gap:8px;">
                    <span v-if="slot.memberNames?.length">{{ slot.memberNames.join(' · ') }}</span>
                    <span v-if="slot.memberNames?.length" style="opacity:0.2;font-size:0.9em;">|</span>
                    <span
                      class="position-label"
                      :class="{
                        'position-left':   getPositionLabel(sIdx, currentSlots.length) === 'LEFT',
                        'position-middle': getPositionLabel(sIdx, currentSlots.length) === 'MIDDLE',
                        'position-right':  getPositionLabel(sIdx, currentSlots.length) === 'RIGHT',
                      }"
                    >{{ getPositionDisplay(sIdx, currentSlots.length) }}</span>
                  </div>
                </div>
              </div>
              <!-- Divider between entries (including 3-way) -->
              <div v-if="sIdx < currentSlots.length - 1" class="pair-divider" aria-hidden="true"></div>
            </template>
          </div>

          <!-- Right: timer -->
          <div class="timer-display" :class="{ 'timer-near-end': isNearEnd, 'timer-finished': isFinished }">
            <div class="type-stat timer-number">{{ timerLabel || '0' }}</div>
          </div>
        </div>

        <!-- SOLO mode: number+name | Timer -->
        <div v-else class="slot-timer-row">
          <div class="current-slots">
            <template v-if="currentSlots[0]?.placeholder">
              <div class="slot-placeholder type-stat" style="font-size:clamp(50px,8vw,80px);color:rgba(245,158,11,0.3)">
                #{{ currentSlots[0].auditionNumber }} — TBD
              </div>
            </template>
            <div v-else-if="currentSlots[0]" class="slot-entry">
              <div class="type-stat audition-number" :style="numberColor ? { color: numberColor } : {}">
                #{{ currentSlots[0].auditionNumber }}
              </div>
              <div class="type-body participant-name">
                {{ currentSlots[0].participantName }}
              </div>
              <div v-if="currentSlots[0].memberNames?.length" class="type-label member-names">
                {{ currentSlots[0].memberNames.join(' · ') }}
              </div>
            </div>
          </div>
          <div v-if="timerLabel" class="timer-display" :class="{ 'timer-near-end': isNearEnd, 'timer-finished': isFinished }">
            <div class="type-stat timer-number">{{ timerLabel }}</div>
          </div>
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
              <span class="next-slot-name" style="margin-left:8px">{{ slot.participantName }}</span>
              <span v-if="slot.memberNames?.length" class="next-slot-members" style="margin-left:8px">{{ slot.memberNames.join(' · ') }}</span>
            </div>
            <span v-if="mode === 'PAIR' && sIdx < nextSlots.length - 1" style="opacity:0.2;margin:0 8px">&amp;</span>
          </template>
        </div>
      </div>
    </div>

    <!-- Operator overlay — always visible to logged-in operators, even in standby -->
    <div v-if="isOperator" class="operator-overlay">
      <button
        class="op-toggle"
        :class="{ 'op-toggle-active': showOperatorPanel }"
        @click="showOperatorPanel = !showOperatorPanel"
        title="Display settings"
      >
        <i class="pi" :class="showOperatorPanel ? 'pi-times' : 'pi-cog'"></i>
      </button>
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
    </div>
  </div>
</template>

<style scoped>
/* ── Root layout ──────────────────────────────────────────────────────────── */
.display-root {
  position: fixed;
  inset: 0;
  background: #111111;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-family: 'Oswald', sans-serif;
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

.main-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  gap: 10px;
  margin-top: 12vh;
}

/* Event name + category — pinned to top, independent of the centred content */
.event-header {
  position: absolute;
  top: 5vh;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.event-header-name {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(42px, 8vw, 100px);
  letter-spacing: 0.04em;
  text-transform: none;
  color: rgba(255,255,255,0.9);
}
.event-header-category {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(22px, 3.5vw, 52px);
  letter-spacing: 0.04em;
  text-transform: none;
  color: rgba(255,255,255,0.45);
}

/* ── PAIR layout: stacked names left | timer right ───────────────────────── */
.pair-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  justify-content: center;
  gap: clamp(40px, 8vw, 130px);
  width: 100%;
  max-width: 90vw;
}
/* Left column: both names stacked */
.pair-names {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
}
.pair-name-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.pair-entry-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}
.pair-name-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: nowrap;
}
.pair-member-names {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(14px, 1.8vw, 22px);
  letter-spacing: 0.02em;
  color: rgba(255,255,255,0.45);
  text-transform: none;
  margin-top: 2px;
}
/* 3-way: shrink name + number to fit 3 entries on screen */
.pair-names-threeway .audition-number { font-size: clamp(28px, 4vw, 52px) !important; }
.pair-names-threeway .participant-name { font-size: clamp(34px, 5.5vw, 80px) !important; }
.pair-names-threeway .pair-member-names { font-size: clamp(12px, 1.4vw, 18px); }
/* Thin divider between the two entries */
.pair-divider {
  height: 2px;
  width: 100%;
  background: rgba(255,255,255,0.1);
  margin: 4px 0;
}

/* ── Battle position labels ──────────────────────────────────────────────── */
.position-label {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(13px, 1.5vw, 20px);
  letter-spacing: 0.22em;
  text-transform: none;
  opacity: 0.85;
  flex-shrink: 0;
  align-self: center;
}
.position-left   { color: rgb(252, 211, 77); text-transform: none; }  /* amber-300 */
.position-middle { color: rgb(252, 211, 77); text-transform: none; }  /* amber-300 */
.position-right  { color: rgb(252, 211, 77); text-transform: none; }  /* amber-300 */

/* ── SOLO layout: slot | timer ────────────────────────────────────────────── */
.slot-timer-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: clamp(60px, 10vw, 160px);
  width: 100%;
  max-width: 90vw;
}

.current-slots {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  min-width: 0;
}

.slot-entry {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.audition-number {
  font-size: clamp(36px, 6vw, 72px);
  line-height: 1;
  letter-spacing: 0.02em;
  text-shadow: 2px 2px 0 var(--accent-muted, rgba(255,255,255,0.15));
  color: var(--accent-color, rgba(255,255,255,0.6));
}

.participant-name {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(48px, 9vw, 120px);
  line-height: 1.05;
  letter-spacing: 0.02em;
  text-transform: none;
  color: #ffffff;
  margin-top: 2px;
  padding-bottom: 0.15em; /* room for lowercase descenders so member names don't get covered */
  hyphens: none;
  overflow-wrap: normal;
  word-break: keep-all;
}

.member-names {
  font-family: 'Oswald', sans-serif;
  font-size: 18px;
  letter-spacing: 0.02em;
  color: rgba(255,255,255,0.45);
  text-transform: none;
  margin-top: 6px;
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
  flex-shrink: 0;
  /* Inherit timer font-size so min-width in ch scales with the digits.
     2.5ch covers typical 2-digit timers (30-90s); tabular-nums
     prevents intra-digit jitter without reserving excess space. */
  font-size: clamp(100px, 18vw, 220px);
  min-width: 2.5ch;
  text-align: center;
}

.timer-number {
  font-size: inherit;
  line-height: 1;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
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

/* Up Next: name + members shown as typed (sentence case) */
.next-slot-name {
  font-family: 'Oswald', sans-serif;
  font-size: 22px;
  letter-spacing: 0.02em;
  text-transform: none;
  color: rgba(255,255,255,0.55);
}
.next-slot-members {
  font-family: 'Oswald', sans-serif;
  font-size: 14px;
  letter-spacing: 0.02em;
  text-transform: none;
  color: rgba(255,255,255,0.30);
}

/* ── Section rule (defined locally since this is a standalone page) ───────── */
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

/* ── Operator overlay (hidden in OBS, visible when logged in) ─────────────── */
.operator-overlay {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  font-family: 'Oswald', sans-serif;
  text-transform: uppercase;
}
.op-toggle {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(20, 20, 20, 0.7);
  color: rgba(255,255,255,0.55);
  border: 1px solid rgba(255,255,255,0.12);
  cursor: pointer;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  transition: color 0.15s ease, background 0.15s ease;
  backdrop-filter: blur(6px);
}
.op-toggle:hover { color: rgba(255,255,255,0.9); background: rgba(30,30,30,0.75); }
.op-toggle-active { color: var(--accent-color); }
.op-panel {
  width: 320px;
  background: rgba(15,15,15,0.92);
  border: 1px solid rgba(255,255,255,0.10);
  backdrop-filter: blur(8px);
  padding: 16px;
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  box-shadow: 0 12px 40px rgba(0,0,0,0.55);
}
.op-panel-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  margin-bottom: 12px;
}
.op-panel-title {
  font-size: 13px;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.7);
}
.op-panel-category {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--accent-color);
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.op-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.op-row-label {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.55);
}
.op-row-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
.op-color-input {
  width: 32px;
  height: 32px;
  padding: 2px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.12);
  cursor: pointer;
  border-radius: 2px;
}
.op-color-input:disabled { cursor: not-allowed; opacity: 0.4; }
.op-row-value {
  font-size: 10px;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.45);
  min-width: 56px;
  text-transform: uppercase;
}
.op-reset {
  background: transparent;
  border: none;
  color: rgba(255,255,255,0.4);
  cursor: pointer;
  font-size: 10px;
}
.op-reset:hover { color: rgba(255,255,255,0.85); }
.op-warn {
  margin-top: 12px;
  font-family: 'Inter', sans-serif;
  font-size: 11px;
  letter-spacing: 0.01em;
  text-transform: none;
  color: rgba(245,158,11,0.85);
  line-height: 1.4;
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
</style>
