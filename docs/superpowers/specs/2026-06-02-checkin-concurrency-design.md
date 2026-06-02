# Check-in Concurrency & Display Design

**Date:** 2026-06-02
**Branch:** `fix/checkin-concurrency`
**Scope:** EventDetails.vue check-in flow · AuditionNumber.vue display · AuditionList.vue reset gate

---

## Problem

3–4 operators run the check-in desk simultaneously. Several race conditions exist:

1. **Double check-in** — stale list (30s poll) lets two operators check in the same person. Backend has no duplicate guard.
2. **Preview-then-cancel stuck** — `sendCheckinPreview` fires on dialog open (not confirm). Cancelling leaves AuditionNumber permanently showing a ghost preview with no numbers.
3. **Serial animation blocks concurrent check-ins** — the global `auditionQueue` processes one genre animation at a time across all participants. Two simultaneous confirmations queue serially instead of animating in parallel.
4. **AuditionNumber single-slot design** — new person's preview immediately flushes current person to history, losing their animation if their numbers haven't arrived yet.
5. **Reset Scores too easy to trigger accidentally** — no friction before wiping all judge scores for a genre.
6. **Double-confirm** — confirm button has no debounce; rapid double-tap fires two check-in requests.

---

## Decisions

| # | Decision |
|---|---|
| 1 | Backend returns **409** if participant already has all audition numbers assigned. Frontend shows "Already checked in at another desk." |
| 2 | Preview fires **on dialog open** (existing behaviour kept) — important for the display screen to show the person before the number is generated. Cancel sends an explicit cancel signal. |
| 3 | AuditionNumber moves to a **multi-slot grid** — up to 3 slots per row, wrapping to a second row. Each slot is independent. |
| 4 | Each slot has its **own animation queue** — parallel animations across slots. `fakeNums` and `rollingIntervals` keyed by `slotId-genreName` to avoid collision when two slots share the same genre name. |
| 5 | Cancel sends a WS cancel signal on the **existing `/topic/checkin-preview/`** channel with `cancelled: true`. Only `AuditionNumber.vue` subscribes to this channel — no other consumers affected. |
| 6 | Reset Scores requires **re-entering the event access code** before executing. |
| 7 | Confirm button **disabled after first click**, re-enabled only on error response. |

---

## Architecture

### AuditionNumber.vue — multi-slot state model

Replace `currentPerson` (single ref) with `activeSlots` (array):

```js
// Before
const currentPerson = ref(null)
const auditionQueue = []       // global, serial
let queueRunning = false
const fakeNums = ref({})       // keyed by genreName
const rollingIntervals = {}    // keyed by genreName

// After
const activeSlots = ref([])
// Each slot: {
//   slotId,          // participantId (or name fallback)
//   person,          // { participantId, name, refCode, memberNames, genres[] }
//   queue: [],       // per-slot pending number messages
//   running: false,  // per-slot animation flag
// }
const fakeNums = ref({})       // keyed by `${slotId}-${genreName}`
const rollingIntervals = {}    // keyed by `${slotId}-${genreName}`
```

`history` stays unchanged — completed slots move to history exactly as before.

### WS message handling (AuditionNumber.vue only)

**`/topic/checkin-preview/` → `onPreview(msg)`**

```
if msg.cancelled:
  remove slot matching msg.participantId → done

if slot exists for msg.participantId:
  merge new genres into existing slot (same-person duplicate preview)
else:
  add new slot to activeSlots
```

No flushing, no queue clearing. Each slot is created/removed independently.

**`/topic/audition/` → `onReceiveAuditionNumber(msg)`**

```
find slot matching msg.participantId:
  if found:
    push msg to slot.queue
    if !slot.running: processSlotQueue(slotId)
  else:
    check history → patch directly (late message, no animation)
```

`processSlotQueue(slotId)` runs the 2-second slot-machine animation using `fakeNums[slotId-genre]` and `rollingIntervals[slotId-genre]`. When slot's queue empties and all genres have numbers, move slot to history after a short pause (1.5s so the audience sees the final numbers).

### Channel safety — `/topic/audition/` subscribers

The message format broadcast on `/topic/audition/` does **not change**. Only `AuditionNumber.vue`'s internal processing logic changes. The other three subscribers are unaffected:

| File | What it does with `/topic/audition/` | Impact |
|------|--------------------------------------|--------|
| `EventDetails.vue` | Patches `checkinList[participant].genre.auditionNumber` | None |
| `AuditionList.vue` | Adds/updates participant rows | None |
| `AuditionAdjust.vue` | Patches `checkinList` via `applyAuditionMsg` | None |

### Cancel signal — backend

Extend the existing `POST /api/v1/event/{eventName}/checkin-preview` endpoint — add an optional `cancelled` boolean to the request body:

```json
{ "participantId": 123, "cancelled": true }
```

Backend checks `cancelled` and broadcasts the same payload on `/topic/checkin-preview/`. No new route needed.

`AuditionNumber.vue` checks `msg.cancelled` first in `onPreview` before any other logic — if true, remove the matching slot and return.

**EventDetails.vue:** call `sendCheckinPreview(eventName, { participantId, cancelled: true })` when the confirm dialog closes while still in `confirm` phase (not `generating`, not `done`). No call needed if the dialog closes after confirming — the slot moves to history naturally once numbers arrive.

### Backend — duplicate check-in guard

In the service method called by `GET /api/v1/event/register-participant/{participantId}/{eventId}`:

- Before assigning numbers, check if all `EventGenreParticipant` rows for this participant+event already have `auditionNumber != null`.
- If yes → return `409 Conflict` with body `"already_checked_in"`.
- Frontend `checkIn()` catches 409 → sets `checkinConfirm.phase = 'error'` → shows "Already checked in at another desk." with a close button.

### EventDetails.vue — double-confirm guard

```js
// confirmCheckIn()
const confirmCheckIn = async () => {
  if (confirming.value) return   // guard
  confirming.value = true
  try {
    // ... existing logic
  } finally {
    confirming.value = false
  }
}
```

Confirm button: `:disabled="checkinConfirm.phase !== 'confirm' || confirming"`.

### AuditionList.vue — Reset Scores gate

Replace the current single-confirm modal with a two-step flow:

1. Operator clicks Reset → warning modal appears explaining consequences.
2. Modal shows an input: "Enter event access code to confirm".
3. Frontend calls existing `GET /api/v1/event/{eventName}` (or a dedicated endpoint) to validate the access code.
4. Only on match → execute `resetJudgeScores` + `resetJudgeFeedback`.

The access code is already stored on the `Event` entity and visible to organisers on EventDetails. No new data model needed.

---

## Grid layout (AuditionNumber.vue template)

```
1 slot  → single full-width card
2 slots → 2-column grid
3 slots → 3-column grid
4 slots → 3-column grid (row 1: 3, row 2: 1)
5 slots → 3-column grid (row 1: 3, row 2: 2)
```

CSS: `grid-template-columns: repeat(min(activeSlots.length, 3), 1fr)` — Vue computed from slot count.

Each slot card shows: operator tag · status dot (pending amber / rolling amber-pulse / done white) · participant name · member names · per-genre rows with number area.

Slot auto-moves to history after animation completes + 1.5s display pause.

---

## What does NOT change

- `/topic/audition/` message format — untouched
- `/topic/checkin-preview/` message format for normal previews — untouched (cancel adds a new optional field)
- `history` display in AuditionNumber — untouched
- `onRepeatAudition` (`/topic/error/`) — untouched
- All other EventDetails functionality (walk-in, genre adjust, payment verify, etc.)
- AuditionList scoring, feedback, and emcee view
