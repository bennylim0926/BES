# Google Sheet Service — Stability Review

## What It Currently Does

The sheet import pipeline works as follows:
1. Reads the first row to detect column headers by keyword matching
2. Identifies "name" columns — if more than one, applies team-name resolution logic
3. Builds a `colIndexMap` (keyword → column index) for the critical fields
4. Fetches all data rows and maps each row via `RegistrationDtoMapper`
5. Genre columns are detected separately via `getCategoriesColumns` and resolved against the known genre list

---

## Findings

### 1. NullPointerException on Empty Sheet Data

**File:** `GoogleSheetService.java:223`, `GoogleSheetService.java:87`

`results.getValues()` and `valueRanges.get(0).getValues()` from the Google Sheets API return `null` (not an empty list) when the range contains no data. Neither `getsheetAllRows` nor `getParticipantsBreakDown` null-checks before calling `.stream()` or `.size()`.

- `getsheetAllRows`: if the sheet has only a header row and no participants, `getValues()` is null → NPE.
- `getParticipantsBreakDown`: if a category column is entirely empty, `getValues()` is null → NPE.

**What happens when an event folder has no sheet?**

The expected folder structure is: root folder → event folder → one sheet per event. If an event folder contains no sheet, the frontend sheet selector would receive an empty list from `GoogleDriveFileService` and show nothing to select. The user simply cannot proceed past the sheet selection step, so no backend import call would be made — this path is implicitly safe.

However, if a `fileId` is somehow passed to the backend without a valid sheet (e.g., a folder ID mistakenly passed as a file ID, or a sheet that was deleted after selection), `sheetClient.getRange()` would throw a Google API `IOException`. This is currently unhandled and would bubble up as a 500. A dedicated error message would be more helpful here.

---

### 2. Name Detection: Group Names Beyond "team name" Not Handled

**File:** `GoogleSheetService.java:184–185`

`removeExtraName` only recognises `"team name"` as the authoritative multi-person name column. If an organiser labels the column `"crew name"`, `"group name"`, or `"squad name"`, the logic falls into Case B (keep first "name" encountered) instead, which may return a member's individual name rather than the collective name.

**Recommended approach:**
1. If only one "name" column exists → use it as the individual display name.
2. If multiple "name" columns exist → scan for any column containing group-hint keywords: `"team"`, `"crew"`, `"group"`, `"squad"`. If found, use that as the display name.
3. If no group hint is found → fall back to the first "name" column (current Case B behaviour).

**On the email / team name concern:**

When a team registers, the display name will be the team/crew name, but the email will be whichever individual filled out the form. This is the correct behaviour — the email is the contact point for QR and communication, not the "owner" of the display name. There is no conflict here: `participantEmail` stays as the representative's email; `participantName` becomes the crew name. Both are stored independently on the `Participant` entity.

---

### 3. `colIndexMap` Silently Overwrites on Duplicate Keyword Matches

**File:** `GoogleSheetService.java:151–156`

The loop that builds `colIndexMap` does `put(keyword, i)` with no guard against overwriting. If two headers both contain the same keyword, the map keeps the **last** matching column.

In practice, duplicate columns for `email`, `payment status`, or `local/overseas` are uncommon. The name column is already protected by `removeExtraName`. A first-match-wins guard should still be added as a safety net for unexpected form layouts.

---

### 4. `getParticipantsBreakDown` Crashes When There Is Only One Category Column

**File:** `GoogleSheetService.java:87–100`

The method was originally built around a local + overseas category column split. Since local/overseas is no longer tracked, a sheet will typically have just **one** category column. However, the loop still tries to access `overseasGenre.get(i)` whenever a local row cell is empty, which throws `IndexOutOfBoundsException` on a blank signup.

The fix is to simplify this method: iterate over however many category columns exist and combine all non-empty values, without assuming two columns or a local/overseas split.

---

### 5. Genre Matching: Complex Labels Need Alias Support

**File:** `GoogleSheetParser.java:7–11`

`normalizeGenre` checks `event::contains` — i.e., the cell value must contain the genre label as a substring. This works for most genres but breaks for `SMOKE`, whose label is `"7 to smoke"`:

- `"Seven to Smoke"`, `"Smoke Battle"`, `"7toSmoke"` → no match → participant imported with no genre.

Any genre with a long or stylised label has this fragility. The solution is to support **aliases** per genre — a primary label plus a list of alternative strings that should all resolve to the same genre. For example, SMOKE could also match `"smoke"`, `"7 to smoke"`, `"seven to smoke"`.

`normalizeGenre` also has an invisible caller contract: callers must lowercase the input before calling since the method does not normalise internally. This should be moved inside the method.

---

### 6. `getsheetAllRows` Truncates Rows Wider Than the Header

**File:** `GoogleSheetService.java:220`

The data range is `A2:<lastHeaderCol>`, where the end column is derived from `headers.size()`. If any data row has more columns than the header row (e.g., Google Forms appending a timestamp or hidden column beyond the declared headers), those extra cells are silently excluded. This is harmless for current fields but worth knowing.

---

## Summary Table

| # | Issue | Severity | Breaks? | Action |
|---|-------|----------|---------|--------|
| 1 | NPE on empty data / null `getValues()` | High | Yes — hard crash | Fix |
| 1b | Invalid `fileId` returns generic 500 | Low | No crash, bad UX | Nice to have |
| 2 | `crew/group/squad name` not handled; Case B only | Medium | Silent wrong data | Fix |
| 3 | `colIndexMap` overwrites on duplicate keywords | Low | Unlikely in practice | Add guard |
| 4 | `getParticipantsBreakDown` crashes with one category column | High | Yes — crash on blank cell | Fix |
| 5 | Genre alias support missing (`"7 to smoke"` variants) | Medium | Silent missing genre | Fix |
| 6 | Rows truncated at header width | Low | Rare, silent drop | Informational |

---

## Proposed Solutions

1. **Null-guard all `getValues()` calls.** Treat a null return as an empty list. Both `getsheetAllRows` and `getParticipantsBreakDown` need this.

2. **Extend group-name detection in `removeExtraName`.** Check for `"team"`, `"crew"`, `"group"`, `"squad"` as group-hint keywords. If any name column contains a group hint, use it as the display name and ignore the rest. If none found, keep first-match behaviour.

3. **First-match wins for `colIndexMap`.** Use `putIfAbsent` instead of `put` so the first matching column for each keyword wins. Document this as intentional.

4. **Simplify `getParticipantsBreakDown` to be column-count agnostic.** Iterate over all detected category columns, collect all non-empty values into a single combined list, then pass to `setDtoCategory`. Remove the hardcoded index-0/index-1 assumption entirely.

5. **Add genre alias support.** Extend the `Genre` enum (or a companion map) with a list of aliases per entry. `normalizeGenre` checks both the primary label and all aliases. Example aliases for SMOKE: `"smoke"`, `"seven to smoke"`, `"7tosmoke"`. Also move the `toLowerCase()` call inside `normalizeGenre` so it is not caller-dependent.
