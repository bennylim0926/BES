<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { getBracketState } from '@/utils/api'
import { createClient } from '@/utils/websocket'

const bracketState   = ref(null)
const activePair     = ref(null)
const currentGenre   = ref(null)
let   wsClient       = null

// ── animation state ──────────────────────────────────────
const pendingBracket  = ref(null)
const glowSlotKey     = ref(null)   // "roundKey-matchIdx-slot" → glow effect
const hiddenSlotKey   = ref(null)   // dest slot name hidden until ball arrives
const ballVisible     = ref(false)
const ballOrigin      = ref({ x: 0, y: 0 })
let   animRunning     = false
// Saves the bracket state that existed BEFORE the most recent bracket update.
// Used when the bracket WebSocket message arrives before the score message.
let   prevBracketUpdate = null  // { state, timestamp }

// DOM registry for every battler slot
const slotEls = {}
const regSlot = (el, key, mIdx, s) => {
  const k = `${key}-${mIdx}-${s}`
  if (el) slotEls[k] = el; else delete slotEls[k]
}

// ── bracket data ──────────────────────────────────────────
const topSize      = computed(() => Number(bracketState.value?.topSize ?? 0))
const isSmoke      = computed(() => topSize.value === 7)

const roundKeys = computed(() => {
  if (!bracketState.value?.rounds || isSmoke.value) return []
  return Object.keys(bracketState.value.rounds).sort((a, b) =>
    parseInt(b.replace('Top', '')) - parseInt(a.replace('Top', ''))
  )
})

const sideRoundKeys  = computed(() => roundKeys.value.filter(k => k !== 'Top2'))
const finalMatch     = computed(() => bracketState.value?.rounds?.Top2?.[0] ?? null)
const champion       = computed(() => finalMatch.value?.[2] ?? null)
const rightRoundKeys = computed(() => [...sideRoundKeys.value].reverse())

const smokeList = computed(() => {
  if (!isSmoke.value) return []
  const r = bracketState.value?.rounds
  return Array.isArray(r) ? r : []
})

const isEmpty = computed(() => {
  if (!bracketState.value) return true
  if (isSmoke.value) return smokeList.value.length === 0
  return roundKeys.value.length === 0
})

const getMatches   = (key) => bracketState.value?.rounds?.[key] ?? []
const leftMatches  = (key) => { const m = getMatches(key); return m.slice(0, Math.ceil(m.length / 2)) }
const rightMatches = (key) => { const m = getMatches(key); return m.slice(Math.ceil(m.length / 2)) }
// right-side slots need original indices (not slice-local indices)
const rightBase    = (key) => Math.ceil(getMatches(key).length / 2)

const isActiveMatch = (match) => {
  if (!activePair.value || !match) return false
  const [l, r] = match
  return (l === activePair.value.left  && r === activePair.value.right) ||
         (l === activePair.value.right && r === activePair.value.left)
}

const slotClass = (match, slot) => ({
  'slot-winner': match[2] && match[2] === match[slot],
  'slot-loser':  match[2] && match[2] !== match[slot] && match[slot],
})

const formatLabel = (key) => key === 'Top2' ? 'FINAL' : key.replace('Top', 'TOP ')

// ── ticker ────────────────────────────────────────────────
const nextMatch = computed(() => {
  if (!bracketState.value?.rounds) return null
  for (const key of roundKeys.value) {
    for (const m of getMatches(key)) {
      if (!m[2] && !isActiveMatch(m) && (m[0] || m[1])) return m
    }
  }
  return null
})

const activeRoundLabel = computed(() => {
  if (!activePair.value) return null
  for (const key of roundKeys.value) {
    if (getMatches(key).some(m => isActiveMatch(m))) return formatLabel(key)
  }
  return null
})

const tickerItems = computed(() => {
  const items = []
  if (activePair.value) {
    if (activeRoundLabel.value) items.push({ type: 'label', text: activeRoundLabel.value })
    items.push({ type: 'now', text: `${activePair.value.left}  ⚔  ${activePair.value.right}` })
  }
  if (nextMatch.value) {
    items.push({ type: 'next', text: `${nextMatch.value[0] || '—'}  ⚔  ${nextMatch.value[1] || '—'}` })
  }
  if (currentGenre.value) items.push({ type: 'genre', text: currentGenre.value })
  if (!items.length) items.push({ type: 'label', text: 'LIVE BATTLE' })
  return items
})

// ── animation helpers ─────────────────────────────────────
const sleep = ms => new Promise(r => setTimeout(r, ms))

// Find the slot in newState that gained a new name vs oldState (slot 0 or 1 only)
function findDestSlot(oldS, newS) {
  for (const key of Object.keys(newS?.rounds ?? {})) {
    const oldMs = oldS?.rounds?.[key] ?? []
    const newMs = newS.rounds[key]
    for (let i = 0; i < newMs.length; i++) {
      for (let s = 0; s < 2; s++) {
        if (!oldMs[i]?.[s] && newMs[i]?.[s]) return `${key}-${i}-${s}`
      }
    }
  }
  return null
}

async function travelBall(winnerKey, destKey) {
  const winEl = slotEls[winnerKey]
  if (!winEl) return

  const wr    = winEl.getBoundingClientRect()
  const pg    = winEl.closest('.pair-group')
  const pgR   = pg?.getBoundingClientRect()
  const COL_GAP = 24

  // Determine side by comparing slot center to viewport center
  const isLeft = (wr.left + wr.right) / 2 < window.innerWidth / 2

  const startX = isLeft ? wr.right      : wr.left
  const startY = wr.top + wr.height / 2
  const midY   = pgR ? pgR.top + pgR.height / 2 : startY
  const armX   = isLeft ? startX + COL_GAP : startX - COL_GAP

  let endX = armX, endY = midY
  if (destKey && slotEls[destKey]) {
    const dr = slotEls[destKey].getBoundingClientRect()
    endX = dr.left + dr.width / 2
    endY = dr.top  + dr.height / 2
  }

  // Place ball origin at start position (ball is 12×12, center offset 6px)
  ballOrigin.value = { x: startX - 6, y: startY - 6 }
  ballVisible.value = true
  await nextTick()

  const ballEl = document.querySelector('.anim-ball')
  if (!ballEl) { ballVisible.value = false; return }

  // Path: start → arm edge (horizontal) → midpoint (vertical) → destination
  await ballEl.animate([
    { transform: 'translate(0px, 0px)',                                          offset: 0    },
    { transform: `translate(${armX - startX}px, 0px)`,                          offset: 0.15 },
    { transform: `translate(${armX - startX}px, ${midY - startY}px)`,           offset: 0.50 },
    { transform: `translate(${endX - startX}px, ${endY - startY}px)`,           offset: 1.00 },
  ], { duration: 5000, easing: 'ease-in-out', fill: 'forwards' }).finished

  ballVisible.value = false
}

async function runWinnerAnimation(winnerKey, pending) {
  // Phase 1 — winner glow (2.5 s)
  glowSlotKey.value = winnerKey
  await sleep(2500)
  glowSlotKey.value = null

  // Phase 2 — ball travel (5 s)
  const destKey = findDestSlot(bracketState.value, pending)
  if (destKey) {
    hiddenSlotKey.value = destKey   // hide dest name before applying update
    bracketState.value  = pending
    await nextTick()
    await travelBall(winnerKey, destKey)

    // Phase 3 — name reveal (1 s CSS fade)
    hiddenSlotKey.value = null
    await sleep(1200)
  } else {
    // Final match or no advancement: just apply + short pause
    bracketState.value = pending
    await sleep(500)
  }

  animRunning = false
}

function getActiveMatchInfo() {
  if (!activePair.value) return null
  for (const key of roundKeys.value) {
    const ms = getMatches(key)
    for (let i = 0; i < ms.length; i++) {
      if (isActiveMatch(ms[i])) return { key, i }
    }
  }
  return null
}

// ── lifecycle ─────────────────────────────────────────────
onMounted(async () => {
  const state = await getBracketState()
  if (state && (state.topSize || state.rounds)) bracketState.value = state

  wsClient = createClient()
  wsClient.onConnect = () => {

    wsClient.subscribe('/topic/battle/bracket', (msg) => {
      const newState = JSON.parse(msg.body)
      if (animRunning) {
        pendingBracket.value = newState   // defer until animation finishes
      } else {
        // Save snapshot before applying — needed if score arrives after this
        prevBracketUpdate = { state: bracketState.value, timestamp: Date.now() }
        bracketState.value = newState
      }
    })

    wsClient.subscribe('/topic/battle/battle-pair', (msg) => {
      const data = JSON.parse(msg.body)
      activePair.value = { left: data.left, right: data.right }
    })

    wsClient.subscribe('/topic/battle/phase', () => {
      // Phase subscription for future phase-aware bracket display
    })

    wsClient.subscribe('/topic/battle/genre', (msg) => {
      const data = JSON.parse(msg.body)
      currentGenre.value = data.genre ?? data.message ?? null
    })

    wsClient.subscribe('/topic/battle/score', (msg) => {
      const data = JSON.parse(msg.body)
      if (data.message !== 0 && data.message !== 1) return
      if (animRunning) return

      const info = getActiveMatchInfo()
      if (!info) return

      animRunning = true   // set before any await so bracket handler defers immediately
      const winKey = `${info.key}-${info.i}-${data.message}`;

      (async () => {
        // Wait up to 500 ms for the bracket update to arrive after the score
        for (let i = 0; i < 5; i++) {
          if (pendingBracket.value) break
          await sleep(100)
        }

        let pending

        if (pendingBracket.value) {
          // Normal case: bracket arrived AFTER score — snapshot is current bracketState
          pending = pendingBracket.value
          pendingBracket.value = null

        } else if (prevBracketUpdate && Date.now() - prevBracketUpdate.timestamp < 2000) {
          // Bracket arrived BEFORE score — restore old state so dest slot appears empty,
          // then use the already-applied new state as the pending target
          pending = bracketState.value          // currently the new state
          bracketState.value = prevBracketUpdate.state  // restore to pre-update state
          prevBracketUpdate = null
          await nextTick()

        } else {
          // No bracket update detected — glow only, no ball
          pending = bracketState.value
        }

        await runWinnerAnimation(winKey, pending)
      })()
    })
  }
  wsClient.activate()
})

onUnmounted(() => { if (wsClient) wsClient.deactivate() })
</script>

<template>
  <div class="bracket-root">

    <!-- ── Scanlines overlay ──────────────────────────── -->
    <div class="scanlines" aria-hidden="true"></div>

    <!-- ── Header ─────────────────────────────────────── -->
    <header class="bracket-header">
      <div class="header-brand">
        <span class="brand-dot"></span>
        <span class="brand-title">LIVE BRACKET</span>
        <span class="brand-dot"></span>
      </div>
      <div v-if="activePair" class="active-pill">
        <span class="pill-dot"></span>
        {{ activePair.left }}
        <span class="vs-sep">vs</span>
        {{ activePair.right }}
      </div>
    </header>

    <!-- ── Empty state ────────────────────────────────── -->
    <div v-if="isEmpty" class="empty-state">
      <div class="empty-icon">⏳</div>
      <p class="empty-title">Waiting for bracket</p>
      <p class="empty-sub">The organiser hasn't set up the bracket yet</p>
    </div>

    <!-- ── 7-to-Smoke ─────────────────────────────────── -->
    <div v-else-if="isSmoke" class="smoke-wrap">
      <h2 class="round-label mb-4">7 TO SMOKE — QUEUE</h2>
      <div class="smoke-list">
        <div
          v-for="(battler, idx) in smokeList" :key="idx"
          class="smoke-slot"
          :class="{ 'smoke-active': idx < 2, 'smoke-next': idx === 2 }"
        >
          <span class="smoke-pos">{{ idx + 1 }}</span>
          <span class="smoke-name">{{ battler.name || '—' }}</span>
          <span class="smoke-score">{{ battler.score }}</span>
          <span v-if="idx < 2" class="smoke-badge">FIGHTING</span>
          <span v-else-if="idx === 2" class="smoke-badge smoke-badge-next">NEXT</span>
        </div>
      </div>
    </div>

    <!-- ── Standard bracket ───────────────────────────── -->
    <div v-else class="bracket-area">

      <!-- LEFT half -->
      <div class="bracket-half bracket-left">
        <div v-for="key in sideRoundKeys" :key="key" class="round-col">
          <div class="round-label">{{ formatLabel(key) }}</div>
          <div class="matches-col">
            <div
              v-for="(match, mIdx) in leftMatches(key)" :key="mIdx"
              class="pair-group"
              :class="{ 'match-active': isActiveMatch(match) }"
            >
              <div class="match-wrap">
                <div
                  class="battler-slot"
                  :ref="el => regSlot(el, key, mIdx, 0)"
                  :class="[
                    slotClass(match, 0),
                    glowSlotKey  === `${key}-${mIdx}-0` ? 'slot-glow'   : '',
                    hiddenSlotKey === `${key}-${mIdx}-0` ? 'name-hidden' : '',
                  ]"
                >
                  <span class="battler-name">{{ match[0] || '—' }}</span>
                  <i v-if="match[2] === match[0] && match[0]" class="pi pi-crown crown-icon"></i>
                </div>
              </div>
              <div class="match-wrap">
                <div
                  class="battler-slot"
                  :ref="el => regSlot(el, key, mIdx, 1)"
                  :class="[
                    slotClass(match, 1),
                    glowSlotKey  === `${key}-${mIdx}-1` ? 'slot-glow'   : '',
                    hiddenSlotKey === `${key}-${mIdx}-1` ? 'name-hidden' : '',
                  ]"
                >
                  <span class="battler-name">{{ match[1] || '—' }}</span>
                  <i v-if="match[2] === match[1] && match[1]" class="pi pi-crown crown-icon"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- CENTER: Final + Champion -->
      <div class="center-col">
        <div class="round-label">FINAL</div>
        <div class="final-area">
          <div v-if="finalMatch" class="final-match" :class="{ 'match-active': isActiveMatch(finalMatch) }">
            <div
              class="battler-slot"
              :ref="el => regSlot(el, 'Top2', 0, 0)"
              :class="[
                slotClass(finalMatch, 0),
                glowSlotKey  === 'Top2-0-0' ? 'slot-glow'   : '',
                hiddenSlotKey === 'Top2-0-0' ? 'name-hidden' : '',
              ]"
            >
              <span class="battler-name">{{ finalMatch[0] || '—' }}</span>
              <i v-if="finalMatch[2] === finalMatch[0] && finalMatch[0]" class="pi pi-crown crown-icon"></i>
            </div>
            <div class="final-vs">VS</div>
            <div
              class="battler-slot"
              :ref="el => regSlot(el, 'Top2', 0, 1)"
              :class="[
                slotClass(finalMatch, 1),
                glowSlotKey  === 'Top2-0-1' ? 'slot-glow'   : '',
                hiddenSlotKey === 'Top2-0-1' ? 'name-hidden' : '',
              ]"
            >
              <span class="battler-name">{{ finalMatch[1] || '—' }}</span>
              <i v-if="finalMatch[2] === finalMatch[1] && finalMatch[1]" class="pi pi-crown crown-icon"></i>
            </div>
          </div>
          <div v-if="champion" class="champion-card">
            <i class="pi pi-crown champ-icon"></i>
            <span class="champ-name">{{ champion }}</span>
          </div>
        </div>
      </div>

      <!-- RIGHT half -->
      <div class="bracket-half bracket-right">
        <div v-for="key in rightRoundKeys" :key="key" class="round-col">
          <div class="round-label">{{ formatLabel(key) }}</div>
          <div class="matches-col">
            <div
              v-for="(match, mIdx) in rightMatches(key)" :key="mIdx"
              class="pair-group"
              :class="{ 'match-active': isActiveMatch(match) }"
            >
              <div class="match-wrap">
                <div
                  class="battler-slot"
                  :ref="el => regSlot(el, key, rightBase(key) + mIdx, 0)"
                  :class="[
                    slotClass(match, 0),
                    glowSlotKey  === `${key}-${rightBase(key) + mIdx}-0` ? 'slot-glow'   : '',
                    hiddenSlotKey === `${key}-${rightBase(key) + mIdx}-0` ? 'name-hidden' : '',
                  ]"
                >
                  <span class="battler-name">{{ match[0] || '—' }}</span>
                  <i v-if="match[2] === match[0] && match[0]" class="pi pi-crown crown-icon"></i>
                </div>
              </div>
              <div class="match-wrap">
                <div
                  class="battler-slot"
                  :ref="el => regSlot(el, key, rightBase(key) + mIdx, 1)"
                  :class="[
                    slotClass(match, 1),
                    glowSlotKey  === `${key}-${rightBase(key) + mIdx}-1` ? 'slot-glow'   : '',
                    hiddenSlotKey === `${key}-${rightBase(key) + mIdx}-1` ? 'name-hidden' : '',
                  ]"
                >
                  <span class="battler-name">{{ match[1] || '—' }}</span>
                  <i v-if="match[2] === match[1] && match[1]" class="pi pi-crown crown-icon"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- ── LED Ticker ─────────────────────────────────── -->
    <div class="ticker-bar">
      <div class="ticker-label">
        <span class="ticker-dot"></span>
        LIVE
      </div>
      <div class="ticker-track">
        <div class="ticker-reel" :style="{ '--item-count': tickerItems.length }">
          <template v-for="pass in 2" :key="pass">
            <span
              v-for="(item, idx) in tickerItems" :key="`${pass}-${idx}`"
              class="ticker-item" :class="`ticker-${item.type}`"
            >
              <span v-if="item.type === 'now'"   class="ticker-tag">NOW BATTLING</span>
              <span v-else-if="item.type === 'next'"  class="ticker-tag">NEXT UP</span>
              <span v-else-if="item.type === 'genre'" class="ticker-tag">GENRE</span>
              <span v-else-if="item.type === 'label'" class="ticker-tag">{{ item.text }}</span>
              <span v-if="item.type !== 'label'" class="ticker-text">{{ item.text }}</span>
              <span class="ticker-sep">◆</span>
            </span>
          </template>
        </div>
      </div>
    </div>

  </div>

  <!-- ── Travelling ball (outside bracket-root to avoid overflow clipping) ── -->
  <Teleport to="body">
    <div
      v-if="ballVisible"
      class="anim-ball"
      :style="{ left: ballOrigin.x + 'px', top: ballOrigin.y + 'px' }"
    ></div>
  </Teleport>
</template>

<style scoped>
/* ── Theme tokens ─────────────────────────────────────── */
.bracket-root {
  --c-bg:         #060818;
  --c-surface:    rgba(255,255,255,0.04);
  --c-border:     rgba(255,255,255,0.08);
  --c-border-act: rgba(255,255,255,0.35);
  --c-text:       #f0f0f0;
  --c-muted:      rgba(255,255,255,0.28);
  --c-accent:     rgba(255,255,255,0.55);
  --c-win-bg:     rgba(245,158,11,0.15);
  --c-lose-text:  rgba(255,255,255,0.16);
  --c-connector:  rgba(255,255,255,0.12);
  --c-champ-bg:   rgba(245,158,11,0.1);
  --c-champ:      #f59e0b;
  --left-color:   #dc2626;
  --right-color:  #2563eb;
  --col-gap:      24px;
  --header-h:     52px;
  --slot-h:       38px;
}

/* ── Root ─────────────────────────────────────────────── */
.bracket-root {
  height: 100dvh;
  background:
    radial-gradient(ellipse 50vw 55vh at 5% 8%,  rgba(255,255,255,0.04) 0%, transparent 70%),
    radial-gradient(ellipse 45vw 50vh at 95% 92%, rgba(245,158,11,0.06) 0%, transparent 70%),
    #060818;
  display: flex; flex-direction: column;
  font-family: 'Inter', sans-serif; color: var(--c-text);
  overflow: hidden; position: relative;
}

/* ── Scanlines overlay ───────────────────────────────── */
.scanlines {
  position: absolute; inset: 0;
  z-index: 100; pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0px, transparent 3px,
    rgba(0,0,0,0.045) 3px, rgba(0,0,0,0.045) 4px
  );
}

/* ── Header ───────────────────────────────────────────── */
.bracket-header {
  flex-shrink: 0; height: var(--header-h);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px;
  background: linear-gradient(135deg, rgba(6,8,20,0.98) 0%, rgba(8,10,26,0.95) 100%);
  border-bottom: 1px solid rgba(255,255,255,0.07);
  position: relative; z-index: 10;
}
.header-brand { display: flex; align-items: center; gap: 10px; }
.brand-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--c-accent);
  box-shadow: 0 0 10px rgba(255,255,255,0.5), 0 0 22px rgba(255,255,255,0.2);
  animation: dotPulse 2s ease-in-out infinite;
}
.brand-title {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px; letter-spacing: 0.38em;
  color: rgba(255,255,255,0.42);
  text-transform: uppercase;
}
.active-pill {
  display: flex; align-items: center; gap: 8px;
  font-family: 'Anton SC', sans-serif;
  font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase;
  color: rgba(255,255,255,0.75);
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.18);
  border-radius: 2px; padding: 4px 16px 4px 12px;
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
}
.pill-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: rgba(255,255,255,0.7); box-shadow: 0 0 6px rgba(255,255,255,0.5);
  animation: dotPulse 1s ease-in-out infinite;
}
.vs-sep { color: rgba(255,255,255,0.2); font-family: 'Inter', sans-serif; font-size: 9px; font-weight: 400; }

/* ── Empty / Smoke ────────────────────────────────────── */
.empty-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; }
.empty-icon  { font-size: 36px; opacity: 0.45; }
.empty-title { font-family: 'Anton SC', sans-serif; font-size: 18px; letter-spacing: 0.2em; text-transform: uppercase; color: var(--c-muted); }
.empty-sub   { font-size: 12px; color: var(--c-muted); opacity: 0.5; }

.smoke-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 24px; overflow-y: auto; }
.smoke-list { width: 100%; max-width: 420px; display: flex; flex-direction: column; gap: 8px; }
.smoke-slot {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  background: var(--c-surface); border: 1px solid var(--c-border);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
}
.smoke-active { background: rgba(255,255,255,0.07); border-color: rgba(255,255,255,0.25); }
.smoke-next   { border-color: rgba(255,255,255,0.1); }
.smoke-pos    { font-family: 'Anton SC', sans-serif; font-size: 11px; color: var(--c-muted); width: 18px; letter-spacing: 0.08em; }
.smoke-name   { flex: 1; font-family: 'Anton SC', sans-serif; font-size: 13px; letter-spacing: 0.06em; text-transform: uppercase; color: var(--c-text); }
.smoke-score  { font-family: 'Anton SC', sans-serif; font-size: 13px; color: rgba(255,255,255,0.65); letter-spacing: 0.05em; }
.smoke-badge  {
  font-family: 'Anton SC', sans-serif; font-size: 8px; letter-spacing: 0.2em;
  padding: 2px 9px; background: var(--c-win-bg); color: rgba(255,255,255,0.65);
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}
.smoke-badge-next { background: var(--c-surface); color: var(--c-muted); }

/* ── Round label ──────────────────────────────────────── */
.round-label {
  flex-shrink: 0;
  font-family: 'Anton SC', sans-serif;
  font-size: 9px; letter-spacing: 0.3em;
  color: rgba(255,255,255,0.38); text-align: center;
  padding: 6px 0; opacity: 0.52;
  text-transform: uppercase;
}
.mb-4 { margin-bottom: 16px; }

/* ── Bracket layout ───────────────────────────────────── */
.bracket-area  { flex: 1; display: flex; flex-direction: row; gap: var(--col-gap); padding: 8px 16px 12px; overflow: hidden; min-height: 0; position: relative; z-index: 1; }
.bracket-half  { flex: 1; display: flex; flex-direction: row; gap: var(--col-gap); min-width: 0; }
.round-col     { flex: 1; display: flex; flex-direction: column; min-width: 0; position: relative; }
.matches-col   { flex: 1; display: flex; flex-direction: column; min-height: 0; }

.pair-group {
  flex: 1; display: flex; flex-direction: column; position: relative;
  min-height: calc(var(--slot-h) * 2 + 16px);
}

/* Connector arms */
.bracket-left .pair-group::after {
  content: ''; position: absolute;
  right: calc(-1 * var(--col-gap)); top: 25%; height: 50%; width: var(--col-gap);
  border-top: 1px solid var(--c-connector);
  border-right: 1px solid var(--c-connector);
  border-bottom: 1px solid var(--c-connector);
  pointer-events: none; z-index: 1;
}
.bracket-right .pair-group::after {
  content: ''; position: absolute;
  left: calc(-1 * var(--col-gap)); top: 25%; height: 50%; width: var(--col-gap);
  border-top: 1px solid var(--c-connector);
  border-left: 1px solid var(--c-connector);
  border-bottom: 1px solid var(--c-connector);
  pointer-events: none; z-index: 1;
}
.match-active.pair-group::after { border-color: rgba(255,255,255,0.3); }

.match-wrap { flex: 1; display: flex; align-items: center; padding: 0 2px; }

/* ── Battler slot ─────────────────────────────────────── */
.battler-slot {
  width: 100%; height: var(--slot-h);
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 0 10px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 2px;
  transform: skewX(-4deg);
  transition: background 0.25s, border-color 0.25s, box-shadow 0.25s;
}
.match-active .battler-slot {
  background: rgba(255,255,255,0.05);
  border-color: rgba(255,255,255,0.18);
  box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 0 14px rgba(255,255,255,0.08);
}
.slot-winner {
  background: rgba(245,158,11,0.15) !important;
  border-color: rgba(245,158,11,0.45) !important;
  box-shadow: 0 0 18px rgba(245,158,11,0.25) !important;
}
.slot-winner .battler-name { color: #fde68a; }
.slot-loser { background: rgba(255,255,255,0.02) !important; border-color: rgba(255,255,255,0.03) !important; opacity: 0.42; }
.slot-loser  .battler-name { color: var(--c-lose-text); }

.battler-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--c-text); opacity: 0.88;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: center;
  display: inline-block; transform: skewX(4deg);
  transition: opacity 1s ease;
}
.name-hidden .battler-name { opacity: 0; transition: none; }

.crown-icon {
  font-size: 11px; color: var(--c-champ); flex-shrink: 0;
  display: inline-block; transform: skewX(4deg);
}

/* ── Winner glow animation ────────────────────────────── */
.slot-glow {
  animation: slotGlow 2.5s ease-in-out forwards;
  z-index: 10; position: relative;
}
@keyframes slotGlow {
  0%   { background: var(--c-surface);        border-color: var(--c-border);         box-shadow: none; }
  20%  { background: rgba(245,158,11,0.28);   border-color: rgba(245,158,11,0.8);    box-shadow: 0 0 0 2px rgba(245,158,11,0.35), 0 0 22px rgba(245,158,11,0.7), 0 0 44px rgba(245,158,11,0.3); }
  70%  { background: rgba(245,158,11,0.18);   border-color: rgba(245,158,11,0.55);   box-shadow: 0 0 0 2px rgba(245,158,11,0.25), 0 0 16px rgba(245,158,11,0.5), 0 0 32px rgba(245,158,11,0.18); }
  100% { background: var(--c-surface);        border-color: var(--c-border);         box-shadow: none; }
}
.slot-glow .battler-name { color: #fde68a; opacity: 1; }

/* ── Center column ────────────────────────────────────── */
.center-col { flex-shrink: 0; width: 170px; display: flex; flex-direction: column; }
.final-area { flex: 1; display: flex; flex-direction: column; align-items: stretch; justify-content: center; gap: 6px; min-height: 0; }
.final-match { display: flex; flex-direction: column; gap: 6px; }
.final-match.match-active .battler-slot {
  border-color: rgba(255,255,255,0.3);
  box-shadow: 0 0 0 1px rgba(255,255,255,0.08), 0 0 16px rgba(255,255,255,0.1);
}
.final-vs {
  text-align: center;
  font-family: 'Anton SC', sans-serif;
  font-size: 9px; letter-spacing: 0.32em;
  color: rgba(255,255,255,0.3); opacity: 0.38;
}

/* ── Champion card ────────────────────────────────────── */
.champion-card {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 10px 14px;
  background: var(--c-champ-bg);
  border: 1px solid rgba(245,158,11,0.22);
  border-radius: 2px;
  transform: skewX(-4deg);
  box-shadow: 0 0 24px rgba(245,158,11,0.22);
}
.champ-icon {
  font-size: 14px; color: var(--c-champ); flex-shrink: 0;
  display: inline-block; transform: skewX(4deg);
  filter: drop-shadow(0 0 8px rgba(245,158,11,0.9));
}
.champ-name {
  font-family: 'Anton SC', sans-serif; font-size: 13px;
  color: var(--c-champ); letter-spacing: 0.08em; text-transform: uppercase;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  display: inline-block; transform: skewX(4deg);
}

/* ── LED Ticker ───────────────────────────────────────── */
.ticker-bar {
  flex-shrink: 0; height: 30px;
  display: flex; align-items: stretch;
  background: #030610;
  border-top: 1px solid rgba(255,255,255,0.07);
  overflow: hidden; position: relative; z-index: 10;
}
.ticker-label {
  flex-shrink: 0; display: flex; align-items: center; gap: 6px;
  padding: 0 18px 0 14px;
  background: rgba(255,255,255,0.9);
  font-family: 'Anton SC', sans-serif;
  font-size: 10px; letter-spacing: 0.25em;
  color: #060818; z-index: 1;
  clip-path: polygon(0% 0%, calc(100% - 10px) 0%, 100% 50%, calc(100% - 10px) 100%, 0% 100%);
}
.ticker-dot   { width: 5px; height: 5px; border-radius: 50%; background: #030610; animation: dotPulse 1s ease-in-out infinite; }
.ticker-track { flex: 1; overflow: hidden; position: relative; mask-image: linear-gradient(to right, transparent 0%, black 3%, black 97%, transparent 100%); }
.ticker-reel  { display: inline-flex; align-items: center; white-space: nowrap; height: 100%; animation: tickerScroll calc(var(--item-count, 4) * 8s) linear infinite; }
.ticker-item  { display: inline-flex; align-items: center; gap: 8px; padding: 0 6px; }
.ticker-tag   { font-family: 'Anton SC', sans-serif; font-size: 8px; letter-spacing: 0.2em; text-transform: uppercase; color: var(--c-muted); opacity: 0.62; }
.ticker-now   .ticker-tag  { color: rgba(255,255,255,0.85); opacity: 1; }
.ticker-next  .ticker-tag  { color: rgba(249,115,22,0.85); opacity: 1; }
.ticker-genre .ticker-tag  { color: rgba(167,139,250,0.85); opacity: 1; }
.ticker-text  { font-family: 'Inter', sans-serif; font-size: 11px; font-weight: 600; letter-spacing: 0.04em; color: var(--c-text); }
.ticker-now   .ticker-text { color: rgba(255,255,255,0.85); }
.ticker-next  .ticker-text { color: rgba(249,115,22,0.9); }
.ticker-genre .ticker-text { color: rgba(167,139,250,0.9); }
.ticker-sep   { font-size: 7px; color: var(--c-muted); opacity: 0.22; padding: 0 16px; }

/* ── Travelling ball ─────────────────────────────────── */
:global(.anim-ball) {
  position: fixed;
  width: 12px; height: 12px; border-radius: 50%;
  background: radial-gradient(circle, #ffffff 0%, rgba(245,158,11,0.9) 55%, transparent 100%);
  box-shadow: 0 0 6px rgba(245,158,11,0.9), 0 0 14px rgba(245,158,11,0.7), 0 0 28px rgba(245,158,11,0.4);
  pointer-events: none; z-index: 9999;
}

/* ── Animations ───────────────────────────────────────── */
@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.32; transform: scale(0.62); }
}
@keyframes tickerScroll {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}
</style>
