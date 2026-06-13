# Score.vue Redesign — Design Spec

**Date:** 2026-06-13
**Branch:** `feat/score-redesign`
**Scope:** `BES-frontend/src/views/Score.vue` only. Modals (`FeedbackPopout`-style feedback panel, score breakdown) are reskinned in-file; no shared components introduced. Branch is `feat/` not `ui/` because the redesign introduces new behavior beyond visual changes (mode toggle, expandable tie pool, eliminated-tail collapse).

## 1 — What this page is for

Score.vue is a **Top N qualifier tool**. The job is to look at scored rankings for one genre and quickly identify Top 32 / 16 / 8, settle any tie at the Top N line, and (for admin/organiser) release results.

The current page treats this as a generic leaderboard — top-3 podium, then a wide table — which puts cosmetic emphasis on "who won" instead of "where the Top N line falls and who's tied at it." Tie-breaking lives in a separate panel that is visually disconnected from the rank where the tie occurs.

The redesign reorients the entire page around the Top N decision.

### Audience and use moments

- **Organiser / Admin** — opens this during the event, mid-round and right after the round closes. They set Top N, watch for ties, resolve the tie at the Top N line, release results. Needs row actions (score breakdown, judge feedback, results QR).
- **Emcee** — opens this to read the standings aloud and on a side monitor for the audience. Needs no controls; needs all scores readable from across the room.
- **Judges** do not use this page.
- **Public** does not see this page (the public-facing results portal is `/results`).

## 2 — Architecture: mode toggle

The page has **two modes** that share data and render-state but differ in chrome.

| Mode | Default for | Shows | Hides |
|------|-------------|-------|-------|
| **Control** | Admin, Organiser | Top N picker, status banner, leaderboard with row actions and inline tie resolver, Release Results pill, full filter row, By-Judge sub-view | nothing |
| **Broadcast** | Emcee | Event/genre header (large), uniform-row leaderboard, glowing Top N line, eliminated tail summary | filter row, row actions, release pill, tie resolver (becomes a quiet pending strip) |

A single chip-pair toggle (top-right) flips between them. **Either role can flip into either mode.** The default is role-driven on first load; the user's choice persists in `localStorage` (key: `score_mode_<event>`) for the rest of the session.

Both modes render from the same computed `finalRows`/`topNResult` chain that already exists; the difference is template branches and chrome density. No new backend work — this is a frontend-only redesign.

## 3 — Control mode layout

Top to bottom, single column:

### 3.1 Header row
- Left: small uppercase label `SCOREBOARD · <Event Name>` (Anton SC, `.type-label`-tier letter-spacing), then large `<Genre> — <Entry Type>` title (Anton SC, page-title scale). Entry-type suffix only appears when the genre has a Teams/Solo mix (existing `hasTeamAndSoloMix` logic).
- Right: **Release Results status pill** (admin/organiser only) — `RELEASED` (emerald, glowing dot) or `HIDDEN` (neutral). Click toggles. Replaces the current bottom-of-filter-card Release section.
- Right: **Mode toggle** — two-chip pair (`CONTROL` / `BROADCAST`), parallelogram clip, active chip uses `.bg-accent` outline.

### 3.2 Secondary filter row
A single compact strip below the header. Three groups separated by `|`:
- **Genre** chips
- **View** chips (`BY TOTAL` / `BY JUDGE`) — formerly "Group By"
- **Type** chips (`TEAMS` / `SOLO`) — conditional, same rule as today

All use `.para-chip-sm` with `aria-pressed`. Active chip uses `text-accent` + `border-[color:var(--accent-muted)]` (current pattern).

Removed from this row: Top N (promoted to hero), Release Results (promoted to header).

### 3.3 Top N hero picker
A 4-tile grid that occupies the visual centerpiece directly under the filter row.

- 4 tiles: `8` · `16` · `32` · `ALL` (label `ALL · <count>`).
- Each tile is a parallelogram (`.para-chip` shape) with two stacked lines: a large Anton SC number (~36–42px when selected, 28px when inactive) and a `TOP` micro-label below.
- Selected tile: full opacity, `border-color: var(--accent-color)`, `box-shadow: 0 0 20px var(--accent-subtle)`.
- Inactive tiles: opacity ~0.5.
- Section label above the row: `QUALIFY` (Anton SC label tier).

Below the picker, a **status banner** reacts to the choice:
- **Clean** (no tie, enough qualifiers) → emerald left-border strip, glowing dot, `TOP <N> READY — 0 TIES`.
- **Tie at Top N** → amber left-border strip, glowing dot, `TIE AT RANK <N> — <count> AT <score> — RESOLVE BELOW`. (Existing semantic-state pattern from the design system.)
- **Insufficient** (fewer scored than N) → red left-border strip, `ONLY <count> SCORED — TOP <N> NOT YET REACHABLE`.

When `Top N = ALL`, the picker stays but the status banner is hidden.

### 3.4 Leaderboard
A vertical stack of one-row-per-participant. Three row variants share the same parallelogram shape and padding rhythm:

**Leader row (rank 1, qualifying only):**
- Larger row (~64px tall) with a 3px left-border in `var(--accent-color)`.
- `font-source` weight 700, rank number ~24px, name ~16px, score ~28px.
- Inline meta line beneath the name: per-judge scores joined with `·` (e.g. `A 7.2 · J 7.1 · M 7.0 · S 7.1`) at `.type-label` tier — replaces the wide per-judge columns of the current admin table.
- Row actions (admin/organiser only) appear as a right-aligned 3-icon group: score breakdown (`pi-chart-bar`, only when `isMultiAspect`), judge feedback (`pi-comment`), and results QR (`pi-qrcode`, only when `resultsReleased && refsMap[name]`). Idle at 50% opacity, full opacity on row hover. Existing aria-labels preserved.

**Standard qualifying row (ranks 2 through N):**
- Compact (~40px tall), parallelogram, faint background tint (`rgba(255,255,255,.02)`).
- Rank, name, score on a single line. Per-judge meta line tucked under the name at smaller size (`.type-label` tier, 0.45 opacity).
- Same row-action icons on the right, same idle/hover treatment.

**Eliminated row (below the Top N line):**
- Same shape, ~30% opacity. No row actions visible.
- After the first 2–3 eliminated rows, a single line collapses the rest: `⋯ <N> MORE ELIMINATED ⋯`. Clicking it expands to show all.

### 3.5 Tie resolver — inline at the Top N line

When `topNResult.hasTieBreaker && !tieBreakerConfirmed`, the leaderboard renders an **amber-bordered band** in place of the tied rows at their natural position. The band represents the **eligibility pool** (who can be picked) and the **selection** within it.

#### Pool composition

Two ordered groups inside the band:

1. **Tie base** — the auto-detected tied participants from `topNResult` at the cutoff score. Labelled `TIE BASE · LOCKED`. These can never be removed from the pool.
2. **Manually added** — participants the organiser pulled in via the stepper (see below). Labelled `ADDED · <count>`. Each manually-added row carries a 2px amber left-border so it's visually distinct from the tie base.

#### Stepper — one row at a time

Two buttons sit directly below the pool, side by side:

- **`− EXCLUDE <name> · <score>`** — removes the most-recently-added (= lowest-eligible) member. Disabled when the pool is back to the tie base.
- **`+ INCLUDE <name> · <score>`** — adds the next eligible participant below the pool's current floor. Disabled when no more eliminated participants exist.

Each button label shows the target's name and score, so the organiser always knows who they're including or removing before clicking.

**Determining "next eligible":**
- Sort all not-yet-included participants by `(totalScore DESC, auditionNumber ASC)`.
- `+ INCLUDE` takes the first of that list and adds it.
- `− EXCLUDE` removes the lowest-ranked manually-added entry (the last one added by the deterministic order above).

Audition-number ASC is the canonical tiebreaker for participants at the same score (see §9.3). This makes the stepper deterministic and repeatable — the judge can stop at any point, including mid-score-tier (e.g., include Halo (#07) at 7.2 without including Pulse (#22) also at 7.2).

#### Selection within the pool

Independent of pool composition, the organiser picks **`spotsFromTie`** advancing participants from the full pool (tie base + manually added). Per-row toggles:

- Selected (advances): emerald background tint, emerald 2px left-border, check icon, `ADVANCES` badge.
- Unselected, slots remain: neutral tint, hollow circle, no badge.
- Unselected, slots full: 0.35 opacity, `ELIMINATED` neutral badge.

Selection can cross any score boundary — picking a manually-added 7.2 participant over a tie-base 7.3 participant is allowed and is the judge's call. Eligibility is rank-ordered; selection is discretionary.

#### Band header

- Left: `RESOLVE TIE @ <baseScore> — POOL OF <poolSize> FOR <spotsFromTie> SPOT(S)`.
- Right: `<selectedCount> SELECTED`.

#### Footer buttons

- **`CONFIRM TOP <N>`** (emerald, primary; disabled until `selected === spotsFromTie`) — calls existing `setResolvedParticipants` with the chosen names. API and backend behavior unchanged.
- **`RESET`** (ghost) — clears selection, clears the manually-added pool, returns to the tie base. Calls existing `resetTieBreaker` (extended to also clear `addedToPool`).

#### Resolved state

When `tieBreakerConfirmed`, the band collapses to a one-row emerald summary: `✓ TOP <N> CONFIRMED — <N> ADVANCED · <RESET>`.

#### Black-box rule

The manually-added pool is **only ever visible in Control mode**. Broadcast mode always renders the tie summary using the auto-detected tied count and base score from `topNResult` (never the expanded pool). Any future AuditionDisplay integration follows the same rule. The public `/results` portal is unaffected — it has no pool state.

### 3.6 Top N line

A 1px horizontal rule across the full leaderboard width, using a `linear-gradient(90deg, transparent, var(--accent-color) 50%, transparent)` with a soft `box-shadow` glow in `var(--accent-muted)`. A right-aligned label sits 18px above: `▲ TOP <N>` (no "cut" word — the chevron and number speak for themselves).

The line is **hidden when Top N = ALL** or when the tie is unresolved (it shows once `CONFIRM TOP <N>` runs).

### 3.7 By-Judge sub-view
When the user selects `VIEW: BY JUDGE`, the page replaces the Top-N hero + leaderboard with a vertical stack of mini-leaderboards (one per judge). Each mini-leaderboard:
- Has a `section-rule`-style header with the judge's name.
- Renders the same standard row shape, but without the leader stripe, without row actions, and without the Top N line. This is an audit view, not a qualifier tool.
- Reuses the existing `byJudge` data shape from `transformForScore`.

The header, filter row, mode toggle, and Release pill all stay visible above. The Top N picker and status banner are hidden in this sub-view (Top N doesn't apply per-judge).

## 4 — Broadcast mode layout

### 4.1 Header
- Left: small label `<Event Name> · <Type>`. Large title: `<Genre> · TOP <N>` (or `<Genre>` if Top N = ALL). Sub-label: `<count> SCORED`.
- Right: Mode toggle chip pair only. No release pill, no filter chips.

### 4.2 Leaderboard — uniform rows, 2-column packing
- All rows the same height and the same type scale. Rank 1 keeps a leader stripe (3px left-border in accent) but the score number is the same size as everyone else.
- Layout switches based on N:
  - **Top 8 or ALL ≤ 12**: single column.
  - **Top 16, Top 32, or ALL > 12**: 2-column grid. Reading order is **column-major** — ranks 1–8 fill the left column, 9–16 fill the right (for Top 16). For Top 32, 1–16 left, 17–32 right.
- Each row: parallelogram chip, rank number (~22px), name (~16px), score (~22px). All in Anton SC, uppercased per the design system.
- Below the Top N line: collapsed to one summary line — `⋯ <N> ELIMINATED · LOWEST <score> ⋯`. Tap to expand.

### 4.3 Top N line
Same glowing horizontal rule as Control mode, spanning both columns. Right-aligned label `▲ TOP <N>`.

### 4.4 Unresolved tie
If a tie at the Top N line is unresolved when an emcee opens Broadcast mode, the tied rows show in an amber-tinted strip in their natural position with the label `TIES PENDING — RESOLVE IN CONTROL`. The count and base score shown reflect the auto-detected tie only — manual pool additions from Control mode are not surfaced (see §3.5 black-box rule). **No interactive resolver here** — Broadcast is read-only.

## 5 — Modals (reskin only)

The feedback panel and score breakdown modals keep their current Teleport-to-body structure and current bottom-sheet-on-mobile behavior. They are restyled to match the new chrome:
- Panel background: `bg-surface-800`, parallelogram clip on the panel itself.
- Header: section-rule pattern instead of border-bottom.
- Tag groups in feedback panel: keep emerald/amber semantics, switch to parallelogram chip shape.
- Close buttons keep their current 44px hit area and `aria-label`.

No behavioral changes. Same data fetches (`getParticipantFeedback`, `breakdownRows`).

## 6 — Mobile

The qualifier leaderboard is naturally single-column and mobile-safe.

- **Header** stacks vertically: title, then a row containing Release pill + Mode toggle.
- **Filter row** wraps; chip groups keep their `|` separators.
- **Top N picker** stays a 4-tile grid (smaller tiles, ~70px tall).
- **Row actions** in Control mode collapse to a single overflow icon at narrow widths; tap reveals breakdown/feedback/QR.
- **Tie band** is full-width and stays inline.
- **Broadcast mode** drops to single column under ~640px (no 2-col packing on phones).

Existing pagination logic (`PAGE_SIZE = 10`) is **removed** — the qualifier leaderboard scrolls naturally. Pagination contradicts the design (the Top N line and paging fight each other).

## 7 — State and behavior changes from current code

This is what changes in the script section. Most logic stays.

### Kept as-is
- `transformForScore` (both By Total and By Judge branches).
- `aggregateJudgeScore` and `criteriaForGenre` loading.
- `topNResult` computed and tie-detection logic.
- `tieBreakerWinners`, `tieBreakerConfirmed`, `tbKey`, `saveTieBreaker`, `loadTieBreaker`, `resetTieBreaker` — extended to also persist/clear `addedToPool` (see Added section).
- `confirmTieBreaker` (still posts to `setResolvedParticipants` — the resolved set now comes from `eligibilityPool` ∩ `tieBreakerWinners`).
- `finalRows` computed.
- `loadAdminData`, `resultsReleased`, `refsMap`, `toggleRelease`, `openQR`.
- `viewBreakdown`, `viewFeedback`, `breakdownRows`, `groupTags`.
- All existing watchers on `selectedEvent`, `selectedGenre`, `selectedTabulation`, `tbKey`.

### Removed
- `PAGE_SIZE`, `tablePage`, `totalTablePages`, `pagedFinalRows` — no pagination.
- `judgeColumnKeys` computed — judge scores are now rendered inline as a meta line under each row, not as columns.
- The current podium block (`finalRows.length >= 3` top-3 cards).
- The current DynamicTable usage block for non-admin viewers (admin custom table also goes — both replaced by the new row component).

### Added
- `mode` ref (`'control' | 'broadcast'`), initialized to `userRole === 'ROLE_EMCEE' ? 'broadcast' : 'control'`, persisted to `localStorage` under `score_mode_<event>`.
- Computed `statusBanner` returning `{ tone: 'clean' | 'tie' | 'insufficient', message: string }` for the Control-mode banner. Derived from `topNResult` and `filteredParticipantsForScore.rows.length`.
- Computed `broadcastColumns` returning `'1col' | '2col'` from a viewport check + Top N value. Use a simple resize listener (no external dep).
- Computed `eliminatedSummary` returning `{ count, lowestScore }` for the collapsed eliminated line.
- Local ref `eliminatedExpanded: ref(false)` for the collapse toggle.
- `addedToPool` ref (`Set<string>`) — manually-added pool members for the tie resolver. Persisted alongside `tieBreakerWinners` and `tieBreakerConfirmed` in the `tbKey` localStorage payload (new shape: `{ winners, confirmed, addedToPool }`). `resetTieBreaker` clears it.
- Computed `eligibilityPool` returning the ordered list `[...tieBaseParticipants, ...addedToPoolOrdered]` — the full pool the band renders.
- Computed `nextEligibleAdd` returning `{ name, score } | null` — the next participant `+ INCLUDE` would add. Derived by sorting all not-yet-included rows by `(totalScore DESC, auditionNumber ASC)` and taking the first. `null` when no eligible participants remain.
- Computed `nextEligibleRemove` returning `{ name, score } | null` — the most-recently-added member `− EXCLUDE` would remove. `null` when the pool is back to the tie base.
- Methods `includeNextInPool()` and `excludeLastFromPool()` — mutate `addedToPool` and call `saveTieBreaker`.
### Bottom-up order (per CLAUDE.md)

- **Frontend (default path):** single-file redesign of `Score.vue`. Existing endpoints called: `getParticipantScore`, `getParticipantFeedback`, `getResultsStatus`, `releaseResults`, `getParticipantRefs`, `getScoringCriteria`, `setResolvedParticipants` — all unchanged.

- **One conditional backend change:** the stepper requires `auditionNumber` on every score row to compute `nextEligibleAdd`. The implementation plan **must verify** that `GetParticipantScoreDto` already exposes `auditionNumber` (the underlying `EventGenreParticipant` entity has it). If the DTO does not expose it, add the field on the DTO + mapper as a precondition step — bottom-up per CLAUDE.md. This is the **only** acceptable backend change for this spec; if anything else looks like it needs backend work, stop and re-spec.

## 8 — Accessibility

- Mode toggle: `role="group"`, `aria-label="Display mode"`. Each chip carries `aria-pressed`.
- Top N tiles: `role="group"`, `aria-label="Qualify top N"`. Each tile carries `aria-pressed`.
- Status banner: `role="status"` with `aria-live="polite"` so screen readers announce tie/clean/insufficient changes.
- Top N line: `role="separator"` with `aria-label="Top <N> line"`.
- Tie band: existing `aria-label`s on the per-participant toggle buttons are preserved; the band itself gets `role="region"` `aria-label="Tie-breaker resolution"`.
- Stepper buttons: `aria-label="Include <name> in tie pool, score <score>"` and `aria-label="Exclude <name> from tie pool, score <score>"`. Disabled state uses `aria-disabled`.
- Row action icons: keep current `aria-label` strings (`View score breakdown for <name>`, etc.).
- Mode change announces via `aria-live` on the toggle itself.

## 9 — Layout invariants

These hold across every element on the page in either mode.

### 9.1 No text truncation
No participant name, genre name, event name, judge name, filter chip label, or status banner string is ever truncated with ellipsis. **No `text-overflow: ellipsis`. No `white-space: nowrap` paired with `overflow: hidden` on any text-bearing element.** Text must always be fully visible.

This drives several concrete rules:

- **Leaderboard rows wrap, not truncate.** If the participant name doesn't fit on a single line at the row's natural width, the name wraps to two lines and the row grows vertically. The rank number and score stay aligned to the row baseline (`align-items: flex-start` for the row, name has `flex: 1` with no nowrap).
- **Broadcast 2-column packing** uses `grid-template-columns: 1fr 1fr` with `min-width: 0` on the row containers so flex children can wrap freely. If long names cause one column to grow taller than the other, the grid keeps both columns aligned to the top — the Top N line still sits cleanly below.
- **Tie-band rows** follow the same wrap-not-truncate rule.
- **Header title** (`<Genre> — <Entry Type>` or `<Genre> · TOP <N>`) wraps to two lines when needed; the page title scale (`type-page-title`) tolerates two lines.
- **Filter chips** never set `max-width` or `overflow: hidden`. If a genre name is long ("HOUSE / FREESTYLE / VOGUE"), the chip grows; the row wraps to a new line via the existing `flex-wrap: wrap` on the filter container.
- **Status banner** uses `flex-wrap: wrap` so the label, severity word, and detail string can wrap onto multiple lines on narrow screens.

### 9.2 No score truncation
Score numbers always render with the precision the data carries (`Number.toFixed(1)` for totals, `toFixed(2)` for per-judge aggregates). No abbreviation (e.g., "28.4" never becomes "28"). The score cell has `flex-shrink: 0` to guarantee it claims its space before the name wraps.

### 9.3 Canonical ordering for same-score participants
Wherever the system needs to order participants who have identical totals — the tie band stepper (who comes next when you click `+ INCLUDE`), any future broadcast view of ties, AuditionDisplay tie integration — the tiebreaker is **audition number ASC**. This is deterministic, matches the order participants performed in, and reuses the convention already established by AuditionDisplay's `UP NEXT` rendering.

When audition numbers are also tied (a registration edge case where two participants share the same number), fall back to alphabetical participant name. This case is rare but the rule must be specified to keep the stepper repeatable.

This rule applies to the eligibility-pool ordering only. The judge's **selection** within the pool remains discretionary (§3.5) — they may pick any combination of pool members regardless of audition number.

## 10 — Out of scope

- Animations beyond the existing transition utilities. The event-scoped animation theme system (IMPACT/HYPE introduced in #133) is **not** applied to this page — auditions aren't a hype moment.
- Changes to the public `/results` portal.
- Changes to `BattleOverlay.vue`, `BracketVisualization.vue`, `Chart.vue` (per the design system invariant).
- Changes to the `FeedbackPopout` component used by judges in `AuditionList`. The feedback panel inside Score.vue is its own viewer block.
- Releasing results UX changes — still a single click, just relocated.
- Backend, DB, or API contract changes.

## 11 — Open questions

None. All decisions captured above.
