# Battle Audition Sub-Mode Design

**Date:** 2026-06-25
**Status:** Approved

## Overview

Introduces a `pairSubMode` field on `EventCategory` that selects between two behaviours when judging mode is PAIR:

- **SHOWCASE** (default) — existing behaviour, unchanged. Gaps in audition numbers are filled with placeholder slots.
- **BATTLE** — compact pairing over real participants only. No placeholders. Odd participant count folds the last participant into the previous pair for a 3-way battle. Every slot shows a LEFT / MIDDLE / RIGHT position label.

## Data Model

### DB Migration — `V44__add_pair_sub_mode_to_event_category.sql`

```sql
ALTER TABLE event_category ADD COLUMN pair_sub_mode VARCHAR(20) NOT NULL DEFAULT 'SHOWCASE';
```

### `EventCategory.java`

```java
@Column(name = "pair_sub_mode")
private String pairSubMode = "SHOWCASE"; // "SHOWCASE" | "BATTLE"
```

## Backend

### New API Endpoint

```
POST /api/v1/event/{eventName}/categories/{categoryId}/pair-sub-mode
Body: { "pairSubMode": "SHOWCASE" | "BATTLE" }
```

Same implementation pattern as `updateCategoryNumberColor` / `updateCategoryRoundLabel`.

### DTOs

- `GetEventCategoryDto` — add `pairSubMode` field so EventDetails renders the current value on load.
- Display state payload (`buildStatePayload` in `EmceeRoundView`) — add `pairSubMode` so `AuditionDisplay` receives it via WebSocket with no extra fetch.

## Shared Pair-Building Helper — `utils/auditionPairs.js`

Extracts the pair-building logic that is currently duplicated across `EmceeRoundView.vue` and `PairScoreCards.vue` into a single testable function.

```js
// buildPairs(participants, pairSubMode)
// Returns array of rounds; each round is an array of 1–3 slot objects.
export function buildPairs(participants, pairSubMode) {
  const sorted = [...participants].sort((a, b) => a.auditionNumber - b.auditionNumber)

  if (pairSubMode !== 'BATTLE') {
    // SHOWCASE: existing behaviour — iterate 1..max, fill gaps with placeholders
    if (!sorted.length) return []
    const maxNum = sorted[sorted.length - 1].auditionNumber
    const result = []
    for (let i = 1; i <= maxNum; i += 2) {
      result.push([
        sorted.find(p => p.auditionNumber === i) ?? { _placeholder: true, auditionNumber: i },
        sorted.find(p => p.auditionNumber === i + 1) ?? { _placeholder: true, auditionNumber: i + 1 },
      ])
    }
    return result
  }

  // BATTLE: compact sequential pairing, no placeholders
  const rounds = []
  for (let i = 0; i < sorted.length; i += 2) {
    rounds.push([sorted[i], sorted[i + 1]].filter(Boolean))
  }
  // Odd count: fold last lone participant into previous pair → 3-way
  if (sorted.length % 2 !== 0 && rounds.length >= 2) {
    const lone = rounds.pop()[0]
    rounds[rounds.length - 1].push(lone)
  }
  return rounds
}
```

### Position Mapping

Derived from slot index within a round — no extra field:

| Index | Position | Display order |
|-------|----------|---------------|
| 0 | LEFT | first |
| 1 | RIGHT (2-way) / MIDDLE (3-way) | second |
| 2 | RIGHT (3-way only) | third |

For 3-way display order: LEFT · MIDDLE · RIGHT (ascending audition number).

## Frontend — EventDetails.vue

When judging mode is PAIR, render a per-category sub-mode picker below the existing mode description:

```
PAIR MODE — AUDITION STYLE

[ Category: Breaking Open ]   [SHOWCASE]  [BATTLE]
[ Category: Hip-Hop Open  ]   [SHOWCASE]  [BATTLE]
```

- Each row shows the category name and two parallelogram toggle buttons.
- Selecting a button calls the new `pair-sub-mode` API endpoint and updates `GetEventCategoryDto.pairSubMode` locally.
- Default is SHOWCASE (backwards-compatible).
- Only visible when `judgingMode === 'PAIR'`.

## Frontend — AuditionList.vue

Loads `pairSubMode` from `GetEventCategoryDto` for the active category and passes it as a new prop to both `EmceeRoundView` and `PairScoreCards`.

## Frontend — EmceeRoundView.vue

### Props

```js
pairSubMode: { type: String, default: 'SHOWCASE' }
```

### Pair building

Replace inline pair-building with `buildPairs(props.participants, props.pairSubMode)`.

### Gap warning (`gapAfterCurrent`)

Hidden entirely in BATTLE mode (`pairSubMode === 'BATTLE'`). No gaps exist, so the warning is never needed.

### NOW card — position badges

In BATTLE mode, render a small parallelogram position chip beside each audition number:

```
[ LEFT ]  #1  Name A
[ RIGHT ] #2  Name B
```

3-way:
```
[ LEFT ]   #1  Name A
[ MIDDLE ] #3  Name C
[ RIGHT ]  #5  Name E
```

Color scheme:
- LEFT — amber (`text-amber-400`)
- RIGHT — neutral white/muted
- MIDDLE — accent color

### Queue (upcoming rounds)

Same position badges at reduced opacity so the emcee can pre-announce sides.

### State payload

`buildStatePayload()` adds `pairSubMode` to the published object.

## Frontend — PairScoreCards.vue

### Props

```js
pairSubMode: { type: String, default: 'SHOWCASE' }
```

### Pair building

Replace inline pair-building with `buildPairs(props.cards, props.pairSubMode)`.

### Audition number selector buttons

In BATTLE mode, a small position label chip appears above each number button:

```
┌─────────┐  ┌─────────┐
│  LEFT   │  │  RIGHT  │
│   #1    │  │   #2    │
└─────────┘  └─────────┘
```

3-way adds a third button (LEFT / MIDDLE / RIGHT stacked vertically).

The active button's highlight extends to the position label chip.

### Active participant detail panel

In BATTLE mode, a small position badge appears inline next to the audition number:
`LEFT · #1 · Name A`

## Frontend — AuditionDisplay.vue

`pairSubMode` arrives via the existing WebSocket state payload — no extra fetch needed.

### PAIR BATTLE layout

Position labels added to stacked names in the current-slots area:

**2-way:**
```
[ LEFT ]  #1  Name A
────────────────────
[ RIGHT ] #2  Name B
```

**3-way:**
```
[ LEFT ]   #1  Name A
────────────────────
[ MIDDLE ] #3  Name C
────────────────────
[ RIGHT ]  #5  Name E
```

Position labels are small, uppercase, low-opacity chips — visible but not dominant against the large participant names.

The existing `pair-divider` element is reused; a 3-way round simply renders two dividers.

SOLO mode, SHOWCASE PAIR mode, and the UP NEXT section are unaffected.

## Change Summary

| Layer | File(s) | Change |
|-------|---------|--------|
| DB | `V44__add_pair_sub_mode_to_event_category.sql` | New column |
| Model | `EventCategory.java` | `pairSubMode` field |
| Endpoint | `EventController.java` | `POST .../pair-sub-mode` |
| DTOs | `GetEventCategoryDto.java` | `pairSubMode` field |
| Helper | `utils/auditionPairs.js` | New — `buildPairs()` |
| Settings UI | `EventDetails.vue` | Per-category sub-mode picker |
| Orchestration | `AuditionList.vue` | Pass `pairSubMode` prop |
| Emcee | `EmceeRoundView.vue` | `buildPairs`, position badges, hide gap warning |
| Judge | `PairScoreCards.vue` | `buildPairs`, position labels on buttons |
| OBS | `AuditionDisplay.vue` | Position labels, 3-way dividers |

## Backwards Compatibility

All existing PAIR categories default to `SHOWCASE`. No behaviour changes for events already using PAIR mode.
