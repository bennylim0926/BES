# EventDetails UX Overhaul — Design Spec
**Date:** 2026-06-09
**Issue:** #123
**Branch:** `ui/event-details-ux-overhaul`
**Constraint:** UI-only unless explicitly marked as logic change (user-requested).

---

## 1. Scope Summary

Six focused improvements to `EventDetails.vue` and `ScoringCriteriaModal.vue`. No routing, no new views, no new API endpoints except one session-token auto-generation behaviour change.

---

## 2. Judge Pool — Mini-Profile Cards

### Problem
Two separate places manage judges: "Event Judges" (global add) and per-genre "Judges" (assign). No explanation of the relationship. New organisers don't know why they add judges twice.

### Design
Replace the flat "Event Judges" chip list with a **card-per-judge grid**.

**Judge card anatomy:**
- Judge name with green status dot (amber dot + amber border tint when no categories assigned)
- "CATEGORIES" label with chips for each assigned category (× to remove — calls `removeJudgeFromDivision`)
- "Assign…" dropdown listing all event categories not yet assigned to this judge (calls `submitAssignJudge`)
- "+" placeholder card at the end to add a new judge (inline name input)
- ✕ remove-from-pool button (top-right of card)

**Section header copy:**
```
JUDGE POOL
Add your judges here first — assign categories below.
```

**Empty unassigned state:** Amber border + amber dot on the card. "No categories assigned yet" text inside.

### Per-Genre "Judges" Section → Read-only
The Judges row inside each genre tab becomes a non-interactive chip list:
```
JUDGES  ───────────────────  manage in judge pool above
● Alex Chen   ● Maria L.
```
No assign/remove controls. Green dot per judge chip. If no judges assigned, show "None assigned — add in Judge Pool above."

---

## 3. Genre Tab Content — Section Rules

### Problem
Participant stats (Total/Reg/Unreg), scoring criteria, and judges all appear at equal visual weight inside the genre tab with no grouping.

### Design
Three explicit sections inside each genre tab, each with a section rule label:

```
ROSTER STATUS  ────────────────────────
[ 24 total ]  [ 22 registered ]  [ 2 unregistered ]
(unregistered participant chips if any)
─ divider ─
SCORING CRITERIA  ──────────────────   [ ⚙ CONFIGURE ]
[ Musicality ×2 ]  [ Timing ×1 ]  [ Execution ×1 ]
(or: "Default — single 0–10 score" if none set)
─ divider ─
JUDGES  ────────────────────  manage in judge pool above
● Alex Chen   ● Maria L.
```

Badge label changes: `Total:` → `{n} total`, `Reg:` → `{n} registered`, `Unreg:` → `{n} unregistered` (word, not abbreviation).

---

## 4. Session Links — Auto-Generated, Refresh-to-Extend

### Problem
Organiser must manually generate tokens per role, then delete and recreate when expired. Judge tokens must be created separately after judges are added. No expiry urgency signal.

### Design (logic changes — user-requested)

**Auto-generation:**
- On `loadSessionTokens()` (called on mount — section is always visible, expand watcher removed): if no EMCEE token exists → auto-call `generateToken('EMCEE')`. Same for HELPER.
- On `submitAddJudgeGlobal()` success: immediately auto-call `generateToken('JUDGE', judgeId)` for the new judge.
- On judge removal (`askRemoveJudgeGlobal` confirmed): revoke that judge's session token before removing the judge.

**UI layout — always-expanded section:**
```
SESSION LINKS  ──────────────────  auto-generated · refresh to extend

[ EMCEE ]                   Expires Jun 15      [ ↻ Refresh ]  [ ⎘ Copy ]
[ HELPER ]                  Expires Jun 9 ⚠     [ ↻ Refresh ]  [ ⎘ Copy ]
[ JUDGE ]  Alex Chen        Expires Jun 12      [ ↻ Refresh ]  [ ⎘ Copy ]
[ JUDGE ]  Maria L.         Expires Jun 12      [ ↻ Refresh ]  [ ⎘ Copy ]

Judge links are removed automatically when a judge is deleted.
```

**Refresh:** `handleRevokeToken(t.tokenId)` → `generateToken(role, judgeId)` → reload list. Copy button always reads from the freshly loaded token.

**Expiry colour:**
- > 3 days: muted grey date
- ≤ 3 days: amber date + ⚠ symbol

**Copy feedback:** Same pattern as Audition Screen button — `copiedTokenId` ref, green ✓ icon for 2s.

**Removed:** Manual generate buttons (+ EMCEE, + HELPER, + JUDGE), delete button per token, collapsed expand toggle (section always visible).

---

## 5. Categories Section (renamed from Divisions)

### Terminology
- **Genre** = top-level discipline (Popping, Hip Hop, House) — unchanged
- **Category** = competition format within a genre (Popping 1v1, Popping 7 to Smoke)
- All UI text changes: "Divisions" → "Categories", "division" → "category" throughout `EventDetails.vue`

### Help text (under section rule)
```
Categories are competition formats within each genre (e.g. Popping 1v1, Popping 7 to Smoke).
Names must match your Google Sheet column values exactly — suggestions below are pulled from your sheet.
```

### Sheet Suggestions Strip (shown when `sheetCategories.length > 0`)
A panel above the per-genre groups:
```
FROM YOUR SHEET — click to add as a category
[ Popping 1v1 (covered) ]  [ Popping 7 to Smoke (covered) ]  [ + Hip Hop 1v1 ]  [ + House Open ]
```
- **Covered** = a category's `name` matches this sheet value exactly (case-insensitive). Shown greyed out with strikethrough, not clickable.
- **Uncovered** chips = no matching category yet. Clickable. On click → show an inline genre picker popover ("Add to which genre?") listing the available genre groups. Selecting a genre calls `addDivisionToGroup(genreId, chipValue)` with the sheet value as the category name.
- Strip hidden when no sheet is connected (`sheetCategories.length === 0`).

### Category Row
```
● Popping 1v1     [ 18 matched ]   [ 1v1 ▾ ]   [ ✕ ]
```
- Green dot = has matched sheet rows; amber dot + amber left border = no match
- Match count replaces the old green/amber count badges
- No alias button in normal view (aliases hidden — see below)

### Alias — Hidden by Default
Aliases were a workaround for name mismatches. With suggestion chips providing the exact sheet value, aliases are an edge case. Collapse aliases behind a small "add alias manually" text link at the bottom of each expanded category row. Alias chips (if any exist) still shown inline on the row.

### Unmatched Sheet Values Strip (bottom of section)
Same amber strip as current, but now uses **exact case-insensitive equality** (not substring). Each unmatched value is also a clickable "+ add" chip (same as suggestions strip).

### No Solo Default (logic change — user-requested)
When `saveDivisionFormat` sets format to `2v2`, `3v3`, `4v4`, or `5v5`, immediately call `updateDivisionSoloAllowed(div, false)` after the format save. The Solo toggle remains visible for manual override.

### Matching Bug Fix (logic change — user-requested)
Change `cat.includes(n)` → `cat.toLowerCase() === n.toLowerCase()` in both `matchCounts` and `unmatchedSheetValues` computed properties. This is the root cause of false positives (e.g., category "1v1" matching "Hip Hop 1v1", "House 1v1", "Popping 1v1" simultaneously).

---

## 6. Scoring Criteria Modal — "Copy to all genres"

### Changes
- Button label: `Apply to all genres` → `Copy to all genres`
- Button tooltip: `Replaces criteria in all other genres with these`
- After `applyToAllGenres()` resolves: show brief `Copied!` state on the button for 1.5s (same pattern as copy buttons elsewhere)

---

## 7. Assign Dropdown Direction Fix

The judge assign dropdown in the genre tab currently opens upward (`bottom-full`). Change to `top-full` (opens downward) so it doesn't get clipped at the bottom of the panel.

---

## 8. Deferred — Display Name for Categories

**Tracked as separate issue.** The concept: `name` = sheet match key (immutable); `displayName` = human-facing label (editable, defaults to `name`). Requires DB migration + DTO changes + updating all display sites (Score.vue, AuditionList.vue, BattleControl.vue, Results.vue). Out of scope for this branch.

---

## Affected Files

| File | Changes |
|------|---------|
| `BES-frontend/src/views/EventDetails.vue` | All sections above |
| `BES-frontend/src/components/ScoringCriteriaModal.vue` | Section 6 only |

No backend changes required for sections 2–3, 5 (UI-only). Sections 4 (session auto-generation) and 5 (matching fix, no-solo default) involve frontend logic changes only — no new endpoints.

---

## Logic Change Checklist (user-requested, frontend only)

- [ ] Session auto-generate EMCEE/HELPER on load if missing
- [ ] Session auto-generate JUDGE token when judge added to pool
- [ ] Session auto-revoke JUDGE token when judge removed from pool
- [ ] Refresh = revoke + regenerate
- [ ] `matchCounts` / `unmatchedSheetValues`: exact equality, not substring
- [ ] Team format (2v2+) → soloAllowed defaults to false on format set
