# Registration Rules Design

## Goal

Harden participant registration — both walk-in and Google Sheets import — so that entry type (solo vs team) is always explicit, required fields are always validated, and no silent data corruption can occur.

## Context

### Genre formats

Every `EventGenre` has a `format` field: `1v1`, `2v2`, `3v3`, or `4v4`.

**Key constraints established during design:**
- An event will not have two different team sizes simultaneously (no 2v2 + 3v3 in the same event). Team-format genres within one event all share the same crew size.
- Solo participants commonly join multiple genres.
- Teams almost never join multiple genres, but a single person can be on Team A for one genre and Team B for another (different partners per genre). The walk-in form must support this; the sheet import does not (one team config per row).

### Entry types

| Entry type | When used | EGP.format stored |
|---|---|---|
| Solo 1v1 | 1v1 genre | `"1v1"` |
| Team | Team-format genre, participant registers with full crew | genre format (e.g. `"2v2"`) |
| Solo pickup crew | Team-format genre, participant auditions alone and may be grouped into a crew later | `null` |

---

## Walk-in Form Rules

### Fields

| Field | Scope | Required |
|---|---|---|
| Stage name | Global (all genres) | Yes |
| Judge | Global | No |
| Genre selection | Checkboxes | At least one |
| Entry mode (Team \| Solo) | Per team-format genre | — |
| Team name | Shared, shown if any team-format genre = Team | Yes if any team entry |
| Member fields (N−1) | Shared, count = format size − 1 | Yes, all fields, if any team entry |

### Behaviour

1. **1v1 genres** never show the entry mode toggle — they are always solo. No team fields.

2. **Team-format genres** each show their own **Team | Solo** toggle. Default is `Team`.

3. If a team-format genre is set to `Team`, a team block appears **for that genre specifically**:
   - Team name field (required)
   - Exactly N−1 member name fields where N is the format size (e.g. 2v2 → 1 field, 3v3 → 2 fields)
   - All member fields are required — no silent drops
   - Each genre's team block is independent — a person can be in Team A for Popping 2v2 and Team B for HipHop 2v2 in a single submission

4. If a team-format genre is set to `Solo`, no team fields are shown for that genre.

5. Since all team-format genres in one event share the same format size, all per-genre team blocks have the same number of member fields — only the team name and member names differ per genre.

### Validation (blocks submission)

- Stage name blank → error
- Any team-format genre set to `Team` but team name blank → error
- Any team-format genre set to `Team` but any member field blank → error
- Member count mismatch vs format (should not be possible via UI, but validated server-side as defence)

### What this fixes

- **Silent member-drop bug**: blank member fields no longer silently convert a "team" entry to a solo. The form blocks submission.
- **Implicit solo intent**: the per-genre toggle makes solo-in-team-genre an explicit choice, not an accidental side effect.
- **Mixed-format confusion**: each genre's entry mode is independent — solo in one 2v2 genre, team in another, is fully supported.

---

## Sheet Import Rules

### New column: `ENTRY_TYPE`

Add an `ENTRY_TYPE` column to the import sheet. Required for any row that includes a team-format genre.

| Value | Meaning |
|---|---|
| `team` (case-insensitive) | Full crew registration — `TEAM_NAME` and member columns required |
| `solo` (case-insensitive) | Solo pickup crew — team columns ignored |

For rows that only contain 1v1 genres, `ENTRY_TYPE` is optional and ignored.

**Sheet import limitation:** one team config per row applies to all team-format genres on that row. If a participant is on different teams for different genres, use the walk-in form instead, or submit them as two separate sheet rows (one per team entry).

### Column requirements by entry type

| Column | `ENTRY_TYPE=team` | `ENTRY_TYPE=solo` | 1v1 only |
|---|---|---|---|
| `NAME` | Required | Required | Required |
| `STAGE_NAME` | Optional (falls back to NAME) | Optional | Optional |
| `ENTRY_TYPE` | `team` | `solo` | Ignored |
| `TEAM_NAME` | Required | Ignored | Ignored |
| `MEMBER_2` … `MEMBER_N` | All required, count = format − 1 | Ignored | Ignored |

### Validation per row

Each row is validated independently. Invalid rows are skipped; valid rows are imported.

| Error condition | Behaviour |
|---|---|
| Team-format genre present but `ENTRY_TYPE` blank or missing | Row skipped, error logged |
| `ENTRY_TYPE=team` but `TEAM_NAME` blank | Row skipped, error logged |
| `ENTRY_TYPE=team` but member count doesn't match format | Row skipped, error logged |
| `ENTRY_TYPE=solo` | Imported as solo pickup crew (`EGP.format = null`), team columns ignored |
| 1v1 genres only | Always imported as solo (`EGP.format = "1v1"`), `ENTRY_TYPE` ignored |

### Import response

The import endpoint returns a structured result:

```json
{
  "imported": 42,
  "skipped": 3,
  "errors": [
    { "row": 5, "name": "John Doe", "reason": "ENTRY_TYPE missing for team-format genre" },
    { "row": 11, "name": "Jane Smith", "reason": "TEAM_NAME required for team entry" },
    { "row": 19, "name": "Bob Lee", "reason": "Member count mismatch: 2v2 requires 1 additional member, got 0" }
  ]
}
```

The frontend displays this result so the organiser can fix their sheet and re-import. Re-importing skips already-imported participants (existing behaviour).

### What this fixes

- **Implicit intent**: `ENTRY_TYPE` makes solo-in-team-genre an explicit declaration, not inferred from empty cells. A missing `TEAM_NAME` is now an error, not a silent solo conversion.
- **Missing data errors reported**: organisers see exactly which rows failed and why.

---

## Backend Fixes

### Fix 1 — `addGenreToExistingParticipant` never sets format

**File:** `EventGenreParticpantService.java`

When adding a genre to an existing participant (e.g. from UpdateEventDetails), the current code saves the `EventGenreParticipant` with `format = null` regardless of genre type. Fix:

- Load the `EventGenre.format` for the target genre
- Load the participant's existing `EventParticipant` (has `teamName` + `teamMembers`)
- Apply the same `isTeamEntry` logic used in walk-in and sheet import
- Set `EGP.format` correctly: team entry → genreFormat, solo in team genre → null, 1v1 → "1v1"

### Fix 2 — Shared member count validation

Extract a shared utility method used by both walk-in and sheet import:

```java
private void validateTeamEntry(String format, String teamName, List<String> memberNames) {
    int required = parseFormatSize(format) - 1; // "2v2" → 1, "3v3" → 2
    if (teamName == null || teamName.isBlank())
        throw new IllegalArgumentException("Team name is required for team entry");
    if (memberNames == null || memberNames.stream().filter(m -> m != null && !m.isBlank()).count() != required)
        throw new IllegalArgumentException("Expected " + required + " additional member(s) for " + format);
}
```

### Fix 3 — Walk-in endpoint rejects invalid team entries

`EventController.addWalkInToSystem` / `EventGenreParticpantService.addWalkInToEventGenreParticipant`:

- If genre is team-format and entry has teamName or members → call `validateTeamEntry` before saving
- Return HTTP 400 with the validation error message

### Fix 4 — Sheet import returns structured errors

`RegistrationService.addParticipantToEvent`:

- Change return type to carry import results (imported count, skipped rows with reasons)
- Per-row validation: catch validation errors, add to skipped list, continue to next row
- Controller returns the result object to the frontend

---

## Data Model

### Schema change required

Team info currently lives on `EventParticipant` (one record per person per event), which means a person can only have one team name and one set of members regardless of how many genres they compete in. To support per-genre team configuration, team info moves to the `EventGenreParticipant` level.

**Changes:**
- Add `team_name` column to `EventGenreParticipant` (nullable String)
- Create new table `EventGenreParticipantMember` (FK to `EventGenreParticipant`, `member_name`)
- `EventParticipant.teamName` and `EventParticipantTeamMember` are **deprecated** — kept in DB for backwards compatibility but no longer written by new registration paths. Read paths switch to EGP-level data.

**New Flyway migration:** `V19__add_egp_team_info.sql`

| Scenario | `EGP.format` | `EGP.teamName` | `EGP.members` |
|---|---|---|---|
| Solo 1v1 | `"1v1"` | null | empty |
| Team 2v2 | `"2v2"` | set | set (1 member) |
| Solo pickup crew in 2v2 | `null` | null | empty |

The `ENTRY_TYPE` column is a sheet-only concept — it drives validation and determines which DB fields are populated, but is not persisted.

---

## Files to Change

### Database
- `BES/src/main/resources/db/migration/V19__add_egp_team_info.sql` — add `team_name` to `event_genre_participant`; create `event_genre_participant_member` table

### Backend — models
- `BES/src/main/java/com/example/BES/models/EventGenreParticipant.java` — add `teamName` field + `@OneToMany` members list
- `BES/src/main/java/com/example/BES/models/EventGenreParticipantMember.java` — new entity (FK to EGP, memberName)

### Backend — services
- `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java` — fix `addGenreToExistingParticipant` (set format); fix `getAllEventGenreParticipantByEventService` (read member names from EGP instead of EP); add `validateTeamEntry`
- `BES/src/main/java/com/example/BES/services/RegistrationService.java` — write team info to EGP level; structured error response; call `validateTeamEntry`; handle `ENTRY_TYPE`

### Backend — DTOs / utils
- `BES/src/main/java/com/example/BES/utils/GoogleSheetParser.java` — parse `ENTRY_TYPE` column
- `BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java` — add `entryType` field
- `BES/src/main/java/com/example/BES/dtos/AddWalkInDto.java` — add per-genre team info (list of `{ genreName, teamName, memberNames }`)
- `BES/src/main/java/com/example/BES/controllers/EventController.java` — return import result DTO

### New files
- `BES/src/main/java/com/example/BES/dtos/ImportResultDto.java` — `{ imported, skipped: [{ row, name, reason }] }`
- `BES/src/main/java/com/example/BES/respositories/EventGenreParticipantMemberRepo.java`

### Frontend
- `BES-frontend/src/components/CreateParticipantForm.vue` — per-genre toggles, per-genre team name + member fields, required field validation
- `BES-frontend/src/utils/api.js` — update `addWalkinToSystem` to send per-genre team info
