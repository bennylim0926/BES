<script setup>
import { computed } from 'vue'

const props = defineProps({
  rounds:            { type: Object, required: true },
  topSize:           { type: Number, required: true },
  currentRound:      { type: Number, default: 0 },
  isSmoke:           { type: Boolean, default: false },
  currentBattleLeft:  { type: String, default: '' },
  currentBattleRight: { type: String, default: '' },
})

// ── Round names: repeatedly halve topSize ───────────────────────
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

// ── Smoke mode data ────────────────────────────────────────────
const smokeList = computed(() => {
  if (!props.isSmoke) return []
  // Could be a plain array (current codebase pattern) or wrapped in TopKey
  if (Array.isArray(props.rounds)) return props.rounds
  return props.rounds?.Top7 ?? props.rounds?.['7-to-Smoke'] ?? []
})

// ── Round label formatting ─────────────────────────────────────
function formatLabel(roundKey) {
  if (roundKey === 'Top2') return 'FINAL'
  return roundKey.replace('Top', 'TOP ')
}

// ── Slot state class computation ───────────────────────────────
function slotClass(match, slotIdx) {
  const name = match[slotIdx]
  if (!name) return 'bracket-slot-empty'

  // Currently battling
  if (name === props.currentBattleLeft || name === props.currentBattleRight) {
    return 'bracket-slot-active'
  }

  // Match has a winner
  if (match[2] != null) {
    return match[2] === name ? 'bracket-slot-winner' : 'bracket-slot-loser'
  }

  // Filled but undecided
  return 'bracket-slot-filled'
}

function isDecided(match) {
  return match[2] != null
}
</script>

<template>
  <div class="bracket-viewer">

    <!-- ══════════════════════════════════════════════════════════
         7-TO-SMOKE MODE
    ══════════════════════════════════════════════════════════════ -->
    <div v-if="isSmoke" class="smoke-viewer">
      <div class="smoke-header">
        <span class="section-rule-label">7-TO-SMOKE QUEUE</span>
        <span class="section-rule-line"></span>
      </div>

      <div class="smoke-list">
        <div
          v-for="(battler, idx) in smokeList"
          :key="idx"
          class="smoke-slot"
          :class="{
            'smoke-active': idx < 2,
            'smoke-next': idx === 2,
          }"
        >
          <span class="smoke-pos">{{ idx + 1 }}</span>
          <span class="smoke-name">{{ battler.name || '—' }}</span>
          <span class="smoke-score">{{ battler.score ?? 0 }}</span>
          <span v-if="idx < 2" class="smoke-badge">FIGHTING</span>
          <span v-else-if="idx === 2" class="smoke-badge smoke-badge-next">NEXT</span>
        </div>
      </div>
    </div>

    <!-- ══════════════════════════════════════════════════════════
         STANDARD BRACKET MODE
    ══════════════════════════════════════════════════════════════ -->
    <div v-else class="round-list">
      <!-- Empty state when no bracket data -->
      <div v-if="!roundNames.length || !Object.keys(rounds).length" class="round-empty round-empty-full">
        <span class="type-label">No bracket data</span>
      </div>
      <template v-else>
        <div
          v-for="(round, rIdx) in roundNames"
          :key="round"
          class="round-column"
          :class="{ 'round-column-current': rIdx === currentRound }"
        >
        <!-- Round label -->
        <div class="round-label">
          <span class="round-label-text">{{ formatLabel(round) }}</span>
          <span v-if="rIdx === currentRound" class="round-label-dot"></span>
        </div>

        <!-- Match cards -->
        <div class="round-matches">
          <div
            v-for="(match, mIdx) in (rounds[round] || [])"
            :key="mIdx"
            class="match-card"
            :class="{ 'match-card-active': (match[0] === currentBattleLeft && match[1] === currentBattleRight) || (match[0] === currentBattleRight && match[1] === currentBattleLeft) }"
          >
            <!-- Accent corner bar -->
            <div class="match-corner-bar"></div>

            <!-- Left slot -->
            <div
              class="match-slot match-slot-top"
              :class="slotClass(match, 0)"
            >
              <span class="slot-name">{{ match[0] || '—' }}</span>
              <span v-if="isDecided(match) && match[2] === match[0]" class="match-winner-badge">WIN</span>
            </div>

            <!-- Right slot -->
            <div
              class="match-slot match-slot-bottom"
              :class="slotClass(match, 1)"
            >
              <span class="slot-name">{{ match[1] || '—' }}</span>
              <span v-if="isDecided(match) && match[2] === match[1]" class="match-winner-badge">WIN</span>
            </div>
          </div>

          <!-- Empty state: round exists but no matches -->
          <div v-if="!rounds[round]?.length" class="round-empty">
            <span class="type-label">No matches</span>
          </div>
        </div>
      </div>
      </template>
    </div>

  </div>
</template>

<style scoped>
/* ─────────────────────────────────────────────────────────────
   BRACKET VIEWER — Read-only bracket display
   Cinema design system: Anton SC, parallelogram clip-path,
   accent color tokens, semantic state colours.
───────────────────────────────────────────────────────────── */

.bracket-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* ── Shared section rule (used in smoke header) ───────────── */
.section-rule-label {
  font-family: 'Anton SC', sans-serif;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.28);
  white-space: nowrap;
}
.section-rule-line {
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.07);
}

/* ════════════════════════════════════════════════════════════
   SMOKE MODE
═══════════════════════════════════════════════════════════════ */
.smoke-viewer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.smoke-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
}

.smoke-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 12px;
  overflow-y: auto;
  flex: 1;
}

.smoke-slot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.smoke-slot.smoke-active {
  background: var(--accent-subtle);
  border-color: var(--accent-muted);
  box-shadow: 0 0 12px var(--accent-muted);
}

.smoke-slot.smoke-next {
  border-color: rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.06);
}

.smoke-pos {
  font-family: 'Anton SC', sans-serif;
  font-size: 18px;
  letter-spacing: 0.02em;
  color: rgba(255, 255, 255, 0.25);
  line-height: 1;
  min-width: 20px;
  text-align: center;
}

.smoke-active .smoke-pos {
  color: var(--accent-color);
}

.smoke-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 14px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.80);
  line-height: 1.1;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.smoke-score {
  font-family: 'Anton SC', sans-serif;
  font-size: 22px;
  letter-spacing: 0.02em;
  color: rgba(255, 255, 255, 0.40);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  min-width: 28px;
  text-align: right;
}

.smoke-active .smoke-score {
  color: var(--accent-color);
}

.smoke-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: 9px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  padding: 2px 8px;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: var(--accent-muted);
  color: var(--accent-color);
  border: 1px solid var(--accent-muted);
  white-space: nowrap;
  line-height: 1;
}

.smoke-badge-next {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.35);
  border-color: rgba(255, 255, 255, 0.10);
}

/* ════════════════════════════════════════════════════════════
   STANDARD BRACKET — Horizontal round columns
═══════════════════════════════════════════════════════════════ */
.round-list {
  display: flex;
  gap: 16px;
  padding: 8px 12px;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.10) transparent;
  align-items: stretch;
}

.round-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 180px;
  max-width: 220px;
  flex-shrink: 0;
}

.round-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  margin-bottom: 2px;
}

.round-label-text {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.30);
  line-height: 1;
}

.round-label-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent-color);
  box-shadow: 0 0 6px var(--accent-muted);
  flex-shrink: 0;
}

.round-matches {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.match-card {
  position: relative;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.match-card.match-card-active {
  border-color: var(--accent-muted);
  box-shadow: 0 0 14px var(--accent-muted);
  background: var(--accent-subtle);
}

/* Accent corner bar on match cards */
.match-corner-bar {
  position: absolute;
  top: 0;
  left: 0;
  width: 2px;
  height: 32%;
  background: linear-gradient(to bottom, var(--accent-color), transparent);
  pointer-events: none;
  opacity: 0.3;
}

.match-card-active .match-corner-bar {
  opacity: 0.7;
}

/* ── Match slot (left/right) ──────────────────────────────── */
.match-slot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  min-height: 32px;
  position: relative;
}

.match-slot-top {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.slot-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  line-height: 1.1;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Winner badge — absolute positioned over the winning slot */
.match-winner-badge {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  font-family: 'Anton SC', sans-serif;
  font-size: 8px;
  letter-spacing: 0.18em;
  padding: 2px 6px;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
  background: rgba(16, 185, 129, 0.15);
  color: rgb(52, 211, 153);
  border: 1px solid rgba(52, 211, 153, 0.25);
  line-height: 1;
  text-transform: uppercase;
}

/* ── Slot state classes ────────────────────────────────────── */

/* Empty slot (no name) */
.bracket-slot-empty {
  opacity: 0.35;
}
.bracket-slot-empty .slot-name {
  color: rgba(255, 255, 255, 0.20);
}

/* Currently battling */
.bracket-slot-active {
  background: var(--accent-subtle);
}
.bracket-slot-active .slot-name {
  color: var(--accent-color);
}

/* Winner */
.bracket-slot-winner {
  background: rgba(16, 185, 129, 0.06);
}
.bracket-slot-winner .slot-name {
  color: rgb(52, 211, 153);
}

/* Loser */
.bracket-slot-loser .slot-name {
  color: rgba(255, 255, 255, 0.20);
}

/* Filled but undecided */
.bracket-slot-filled .slot-name {
  color: rgba(255, 255, 255, 0.70);
}

/* ── Empty round state ─────────────────────────────────────── */
.round-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 8px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed rgba(255, 255, 255, 0.06);
}

.round-empty .type-label {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.20);
}

/* Full-width empty state for top-level bracket placeholder */
.round-empty-full {
  width: 100%;
  min-height: 120px;
}

/* ── Current round column indicator ────────────────────────── */
.round-column-current .round-label-text {
  color: rgba(255, 255, 255, 0.55);
}

/* ── Scrollbar styling ─────────────────────────────────────── */
.round-list::-webkit-scrollbar {
  height: 4px;
}
.round-list::-webkit-scrollbar-track {
  background: transparent;
}
.round-list::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.10);
  border-radius: 2px;
}
.round-list::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.20);
}
</style>
