# Genre & Division UX Redesign

## Goal
Redesign the genre/division setup flow so it matches the organiser's mental model — Genre → Level/Sub-style → Format — and surfaces sheet matching feedback in real time so import failures are caught during setup, not at import time.

## Architecture
Event setup splits into two distinct phases: a lightweight init (pick genres, done) and a persistent division configuration section in EventDetails that handles names, formats, aliases, and live sheet matching. No new DB columns are needed — all fields exist from the V23 migration.

## Tech Stack
Vue 3 frontend · Spring Boot backend · PostgreSQL · Google Sheets API (existing)

---

## Mental Model

Three orthogonal concepts make up a division:
- **Genre** — dance style category (Breaking, Popping, Waacking). Admin-managed global list.
- **Label/Level** — free-text subdivision (Junior, Open, Popping & Boogaloo, Handstyles and Tut). Defined per event.
- **Format** — competition format (1v1, 2v2, 3v3, 7 to smoke, solo).

A division is the combination: `"Junior Breaking 2v2"` = Breaking genre + Junior label + 2v2 format.

---

## What Changes

| Area | Before | After |
|---|---|---|
| Admin genres | Name + aliases (aliases used for import) | Name only — purely a category label |
| Event init | Genre picker + division config + initialise (one step) | Genre picker → initialise only (divisions configured after) |
| Division config | Format-only inline edit in Genre Configuration accordion | Full edit: name, format, aliases. Live sheet match counts. Add/remove divisions at any time. |
| Walk-in | Free-text genre field | Genre dropdown → division picker (filtered by genre) |

---

## Section 1: Admin Genres (AdminPage.vue)

**Change:** Remove the aliases input/display from the genre panel. Genres become purely a label list.

**Label change:** Panel description changes from "Genres — used for matching" to "Genres — used to group divisions when setting up events."

**Backend:** No change. `Genre.sheetAliases` column stays in the DB but is no longer exposed in the UI. `buildGenreCounts` in `GoogleSheetService` (which used genre aliases for count estimation) is no longer called now that live division matching replaces it.

---

## Section 2: Event Initialisation (EventDetails.vue — "Event Setup Required" panel)

**Before:** Genre checkboxes → division config → payment toggle → initialise.

**After:** Genre checkboxes → payment toggle → initialise. Division config moves out.

**Behaviour on init:**
- Each checked genre creates one `EventGenre` row with `name` set to the genre's name and `format` null.
- The organiser configures division names, formats, and aliases after init in the Divisions section.
- Clicking "+ Custom" creates an `EventGenre` with a blank name (organiser fills it in after init).

**UI:**
```
[ Event Setup Required ]

GENRES / CATEGORIES
□ Breaking  □ Popping  ☑ Waacking  □ House  □ Open Styles  + Custom

□ Payment Required

[ Initialise Event ]
```

---

## Section 3: Divisions Section (EventDetails.vue — post-init)

New persistent section that replaces the current "Genre Configuration" accordion. Always visible after init.

### Layout

Single-column. Divisions grouped by genre with a section rule per genre. Each division row is two lines:
- **Line 1:** Name input · Format dropdown · Match count badge · Remove button
- **Line 2 (collapsed by default, always visible if aliases exist):** Aliases row

### Division row states

| State | Visual |
|---|---|
| Has sheet matches | Green left border + green glowing dot + count |
| Zero matches | Neutral border + grey dot + "0" (not a warning — no sheet entries yet is valid) |
| No sheet linked | No match indicator shown |

### Unmatched sheet values strip

Appears below all divisions when at least one sheet value has no matching division. Amber border + glow dot. Shows each unique unmatched string once with entry count and a quick action:
- **"Add alias"** — opens the alias input on the nearest division inline
- **"Create division"** — adds a new division row pre-filled with the unmatched string

### Sheet matching mechanics (unchanged)

- Match strings = `EventGenre.name` + each entry in `EventGenre.sheetAliases` (comma-separated)
- Match is **case-insensitive substring**: cell contains match string → matched
- Cell values are split on comma first → one cell can match multiple divisions (participant enrolled in each)
- Format is extracted separately via `\d+v\d+` regex; "7 to smoke" and "solo" are not auto-detected from cell values (they are set on the division, not parsed from the sheet)

### Live match counts

When a Google Sheet is linked to the event:
1. On load of the Divisions section, fetch all unique category column values via a new API endpoint: `GET /api/v1/event/{eventName}/sheet-categories`
2. Match each unique value against all division names + aliases client-side
3. Display count per division; collect unmatched values for the strip
4. Refresh button to re-fetch (form responses may grow over time)

**New backend endpoint:** `GET /api/v1/event/{eventName}/sheet-categories`
- Reads the linked sheet's category column(s) via existing `GoogleSheetService`
- Returns `{ values: ["Junior Breaking 2v2", "Popping & Boogaloo", ...] }` — unique strings only
- Returns empty list if no sheet linked

### Alias management

Each division has an aliases field (maps to `EventGenre.sheetAliases`). Aliases are shown as monospace chips. Clicking "+ add" shows an inline text input; pressing Enter saves and appends to the comma-separated string. Clicking a chip removes it.

### Adding divisions

- **"+ Add division"** button under each genre group — creates a new row pre-filled with the genre name, blank format
- **"+ Add genre"** at the bottom — opens the genre picker modal to add another genre group and its first division

### Format options
`1v1 · 2v2 · 3v3 · 4v4 · 5v5 · 7 to smoke · Solo · (blank — no format)`

---

## Section 4: Walk-in Form

**Change:** Replace the free-text genre input with a two-step picker.

**Step 1 — Genre:** Dropdown of genres configured for this event (from `EventGenre` grouped by `genreId` or alphabetically if custom).

**Step 2 — Division:** Filtered list of division names under the selected genre. Shown as radio buttons or a dropdown.

**Format:** Auto-fills from the selected division's `format` field. If blank, a format dropdown appears.

Everything else (participant name, team name, member names, judge name) is unchanged.

**Backend:** Walk-in endpoint already receives `genre` and `eventName`. Update to receive `divisionName` (the EventGenre name) instead of `genre`. The lookup `eventGenreRepo.findByEventAndName` already works for this.

---

## Section 5: Division Editing API

New endpoints needed:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/event/{eventName}/divisions` | Add a division (name, format, genreId) |
| `PATCH` | `/api/v1/event/{eventName}/divisions/{id}/name` | Rename a division |
| `PATCH` | `/api/v1/event/{eventName}/divisions/{id}/aliases` | Update aliases |
| `DELETE` | `/api/v1/event/{eventName}/divisions/{id}` | Remove a division |
| `GET` | `/api/v1/event/{eventName}/sheet-categories` | Fetch unique category values from linked sheet |

`updateEventGenreFormat` already exists for format edits.

---

## What Does Not Change

- Sheet import flow and `RegistrationService` logic — unchanged
- Comma-separated multi-division cells — already handled by `GoogleSheetParser.parseGenreFormats`
- Scoring, criteria, judging, battle, results — all unchanged
- `EventGenre` DB schema — no new migrations needed
