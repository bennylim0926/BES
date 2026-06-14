<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { getBattleState, getBracketState, getOverlayConfig } from '@/utils/api'
import { createClient } from '@/utils/websocket'
import { useRoute } from 'vue-router'

const bracketState   = ref(null)
const lastBracketSnapshot = ref('')  // JSON diff guard
const overlayConfig  = ref({ leftColor: '#dc2626', rightColor: '#2563eb', logoUrl: null, animTheme: 'impact' })
const championReveal = ref(null)   // null = hidden; { genreName, championName } = showing
const activePair     = ref(null)
const currentGenre   = ref(null)
let   wsClient       = null
const route = useRoute()
const eventName = ref(route.query.event || '')
const topic = (path) => `/topic/battle/${eventName.value}/${path}`

// ── animation state ──────────────────────────────────────
const pendingBracket  = ref(null)
const glowSlotKey     = ref(null)   // "roundKey-matchIdx-slot" → glow effect
const hiddenSlotKey   = ref(null)   // dest slot name hidden until ball arrives
let   animRunning     = false
// Saves the bracket state that existed BEFORE the most recent bracket update.
// Used when the bracket WebSocket message arrives before the score message.
let   prevBracketUpdate = null  // { state, timestamp }
let   lastBracketBody   = ''

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

const slotClass = (match, slot, isFinal = false) => {
  if (match[2]) {
    if (match[2] === match[slot]) return isFinal ? 'slot-winner' : 'slot-winner-round'
    if (match[slot]) return 'slot-loser'
    return ''
  }
  if (isActiveMatch(match) && match[slot]) {
    return slot === 0 ? 'slot-active-left' : 'slot-active-right'
  }
  return ''
}

const slotHeight = computed(() => {
  const n = topSize.value
  if (n <= 8)  return '54px'
  if (n <= 16) return '46px'
  return '38px'
})
const finalSlotHeight = computed(() => {
  const n = topSize.value
  if (n <= 8)  return '74px'
  if (n <= 16) return '66px'
  return '58px'
})
const centerColWidth = computed(() => {
  const n = topSize.value
  if (n <= 8)  return '210px'
  if (n <= 16) return '195px'
  return '185px'
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
  const winEl  = slotEls[winnerKey]
  const destEl = destKey ? slotEls[destKey] : null
  if (!winEl) return

  const bracketEl = document.querySelector('.bracket-area')
  if (!bracketEl) return
  const bRect  = bracketEl.getBoundingClientRect()
  const wRect  = winEl.getBoundingClientRect()

  // ── Phase 1: Ring burst on source slot ──────────────────────────
  const ring = document.createElement('div')
  ring.style.cssText = `
    position: absolute;
    left: ${wRect.left - bRect.left}px;
    top:  ${wRect.top  - bRect.top}px;
    width:  ${wRect.width}px;
    height: ${wRect.height}px;
    border: 2px solid rgba(245,158,11,0.9);
    border-radius: 1px;
    box-shadow: 0 0 20px rgba(245,158,11,0.6);
    pointer-events: none;
    z-index: 20;
    transform: skewX(-4deg);
  `
  bracketEl.style.position = 'relative'
  bracketEl.appendChild(ring)

  await ring.animate(
    [{ transform: 'skewX(-4deg) scale(1)', opacity: 1 },
     { transform: 'skewX(-4deg) scale(1.65)', opacity: 0 }],
    { duration: 380, easing: 'ease-out', fill: 'forwards' }
  ).finished
  ring.remove()

  if (!destEl) return

  const dRect = destEl.getBoundingClientRect()

  // ── Phase 2: Lightning streak ────────────────────────────────────
  const srcCenterX = wRect.left  + wRect.width  / 2 - bRect.left
  const srcCenterY = wRect.top   + wRect.height / 2 - bRect.top
  const dstCenterX = dRect.left  + dRect.width  / 2 - bRect.left
  const dstCenterY = dRect.top   + dRect.height / 2 - bRect.top

  const dx = dstCenterX - srcCenterX
  const dy = dstCenterY - srcCenterY
  const length = Math.sqrt(dx * dx + dy * dy)
  const angle  = Math.atan2(dy, dx) * (180 / Math.PI)

  const streak = document.createElement('div')
  streak.style.cssText = `
    position: absolute;
    left:   ${srcCenterX}px;
    top:    ${srcCenterY - 2}px;
    width:  0;
    height: 3px;
    transform-origin: left center;
    transform: rotate(${angle}deg);
    background: linear-gradient(90deg, rgba(245,158,11,0.2) 0%, rgba(245,158,11,0.95) 55%, #fff 100%);
    border-radius: 2px;
    box-shadow: 0 0 8px rgba(245,158,11,0.7);
    pointer-events: none;
    z-index: 20;
  `
  bracketEl.appendChild(streak)

  await streak.animate(
    [{ width: '0px' }, { width: `${length}px` }],
    { duration: 200, easing: 'ease-in', fill: 'forwards' }
  ).finished

  await streak.animate(
    [{ opacity: 1 }, { opacity: 0 }],
    { duration: 140, easing: 'linear', fill: 'forwards' }
  ).finished
  streak.remove()

  // ── Phase 3: Destination slot ignites ───────────────────────────
  destEl.style.transition = 'none'
  destEl.style.boxShadow  = '0 0 40px rgba(245,158,11,0.9), 0 0 80px rgba(245,158,11,0.4)'
  await sleep(60)
  destEl.style.transition = 'box-shadow 0.45s ease-out'
  destEl.style.boxShadow  = ''
  await sleep(450)
  destEl.style.transition = ''
}

async function runWinnerAnimation(winnerKey, pending) {
  // Phase 1 — winner glow (2.5 s)
  glowSlotKey.value = winnerKey
  await sleep(2500)
  glowSlotKey.value = null

  // Phase 2 — ball travel (5 s)
  // Bracket update may have arrived during the glow phase — use it as the target
  if (pending === bracketState.value && pendingBracket.value) {
    pending = pendingBracket.value
    pendingBracket.value = null
  }
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

  // Apply any bracket update that arrived while we were animating and wasn't consumed
  if (pendingBracket.value) {
    bracketState.value = pendingBracket.value
    pendingBracket.value = null
  }
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
  // Restore state from backend — authoritative source for bracket/pair/genre
  const battleState = await getBattleState(eventName.value)
  if (battleState?.bracket && (battleState.bracket.topSize || battleState.bracket.rounds)) {
    bracketState.value = battleState.bracket
  }
  if (battleState?.currentPair?.left) {
    activePair.value = { left: battleState.currentPair.left, right: battleState.currentPair.right }
  }
  if (battleState?.genreName) {
    currentGenre.value = battleState.genreName
  }

  if (!bracketState.value) {
    const state = await getBracketState(eventName.value)
    if (state && (state.topSize || state.rounds)) bracketState.value = state
  }

  const cfg = await getOverlayConfig(eventName.value)
  if (cfg) overlayConfig.value = cfg

  wsClient = createClient()
  wsClient.onConnect = () => {

    wsClient.subscribe(topic('state'), (msg) => {
      // Diff guard: compare raw body string to skip no-op updates.
      // Avoids unnecessary JSON.parse + JSON.stringify round-trip on every message.
      if (msg.body === lastBracketSnapshot.value) return
      lastBracketSnapshot.value = msg.body
      const data = JSON.parse(msg.body)
      if (data?.bracket && (data.bracket.topSize || data.bracket.rounds)) {
        if (animRunning) {
          pendingBracket.value = data.bracket
        } else {
          pendingBracket.value = null
          prevBracketUpdate = { state: bracketState.value, timestamp: Date.now() }
          bracketState.value = data.bracket
        }
      }
      if (data?.currentPair?.left) {
        activePair.value = { left: data.currentPair.left, right: data.currentPair.right }
      }
      if (data?.genreName) {
        if (data.genreName !== currentGenre.value) championReveal.value = null
        currentGenre.value = data.genreName
      }
    })

    wsClient.subscribe(topic('bracket'), (msg) => {
      if (msg.body === lastBracketBody) return
      lastBracketBody = msg.body
      const newState = JSON.parse(msg.body)
      if (animRunning) {
        pendingBracket.value = newState   // defer until animation finishes
      } else {
        // Clear any stale deferred state from a previous animation cycle
        pendingBracket.value = null
        // Save snapshot before applying — needed if score arrives after this
        prevBracketUpdate = { state: bracketState.value, timestamp: Date.now() }
        bracketState.value = newState
      }
    })

    wsClient.subscribe(topic('battle-pair'), (msg) => {
      const data = JSON.parse(msg.body)
      activePair.value = { left: data.left, right: data.right }
    })

    wsClient.subscribe(topic('phase'), () => {
      // Phase subscription for future phase-aware bracket display
    })

    wsClient.subscribe('/topic/battle/genre', (msg) => {
      const data = JSON.parse(msg.body)
      currentGenre.value = data.genre ?? data.message ?? null
    })

    wsClient.subscribe(topic('overlay-config'), (msg) => {
      const cfg = JSON.parse(msg.body)
      overlayConfig.value = cfg
    })

    wsClient.subscribe(topic('champion-reveal'), (msg) => {
      const data = JSON.parse(msg.body)
      if (data.dismiss) {
        championReveal.value = null
      } else {
        championReveal.value = { genreName: data.genreName, championName: data.championName }
      }
    })

    wsClient.subscribe(topic('score'), (msg) => {
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

    // Re-hydrate on reconnect — REST covers gap while WS was disconnected
    getBattleState(eventName.value).then(state => {
      if (state && (state.bracket?.topSize || state.bracket?.rounds)) {
        if (state.bracket && !animRunning) {
          const incoming = JSON.stringify(state.bracket)
          if (incoming !== JSON.stringify(bracketState.value)) {
            bracketState.value = state.bracket
          }
        }
        if (state.currentPair?.left) {
          activePair.value = { left: state.currentPair.left, right: state.currentPair.right }
        }
        if (state.genreName) currentGenre.value = state.genreName
      }
    }).catch(() => {})
  }
  wsClient.activate()
})

onUnmounted(() => { if (wsClient) wsClient.deactivate() })
</script>

<template>
  <div class="bracket-root" :data-anim-theme="overlayConfig.animTheme || 'impact'" :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor, '--slot-h': slotHeight, '--final-slot-h': finalSlotHeight, '--center-col-w': centerColWidth }">

    <!-- ── Scanlines overlay ──────────────────────────── -->
    <div class="scanlines" aria-hidden="true"></div>

    <!-- ── Champion Reveal Overlay ──────────────────────── -->
    <Transition name="champ-reveal">
      <div v-if="championReveal" class="champ-overlay">
        <div class="champ-overlay-bg"></div>
        <div class="champ-overlay-content">
          <div class="champ-genre-tag">{{ championReveal.genreName }} · Final</div>
          <div class="champ-label">CHAMPION</div>
          <div class="champ-name-slam">{{ championReveal.championName }}</div>
          <div class="champ-gold-bar"></div>
        </div>
      </div>
    </Transition>

    <!-- ── Logo banner ────────────────────────────────── -->
    <div v-if="overlayConfig.logoUrl || currentGenre" class="logo-banner">
      <img v-if="overlayConfig.logoUrl" :src="overlayConfig.logoUrl" class="logo-banner-img" alt="Event logo" />
      <span v-if="currentGenre" class="logo-banner-genre">{{ currentGenre }}</span>
    </div>

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
                  <span v-if="match[2] === match[0] && match[0]" class="takes-it-badge">TAKES IT</span>
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
                  <span v-if="match[2] === match[1] && match[1]" class="takes-it-badge">TAKES IT</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- CENTER: Final + Champion -->
      <div class="center-col">
        <div class="final-area">
          <div v-if="finalMatch" class="final-match" :class="{ 'match-active': isActiveMatch(finalMatch) }">
            <div
              class="battler-slot"
              :ref="el => regSlot(el, 'Top2', 0, 0)"
              :class="[
                slotClass(finalMatch, 0, true),
                glowSlotKey  === 'Top2-0-0' ? 'slot-glow'   : '',
                hiddenSlotKey === 'Top2-0-0' ? 'name-hidden' : '',
              ]"
            >
              <span class="battler-name">{{ finalMatch[0] || '—' }}</span>
              <span v-if="finalMatch[2] === finalMatch[0] && finalMatch[0]" class="win-badge">WIN</span>
            </div>
            <div class="final-vs">VS</div>
            <div
              class="battler-slot"
              :ref="el => regSlot(el, 'Top2', 0, 1)"
              :class="[
                slotClass(finalMatch, 1, true),
                glowSlotKey  === 'Top2-0-1' ? 'slot-glow'   : '',
                hiddenSlotKey === 'Top2-0-1' ? 'name-hidden' : '',
              ]"
            >
              <span class="battler-name">{{ finalMatch[1] || '—' }}</span>
              <span v-if="finalMatch[2] === finalMatch[1] && finalMatch[1]" class="win-badge">WIN</span>
            </div>
          </div>
        </div>
      </div>

      <!-- RIGHT half -->
      <div class="bracket-half bracket-right">
        <div v-for="key in rightRoundKeys" :key="key" class="round-col">
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
                  <span v-if="match[2] === match[0] && match[0]" class="takes-it-badge">TAKES IT</span>
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
                  <span v-if="match[2] === match[1] && match[1]" class="takes-it-badge">TAKES IT</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>

  </div>

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
  --col-gap:       24px;
  --header-h:      52px;
  --slot-h:        38px;
  --final-slot-h:  58px;
  --center-col-w:  185px;
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

/* ── Logo banner ──────────────────────────────────────── */
.logo-banner {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 32px 20px;
}
.logo-banner-img {
  max-height: 240px;
  max-width: 720px;
  width: auto;
  object-fit: contain;
  filter: drop-shadow(0 0 16px rgba(255,255,255,0.18));
}
.logo-banner-genre {
  font-family: 'Oswald', sans-serif;
  font-size: 72px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(180,180,180,0.12);
  text-align: center;
  text-shadow: none;
}

/* ── Empty / Smoke ────────────────────────────────────── */
.empty-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; }
.empty-icon  { font-size: 36px; opacity: 0.45; }
.empty-title { font-family: 'Oswald', sans-serif; font-size: 18px; letter-spacing: 0.2em; text-transform: uppercase; color: var(--c-muted); }
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
.smoke-pos    { font-family: 'Oswald', sans-serif; font-size: 11px; color: var(--c-muted); width: 18px; letter-spacing: 0.08em; }
.smoke-name   { flex: 1; font-family: 'Oswald', sans-serif; font-size: 14px; letter-spacing: 0.02em; color: var(--c-text); }
.smoke-score  { font-family: 'Oswald', sans-serif; font-size: 13px; color: rgba(255,255,255,0.65); letter-spacing: 0.05em; }
.smoke-badge  {
  font-family: 'Oswald', sans-serif; font-size: 8px; letter-spacing: 0.2em;
  padding: 2px 9px; background: var(--c-win-bg); color: rgba(255,255,255,0.65);
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}
.smoke-badge-next { background: var(--c-surface); color: var(--c-muted); }

/* ── Round label ──────────────────────────────────────── */
.round-label {
  flex-shrink: 0;
  font-family: 'Oswald', sans-serif;
  font-size: 9px; letter-spacing: 0.3em;
  color: rgba(255,255,255,0.38); text-align: center;
  padding: 6px 0; opacity: 0.52;
  text-transform: uppercase;
}
.mb-4 { margin-bottom: 16px; }

/* ── Bracket layout ───────────────────────────────────── */
.bracket-area  { flex: 1; display: flex; flex-direction: row; gap: var(--col-gap); padding: 8px 16px 12px; overflow: hidden; min-height: 0; position: relative; z-index: 2; }
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
.match-active.pair-group::after { border-color: color-mix(in srgb, var(--accent-color) 45%, transparent); }

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
  background: color-mix(in srgb, var(--accent-color) 6%, transparent);
  border-color: color-mix(in srgb, var(--accent-color) 35%, transparent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent-color) 12%, transparent), 0 0 16px color-mix(in srgb, var(--accent-color) 18%, transparent);
}
.slot-active-left {
  background: color-mix(in srgb, var(--left-color) 18%, transparent) !important;
  border-color: color-mix(in srgb, var(--left-color) 50%, transparent) !important;
  box-shadow: 0 0 16px color-mix(in srgb, var(--left-color) 28%, transparent) !important;
}
.slot-active-left .battler-name {
  color: color-mix(in srgb, var(--left-color) 80%, #fff) !important;
}
.slot-active-right {
  background: color-mix(in srgb, var(--right-color) 18%, transparent) !important;
  border-color: color-mix(in srgb, var(--right-color) 50%, transparent) !important;
  box-shadow: 0 0 16px color-mix(in srgb, var(--right-color) 28%, transparent) !important;
}
.slot-active-right .battler-name {
  color: color-mix(in srgb, var(--right-color) 80%, #fff) !important;
}
/* Final winner — gold */
.slot-winner {
  background: rgba(245,158,11,0.15) !important;
  border-color: rgba(245,158,11,0.45) !important;
  box-shadow: 0 0 18px rgba(245,158,11,0.25) !important;
}
.slot-winner .battler-name { color: #fde68a; }

/* Non-final winner — neutral white (not side color) */
.slot-winner-round {
  background: rgba(255,255,255,0.10) !important;
  border-color: rgba(255,255,255,0.30) !important;
  box-shadow: 0 0 14px rgba(255,255,255,0.15) !important;
}
.slot-winner-round .battler-name {
  color: rgba(255,255,255,0.95) !important;
}

.slot-loser { background: rgba(255,255,255,0.02) !important; border-color: rgba(255,255,255,0.03) !important; opacity: 0.62; }
.slot-loser  .battler-name { color: var(--c-lose-text); }

.battler-name {
  font-family: 'Oswald', sans-serif;
  font-size: 14px; letter-spacing: 0.02em;
  color: var(--c-text); opacity: 0.92;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: center;
  display: inline-block; transform: skewX(4deg);
  transition: opacity 1s ease;
}
.name-hidden .battler-name { opacity: 0; transition: none; }

.win-badge {
  display: inline-flex; align-items: center; justify-content: center;
  transform: skewX(5deg); flex-shrink: 0;
  background: rgba(245,158,11,0.22); border: 1px solid rgba(245,158,11,0.5);
  color: #fbbf24; font-size: 9px; font-weight: 900; font-family: 'Inter', sans-serif;
  letter-spacing: 0.12em; padding: 2px 6px; border-radius: 2px;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
}
.takes-it-badge {
  display: inline-flex; align-items: center; justify-content: center;
  transform: skewX(5deg); flex-shrink: 0;
  background: rgba(255,255,255,0.07); border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.6); font-size: 8px; font-weight: 700; font-family: 'Inter', sans-serif;
  letter-spacing: 0.10em; padding: 1px 5px; border-radius: 2px;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
  white-space: nowrap;
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
.center-col { flex-shrink: 0; width: var(--center-col-w, 185px); display: flex; flex-direction: column; }
.final-area { flex: 1; display: flex; flex-direction: column; align-items: stretch; justify-content: center; gap: 6px; min-height: 0; }
.final-match { display: flex; flex-direction: column; gap: 6px; }
.final-match .battler-slot { height: var(--final-slot-h, 58px); }
.final-match .battler-name { font-size: 17px; letter-spacing: 0.06em; }
.final-match.match-active .battler-slot {
  border-color: color-mix(in srgb, var(--accent-color) 45%, transparent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent-color) 14%, transparent), 0 0 20px color-mix(in srgb, var(--accent-color) 22%, transparent);
}
.final-vs {
  text-align: center;
  font-family: 'Oswald', sans-serif;
  font-size: 11px; letter-spacing: 0.32em;
  color: rgba(255,255,255,0.3); opacity: 0.38;
}


/* ── Champion Reveal Overlay ────────────────────────────── */
.champ-overlay {
  position: absolute; inset: 0; z-index: 50;
  display: flex; align-items: center; justify-content: center;
  background: #060818;
}
.champ-overlay-bg {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(ellipse 60% 50% at 50% 55%, rgba(245,158,11,0.14) 0%, transparent 68%);
}
.champ-overlay-content {
  position: relative; z-index: 1;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  text-align: center;
}
.champ-genre-tag {
  font-family: 'Oswald', sans-serif; font-size: 9px;
  letter-spacing: 0.45em; text-transform: uppercase;
  color: rgba(255,255,255,0.3);
}
.champ-label {
  font-family: 'Oswald', sans-serif; font-size: 11px;
  letter-spacing: 0.5em; text-transform: uppercase;
  color: rgba(245,158,11,0.85);
}
.champ-name-slam {
  font-family: 'Oswald', sans-serif; font-size: 15vw;
  letter-spacing: 0.02em; text-transform: none; line-height: 1.05;
  color: #fff;
  text-shadow: 0 0 40px rgba(245,158,11,0.65), 0 0 80px rgba(245,158,11,0.3);
}
.champ-gold-bar {
  width: 180px; height: 2px; margin-top: 6px;
  background: linear-gradient(90deg, transparent, rgba(245,158,11,0.8), transparent);
}

/* Transition */
.champ-reveal-enter-active { transition: opacity 0.3s ease; }
.champ-reveal-leave-active { transition: opacity 0.25s ease; }
.champ-reveal-enter-from,
.champ-reveal-leave-to    { opacity: 0; }

.champ-reveal-enter-active .champ-name-slam {
  animation: champNameSlam 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) 0.35s both;
}
.champ-reveal-enter-active .champ-label {
  animation: champFadeUp 0.4s ease 0.2s both;
}
.champ-reveal-enter-active .champ-genre-tag {
  animation: champFadeUp 0.4s ease 0.08s both;
}
.champ-reveal-enter-active .champ-gold-bar {
  animation: champBarExpand 0.6s ease 0.65s both;
}

@keyframes champNameSlam {
  from { opacity: 0; transform: scale(0.72) translateY(18px); }
  to   { opacity: 1; transform: scale(1)    translateY(0); }
}
@keyframes champFadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes champBarExpand {
  from { width: 0; opacity: 0; }
  to   { width: 180px; opacity: 1; }
}

/* ── Animations ───────────────────────────────────────── */
@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.32; transform: scale(0.62); }
}

/* ══════════════════════════════════════════════════
   ANIMATION THEMES — HYPE: oversized, longer champion reveal
══════════════════════════════════════════════════ */
.bracket-root[data-anim-theme="hype"] .champ-reveal-enter-active .champ-name-slam {
  animation: champNameSlamHype 0.75s cubic-bezier(0.175, 0.885, 0.32, 1.275) 0.5s both;
}
.bracket-root[data-anim-theme="hype"] .champ-reveal-enter-active .champ-gold-bar {
  animation: champBarExpandHype 0.85s ease 0.9s both;
}
@keyframes champNameSlamHype {
  from { opacity: 0; transform: scale(0.5)  translateY(24px); }
  to   { opacity: 1; transform: scale(1.18) translateY(0); }
}
@keyframes champBarExpandHype {
  from { width: 0;     opacity: 0; }
  to   { width: 260px; opacity: 1; }
}
</style>
