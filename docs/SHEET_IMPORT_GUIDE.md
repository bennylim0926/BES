# Google Sheets Import — Column Reference

## Universal Rules

| Column | Header (exact, case-insensitive) | Always required? |
|--------|----------------------------------|-----------------|
| Name | `name` | **Yes** — participant's real name. If absent, falls back to `team name`. Row skipped if neither exists. |
| Categories | `categories` | **Yes** — which genre(s) this row enters. Must match genre names on the event. |
| Stage Name | `stage name` | No — defaults to `name` if blank |
| Team Name | `team name` | No — name of the crew/team |
| Member 2…N | any label (e.g. `member 2`) | No — additional member names beyond the representative |
| Entry Type | `entry type` | **Conditionally required** — see rules below |
| Payment Status | `payment status` | No — defaults to **verified** if column is absent |
| Screenshot | `screenshot` | No |

---

## Entry Type Rules

Include the `entry type` column **whenever the event has at least one team-format genre (2v2 or higher)**.

| Value | Meaning |
|-------|---------|
| `team` | Enters as a full team. Team name + member names apply. |
| `solo` | Enters individually (pickup crew). Team name + members ignored. |

- If a participant's categories are **all 1v1**, entry type is not required for that row even if the column exists.
- If a participant's categories include **any team-format genre**, entry type must be filled or the row is skipped with an error.

---

## Member Columns

Add one member column per extra slot the **largest team format** requires:

| Format | Extra member columns needed |
|--------|----------------------------|
| 2v2 | 1 (`member 2`) |
| 3v3 | 2 (`member 2`, `member 3`) |
| 4v4 | 3 |
| 5v5 | 4 |

These are only read when `entry type` = `team`. Solo rows ignore them.

---

## Per-Event Column Sets

### Event type A — All 1v1
*Example: 2 genres both 1v1*

| Required | Recommended |
|----------|-------------|
| `name` | `stage name` |
| `categories` | `payment status` |

No `entry type`, no team columns.

---

### Event type B — All team-format, solo NOT allowed
*Example: 5 genres all 2v2, solo not permitted*

| Required | Recommended |
|----------|-------------|
| `name` | `stage name` |
| `categories` | `team name` |
| `entry type` (always `team`) | `member 2` |

Tip: you can pre-fill the entire `entry type` column with `team` since solo is not an option.

---

### Event type C — All team-format, solo allowed
*Example: 2 genres both 2v2, solo option available*

| Required | Recommended |
|----------|-------------|
| `name` | `stage name` |
| `categories` | `team name` |
| `entry type` (`team` or `solo`) | `member 2` |

---

### Event type D — Mixed 1v1 + team-format, solo allowed
*Example: one 1v1 genre + one 2v2 genre*

| Required | Recommended |
|----------|-------------|
| `name` | `stage name` |
| `categories` | `team name` |
| `entry type`* | `member 2` |

*Required for rows whose categories include the 2v2 genre. Rows with only the 1v1 genre can leave it blank.

---

### Event type E — Team-format + 7-to-Smoke
*Example: one 2v2 genre (solo allowed) + one 7-to-smoke*

7-to-smoke is registered as a 1v1 (individual) entry, so:

| Required | Recommended |
|----------|-------------|
| `name` | `stage name` |
| `categories` | `team name` |
| `entry type`* | `member 2` |

*Same rule as type D — only required for rows that include the 2v2 genre in their categories.

---

## Your Specific Events

| Event | Type | Extra columns needed |
|-------|------|---------------------|
| Event 1 — 2 genres, both 1v1 | A | None |
| Event 2 — 5 genres, all 2v2, solo not allowed | B | `entry type` (always `team`), `team name`, `member 2` |
| Event 3 — 2 genres, all 2v2, solo allowed | C | `entry type`, `team name`, `member 2` |
| Event 4 — 1v1 + 2v2, solo allowed | D | `entry type`*, `team name`, `member 2` |
| Event 5 — 1 genre, 2v2 | C | `entry type`, `team name`, `member 2` |
| Event 6 — 2v2 (solo allowed) + 7-to-smoke | E | `entry type`*, `team name`, `member 2` |

\* Only required for rows whose categories include the team-format genre.

---

## Common Errors

| Error in import report | Cause | Fix |
|------------------------|-------|-----|
| `ENTRY_TYPE missing for team-format genre` | Row has a 2v2+ genre but `entry type` column is blank | Fill in `team` or `solo` |
| Row skipped silently | `name` column missing or row is empty | Ensure header row uses exact column name `name` |
| Genre not saved | Genre name in `categories` doesn't match the event's genre name | Check spelling matches exactly |
| Duplicate participant | Same name already imported | System skips re-import of existing participants |
