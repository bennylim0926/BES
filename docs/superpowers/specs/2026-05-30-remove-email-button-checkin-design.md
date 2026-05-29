# Design: Remove Email Dependency & Button-Based Check-in

**Date:** 2026-05-30
**Branch:** chore/agent-dispatch-setup (to be implemented on a feature branch)

## Problem

The current registration flow depends on email in two ways:
1. As the unique identifier for the `Participant` entity across events
2. As the delivery mechanism for the QR check-in code

This creates operational overhead: tracking how many emails were sent, how many are pending, and requiring participants to present a QR from their email on event day. The goal is to eliminate both dependencies.

## Decision

Remove email from the `Participant` entity entirely. Replace QR-based check-in with an organizer-driven button on the `AuditionNumber` screen. Payment tracking (`paymentVerified` + `screenshotUrl`) is retained as a standalone step with no email consequence.

---

## Section 1: Data Layer

**Flyway migration:** `V17__remove_email_from_participant.sql`

Changes:
- Drop `participant_email` column (and its unique index) from the `participant` table
- Drop `email_sent` column from the `event_participant` table

**Retained fields on `event_participant`:**
- `payment_verified` — organizer manually marks payment; still used for tracking
- `screenshot_url` — payment proof link; still displayed in UpdateEventDetails
- `reference_code` — used for the public results portal; unaffected

**Models:**
- `Participant.java`: remove `participantEmail` field and its `@Column` annotation
- `EventParticipant.java`: remove `emailSent` field

---

## Section 2: Backend

### Import (`GoogleSheetService`)
- Remove the filter `email != null && !email.isBlank()` from `getAllImportableParticipants()`
- Only participant name is required for a row to be importable
- Email column in the spreadsheet is silently ignored if present

### Participant creation (`ParticipantService.addParticipantService()`)
- Remove `ParticipantRepo.findByParticipantEmail()` lookup
- Remove `ParticipantRepo.findByParticipantEmail()` from `ParticipantRepo`
- Every import row always creates a fresh `Participant` record — no cross-event deduplication (not needed since one spreadsheet row = one unique person)

### Payment verification (`RegistrationService`)
- Strip `verifyAndEmail()` down to only `eventParticipant.setPaymentVerified(true)` + save
- Remove all email sending, QR generation, and `emailSent` flag setting
- Rename method to `verifyPayment()` to reflect actual behaviour

### DTOs
- Remove `participantEmail` from `AddParticipantDto` and any response DTOs that surface it
- No new DTOs required

### Unchanged endpoints
- `GET /api/v1/event/register-participant/{participantId}/{eventId}` — unchanged; the new check-in button calls this directly
- `ResultsController` QR (`/api/v1/results/qr`) — unchanged; uses `referenceCode`, not email

### New endpoint
- `GET /api/v1/event/{eventName}/checkin-list` — returns all `EventParticipant` records for the event, each with: `participantId`, display label (priority: `stageName` → `displayName` → `participantName`), list of genres with their audition numbers (`null` = not yet checked in)
- Used by the new frontend check-in list

---

## Section 3: Frontend

### `UpdateEventDetails.vue`
- Remove "Send Email" / "Verify & Email" button
- Replace with a standalone "Mark Payment Verified" button that calls `verifyPayment()`
- Remove `emailSent` column from the participant table display

### `AuditionNumber.vue` — new split-pane layout

**Left pane — participant check-in list:**
- Fetches from the new `checkin-list` endpoint on mount
- Subscribes to the existing WebSocket topic (`/topic/audition/`) — when an audition number is broadcast, the matching participant row is marked checked-in reactively (no re-fetch needed)
- Each row shows:
  - Participant label (`stageName` → `displayName` → `participantName`)
  - Genre badges (e.g. "Popping · Hip Hop")
  - Status: pending → active "Check In" button; checked in → grey checkmark
- "Check In" button calls `GET /api/v1/event/register-participant/{participantId}/{eventId}`
- Already-checked-in participants are visually de-emphasised but remain visible for reference

**Right pane — existing animated reveal:**
- Unchanged; WebSocket-driven audition number animation remains as-is

**Responsive layout:**
- Landscape / desktop: left pane (participant list) + right pane (reveal) side by side
- Portrait / mobile: list stacked above reveal area

### `utils/api.js`
- Remove: email/QR-email send API calls
- Add: `getCheckinList(eventName)` → `GET /api/v1/event/{eventName}/checkin-list`

---

## What Is Not Changing

| Item | Reason |
|------|--------|
| `referenceCode` on `EventParticipant` | Used for results portal; unrelated to email |
| `paymentVerified` + `screenshotUrl` | Retained for payment tracking |
| Audition number assignment logic | Unchanged; button reuses existing endpoint |
| WebSocket audition reveal animation | Unchanged |
| Results QR (`ResultsController`) | Uses reference code, not email |
| Walk-in registration | Already worked without email; unaffected |

---

## Migration Order (bottom-up)

1. Flyway migration (`V17`)
2. `Participant.java` + `EventParticipant.java` models
3. `ParticipantRepo` — remove email query method
4. `ParticipantService` — remove email lookup
5. `RegistrationService` — strip to `verifyPayment()`
6. `GoogleSheetService` — remove email filter
7. DTOs — remove email fields
8. `EventController` — add `checkin-list` endpoint
9. `UpdateEventDetails.vue` — remove email button
10. `AuditionNumber.vue` — add check-in list pane
11. `utils/api.js` — remove email calls, add `getCheckinList`
