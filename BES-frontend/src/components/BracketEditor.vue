<script setup>
import { computed, ref, onUnmounted } from 'vue'
import { parseDropKey } from '@/utils/pointerDnd'

const props = defineProps({
  rounds:                  { type: Object, required: true },
  topSize:                 { type: Number, required: true },
  currentRound:            { type: Number, default: 0 },
  isSmoke:                 { type: Boolean, default: false },
  unplacedParticipants:    { type: Array, default: () => [] },
  setupLocked:             { type: Boolean, default: false },
  memberLookup:            { type: Object, default: () => ({}) },
  guestsForCurrentGenre:   { type: Array, default: () => [] },
})

const emit = defineEmits([
  'update-rounds',
  'start-battle-at',
  'clear-slot',
  'set-winner',
  'clear-winner',
  'start-round',
  'reorder-smoke',
])

// ── Round name computation (repeatedly halve topSize) ──────────
const roundNames = computed(() => {
  if (props.isSmoke) return []
  const names = []
  let size = props.topSize
  while (size >= 2) {
    names.push(`Top${size}`)
    size /= 2
  }
  return names
})

// ── Round label helpers ───────────────────────────────────────
function formatLabel(roundKey) {
  if (roundKey === 'Top2') return 'FINAL'
  return roundKey.replace('Top', 'TOP ')
}

function roundLabel(roundKey) {
  const labels = { Top16: 'Top 16', Top8: 'Quarterfinals', Top4: 'Semifinals', Top2: 'Finals' }
  return labels[roundKey] || roundKey || ''
}

// ── Guest check ───────────────────────────────────────────────
const isGuestSlot = (name) =>
  !!name && props.guestsForCurrentGenre.some(g => g.guestName === name)

// ── Members lookup ────────────────────────────────────────────
const getMembersFor = (name) => props.memberLookup[name] ?? []

// ── Drag-drop state (pointer-based DnD) ───────────────────────
const dragSource = ref(null)   // { roundKey, matchIdx, slotIdx } or { smokeIdx }
const dragOverKey = ref(null)  // `${roundKey}-${matchIdx}-${slotIdx}` or `smoke-${idx}`
const poolDragName = ref(null)

let _ghostEl = null
let _ptrMoveHandler = null
let _ptrUpHandler = null

const _removePtrListeners = () => {
  if (_ptrMoveHandler) { document.removeEventListener('pointermove', _ptrMoveHandler); _ptrMoveHandler = null }
  if (_ptrUpHandler) {
    document.removeEventListener('pointerup', _ptrUpHandler)
    document.removeEventListener('pointercancel', _ptrUpHandler)
    _ptrUpHandler = null
  }
}

const _cleanupDrag = () => {
  if (_ghostEl) { document.body.removeChild(_ghostEl); _ghostEl = null }
  _removePtrListeners()
  dragSource.value = null
  poolDragName.value = null
  dragOverKey.value = null
}

/**
 * Start a pointer-based drag operation.
 * @param {'pool'|'bracket'|'smoke'} type — what is being dragged
 * @param {any} payload — name (pool), {roundKey,matchIdx,slotIdx} (bracket), numeric idx (smoke)
 * @param {PointerEvent} e
 */
const onPointerDragStart = (type, payload, e) => {
  if (props.setupLocked) return
  e.preventDefault()

  // Set drag source refs
  if (type === 'pool') {
    poolDragName.value = payload
    dragSource.value = null
  } else if (type === 'bracket') {
    dragSource.value = payload
    poolDragName.value = null
  } else if (type === 'smoke') {
    dragSource.value = { smokeIdx: payload }
    poolDragName.value = null
  }

  // Resolve ghost label text
  let ghostName = ''
  if (type === 'pool') {
    ghostName = payload
  } else if (type === 'bracket') {
    ghostName = props.rounds[payload.roundKey]?.[payload.matchIdx]?.[payload.slotIdx] ?? ''
  } else if (type === 'smoke') {
    const list = Array.isArray(props.rounds) ? props.rounds : []
    ghostName = list[payload]?.name ?? ''
  }

  // Create drag ghost
  const _ghostOffX = e.pointerType === 'touch' ? -20 : 12
  const _ghostOffY = e.pointerType === 'touch' ? -50 : 12
  _ghostEl = document.createElement('div')
  _ghostEl.textContent = ghostName
  Object.assign(_ghostEl.style, {
    position: 'fixed',
    left: `${e.clientX + _ghostOffX}px`,
    top: `${e.clientY + _ghostOffY}px`,
    padding: '5px 14px',
    background: '#1a1a1a',
    border: `1.5px solid ${type === 'pool' ? 'rgba(255,255,255,0.25)' : 'rgba(248,113,113,0.65)'}`,
    borderRadius: '8px',
    fontSize: '12px',
    fontWeight: '600',
    color: '#f0f0f0',
    boxShadow: '0 10px 28px rgba(0,0,0,0.7)',
    whiteSpace: 'nowrap',
    pointerEvents: 'none',
    zIndex: '9999',
  })
  document.body.appendChild(_ghostEl)

  _ptrMoveHandler = (ev) => {
    const ox = ev.pointerType === 'touch' ? -20 : 12
    const oy = ev.pointerType === 'touch' ? -50 : 12
    _ghostEl.style.left = `${ev.clientX + ox}px`
    _ghostEl.style.top = `${ev.clientY + oy}px`

    // Find drop target under pointer
    _ghostEl.style.display = 'none'
    const el = document.elementFromPoint(ev.clientX, ev.clientY)
    _ghostEl.style.display = ''
    const dropEl = el?.closest('[data-drop-key]')
    if (dropEl) {
      const parsed = parseDropKey(dropEl.dataset.dropKey)
      if (parsed?.type === 'bracket') {
        dragOverKey.value = `${parsed.roundKey}-${parsed.matchIdx}-${parsed.slotIdx}`
      } else if (parsed?.type === 'smoke') {
        dragOverKey.value = `smoke-${parsed.idx}`
      } else {
        dragOverKey.value = null
      }
    } else {
      dragOverKey.value = null
    }
  }

  _ptrUpHandler = (ev) => {
    _removePtrListeners()

    if (_ghostEl) _ghostEl.style.display = 'none'
    const el = document.elementFromPoint(ev.clientX, ev.clientY)
    if (_ghostEl) { document.body.removeChild(_ghostEl); _ghostEl = null }

    const dropKeyEl = el?.closest('[data-drop-key]')
    const parsed = dropKeyEl ? parseDropKey(dropKeyEl.dataset.dropKey) : null

    if (parsed && !props.setupLocked) {
      if (parsed.type === 'bracket') {
        onDrop(parsed.roundKey, parsed.matchIdx, parsed.slotIdx)
      } else if (parsed.type === 'smoke') {
        onSmokeDrop(parsed.idx)
      }
    } else {
      dragSource.value = null
      poolDragName.value = null
      dragOverKey.value = null
    }
  }

  document.addEventListener('pointermove', _ptrMoveHandler)
  document.addEventListener('pointerup', _ptrUpHandler)
  document.addEventListener('pointercancel', _ptrUpHandler)
}

// ── Standard bracket slot drop ────────────────────────────────
function onDrop(tgtRound, tgtMatch, tgtSlot) {
  // Pool → bracket drop
  if (poolDragName.value) {
    const name = poolDragName.value
    poolDragName.value = null
    dragOverKey.value = null
    const guestNames = new Set(props.guestsForCurrentGenre.map(g => g.guestName))
    if (guestNames.has(props.rounds[tgtRound]?.[tgtMatch]?.[tgtSlot])) return
    emit('update-rounds', {
      round: tgtRound,
      matchIdx: tgtMatch,
      slotIdx: tgtSlot,
      name,
      isGuest: false,
    })
    return
  }

  if (!dragSource.value || dragSource.value.smokeIdx !== undefined) return
  const { roundKey: srcRound, matchIdx: srcMatch, slotIdx: srcSlot } = dragSource.value
  dragSource.value = null
  dragOverKey.value = null
  if (srcRound === tgtRound && srcMatch === tgtMatch && srcSlot === tgtSlot) return

  // Swap: clear both slots first, then set them swapped
  emit('clear-slot', { round: srcRound, matchIdx: srcMatch, slotIdx: srcSlot })
  emit('clear-slot', { round: tgtRound, matchIdx: tgtMatch, slotIdx: tgtSlot })
  const srcVal = props.rounds[srcRound]?.[srcMatch]?.[srcSlot]
  const tgtVal = props.rounds[tgtRound]?.[tgtMatch]?.[tgtSlot]
  if (tgtVal) {
    emit('update-rounds', {
      round: srcRound, matchIdx: srcMatch, slotIdx: srcSlot, name: tgtVal, isGuest: isGuestSlot(tgtVal),
    })
  }
  if (srcVal) {
    emit('update-rounds', {
      round: tgtRound, matchIdx: tgtMatch, slotIdx: tgtSlot, name: srcVal, isGuest: isGuestSlot(srcVal),
    })
  }
}

// ── Smoke slot drop (reorder or pool placement) ───────────────
function onSmokeDrop(tgtIdx) {
  const guestNames = new Set(props.guestsForCurrentGenre.map(g => g.guestName))
  const rounds = Array.isArray(props.rounds) ? props.rounds : []

  // Pool → smoke slot
  if (poolDragName.value) {
    const name = poolDragName.value
    poolDragName.value = null
    dragOverKey.value = null
    const tgtName = rounds[tgtIdx]?.name
    if (guestNames.has(tgtName)) return
    emit('update-rounds', {
      round: 0, matchIdx: tgtIdx, slotIdx: 0, name, isGuest: false,
    })
    return
  }

  // Smoke slot → smoke slot swap (reorder)
  if (!dragSource.value || dragSource.value.smokeIdx === undefined) return
  const srcIdx = dragSource.value.smokeIdx
  dragSource.value = null
  dragOverKey.value = null
  if (srcIdx === tgtIdx) return

  const srcName = rounds[srcIdx]?.name
  const tgtName = rounds[tgtIdx]?.name
  if (guestNames.has(tgtName)) return

  emit('reorder-smoke', { fromIdx: srcIdx, toIdx: tgtIdx })
}

// ── Clear smoke slot ──────────────────────────────────────────
function clearSmokeSlot(idx) {
  const guestNames = new Set(props.guestsForCurrentGenre.map(g => g.guestName))
  const rounds = Array.isArray(props.rounds) ? props.rounds : []
  if (rounds[idx]?.name && !guestNames.has(rounds[idx].name)) {
    emit('clear-slot', { round: 0, matchIdx: idx, slotIdx: 0 })
  }
}

// ── Clear bracket slot ────────────────────────────────────────
function clearBracketSlot(roundKey, matchIdx, slotIdx) {
  emit('clear-slot', { round: roundKey, matchIdx, slotIdx })
}

// ── Winner actions ────────────────────────────────────────────
function setWinnerAction(roundKey, matchIdx, slotIdx, name) {
  emit('set-winner', { round: roundKey, matchIdx, winnerName: name })
}

function clearWinnerAction(roundKey, matchIdx) {
  emit('clear-winner', { round: roundKey, matchIdx })
}

// ── Start round ──────────────────────────────────────────────
function startRound() {
  emit('start-round')
}

function startBattleAt(roundKey, matchIdx) {
  emit('start-battle-at', { round: roundKey, matchIdx })
}

// ── Cleanup on unmount ──────────────────────────────────────
onUnmounted(() => {
  _cleanupDrag()
})
</script>

<template>
  <div class="bracket-editor">

    <!-- ══════════════════════════════════════════════════════════
         STANDARD BRACKET MODE
    ══════════════════════════════════════════════════════════════ -->
    <template v-if="!isSmoke">
      <!-- Round tabs -->
      <div v-if="roundNames.length" class="round-tabs">
        <button
          v-for="(rName, idx) in roundNames"
          :key="rName"
          class="round-tab"
          :class="{ 'round-tab-active': idx === currentRound }"
          :title="roundLabel(rName)"
        >
          {{ formatLabel(rName) }}
        </button>
      </div>

      <!-- Bracket editing placeholder — full template migrated in Task 6 -->
      <div class="editor-placeholder">
        <div class="editor-placeholder-icon">
          <i class="pi pi-sitemap"></i>
        </div>
        <span class="editor-placeholder-text">
          BRACKET EDITOR — Interactive slots will be migrated from BattleControl.vue in Task 6
        </span>
        <span class="editor-placeholder-sub">
          Use the standard bracket view (Live Bracket) for read-only display
        </span>
      </div>
    </template>

    <!-- ══════════════════════════════════════════════════════════
         7-TO-SMOKE QUEUE EDITOR
    ══════════════════════════════════════════════════════════════ -->
    <template v-else>
      <div class="smoke-section">
        <div class="section-rule">
          <span class="section-rule-label">7 TO SMOKE — QUEUE</span>
          <span class="section-rule-line"></span>
        </div>
      </div>

      <!-- Smoke queue slots -->
      <div v-if="Array.isArray(rounds) && rounds.length" class="smoke-list">
        <div
          v-for="(battler, idx) in rounds"
          :key="idx"
          :data-drop-key="`smoke-${idx}`"
          @pointerdown="(e) => !!battler?.name && onPointerDragStart('smoke', idx, e)"
          class="smoke-slot"
          :class="{
            'smoke-slot-over': dragOverKey === `smoke-${idx}`,
            'smoke-slot-source': dragSource?.smokeIdx === idx,
            'smoke-slot-guest': battler?.name && isGuestSlot(battler.name),
          }"
          style="touch-action: none;"
        >
          <div class="corner-bar-tl"></div>

          <!-- Position number -->
          <span class="smoke-pos">{{ idx + 1 }}</span>

          <!-- Name / empty state -->
          <span v-if="battler?.name" class="smoke-name">{{ battler.name }}</span>
          <span v-else class="smoke-name smoke-name-empty">—</span>

          <!-- Score -->
          <span class="smoke-score">{{ battler?.score ?? 0 }}</span>

          <!-- Guest badge -->
          <span
            v-if="battler?.name && isGuestSlot(battler.name)"
            class="smoke-guest-badge"
          >
            <i class="pi pi-star" style="font-size:7px"></i> GUEST
          </span>

          <!-- Clear button (non-guest slots only, hidden when locked) -->
          <button
            v-if="!setupLocked && battler?.name && !isGuestSlot(battler.name)"
            @click.stop="clearSmokeSlot(idx)"
            class="smoke-clear-btn"
            title="Clear slot"
          >
            <i class="pi pi-times"></i>
          </button>

          <!-- Drag handle indicator -->
          <span
            v-if="!setupLocked && battler?.name"
            class="smoke-drag-handle"
            title="Drag to reorder"
          >
            <i class="pi pi-bars"></i>
          </span>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else class="smoke-empty">
        <span class="smoke-empty-text">No queue data available</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ─────────────────────────────────────────────────────────────
   BRACKET EDITOR — Interactive bracket editing shell
   Cinema design system: Anton SC, parallelogram clip-path,
   accent color tokens, section rules.
   Full interactive bracket template migrated in Task 6.
───────────────────────────────────────────────────────────── */

/* ── Section rule (repeated here for scoped access) ──────────── */
.section-rule {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-rule-label {
  font-family: 'Anton SC', sans-serif;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.28);
  white-space: nowrap;
  flex-shrink: 0;
}
.section-rule-line {
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.07);
}

/* ════════════════════════════════════════════════════════════════
   STANDARD BRACKET
═══════════════════════════════════════════════════════════════════ */

/* Round tabs bar — same pattern as BattleControl */
.round-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.round-tab {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  padding: 10px 16px;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.35);
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s, color 0.12s;
  flex: 1;
  text-align: center;
}

.round-tab:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.55);
}

.round-tab-active {
  color: var(--accent-color);
  border-color: var(--accent-muted);
  background: var(--accent-subtle);
}

/* Placeholder for the full bracket editor (Task 6 migration) */
.editor-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 20px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed rgba(255, 255, 255, 0.08);
  min-height: 180px;
}

.editor-placeholder-icon {
  font-size: 24px;
  color: rgba(255, 255, 255, 0.12);
  margin-bottom: 4px;
}

.editor-placeholder-text {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.22);
  text-align: center;
}

.editor-placeholder-sub {
  font-family: 'Anton SC', sans-serif;
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.12);
  text-align: center;
}

/* ════════════════════════════════════════════════════════════════
   7-TO-SMOKE QUEUE EDITOR
═══════════════════════════════════════════════════════════════════ */

.smoke-section {
  margin-bottom: 12px;
}

.smoke-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* Individual smoke slot — parallelogram chip with drag affordance */
.smoke-slot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  position: relative;
  transition: background 0.12s, border-color 0.12s, box-shadow 0.12s;
  min-height: 42px;
  cursor: default;
  user-select: none;
}

/* Drag source highlight */
.smoke-slot.smoke-slot-source {
  border-color: rgba(248, 113, 113, 0.5);
  background: rgba(248, 113, 113, 0.08);
  box-shadow: inset 0 0 12px rgba(248, 113, 113, 0.06);
}

/* Drop target highlight */
.smoke-slot.smoke-slot-over {
  border-color: var(--accent-muted);
  background: var(--accent-subtle);
  box-shadow: 0 0 14px var(--accent-muted);
}

/* Guest slots get a subtle visual distinction */
.smoke-slot.smoke-slot-guest {
  border-color: rgba(245, 158, 11, 0.25);
  background: rgba(245, 158, 11, 0.04);
}

/* Position number */
.smoke-pos {
  font-family: 'Anton SC', sans-serif;
  font-size: 18px;
  letter-spacing: 0.02em;
  color: rgba(255, 255, 255, 0.25);
  line-height: 1;
  min-width: 22px;
  text-align: center;
  flex-shrink: 0;
}

/* Participant name */
.smoke-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 14px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.1;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.smoke-name-empty {
  color: rgba(255, 255, 255, 0.18);
  font-style: italic;
}

/* Score display */
.smoke-score {
  font-family: 'Anton SC', sans-serif;
  font-size: 20px;
  letter-spacing: 0.02em;
  color: rgba(255, 255, 255, 0.35);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  min-width: 28px;
  text-align: right;
  flex-shrink: 0;
}

/* Guest badge */
.smoke-guest-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  font-family: 'Anton SC', sans-serif;
  font-size: 9px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(245, 158, 11, 0.9);
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.25);
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
  white-space: nowrap;
  flex-shrink: 0;
}

/* Clear button */
.smoke-clear-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.25);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.12s, background 0.12s;
  border-radius: 0;
  font-size: 10px;
}

.smoke-clear-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

/* Drag handle indicator */
.smoke-drag-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.12);
  cursor: grab;
  font-size: 11px;
  transition: color 0.12s;
}

.smoke-slot:hover .smoke-drag-handle {
  color: rgba(255, 255, 255, 0.30);
}

.smoke-drag-handle:active {
  cursor: grabbing;
}

/* Empty state */
.smoke-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed rgba(255, 255, 255, 0.06);
}

.smoke-empty-text {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.18);
}

/* ── Corner accent bar (reused from design system) ──────────── */
.corner-bar-tl {
  position: absolute;
  top: 0;
  left: 0;
  width: 2px;
  height: 32%;
  background: linear-gradient(to bottom, var(--accent-color), transparent);
  pointer-events: none;
  opacity: 0.25;
}
</style>
