# Design Spec: Real-time Judge Vote Transparency Panel
**Issue:** #49  
**Branch:** `feat/battle-division-state-persistence`  
**Date:** 2026-06-03

---

## Overview

Add a horizontal judge vote panel to the Live Match card in `BattleControl.vue` so the organiser can see each judge's vote in real time during a battle. The panel also drives GET SCORE gating and upgrades the champion display in DECIDED phase.

No backend changes required. All data flows through the existing `battleJudges` ref and per-judge WebSocket subscriptions already in place.

---

## Scope

Frontend only — `BattleControl.vue` is the only file that changes.

---

## Design Decisions (confirmed with user)

| Decision | Choice |
|----------|--------|
| Panel layout | Horizontal grid — one card per judge |
| Vote label | Battler **name** (not LEFT / RIGHT) |
| Pending | Amber "⏳ WAITING" |
| Left vote | Teal — `currentBattlePair[0]` name |
| Right vote | Blue — `currentBattlePair[1]` name |
| Winner preview | Green banner + name + tally when `allJudgesVoted`; gray "TIE" otherwise |
| Champion display | Upgrade existing winner bar in DECIDED phase (Option A: green glow + star label) |
| GET SCORE gate | Disabled until `allJudgesVoted === true` |
| Phase visibility | Panel always visible (IDLE through DECIDED) |

---

## Data Model

Existing refs/computeds used (no new state needed):

| Source | Shape | Notes |
|--------|-------|-------|
| `battleJudges.value.judges` | `[{ id, judgeName, vote }]` | `vote`: `-3` = pending, `0` = left, `1` = right |
| `currentBattlePair.value` | `[leftName, rightName]` | `undefined` when no active battle |
| `allJudgesVoted` | `boolean` | Already computed; gates GET SCORE |
| `tentativeWinner` | `-2 \| -1 \| 0 \| 1` | `-2` = pending, `-1` = tie, `0` = left, `1` = right |
| `genreChampions[selectedGenre]` | `string \| undefined` | Champion name locked by `lockChampion()` |
| `battlePhase` | `string` | IDLE / LOCKED / VOTING / REVEALED / DECIDED |
| `revealActive` | `boolean` | True after organiser clicks Reveal Champion |

### New derived computeds

**`voteCountDisplay`** — for the winner preview tally (e.g. "2 – 1"):
```js
const voteCountDisplay = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  const left  = judges.filter(j => j.vote === 0).length
  const right = judges.filter(j => j.vote === 1).length
  return { left, right }
})
```

No other new computeds are needed — `allJudgesVoted`, `tentativeWinner`, and `currentBattlePair` already carry everything else.

---

## Template Changes

### 1. Judge vote panel

**Position:** Inside the Live Match card, inserted **after** the winner announcement bar and **before** the match-pairs grid. No `v-if` guard — always rendered when the Live Match card is visible.

**Structure:**

```
[Judge A card] [Judge B card] [Judge C card]   ← grid-cols-N, N = judge count
[Winner preview banner]                         ← shown only when allJudgesVoted
```

**Judge card states:**

| State | Border | Label text | Label color |
|-------|--------|-----------|-------------|
| Pending (`vote === -3`) | `#f59e0b` (amber) | ⏳ WAITING | amber-400 |
| Left voted (`vote === 0`) | `#34d399` (teal) | `currentBattlePair[0]` | emerald-400 |
| Right voted (`vote === 1`) | `#3b82f6` (blue) | `currentBattlePair[1]` | blue-400 |

When `currentBattlePair` is undefined (no active battle), fall back to "LEFT" / "RIGHT" as labels for voted judges.

**Winner preview banner** (shown when `allJudgesVoted`):
- Clear winner: green left-border chip, `tentativeWinner === 0 ? currentBattlePair[0] : currentBattlePair[1]`, tally `"${voteCountDisplay.left} – ${voteCountDisplay.right}"`
- Tie: gray left-border chip, "TIE — N – N", "Rematch required"

Empty state (no judges assigned): render a single muted row "No judges assigned for this battle."

### 2. GET SCORE button — disable until all judges voted

Existing button at line ~2558:
```html
<button v-if="battlePhase === 'VOTING' && !showFinalReveal" @click="submitGetScore" ...>
```

Add `:disabled="!allJudgesVoted"` and conditional styling:
- **Enabled** (`allJudgesVoted`): current `bg-accent` style
- **Disabled** (`!allJudgesVoted`): muted surface, `cursor-not-allowed`, `opacity-50`

No change to the button's `v-if` condition — it still only appears in VOTING phase for non-final battles.

### 3. Champion display — DECIDED phase winner bar upgrade

The existing winner announcement bar (lines 2472–2488) renders for all phases. When `battlePhase === 'DECIDED'`, replace it with an upgraded "CHAMPION LOCKED" variant.

**Condition:** `battlePhase === 'DECIDED'`  
**Champion name:** `genreChampions[selectedGenre] ?? currentGenreChampion ?? '—'`

Upgraded bar (Option A — green glow + star label):
```
┌─────────────────────────────────────────────────────┐
│ ⭐ CHAMPION LOCKED                                   │ ← 9px, emerald-400, letter-spacing 3px
│                                                     │
│ CREW ALPHA                                          │ ← 18px, emerald-400, text-shadow glow
│                                                     │
│ FINAL · ORGANISER ONLY — NOT REVEALED YET           │ ← 9px, content-muted (hidden after reveal)
└─────────────────────────────────────────────────────┘
```

Styling: `border-l-4 border-emerald-400 bg-emerald-400/10`, champion name `text-shadow: 0 0 12px rgba(52,211,153,0.4)`.

After `revealActive` becomes true, the "NOT REVEALED YET" disclaimer is hidden.

The normal winner announcement bar continues to render as-is for all other phases (IDLE, LOCKED, VOTING, REVEALED).

---

## Implementation Plan

All changes are in a single section of `BattleControl.vue`.

### Script additions (top of `<script setup>`)

```js
const voteCountDisplay = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return {
    left:  judges.filter(j => j.vote === 0).length,
    right: judges.filter(j => j.vote === 1).length,
  }
})
```

### Template additions

**A.** After the winner announcement bar `</div>` (line ~2488), before `<!-- Match pairs (standard) -->` (line 2490):

Insert the judge grid panel and winner preview banner.

**B.** On the GET SCORE button (~line 2558):

Add `:disabled="!allJudgesVoted"` and dynamic class for enabled/disabled styling.

**C.** The winner announcement bar block (lines 2472–2488):

Wrap in `<template v-else>` paired with a `<template v-if="battlePhase === 'DECIDED'">` that renders the upgraded champion display.

---

## Edge Cases

| Scenario | Handling |
|----------|---------|
| No judges assigned | Panel shows "No judges assigned" muted row |
| All judges voted but no active pair | Voted labels fall back to LEFT / RIGHT |
| DECIDED phase, champion cleared (Unlock) | Falls back gracefully to `'—'` |
| revealActive during DECIDED | "NOT REVEALED YET" disclaimer hidden |
| Smoke format | Panel renders the same way (smoke uses same judge pool) |

---

## Non-Goals

- No backend changes
- No new files
- No change to WebSocket subscription logic (already per-judge real-time)
- No change to BattleOverlay, BattleJudge, or BracketVisualization
