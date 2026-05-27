# 7-to-Smoke Overlay Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign `Chart.vue` (and clean up `BattleOverlay.vue` smoke mode) to show a bold glowing bar chart matching the 1v1 overlay's visual style, with FLIP queue sliding, score pop animations, judge result overlay, and champion takeover overlay.

**Architecture:** Chart.vue is a full rewrite as a standalone full-screen overlay component. It owns all WebSocket subscriptions for smoke mode. BattleOverlay.vue's smoke section already renders `<Chart />`, but currently also subscribes to the same topics and shows a conflicting judge panel — those are removed in Task 6. A small helper module extracts testable pure functions.

**Tech Stack:** Vue 3 Composition API, CSS custom properties (`--left-color`/`--right-color`), Vue `TransitionGroup` (for FLIP), `useDelay` from utils/utils.js, existing WebSocket helpers (createClient/subscribeToChannel/deactivateClient), existing API helpers (getSmokeList, getOverlayConfig, getBattleJudges).

---

## File Map

| File | Action |
|------|--------|
| `BES-frontend/src/utils/smokeChartHelpers.js` | Create — pure helpers for bar height + score gainer detection |
| `BES-frontend/src/utils/__tests__/smokeChartHelpers.test.js` | Create — Vitest tests |
| `BES-frontend/src/views/Chart.vue` | Full rewrite — new overlay UI |
| `BES-frontend/src/views/BattleOverlay.vue` | Modify — remove isSmoke WS block, guard judge panel |

---

## Key Data Shapes (read before coding)

```js
// smokeParticipants — from getSmokeList() → { list: [...] } and WS /topic/battle/smoke → { battlers: [...] }
// [{ name: "VIBRAZE", score: 3 }, { name: "KRAZIX", score: 4 }, { name: "RYZEN", score: 2 }, ...]
// Index 0 = active left, index 1 = active right, index 2+ = waiting queue

// battleJudges — from getBattleJudges() and WS /topic/battle/judges
// { judges: [{ id: 1, name: "JUDGE A", vote: -3|0|1|-1|null }] }
// vote: -3 = cleared (new match), 0 = voted left, 1 = voted right, -1 = tie, null = not yet voted

// Score reveal — WS /topic/battle/score
// { left: <leftScore>, right: <rightScore>, message: 0|1|-1 }
// message: 0 = left wins, 1 = right wins, -1 = tie

// overlayConfig — from getOverlayConfig() and WS /topic/battle/overlay-config
// { showImages: bool, leftColor: "#dc2626", rightColor: "#2563eb" }
```

---

## Task 1: Pure helpers + tests

**Files:**
- Create: `BES-frontend/src/utils/smokeChartHelpers.js`
- Create: `BES-frontend/src/utils/__tests__/smokeChartHelpers.test.js`

- [ ] **Step 1: Write the failing tests**

```js
// BES-frontend/src/utils/__tests__/smokeChartHelpers.test.js
import { describe, it, expect } from 'vitest'
import { barHeightPct, findScoreGainers } from '../smokeChartHelpers'

describe('barHeightPct', () => {
  it('returns 0 for score 0', () => {
    expect(barHeightPct(0)).toBe(0)
  })
  it('returns 100 for score 7', () => {
    expect(barHeightPct(7)).toBe(100)
  })
  it('returns correct percentage for mid scores', () => {
    expect(barHeightPct(3)).toBeCloseTo(42.857, 2)
    expect(barHeightPct(4)).toBeCloseTo(57.143, 2)
  })
  it('clamps to 100 if score exceeds 7', () => {
    expect(barHeightPct(8)).toBe(100)
  })
})

describe('findScoreGainers', () => {
  it('returns names whose score increased', () => {
    const prev = [{ name: 'A', score: 2 }, { name: 'B', score: 1 }]
    const next = [{ name: 'A', score: 3 }, { name: 'B', score: 1 }]
    expect(findScoreGainers(prev, next)).toEqual(['A'])
  })
  it('returns empty array when no scores changed', () => {
    const participants = [{ name: 'A', score: 2 }, { name: 'B', score: 1 }]
    expect(findScoreGainers(participants, participants)).toEqual([])
  })
  it('handles new participant with no previous score', () => {
    const prev = [{ name: 'A', score: 1 }]
    const next = [{ name: 'A', score: 1 }, { name: 'B', score: 1 }]
    expect(findScoreGainers(prev, next)).toEqual(['B'])
  })
  it('returns multiple gainers', () => {
    const prev = [{ name: 'A', score: 1 }, { name: 'B', score: 1 }]
    const next = [{ name: 'A', score: 2 }, { name: 'B', score: 2 }]
    expect(findScoreGainers(prev, next)).toEqual(['A', 'B'])
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd BES-frontend && npm test -- smokeChartHelpers
```
Expected: FAIL — "Cannot find module '../smokeChartHelpers'"

- [ ] **Step 3: Create the helper module**

```js
// BES-frontend/src/utils/smokeChartHelpers.js

/**
 * Returns bar height as a percentage (0–100) for a given score out of 7.
 * Clamped to 100 so bars never overflow their container.
 */
export const barHeightPct = (score) => Math.min((score / 7) * 100, 100)

/**
 * Given two snapshots of participants, returns the names of participants
 * whose score increased between snapshots. Used to trigger score pop animation.
 */
export const findScoreGainers = (prevParticipants, nextParticipants) => {
  const prevMap = Object.fromEntries(prevParticipants.map(p => [p.name, p.score]))
  return nextParticipants
    .filter(n => n.score > (prevMap[n.name] ?? 0))
    .map(n => n.name)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd BES-frontend && npm test -- smokeChartHelpers
```
Expected: PASS — 8 tests passing

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/utils/smokeChartHelpers.js BES-frontend/src/utils/__tests__/smokeChartHelpers.test.js
git commit -m "feat: add smoke chart pure helpers + tests"
```

---

## Task 2: Chart.vue — base structure, bar chart, floating pill, WebSocket

**Files:**
- Modify: `BES-frontend/src/views/Chart.vue` (full rewrite)

**Context:** The current Chart.vue (87 lines) is a placeholder with basic Tailwind bars and image loading. Replace it entirely. The new Chart.vue is a self-contained full-screen overlay. It mounts `transparent-page` class (for OBS), subscribes to all smoke WebSocket topics, and renders the bar chart.

**Important:** `position: fixed; inset: 0` on the root div means Chart.vue works both as a standalone route (`/battle/chart`) and when embedded inside BattleOverlay.vue's smoke section.

- [ ] **Step 1: Write the full Chart.vue rewrite**

```vue
<!-- BES-frontend/src/views/Chart.vue -->
<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { getSmokeList, getOverlayConfig, getBattleJudges } from '@/utils/api'
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket'
import { useDelay } from '@/utils/utils'
import { barHeightPct, findScoreGainers } from '@/utils/smokeChartHelpers'

// ── Overlay config ──────────────────────────────────────────────────────────
const overlayConfig = ref({ leftColor: '#dc2626', rightColor: '#2563eb' })

// ── Chart data ──────────────────────────────────────────────────────────────
// Position 0 = active left, 1 = active right, 2+ = queue
const smokeParticipants = ref([])

// ── Judge data ──────────────────────────────────────────────────────────────
const battleJudges = ref(null)       // { judges: [{ id, name, vote }] }
const votesVisible = ref(false)

// ── Result overlay ──────────────────────────────────────────────────────────
const showResult    = ref(false)
const resultState   = ref(null)  // { winner: 0|1|-1, leftName, rightName }

// ── Champion overlay ────────────────────────────────────────────────────────
const showChampion  = ref(false)
const champName     = ref('')
const champColor    = ref('#dc2626')

// ── Score pop animation ─────────────────────────────────────────────────────
const scorePopNames = ref(new Set())

// ── WebSocket clients ───────────────────────────────────────────────────────
const clients = []
const subscribedVoteTopics = new Set()
let unmounted = false

// ── Computed ────────────────────────────────────────────────────────────────
const activeLeft  = computed(() => smokeParticipants.value[0] ?? null)
const activeRight = computed(() => smokeParticipants.value[1] ?? null)

// ── Score pop: watch for score increases ────────────────────────────────────
watch(
  smokeParticipants,
  (next, prev) => {
    if (!prev?.length) return
    const gainers = findScoreGainers(prev, next)
    gainers.forEach(name => {
      scorePopNames.value = new Set([...scorePopNames.value, name])
      setTimeout(() => {
        scorePopNames.value = new Set([...scorePopNames.value].filter(n => n !== name))
      }, 600)
    })
  },
  { deep: true }
)

// ── WebSocket handlers ──────────────────────────────────────────────────────
const updateList = (msg) => {
  if (msg?.battlers) smokeParticipants.value = msg.battlers
}

const updateBattleJudge = (msg) => {
  battleJudges.value = msg
  msg.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`
    if (!subscribedVoteTopics.has(topic)) {
      subscribedVoteTopics.add(topic)
      const c = createClient(); clients.push(c)
      subscribeToChannel(c, topic, (m) => updateJudgeVote(m))
    }
  })
}

const updateJudgeVote = (msg) => {
  if (!battleJudges.value?.judges) return
  battleJudges.value = {
    ...battleJudges.value,
    judges: battleJudges.value.judges.map(j =>
      j.id === msg.judge ? { ...j, vote: msg.vote } : j
    )
  }
}

// Capture active names at reveal time so result overlay stays correct after queue shifts
const updateScore = async (msg) => {
  const leftName  = activeLeft.value?.name  ?? ''
  const rightName = activeRight.value?.name ?? ''
  const winner    = msg.message // 0 | 1 | -1

  // Check for champion (any fighter reaching 7)
  const allParticipants = smokeParticipants.value
  const champParticipant = allParticipants.find(p => p.score >= 7)

  if (champParticipant) {
    champName.value  = champParticipant.name
    const idx = allParticipants.indexOf(champParticipant)
    champColor.value = idx === 0
      ? overlayConfig.value.leftColor
      : idx === 1
        ? overlayConfig.value.rightColor
        : overlayConfig.value.leftColor
    showChampion.value = true
    return  // Champion overlay never auto-dismisses
  }

  // Regular result overlay
  resultState.value = { winner, leftName, rightName }
  votesVisible.value = true
  showResult.value = true

  await useDelay().wait(4000)
  if (unmounted) return
  showResult.value = false
  votesVisible.value = false
}

// ── Mount ───────────────────────────────────────────────────────────────────
onMounted(async () => {
  document.documentElement.classList.add('transparent-page')
  document.body.classList.add('transparent-page')

  const config = await getOverlayConfig()
  overlayConfig.value = config

  const smoke = await getSmokeList()
  if (smoke?.list) smokeParticipants.value = smoke.list

  battleJudges.value = await getBattleJudges()

  const cConfig = createClient(); clients.push(cConfig)
  subscribeToChannel(cConfig, '/topic/battle/overlay-config', (msg) => {
    if (msg?.leftColor !== undefined) overlayConfig.value = msg
  })

  const cSmoke = createClient(); clients.push(cSmoke)
  subscribeToChannel(cSmoke, '/topic/battle/smoke', updateList)

  const cJudges = createClient(); clients.push(cJudges)
  subscribeToChannel(cJudges, '/topic/battle/judges', updateBattleJudge)

  const cScore = createClient(); clients.push(cScore)
  subscribeToChannel(cScore, '/topic/battle/score', updateScore)
})

onBeforeUnmount(() => {
  unmounted = true
  clients.forEach(c => { if (c) deactivateClient(c) })
  document.documentElement.classList.remove('transparent-page')
  document.body.classList.remove('transparent-page')
})
</script>

<template>
  <div
    class="smoke-root"
    :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor }"
  >
    <!-- Atmospheric background bleeds -->
    <div class="atmo-bleed" aria-hidden="true"></div>

    <!-- Header -->
    <div class="smoke-header" aria-hidden="true">
      <span class="smoke-title">7 TO SMOKE</span>
    </div>

    <!-- Bar chart -->
    <div class="smoke-chart">
      <TransitionGroup tag="div" name="col" class="smoke-cols">
        <template v-for="(item, idx) in smokeParticipants" :key="item.name">
          <!-- VS chip between position 0 and 1 -->
          <div v-if="idx === 1" class="vs-chip" aria-hidden="true">VS</div>

          <!-- Queue gap spacer between position 1 and 2 -->
          <div v-if="idx === 2" class="queue-gap" aria-hidden="true"></div>

          <!-- Fighter column -->
          <div
            class="smoke-col"
            :class="{
              'col-active-left':  idx === 0,
              'col-active-right': idx === 1,
            }"
          >
            <div class="bar-wrap">
              <div
                class="bar"
                :class="{ 'score-pop': scorePopNames.has(item.name) }"
                :style="{ height: barHeightPct(item.score) + '%' }"
                :aria-label="`${item.name}: ${item.score} points`"
              ></div>
            </div>
            <div class="dots-row" aria-hidden="true">
              <div
                v-for="d in 7"
                :key="d"
                class="dot"
                :class="{ filled: d <= item.score }"
              ></div>
            </div>
            <div class="col-name">{{ item.name }}</div>
          </div>
        </template>
      </TransitionGroup>

      <!-- Floating pill: active pair indicator -->
      <div
        v-if="activeLeft && activeRight"
        class="float-pill"
        role="status"
        :aria-label="`Now battling: ${activeLeft.name} vs ${activeRight.name}`"
      >
        <span class="pill-name pill-left">{{ activeLeft.name }}</span>
        <span class="pill-vs">VS</span>
        <span class="pill-name pill-right">{{ activeRight.name }}</span>
        <div class="pill-sep" aria-hidden="true"></div>
        <span class="pill-pts">{{ activeLeft.score }} PTS · {{ activeRight.score }} PTS</span>
      </div>
    </div>

    <!-- Result overlay (win / tie) -->
    <Transition name="result-fade">
      <div
        v-if="showResult && resultState && !showChampion"
        class="result-overlay"
        role="status"
        :aria-live="'assertive'"
      >
        <!-- Win header -->
        <template v-if="resultState.winner === 0">
          <div class="result-name result-left">{{ resultState.leftName }}</div>
          <div class="result-sub">TAKES THE POINT</div>
        </template>
        <template v-else-if="resultState.winner === 1">
          <div class="result-name result-right">{{ resultState.rightName }}</div>
          <div class="result-sub">TAKES THE POINT</div>
        </template>
        <template v-else>
          <div class="result-name result-tie">IT'S A TIE</div>
          <div class="result-sub">BOTH EARN A POINT</div>
        </template>

        <!-- Judge cards -->
        <div v-if="battleJudges?.judges" class="judge-inner">
          <div class="judges-header" aria-hidden="true">
            <span class="judges-line"></span>
            <span class="judges-label">JUDGES</span>
            <span class="judges-line"></span>
          </div>
          <div class="judge-cards-row" role="list">
            <div
              v-for="(j, index) in battleJudges.judges"
              :key="index"
              class="judge-card card-burst"
              :class="{
                'voted-left':  j.vote === 0,
                'voted-right': j.vote === 1,
                'voted-tie':   j.vote === -1,
              }"
              :style="{ animationDelay: `${index * 55}ms` }"
              role="listitem"
              :aria-label="`${j.name}: ${j.vote === 0 ? 'voted left' : j.vote === 1 ? 'voted right' : 'tie'}`"
            >
              <div v-if="j.vote !== -1" class="judge-row">
                <span class="vote-arrow vote-arrow-left" :class="{ 'arrow-lit-left': j.vote === 0 }" aria-hidden="true"></span>
                <span class="judge-name">{{ j.name }}</span>
                <span class="vote-arrow vote-arrow-right" :class="{ 'arrow-lit-right': j.vote === 1 }" aria-hidden="true"></span>
              </div>
              <div v-else class="judge-row">
                <span class="judge-name judge-name-tie">{{ j.name }}</span>
              </div>
              <div v-if="j.vote !== -1" class="vote-track" aria-hidden="true">
                <div class="vote-fill" :class="{ 'fill-left': j.vote === 0, 'fill-right': j.vote === 1, 'fill-blank': j.vote !== 0 && j.vote !== 1 }"></div>
              </div>
              <div v-else class="tie-badge" aria-hidden="true">TIE</div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Champion overlay -->
    <Transition name="champ-fade">
      <div
        v-if="showChampion"
        class="champion-overlay"
        :style="{ '--champ-color': champColor }"
        role="status"
        aria-live="assertive"
        :aria-label="`${champName} is the 7 to Smoke champion`"
      >
        <div class="champ-burst" aria-hidden="true"></div>
        <div class="champ-name">{{ champName }}</div>
        <div class="champ-sub">7 TO SMOKE CHAMPION</div>
      </div>
    </Transition>
  </div>
</template>

<style>
/* OBS transparent background — must be global */
html.transparent-page,
body.transparent-page,
html.transparent-page #app,
body.transparent-page #app {
  background: transparent !important;
  background-color: transparent !important;
}
</style>

<style scoped>
/* ── Root ─────────────────────────────────────────────── */
.smoke-root {
  position: fixed;
  inset: 0;
  font-family: 'Anton SC', sans-serif;
  background: #06080e;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  --left-color:  #dc2626;
  --right-color: #2563eb;
}

/* ── Atmospheric bleeds ───────────────────────────────── */
.atmo-bleed {
  position: absolute; inset: 0; z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 60% at 0% 100%,   color-mix(in srgb, var(--left-color)  16%, transparent) 0%, transparent 70%),
    radial-gradient(ellipse 60% 60% at 100% 100%, color-mix(in srgb, var(--right-color) 16%, transparent) 0%, transparent 70%);
}

/* ── Header ───────────────────────────────────────────── */
.smoke-header {
  position: relative; z-index: 2; flex-shrink: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 18px 6px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(8px);
}
.smoke-title {
  font-size: clamp(10px, 1.4vw, 16px);
  letter-spacing: 0.22em;
  color: rgba(255,255,255,0.5);
}

/* ── Chart area ───────────────────────────────────────── */
.smoke-chart {
  position: relative; z-index: 2;
  flex: 1; min-height: 0;
  display: flex; flex-direction: column;
}

.smoke-cols {
  flex: 1; min-height: 0;
  display: flex;
  align-items: flex-end;
  padding: 10px 14px 0;
  gap: 5px;
  overflow: hidden;
}

/* ── Fighter column ───────────────────────────────────── */
.smoke-col {
  flex: 1;
  display: flex; flex-direction: column; align-items: center;
  height: 100%;
}

/* ── Bar wrap + bar ───────────────────────────────────── */
.bar-wrap {
  flex: 1; width: 100%;
  display: flex; flex-direction: column; justify-content: flex-end;
  position: relative;
}
/* Dashed goal line at top */
.bar-wrap::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0;
  border-top: 1px dashed rgba(255,255,255,0.07);
}
.bar {
  width: 100%;
  border-radius: 5px 5px 2px 2px;
  min-height: 3px;
  position: relative; overflow: hidden;
  transition: height 0.6s cubic-bezier(0.22, 1, 0.36, 1);
  /* Default: waiting/gray */
  background: linear-gradient(0deg, #181818 0%, #2a2a2a 100%);
}
/* Glossy top highlight — only on active bars */
.bar::after {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 45%;
  background: linear-gradient(180deg, rgba(255,255,255,0.14) 0%, transparent 100%);
  border-radius: 5px 5px 0 0;
  pointer-events: none;
  display: none;
}

/* Active left — red */
.col-active-left .bar {
  background: linear-gradient(0deg,
    color-mix(in srgb, var(--left-color) 35%, #000) 0%,
    var(--left-color) 75%,
    color-mix(in srgb, var(--left-color) 50%, #fff) 100%
  );
  box-shadow:
    0 0 22px color-mix(in srgb, var(--left-color) 65%, transparent),
    0 0 60px color-mix(in srgb, var(--left-color) 25%, transparent),
    inset 0 0 0 1.5px rgba(255,255,255,0.2);
}
.col-active-left .bar::after { display: block; }

/* Active right — blue */
.col-active-right .bar {
  background: linear-gradient(0deg,
    color-mix(in srgb, var(--right-color) 35%, #000) 0%,
    var(--right-color) 75%,
    color-mix(in srgb, var(--right-color) 50%, #fff) 100%
  );
  box-shadow:
    0 0 22px color-mix(in srgb, var(--right-color) 65%, transparent),
    0 0 60px color-mix(in srgb, var(--right-color) 25%, transparent),
    inset 0 0 0 1.5px rgba(255,255,255,0.2);
}
.col-active-right .bar::after { display: block; }

/* ── Dots row ─────────────────────────────────────────── */
.dots-row {
  display: flex; gap: 2px; margin-top: 5px; justify-content: center;
  flex-shrink: 0; flex-wrap: wrap;
}
.dot {
  width: clamp(4px, 0.6vw, 7px); height: clamp(4px, 0.6vw, 7px);
  border-radius: 50%;
  border: 1.5px solid currentColor;
  opacity: 0.25;
  color: rgba(255,255,255,0.5);
}
.dot.filled { opacity: 1; background: currentColor; }
.col-active-left  .dot { color: var(--left-color); }
.col-active-right .dot { color: var(--right-color); }

/* ── Name label ───────────────────────────────────────── */
.col-name {
  font-size: clamp(5px, 0.8vw, 9px);
  letter-spacing: 0.1em;
  text-align: center;
  margin-top: 3px; margin-bottom: 5px;
  color: rgba(255,255,255,0.28);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  width: 100%; padding: 0 2px;
}
.col-active-left .col-name {
  color: color-mix(in srgb, var(--left-color) 50%, #fff);
  text-shadow: 0 0 12px color-mix(in srgb, var(--left-color) 80%, transparent);
}
.col-active-right .col-name {
  color: color-mix(in srgb, var(--right-color) 50%, #fff);
  text-shadow: 0 0 12px color-mix(in srgb, var(--right-color) 80%, transparent);
}

/* ── VS chip ──────────────────────────────────────────── */
.vs-chip {
  flex: 0 0 clamp(16px, 2.2vw, 26px);
  align-self: flex-end;
  margin-bottom: clamp(20px, 3vw, 30px);
  display: flex; align-items: center; justify-content: center;
  font-size: clamp(7px, 0.9vw, 10px);
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.5);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 4px;
  background: rgba(255,255,255,0.04);
  padding: 3px 0; line-height: 1;
}

/* ── Queue gap ────────────────────────────────────────── */
.queue-gap { flex: 0 0 clamp(8px, 1.4vw, 16px); }

/* ── Floating pill ────────────────────────────────────── */
.float-pill {
  position: absolute;
  bottom: 10px; left: 50%; transform: translateX(-50%);
  z-index: 3;
  display: flex; align-items: center; gap: 7px;
  background: rgba(0,0,0,0.65);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 99px;
  padding: 4px 16px;
  white-space: nowrap;
}
.pill-name {
  font-size: clamp(9px, 1.3vw, 15px);
  letter-spacing: 0.08em;
}
.pill-left  { color: color-mix(in srgb, var(--left-color)  50%, #fff); }
.pill-right { color: color-mix(in srgb, var(--right-color) 50%, #fff); }
.pill-vs {
  font-size: clamp(7px, 0.85vw, 10px);
  letter-spacing: 0.15em;
  color: rgba(255,255,255,0.35);
  padding: 0 2px;
}
.pill-sep {
  width: 1px; height: 12px;
  background: rgba(255,255,255,0.12);
}
.pill-pts {
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  font-size: clamp(6px, 0.75vw, 8px);
  letter-spacing: 0.12em;
  color: rgba(255,255,255,0.3);
}

/* ── FLIP column slide (TransitionGroup move) ─────────── */
.col-move {
  transition: transform 0.55s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ══════════════════════════════════════════════════
   RESULT OVERLAY
══════════════════════════════════════════════════ */
.result-overlay {
  position: absolute; inset: 0; z-index: 50;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 6px;
  background: rgba(6, 8, 18, 0.72);
  backdrop-filter: blur(4px);
}

.result-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(28px, 5.5vw, 88px);
  letter-spacing: 0.07em;
  line-height: 1;
  text-transform: uppercase;
  animation: resultSlam 520ms cubic-bezier(0.2, 0, 0.3, 1) both;
}
.result-left {
  color: #fff;
  text-shadow: 4px 4px 0 var(--left-color),
               0 0 50px color-mix(in srgb, var(--left-color) 50%, transparent);
}
.result-right {
  color: #fff;
  text-shadow: -4px 4px 0 var(--right-color),
               0 0 50px color-mix(in srgb, var(--right-color) 50%, transparent);
}
.result-tie {
  color: #fff;
  text-shadow: 0 0 40px rgba(255,255,255,0.3);
}
.result-sub {
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  font-size: clamp(9px, 1.1vw, 16px);
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.35);
  margin-top: 2px; margin-bottom: 16px;
}

/* Judge inner container — copied from BattleOverlay.vue */
.judge-inner {
  position: relative;
  display: flex; flex-direction: column; gap: 8px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--left-color)  12%, rgba(6,8,18,0.94)),
    color-mix(in srgb, var(--right-color) 12%, rgba(6,8,18,0.94))
  );
  backdrop-filter: blur(24px);
  border-radius: 14px;
  border: 1px solid rgba(255,255,255,0.12);
  box-shadow: 0 24px 80px rgba(0,0,0,0.75), 0 0 0 1px rgba(255,255,255,0.04) inset;
  padding: 12px 24px 16px;
}
.judges-header { display: flex; align-items: center; gap: 8px; }
.judges-label {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 700;
  letter-spacing: 0.32em; color: rgba(255,255,255,0.28);
  text-transform: uppercase; white-space: nowrap; flex-shrink: 0;
}
.judges-line { flex: 1; height: 1px; background: rgba(255,255,255,0.07); }
.judge-cards-row { display: flex; gap: 8px; }

/* Judge card — parallelogram */
.judge-card {
  display: flex; flex-direction: column; align-items: center; gap: 7px;
  min-width: 130px; padding: 8px 12px;
  clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.06);
}
.voted-left {
  background: color-mix(in srgb, var(--left-color)  12%, transparent);
  border-color: color-mix(in srgb, var(--left-color)  40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--left-color)  25%, transparent);
}
.voted-right {
  background: color-mix(in srgb, var(--right-color) 12%, transparent);
  border-color: color-mix(in srgb, var(--right-color) 40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--right-color) 25%, transparent);
}
.voted-tie {
  background: rgba(255,255,255,0.06);
  border-color: rgba(255,255,255,0.20);
}
.judge-row { display: flex; align-items: center; gap: 9px; }
.judge-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 22px; color: rgba(255,255,255,0.90);
  letter-spacing: 0.06em; text-transform: uppercase; line-height: 1;
}
.judge-name-tie { color: rgba(255,255,255,0.32) !important; }
.vote-arrow {
  display: inline-block; width: 18px; height: 18px; flex-shrink: 0;
  background: rgba(255,255,255,0.15); opacity: 0.2;
  transition: opacity 0.2s ease, background 0.2s ease, filter 0.2s ease;
}
.vote-arrow-left  { clip-path: polygon(100% 0%, 0% 50%, 100% 100%); }
.vote-arrow-right { clip-path: polygon(0% 0%, 100% 50%, 0% 100%); }
.arrow-lit-left  { opacity: 1; background: var(--left-color);  filter: drop-shadow(0 0 8px var(--left-color)); }
.arrow-lit-right { opacity: 1; background: var(--right-color); filter: drop-shadow(0 0 8px var(--right-color)); }
.tie-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px; letter-spacing: 0.35em;
  color: rgba(255,255,255,0.55); text-align: center; width: 100%;
  border: 1px solid rgba(255,255,255,0.18); border-radius: 4px; padding: 2px 0;
}
.vote-track {
  width: 100%; height: 10px;
  background: rgba(255,255,255,0.1); border-radius: 9999px; overflow: hidden;
}
.vote-fill { height: 100%; border-radius: 9999px; width: 0%; }
.fill-left  {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--left-color)  70%, black), var(--left-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--left-color)  90%, transparent);
}
.fill-right {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--right-color) 70%, black), var(--right-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--right-color) 90%, transparent);
}
.fill-blank { background: transparent; }

/* Result overlay fade transition */
.result-fade-enter-active { transition: opacity 0.25s ease; }
.result-fade-leave-active  { transition: opacity 0.4s ease; }
.result-fade-enter-from, .result-fade-leave-to { opacity: 0; }

/* ══════════════════════════════════════════════════
   CHAMPION OVERLAY
══════════════════════════════════════════════════ */
.champion-overlay {
  position: absolute; inset: 0; z-index: 60;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 10px;
  background: rgba(4, 5, 12, 0.88);
  --champ-color: #dc2626;
}
.champ-burst {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(
    ellipse 70% 60% at 50% 50%,
    color-mix(in srgb, var(--champ-color) 30%, transparent) 0%,
    transparent 70%
  );
  animation: champBurst 0.6s ease-out both;
}
.champ-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(36px, 7vw, 110px);
  letter-spacing: 0.08em; line-height: 1;
  text-transform: uppercase;
  color: #fff;
  text-shadow: 4px 4px 0 var(--champ-color),
               0 0 60px color-mix(in srgb, var(--champ-color) 60%, transparent);
  animation: champSlam 720ms cubic-bezier(0.2, 0, 0.3, 1) both;
}
.champ-sub {
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  font-size: clamp(10px, 1.3vw, 18px);
  letter-spacing: 0.4em; text-transform: uppercase;
  color: rgba(255,255,255,0.4);
  animation: champSubFade 0.5s ease both;
  animation-delay: 0.45s;
  opacity: 0;
}

.champ-fade-enter-active { transition: opacity 0.2s ease; }
.champ-fade-leave-active  { transition: opacity 0.5s ease; }
.champ-fade-enter-from, .champ-fade-leave-to { opacity: 0; }

/* ══════════════════════════════════════════════════
   KEYFRAMES
══════════════════════════════════════════════════ */
@keyframes resultSlam {
  0%   { transform: scale(2.2) translateY(-16px); opacity: 0; filter: blur(10px); }
  55%  { transform: scale(0.97) translateY(0);    opacity: 1; filter: blur(0); }
  72%  { transform: scale(1.03); }
  85%  { transform: scale(0.99); }
  100% { transform: scale(1); }
}

@keyframes champSlam {
  0%   { transform: scale(2.5) translateY(-20px); opacity: 0; filter: blur(12px); }
  55%  { transform: scale(0.96) translateY(0);    opacity: 1; filter: blur(0); }
  72%  { transform: scale(1.04); }
  85%  { transform: scale(0.99); }
  100% { transform: scale(1); }
}

@keyframes champBurst {
  0%   { opacity: 0; transform: scale(0.5); }
  60%  { opacity: 1; transform: scale(1.05); }
  100% { opacity: 0.85; transform: scale(1); }
}

@keyframes champSubFade {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes scorePop {
  0%   { transform: scaleY(1) scaleX(1); filter: brightness(1); }
  18%  { transform: scaleY(1.07) scaleX(1.04); filter: brightness(2.5); }
  38%  { transform: scaleY(0.96) scaleX(1.02); filter: brightness(1.5); }
  58%  { transform: scaleY(1.02) scaleX(1.01); filter: brightness(1.1); }
  100% { transform: scaleY(1) scaleX(1); filter: brightness(1); }
}
.score-pop {
  animation: scorePop 600ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

@keyframes cardBurst {
  0%   { transform: scale(1.4) skewX(-5deg); opacity: 0; }
  65%  { transform: scale(0.97) skewX(0);   opacity: 1; }
  100% { transform: scale(1) skewX(0);      opacity: 1; }
}
.card-burst {
  animation: cardBurst 280ms cubic-bezier(0.2, 0, 0.3, 1) both;
}
</style>
```

- [ ] **Step 2: Run the frontend to verify it builds**

```bash
cd BES-frontend && npm run build
```
Expected: build succeeds with no errors

- [ ] **Step 3: Run dev server and open `/battle/chart` in browser to verify layout**

```bash
cd BES-frontend && npm run dev
```
Open `http://localhost:5173/battle/chart` — should see dark overlay with "7 TO SMOKE" header. No data yet (no WebSocket running), so empty chart area with floating pill showing nothing. No console errors.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/Chart.vue BES-frontend/src/utils/smokeChartHelpers.js BES-frontend/src/utils/__tests__/smokeChartHelpers.test.js
git commit -m "feat: rewrite Chart.vue as bold 7-to-smoke bar chart overlay"
```

---

## Task 3: Update BattleOverlay.vue — remove conflicting smoke subscriptions + judge panel

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

**Context:** BattleOverlay.vue currently subscribes to battle topics when `isSmoke=true` AND renders the judge panel for both modes. Chart.vue now owns all smoke WebSocket logic, so BattleOverlay must stop competing. Two changes needed:

1. In `onMounted`, guard the smoke subscription block: delete it (Chart.vue handles this now)
2. On the judge panel `v-if`, add `&& !isSmoke` so the panel is hidden in smoke mode

- [ ] **Step 1: Remove the isSmoke subscription block from onMounted**

Find this block in `BES-frontend/src/views/BattleOverlay.vue` (around line 255):

```js
  if (isSmoke.value) {
    battleJudges.value = await getBattleJudges()
    const cJudges = createClient(); clients.push(cJudges)
    const cPair   = createClient(); clients.push(cPair)
    const cScore  = createClient(); clients.push(cScore)
    subscribeToChannel(cJudges, '/topic/battle/judges',      (msg) => updateBattleJudge(msg))
    subscribeToChannel(cPair,   '/topic/battle/battle-pair', (msg) => updateBattlePair(msg))
    subscribeToChannel(cScore,  '/topic/battle/score',       (msg) => updateScore(msg))
  } else {
    battleJudges.value = await getBattleJudges()
    const res = await getCurrentBattlePair()
    if (res) await updateBattlePair(res)
    const cPair2   = createClient(); clients.push(cPair2)
    const cScore2  = createClient(); clients.push(cScore2)
    const cJudges2 = createClient(); clients.push(cJudges2)
    subscribeToChannel(cPair2,   '/topic/battle/battle-pair', (msg) => updateBattlePair(msg))
    subscribeToChannel(cScore2,  '/topic/battle/score',       (msg) => updateScore(msg))
    subscribeToChannel(cJudges2, '/topic/battle/judges',      (msg) => updateBattleJudge(msg))
  }
```

Replace the entire if/else block with a guard so subscriptions only happen in standard mode:

```js
  if (!isSmoke.value) {
    battleJudges.value = await getBattleJudges()
    const res = await getCurrentBattlePair()
    if (res) await updateBattlePair(res)
    const cPair2   = createClient(); clients.push(cPair2)
    const cScore2  = createClient(); clients.push(cScore2)
    const cJudges2 = createClient(); clients.push(cJudges2)
    subscribeToChannel(cPair2,   '/topic/battle/battle-pair', (msg) => updateBattlePair(msg))
    subscribeToChannel(cScore2,  '/topic/battle/score',       (msg) => updateScore(msg))
    subscribeToChannel(cJudges2, '/topic/battle/judges',      (msg) => updateBattleJudge(msg))
  }
```

(Chart.vue handles all smoke-mode subscriptions — BattleOverlay must not duplicate them.)

- [ ] **Step 2: Guard the judge panel with !isSmoke**

Find the judge panel `v-if` in the template (around line 316):

```html
    <div
      v-if="battleJudges?.judges?.length"
      class="judge-panel"
```

Change to:

```html
    <div
      v-if="battleJudges?.judges?.length && !isSmoke"
      class="judge-panel"
```

- [ ] **Step 3: Build to verify no errors**

```bash
cd BES-frontend && npm run build
```
Expected: build succeeds

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "fix: remove duplicate smoke WS subscriptions and judge panel from BattleOverlay smoke mode"
```

---

## Task 4: Final verification

**Files:** Read-only

- [ ] **Step 1: Run all frontend tests**

```bash
cd BES-frontend && npm test
```
Expected: all tests pass including the new smokeChartHelpers tests

- [ ] **Step 2: Manual smoke test with Docker**

```bash
docker-compose up --build --no-cache
```

Steps to verify:
1. Log in as Admin/Organiser, go to `/battle/control`, start a 7-to-Smoke format event
2. Open `/battle/chart` in a second tab — should see the dark overlay with "7 TO SMOKE" header, bar columns for each participant
3. Active pair shows colored (red/blue) bars; waiting fighters show gray bars
4. VS chip appears between active columns; floating pill at bottom shows "NAME VS NAME · X PTS · Y PTS"
5. In BattleControl, announce a winner — result overlay should appear on `/battle/chart` with winner name slamming in + judge cards
6. Announce another match — result overlay auto-dismisses after 4 seconds
7. When a fighter reaches 7 points — champion overlay appears and stays
8. Open `/battle/overlay?isSmoke=true` — same chart view without BattleOverlay's old judge panel competing

- [ ] **Step 3: Commit if any minor fixes were made during testing**

```bash
git add -p  # review carefully, stage only what was changed
git commit -m "fix: smoke overlay manual test adjustments"
```

---

## Notes for the Implementer

**TransitionGroup FLIP:** Vue's `<TransitionGroup>` handles FLIP automatically when you give each item a stable `:key` (fighter name) and define `.col-move` with a transition. No manual `getBoundingClientRect` needed — Vue does it.

**Score pop timing:** The `setTimeout` in the watch is 600ms — matches the `scorePop` animation duration. After 600ms the class is removed. If the score changes again while the animation is still running, a new Set is created so the class is removed for the old instance and re-applied for the new one.

**Champion detection:** `updateScore` checks for any participant with `score >= 7` _at the time the score event arrives_. The server sends the updated scores, so by the time `updateScore` fires, `smokeParticipants` should reflect the new scores from the preceding smoke update. If timing is tight, a brief `nextTick` wait may be needed before checking.

**Transparent background:** Both Chart.vue (standalone at `/battle/chart`) and BattleOverlay.vue (at `/battle/overlay`) add `transparent-page` to the HTML root for OBS. Chart.vue's `onBeforeUnmount` removes these classes, which is safe since BattleOverlay.vue's `onUnmounted` also removes them.

**overlayConfig.leftColor vs leftColor:** The API returns `{ leftColor, rightColor }` and WS sends the same shape. BattleOverlay.vue uses `overlayConfig.leftColor` in `:style` binding. Chart.vue uses the same shape. Be consistent.
