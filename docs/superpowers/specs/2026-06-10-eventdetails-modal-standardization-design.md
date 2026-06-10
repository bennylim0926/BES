# EventDetails Modal Standardization

**Date:** 2026-06-10
**Branch:** `ui/event-details-ux-overhaul`
**Scope:** Visual/UX only — no API or logic changes

## Problem

EventDetails.vue contains three popup menus with inconsistent designs:

| Modal | Current shell | Shape | Colors | Mobile |
|-------|--------------|-------|--------|--------|
| Walk-in Form | `ActionDoneModal` wrapper | `rounded-xl` | old `primary-*` (red) | always centered |
| Genre Entries | inline `Teleport` | `para-chip` ✓ | `accent` ✓ | slides up from bottom ✓ |
| Scoring Criteria | inline `Teleport` | `card-hover` + `rounded` items | mixed | always centered |

Genre Entries is the reference design. Walk-in Form and Scoring Criteria are brought into line.

## Standardized Shell

Every modal uses this structure:

```
Teleport(body)
  Transition (opacity 200ms ease-out / 150ms ease-in)
    fixed inset-0 z-50  flex items-end sm:items-center  justify-center  p-0 sm:p-4
      backdrop (absolute inset-0 bg-black/60 backdrop-blur-sm, click-to-close)
      card-hover relative w-full sm:max-w-md flex flex-col  max-height: 85vh
        corner-bar-tl + corner-bar-bl
        Header    (flex, px-4 py-3, border-b border-surface-600/30, shrink-0)
        Body      (flex-1, overflow-y-auto, p-4, space-y-4, min-h-0)
        Footer    (flex, px-4 py-3, border-t border-surface-600/30, shrink-0)  — only when needed
```

**Header pattern:** left side = icon + `type-body text-content-primary` title + `badge-neutral` event name badge; right side = `para-chip-sm` close button with `pi-times`.

**Mobile behaviour:** `items-end` on mobile → panel slides up from bottom. `sm:items-center` on desktop → centered. This matches Genre Entries exactly.

## Walk-in Form (`CreateParticipantForm.vue`)

**What changes:** The `ActionDoneModal` wrapper is removed. The component renders its own standardized shell (Teleport + Transition + panel) directly, like Genre Entries.

**Layout inside:**

1. **Stage Name** — `type-label` label + `input-base` field.
2. **Divisions** — `type-label` label with "tap to select" hint. Genres displayed as `para-chip-sm` chips in a flex-wrap grid. Tapping a chip toggles it (selected = accent border + glowing dot, unselected = surface fill).
3. **Per-genre team details** — for each selected genre that requires team entry, a section expands inline below the chip grid:
   - Section rule header: `<GenreName> · <format>` (e.g. "Breaking · 2v2")
   - Team / Solo toggle (para-chip toggle row, hidden if solo not allowed)
   - Team Name input (only when team mode active and format requires it)
   - Member name inputs (one per additional member slot)
4. **Footer** — Cancel (ghost) left, Add Participant (primary, disabled until `canSubmit`) right.

**Success/error feedback:** The four `ActionDoneModal` result dialogs (success, allExisting, submitError, noName) are retained as-is — they are separate overlays that fire after submission, not part of the form shell.

**Props/emits:** unchanged — `show`, `event`, `eventGenres`, `title`; emits `createNewEntry`, `close`.

## Scoring Criteria (`ScoringCriteriaModal.vue`)

**What changes:** Visual/structural update only. Logic (add, edit, delete, applyToAllGenres) is untouched.

**Header:** Add icon (`pi-chart-bar` or similar) and event name badge to match the shell pattern.

**Genre tabs:** Restyled from current free-floating `px-5` row to `para-chip-sm` chips in a `flex gap-1 px-4 pt-3 pb-0 flex-shrink-0` row. Active tab uses `background: var(--accent-color); color: surface-900`. Criteria count badge uses existing inline `span` but restyled as `bg-white/20 text-white` (active) / `bg-surface-600 text-surface-300` (inactive).

**Criteria rows:** Replace `card-hover p-3` nested cards with `para-chip` rows:
```
para-chip  flex items-center gap-3 px-3 py-2.5  (border-surface-600/40 or accent when active)
  glowing dot  (accent when has weight, dim when no weight)
  name (type-body text-content-primary)
  weight badge (badge-neutral, ×N)
  edit + delete buttons (para-chip-sm icon buttons)
```

**Add / Edit forms:** Remain as inline expanded sections toggled by `showAdd` / `editingId` (current behaviour). Fields use `input-base`; Save/Cancel buttons use `para-chip`/`para-chip-sm`. No change to interaction model.

**Footer:** retains "Add Criterion" button (left, toggles `showAdd`) and "Copy to all genres" button (right), restyled to `para-chip-sm` with `type-label`.

**Max width:** increase from `max-w-xl` to keep it, but add `sm:max-w-lg` — the tab row benefits from extra width.

## Genre Entries (unchanged)

Already matches the standard. No edits.

## What is NOT changing

- All business logic in `CreateParticipantForm.vue` (submit flow, team member counting, validation)
- All business logic in `ScoringCriteriaModal.vue` (add/edit/delete/applyToAll)
- The Genre Add Team Form (`genreAddForm`) — already new design, out of scope
- The Confirm Dialog and Check-in Dialog in EventDetails.vue — out of scope
- Backend, API contracts, emits/props interfaces
