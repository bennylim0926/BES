# Granular Results Release — Design Spec

**Date:** 2026-06-17
**Issue:** #156
**Branch:** `feat/granular-results-release`

## Summary

Replace the single `results_released` boolean on `Event` with a 4-mode enum (`NONE` | `SCORE_ONLY` | `FEEDBACK_ONLY` | `BOTH`) giving organisers fine-grained control over what appears on the public results portal, the QR results page, and the post-check-in QR dialog.

## Motivation

Currently `releaseResults` is a single boolean — flipping it on releases **both** score and feedback together. Organisers want per-channel control:
- Heat 1: release **score only** so participants see if they qualified, but feedback is still being finalised
- Heat 2: release **feedback only** — used after a private audition where rankings are not shared
- Final: release **both**

The post-check-in QR in `EventDetails.vue` is currently coupled to `feedbackEnabled`, not to the release concept at all (#150). This spec decouples them: the QR appears whenever there is *anything* to show (mode ≠ NONE).

## Design Decision

**Option A — Single VARCHAR enum** chosen over two booleans:
- 4 states are mutually exclusive by construction
- "Anything to show?" check is `mode != 'NONE'` (single comparison)
- Maps cleanly to a 4-chip UI selector
- Java string constants + JS string matching is well-worn in this codebase (`judgingMode`, `animTheme` follow the same pattern)

## Behaviour Matrix

| Mode | Public `/results` | `/results-qr` | Post-check-in QR |
|------|------------------|---------------|------------------|
| `NONE` | "Results not yet released" (404) | Same | Hidden |
| `SCORE_ONLY` | Show ranking + score breakdown; hide feedback section entirely | Same | Shown |
| `FEEDBACK_ONLY` | Hide ranking/total score; show feedback tags + judge notes only | Same | Shown |
| `BOTH` | Full view (current behaviour) | Same | Shown |

## Scope

### 1. Database Migration — `V45__add_results_release_mode.sql`

```sql
ALTER TABLE event ADD COLUMN results_release_mode VARCHAR(20) DEFAULT 'NONE';
UPDATE event SET results_release_mode = 'BOTH' WHERE results_released = true;
UPDATE event SET results_release_mode = 'NONE' WHERE results_released = false;
ALTER TABLE event DROP COLUMN results_released;
```

### 2. Entity — `Event.java`

- Remove `resultsReleased` boolean field
- Add `resultsReleaseMode` String field (column `results_release_mode`, length 20, default `"NONE"`)
- Add `ReleaseMode` inner class with constants: `NONE`, `SCORE_ONLY`, `FEEDBACK_ONLY`, `BOTH`

### 3. DTOs

- **`GetEventDto`** — add `resultsReleaseMode` String field
- **`GetResultsDto`** — add `resultsReleaseMode` String field (tells frontend which blocks to render)
- **`UpdateResultsReleaseModeDto`** — `eventName` + `mode` (mirrors `UpdateFeedbackDto` pattern)

### 4. Backend Services

**`EventService`:**
- Replace `releaseResults(eventName, released)` → `setResultsReleaseMode(eventName, mode)`
- Replace `isResultsReleased(eventName)` → `getResultsReleaseMode(eventName)` returning String
- Mode change broadcasts via WebSocket to `/topic/release-mode/` (payload: `{eventName, mode}`)
- Default mode for new events: `"NONE"` (safest)

**`ResultsService.getResultsByRefCode(refCode)`:**
- If mode is `NONE`: return `null` (same 404 behaviour as today)
- Otherwise: build full DTO with `resultsReleaseMode` set; frontend conditionally renders blocks
- Scores array is always populated (service doesn't filter); frontend hides based on mode
- Feedback array is always populated; frontend hides based on mode

### 5. Controllers

**`EventController`:**
- Add `POST /{eventName}/results-release-mode` — body `{mode}`, auth: ADMIN/ORGANISER
- Add `GET /{eventName}/results-release-mode` — returns `{eventName, mode}`
- Remove old `POST /{eventName}/release-results` endpoint (hard cut — no external consumers)
- Remove old `GET /{eventName}/results-status` endpoint (replaced by the new GET)

**`ResultsController`:**
- `GET /results?ref=...` — DTO now includes `resultsReleaseMode`; error message unchanged for NONE mode

### 6. WebSocket

- Destination: `/topic/release-mode/`
- Payload: `{eventName: String, mode: String}`
- Pattern mirrors `feedbackEnabled` broadcast at `EventService.java:120-121`
- Subscribers: `Score.vue` (updates the read-only badge in Broadcast mode), `EventDetails.vue` (QR visibility in check-in dialog)

### 7. Frontend — `Score.vue`

- Replace single HIDDEN/RELEASED toggle pill with 4 `para-chip-sm` parallelogram chips:
  - `NOT RELEASED` · `SCORE ONLY` · `FEEDBACK ONLY` · `SCORE + FEEDBACK`
- Active chip: `border-accent text-accent`; inactive: `text-content-muted hover:text-content-primary`
- Only shown in Control mode (admin/organiser)
- Broadcast mode: shows current mode as a read-only badge
- Replace `toggleRelease()` with `setMode(mode)` calling `setResultsReleaseMode()`
- Subscribe to `/topic/release-mode/{eventName}` for live updates

### 8. Frontend — `Results.vue`

- Read `results.resultsReleaseMode` from API response
- `SCORE_ONLY`: render scores section, hide feedback section entirely (including "No feedback" placeholder)
- `FEEDBACK_ONLY`: hide scores section, total score, ranking; show only feedback blocks
- `BOTH`: full view (current behaviour)
- In FEEDBACK_ONLY, suppress rank number and total score in the category header

### 9. Frontend — `ResultsQR.vue`

- No structural changes needed — it just renders the QR image. The backend results page handles conditional rendering.

### 10. Frontend — `EventDetails.vue` (Check-in Confirm)

Current (line ~3083):
```html
v-if="checkinConfirm.phase === 'done' && feedbackEnabled && checkinConfirm.qrImageUrl"
```

Replace with:
```html
v-if="checkinConfirm.phase === 'done' && resultsReleaseMode !== 'NONE' && checkinConfirm.qrImageUrl"
```

Same decoupling for the ref-code fallback (line ~3095).

### 11. Frontend — `api.js`

- Add `setResultsReleaseMode(eventName, mode)` — POST to `/results-release-mode`
- Add `getResultsReleaseMode(eventName)` — GET from `/results-release-mode`
- Remove old `releaseResults(eventName, released)` function
- Update `Score.vue` import accordingly

### 12. Integration Tests

- Update `EventServiceTest` — test all 4 modes, verify backfill
- Update `ResultsController` tests — cover SCORE_ONLY, FEEDBACK_ONLY, BOTH responses
- Verify NONE returns 404 with appropriate error message

## Migration / Backwards Compatibility

- `results_released = true` → `BOTH`
- `results_released = false` → `NONE`
- Old `/release-results` endpoint: hard cut (no external consumers)
- Old `/results-status` endpoint: hard cut (replaced by new GET)

## UX Details

- Default mode for new events: `NONE`
- Chip labels on Score.vue: `NOT RELEASED` · `SCORE ONLY` · `FEEDBACK ONLY` · `SCORE + FEEDBACK`
- Light-mode public results: FEEDBACK_ONLY suppresses rank numbers entirely (don't show `#—`)
- Tie-breaker resolver on Score.vue Control mode: independent of release mode, always visible to organisers

## Key Distinctions

- **`feedbackEnabled`** controls whether judges **can submit** feedback at all (input gate)
- **`resultsReleaseMode`** controls what is **shown publicly** (output gate)
- These are independent concepts. An event can have `feedbackEnabled=true` but `resultsReleaseMode=SCORE_ONLY` (feedback was collected but not released yet).

## References

- #150 — Helper QR fix (prompted the decoupling of QR visibility from `feedbackEnabled`)
- #131 — `feedbackEnabled` per-event toggle (established the pattern for per-event boolean/state toggles via DTO + WS broadcast)
- #156 — This issue
