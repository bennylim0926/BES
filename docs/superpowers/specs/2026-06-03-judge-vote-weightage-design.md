# Judge Vote Weightage per Genre — Design Spec

**Issue:** #48
**Branch:** `feat/judge-vote-weightage`
**Date:** 2026-06-03

---

## Summary

Allow organisers to assign a custom vote weightage (integer ≥ 1, default 1) to each judge per genre in BattleControl. Match outcomes are determined by the sum of weightages on each side rather than a simple headcount.

---

## Data Model

### `BattleService.BattleJudge` (inner class)

Add `weightage: int` field, default `1`.

```java
public static class BattleJudge {
    private Long id;
    private String name;
    private Integer vote;
    private int weightage = 1;   // NEW
    // getters + setters
}
```

No DB migration required. `BattleJudge` is serialised to `battle_genre_state.judges_json` (a TEXT column). The new field rounds-trips automatically. Old rows without the field deserialise with `weightage = 0` from Jackson — the service normalises null/0 to 1 on load.

### No changes to `event_genre_judge`

Weightage is a battle-time setting, not a permanent genre assignment property. It lives entirely in the JSON blob and in frontend localStorage.

---

## Backend Changes

### DTOs

**`SetJudgeDto`** — add optional `weightage` field (default 1, min 1). Used when adding a judge so a single call sets both identity and weightage (needed for genre-switch restore from localStorage).

```java
@Min(1)
private int weightage = 1;
```

**New `UpdateJudgeWeightageDto`** — for inline mid-battle edits:

```java
@NotNull private Long id;
@Min(1) private int weightage;
```

### New Endpoint

`POST /api/v1/battle/judge/weightage` → `updateJudgeWeightageService(dto)`

Service method:
1. Finds judge in in-memory `judges` list by `id`
2. Sets `weightage`
3. Broadcasts updated list via `/topic/battle/judges`
4. Calls `persistActiveState()`

### `setBattleJudgeService` update

When adding a judge, read `dto.getWeightage()` and set it on the new `BattleJudge` (instead of always defaulting to 1).

### Vote Tallying — `setScoreService()`

Replace headcount with weightage sum:

```java
// Before
int leftVotes  = Collections.frequency(score, 0);
int rightVotes = Collections.frequency(score, 1);

// After
int leftWeight  = judges.stream().filter(j -> j.getVote() == 0)
                         .mapToInt(BattleJudge::getWeightage).sum();
int rightWeight = judges.stream().filter(j -> j.getVote() == 1)
                         .mapToInt(BattleJudge::getWeightage).sum();
```

Tie condition: `leftWeight == rightWeight`. Left wins: `leftWeight > rightWeight`. Right wins: `rightWeight > leftWeight`.

---

## Frontend Changes (`BattleControl.vue`)

### Judge management section

Each judge row gains an inline **Weight** number input (min=1) beside the remove button. On `change`/`blur`, calls `updateJudgeWeightage(judgeId, weightage)` (new `api.js` function → `POST /api/v1/battle/judge/weightage`).

### Vote panel

Each judge card shows a `×N` weightage badge below the judge name. Badge uses accent blue (`#93c5fd`) to distinguish from vote indicators.

### Tentative winner computed (`tentativeWinner`)

Replace headcount with weightage sum — mirrors backend logic:

```js
const leftWeight  = judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0)
const rightWeight = judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0)
```

### `voteCountDisplay` → `voteWeightDisplay`

Returns `{ left: leftWeightSum, right: rightWeightSum }`. The tie banner and winner banner show these weighted totals (e.g. "TIE — 2 – 2", "LEFT — 3 : 1").

### `api.js` — new function

```js
export const updateJudgeWeightage = async (id, weightage) => { ... }
```

---

## Persistence

### Backend (cross-device ✓)

`persistActiveState()` serialises the full `BattleJudge` list (including `weightage`) to `judges_json` on every mutation. On page load any device calls `getBattleJudges()` → reads from in-memory state (loaded from DB) → weightages come back correctly.

### Frontend localStorage

Extend saved shape from `{ id, vote }` to `{ id, vote, weightage }` in `saveGenreJudges()`. On genre-switch restore, `addBattleJudge(id, weightage)` passes the saved weightage in one call.

### Known limitation — genre-switch on a different device

`syncJudgesForGenre` removes backend judges and restores from localStorage. A device that has never had a given genre as the active genre will have an empty localStorage entry for it, and will clear the backend judges for that genre. This pre-existing behaviour affects all judge data (names, votes, and weightage) — it is **not introduced by this feature**.

**Follow-up:** Create a GitHub issue to fix `syncJudgesForGenre` to fall back to backend DB state when localStorage is empty for a genre, making genre switching cross-device safe.

---

## Acceptance Criteria

- [ ] Organiser can set a custom weightage (≥ 1) per judge per genre via inline input in BattleControl
- [ ] Default weightage is 1 — no change to existing behaviour for events with equal weightages
- [ ] Vote outcome uses sum of weightages, not headcount
- [ ] Tie/draw correctly detected when both sides have equal total weightage
- [ ] Weightage survives page refresh (backend DB authoritative)
- [ ] Weightage survives genre switching on the same device (localStorage per genre key)
- [ ] `×N` badge visible on each judge card in the vote panel
- [ ] Winner/tie banner shows weighted totals

---

## Out of Scope

- Setting weightage from EventDetails or AdminPage
- Displaying weightage on the `BattleJudge.vue` judge voting screen
- Cross-device genre-switch fix (tracked separately)
