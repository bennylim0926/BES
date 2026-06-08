# Participant Management Rework

**Date:** 2026-06-08
**Branch:** `feat/participant-management`

## Overview

Rework `UpdateEventDetails.vue` into a full participant management page. Each participant appears as one expandable row (genres as sub-rows). Supports filtering by genre/name, editing names and team details, removing from individual genres, and deleting entirely from the event.

---

## 1. Page Layout

### Filter Bar

- **Search input** — filters by participant name / team name (client-side, case-insensitive)
- **Genre chips** — one chip per genre in the event, each showing participant count badge. "All" chip always first and selected by default. Clicking a chip filters the table to that genre. Active chip visually highlighted. Chips wrap on mobile.
- Search and genre filter combine with AND: "show participants whose name contains X AND who are in genre Y"

### Participant Count

Below filter bar: "Showing N of M participants"

### Table

One row per participant (grouped by `participantId`). Columns:

| Column | Content |
|--------|---------|
| Expand toggle | ▸ / ▾ arrow; click to show/hide genre sub-rows |
| Name | `participantName` for solo; `teamName` for team entries |
| Format | Badge: `Solo`, `1v1`, `2v2`, `3v3`, `7 to Smoke` etc — derived from the participant's genre format |
| Genres | Summary chips of all genres the participant is registered in |
| Actions | `Edit` button + `Delete` button |

**Expanded sub-rows** (one per genre):
- Genre chip
- Judge: current judge name or "—"
- `Change Judge` button — opens judge select (existing dropdown pattern)
- `Remove Genre` button — triggers confirm prompt

---

## 2. Edit Modal

- Max-width: 480px, centered, responsive (full-width on mobile)
- Triggered by clicking `Edit` on a participant row
- Form adapts to participant type:

### Solo / 1v1 / 7-to-Smoke

Single field: **Name** (required)

### Team (NvN format)

- **Team Name** field (required)
- **N member fields** — fixed count, no add/remove buttons. Count parsed from format string: `2v2 → 2`, `3v3 → 3`. All slots required.
- Member names are informational — no uniqueness check on members.

### Format detection

If a participant is registered in multiple genres of mixed format (e.g., one solo genre + one 2v2 genre), team format takes precedence for the edit form. Slot count comes from the team format.

### Validation (client-side before save)

- Name / team name: non-empty, no duplicate in same event (excluding self)
- All member slots: non-empty (count fixed by format)
- On duplicate: show inline error, do not submit

---

## 3. Confirm Prompts

All destructive actions require a confirm modal before executing.

**Delete Participant:**
> "Remove [Name] from [Event]? This will remove them from all genres ([Genre 1, Genre 2]) and release all audition numbers. This cannot be undone."

**Remove Genre:**
> "Remove [Name] from [Genre]? Their audition number will be released."

**Edit save**: no confirm prompt — inline validation only.

---

## 4. Backend

### New: DELETE participant from event

```
DELETE /api/v1/event/participant/{participantId}/{eventId}
```

Roles: `Admin`, `Organiser`

**Cascade order (must execute in this order):**
1. Delete `Score` rows where `participantId` and `eventId` match
2. Delete `AuditionFeedback` rows for this participant in this event
3. Delete `EventGenreParticipant` rows (releases audition numbers)
4. Delete `EventParticipantTeamMember` rows (via `EventParticipant` FK)
5. Delete `EventParticipant` row
6. Delete `Participant` row **only if** no other `EventParticipant` references this `participantId`

Returns: `200 OK` on success, `404` if not found.

### New: PUT update participant

```
PUT /api/v1/event/participant/{participantId}/{eventId}
```

Roles: `Admin`, `Organiser`

Request body:
```json
{
  "name": "string",         // participant name (solo) or team name (team)
  "memberNames": ["string"] // omit or empty for solo; required for team
}
```

**Backend validation:**
- `name` non-empty
- No other `Participant` or `EventParticipant.teamName` with the same name in the same event (excluding self)
- `memberNames` count must match format slot count for at least one of their genres

**Updates:**
- Solo: `Participant.participantName`
- Team: `EventParticipant.teamName` + replace all `EventParticipantTeamMember` rows (delete old, insert new)

Returns: `200 OK` on success, `409` on duplicate name, `422` on validation failure.

### Existing: Remove genre (already exists)

```
DELETE /api/v1/event/participant-genre/{participantId}/{eventId}/{eventGenreId}
```

No changes needed.

### Existing: Judge assignment (already exists)

```
POST /api/v1/event/participants-judge/
```

No changes needed — still used per genre sub-row.

---

## 5. Frontend

### Files changed

| File | Change |
|------|--------|
| `BES-frontend/src/views/UpdateEventDetails.vue` | Full rewrite |
| `BES-frontend/src/components/ConfirmModal.vue` | New reusable confirm dialog |
| `BES-frontend/src/utils/api.js` | Add `deleteParticipantFromEvent`, `updateParticipant` |

### Data grouping

The existing `GET /api/v1/event/participants/{eventName}` returns one row per participant-genre (`GetEventGenreParticipantDto`). Group by `participantId` on the frontend to build the table structure:

```js
// grouped shape
{
  participantId,
  name,        // participantName or teamName
  format,      // from first genre's format field
  isTeam,      // true if any genre format is NvN
  memberNames, // from first genre row (shared across genres)
  genres: [
    { genreName, genreId, eventGenreId, judgeName, auditionNumber }
  ]
}
```

### ConfirmModal.vue

Reusable component:
- Props: `show`, `title`, `message`, `confirmLabel` (default "Confirm"), `variant` (default "danger")
- Emits: `confirm`, `cancel`
- Used for: delete participant, remove genre

### `api.js` additions

```js
deleteParticipantFromEvent(participantId, eventId)
  → DELETE /api/v1/event/participant/{participantId}/{eventId}

updateParticipant(participantId, eventId, { name, memberNames })
  → PUT /api/v1/event/participant/{participantId}/{eventId}
```

---

## 6. Out of Scope

- Payment tracking (existing, untouched)
- Participant verification / email (existing, untouched)
- Add participant / walk-in (`CreateParticipantForm.vue` reused as-is)
- Audition number assignment UI (untouched)
