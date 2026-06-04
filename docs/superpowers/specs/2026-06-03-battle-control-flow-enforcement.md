# Battle Control — Flow Enforcement & UI Restructure

**Date:** 2026-06-03
**Issue:** #50
**Branch:** feat/battle-control-flow-enforcement

---

## Goal

Restructure `BattleControl.vue` into two distinct panels (Setup and Live) and enforce the battle program flow by restricting what the organiser can do at each stage. Reduces operational errors during live events, especially when multiple genres are running in interleaved rounds.

---

## Event Day Flow (Context)

Multiple genres interleave **round-by-round**: all genres complete their Top16 matches before any genre advances to Top8. Within a single genre, the organiser completes the **full round** before switching to another genre — they will not switch mid-round (e.g., after match 2 of 8).

Champions are locked quietly at the end of each genre's final (`DECIDED` phase) and revealed one-by-one as a ceremony at the very end of the event.

---

## Page Structure

The current single mixed card splits into two stacked cards:

```
┌─────────────────────────────────────────────┐
│  Quick access links                         │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  SETUP  ▾        [LOCKED · BATTLE IN PROGRESS] │
│  (auto-collapses when battle starts)        │
│  Bracket size · Seeding · Bracket view      │
│  Judges · Guests                            │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  LIVE MATCH                                 │
│  Genre switcher (with status indicators)    │
│  Phase badge · Save indicator               │
│  Prev / Current / Next pair display         │
│  Judge vote panel                           │
│  Phase-appropriate action buttons           │
│  ─────────────────────────────────────────  │
│  Overlay settings · Upload images           │
│  Reset Bracket (destructive, two-step)      │
└─────────────────────────────────────────────┘
```

Overlay settings and image upload move out of Setup into the bottom of the Live panel — they are runtime ops with no harmful side effects and should always be accessible.

---

## Setup Panel

### `setupLocked` Computed

No new localStorage keys. Derived entirely from existing state:

```js
const setupLocked = computed(() =>
  battlePhase.value !== 'IDLE' ||
  currentBattle.value.length > 0 ||
  (!isSmoke.value && Object.values(rounds.value).some(
    list => Array.isArray(list) && list.some(m => Array.isArray(m) && m[2])
  )) ||
  (isSmoke.value && Array.isArray(rounds.value) && rounds.value.some(r => (r?.score ?? 0) > 0))
)
```

### Edit Mode (`setupLocked = false`)

Full controls visible and interactive:
- Bracket size selector
- Seeding tools (rank fill, high-low, random, split) + seeding pool
- Drag-drop bracket (standard) or smoke queue
- Judge management (add / remove / weightage)
- Guest management (add / remove)

### View Mode (`setupLocked = true`)

- Panel auto-collapses the first time `setupLocked` becomes true
- Can be manually re-expanded for reference
- Single banner replaces all controls: *"Setup locked — Reset Bracket to modify"*
- All edit controls are hidden (not just disabled)
- Bracket renders as a **read-only status view**:
  - `◯` — not played (`m[2] === null`, no battle started)
  - `▶` — in progress (current active match)
  - `✓` — complete (`m[2]` set)
- WIN button present on every match slot (see WIN Button section below)
- **"Start from here" button** visible on unplayed matches (`m[2] === null`) in the active round when `battlePhase === 'IDLE'`. Clicking confirms: *"Start from [A] vs [B]? Matches before this one will be skipped."* Calls `initiateBattlePairAt(top, pairList, matchIdx)`.

### Round Tab Progression

- Round tabs for later rounds (Top8, Top4, Top2) show `🔒` when `roundTabStatus === 'locked'` (previous round not fully complete)
- Clicking a locked tab shows an inline message: *"Complete all [TopN] matches first"* — no navigation
- `roundTabStatus === 'done'` is determined by **all matches having `m[2]` set** (bracket-data-driven, not phase-driven). This survives refresh and genre switches.

---

## Live Panel

### Genre Switcher

Chips at the top of the Live panel with per-genre status dot. Since `battlePhase` is a single global ref (reflects the active genre only), non-active genres read their saved phase from `localStorage.getItem(genreBattleStateKey(genre))?.phase`.

Three states (simpler than four — "round complete" and "not started" both show grey since neither needs the organiser's immediate attention):

| Dot | Meaning | Condition |
|-----|---------|-----------|
| ● grey | IDLE — not started or between rounds | saved phase is `IDLE` or no saved state |
| ● amber pulse | Needs attention — match in progress | saved phase is `LOCKED`, `VOTING`, or `REVEALED` |
| ★ gold | Done — champion locked | `genreChampions[genre]` exists |

**Switch rules:**

| Current phase | Switch behaviour |
|--------------|-----------------|
| `IDLE` | Free — switch immediately |
| `DECIDED` | Free — tournament complete for this genre |
| `LOCKED` or `VOTING` | Hard blocked — button inert + tooltip: *"Finish this match first"* |
| `REVEALED` | Blocked — tooltip: *"Click Next to advance, then switch genres"* |

No confirm dialog for the hard-blocked cases — the button simply does not respond (with a tooltip explaining why). This prevents accidental dismissal of the prompt from disrupting a live match.

### Phase Action Buttons

Only the contextually correct button(s) are shown at any time:

| Phase | Buttons |
|-------|---------|
| IDLE, no battle active | *(none — Start Round lives in Setup bracket)* |
| `LOCKED` | `Open Voting` |
| `VOTING` — judges still voting | `Get Score` (disabled until all judges voted) |
| `VOTING` — final, all voted, clear winner | `Lock Champion` |
| `VOTING` — final, all voted, tie | `Get Score` (triggers rematch flow) |
| `REVEALED` | `Next ►` (primary) · `‹ Prev` (small secondary) |
| `DECIDED` | `Reveal Champion` / `Dismiss Reveal` · `Unlock` (small secondary) |

**Previous button:** Demoted to a small text link beside `Next`. Clicking confirms: *"Go back to [A vs B]? The current match result will be cleared."* — almost never used in practice but kept for recovery.

### Judge Vote Panel

Unchanged from current implementation. Visible in all phases. Shows per-judge vote state and winner preview banner when all judges have voted.

### Reset Bracket

Moved to the bottom of the Live panel. Styled as a destructive tertiary button (red text, no fill). Two-step inline confirm:
1. First click → replaces button with: *"This will clear all bracket data and results for [Genre]. Cannot be undone."* + `Confirm Reset` button
2. `Confirm Reset` → executes `confirmResetBracket()` which also unlocks Setup (sets `setupLocked` to false)

---

## WIN Button Behaviour

The WIN button in the bracket is the **surgical correction path** — it allows changing a match result without resetting the entire bracket.

| Match state | Confirmation shown |
|-------------|-------------------|
| `m[2] === null` (no winner yet) | *"Set [name] as winner of this match? They will be placed in the [TopN] slot."* |
| `m[2]` exists (winner already set) | *"Replace winner: [current] → [new name]? The [TopN] slot will be updated. If [name] has already played in a later round, correct those results manually."* |

The second confirmation covers the edge case of correcting a stale result mid-tournament. In practice, organisers won't correct Top16 results once they're in Top4, but the path exists.

---

## Actions Locked by `setupLocked`

All of the following are hidden (not greyed out) when `setupLocked = true`:

- Bracket drag-and-drop (onDragStart / onDrop / onSmokeDrop)
- Seeding buttons (autoFillSeeds, highVsLowFill, randomFill, splitBracketFill)
- Bracket size selector chips
- Bracket slot clear buttons
- Judge add / remove / weightage input
- Guest add / remove

The following are **never locked** (always accessible):
- Overlay settings (colors, show images)
- Image upload / delete
- Genre switcher (subject to phase rules above)
- WIN button (correction path)
- Reset Bracket (behind two-step confirm)
- "Start from here" (behind confirmation, only on unplayed matches)

---

## What Does Not Change

- Phase state machine: IDLE → LOCKED → VOTING → REVEALED → (Next →) LOCKED or IDLE. DECIDED branch for finals.
- WebSocket subscriptions and state persistence (localStorage + backend)
- Refresh recovery via `jumpToRecoveredPair`
- Genre-switch state save/restore via `saveGenreBattleState` / `restoreAndBroadcastGenreBattle`
- Smoke mode behaviour
- Judge vote weightage display

---

## Files Affected

- `BES-frontend/src/views/BattleControl.vue` — primary change (template restructure + computed + guards)
- No backend changes required
- No new API calls
