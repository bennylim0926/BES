# Battle Views Branding & Judge Panel Rework

**Date:** 2026-06-08
**Branch:** `feat/battle-views-branding`
**Issue:** #109

## Overview

Standardize `BattleOverlay.vue` and `BracketVisualization.vue` by removing the OBS-transparent background, adding event logo + active genre display, reworking the bracket slot color hierarchy, and redesigning the judge panel resting position in the overlay.

---

## 1. BracketVisualization.vue

### Removals

- **Header bar** (`<header class="bracket-header">`) — entire element removed. Includes the LIVE BRACKET wordmark, brand dots, and the active pair pill. Active pair is already visible via slot highlighting.
- **LED ticker bar** (`<div class="ticker-bar">`) — entire element removed. Genre is now in the logo banner; active pair is visible in the bracket itself.

### Logo Banner (new)

A new `logo-banner` flex row is added at the top of `.bracket-root`, between the old header position and the bracket area:

```
bracket-root (flex column)
  └── logo-banner          ← NEW (~80px, flex-shrink: 0)
  └── bracket-area / empty-state / smoke-wrap
```

**Logo banner contents (centered):**
- Event logo `<img>` when `overlayConfig.logoUrl` is set (max-height: ~60px, `object-fit: contain`)
- Genre name below the logo in Anton SC, letter-spacing 0.22em, muted white (`rgba(255,255,255,0.55)`)
- If no logo: genre name only, with decorative hairline rule either side (matching existing `judges-line` style)
- If no logo and no genre: banner still renders (provides consistent spacing)

**Layout after removal:**
- No header (−52px) + no ticker (−30px) = +82px
- Logo banner takes back ~80px
- Net: bracket area fills cleanly; Top32/Top16 slots are not cut off

### Slot Color Hierarchy (reworked)

| Slot State | Class | Style |
|------------|-------|-------|
| Active pair — left slot | `slot-active-left` | Left-color red tint, lit border (existing, unchanged) |
| Active pair — right slot | `slot-active-right` | Right-color blue tint, lit border (existing, unchanged) |
| Non-final winner | `slot-winner-round` | White border `rgba(255,255,255,0.35)`, subtle white glow, battler-name full opacity white |
| Final winner (Top2) | `slot-winner` | Gold (existing `rgba(245,158,11,*)` style, unchanged) |
| Loser | `slot-loser` | `opacity: 0.62` (up from 0.42 — less aggressive dimming) |

**`slotClass()` change:**
```js
if (match[2] === match[slot]) return isFinal ? 'slot-winner' : 'slot-winner-round'
```
`slot-winner-left` and `slot-winner-right` are removed entirely.

---

## 2. BattleOverlay.vue

### Background

- Remove all `transparent-page` CSS class manipulation (`document.documentElement.classList.add/remove('transparent-page')` in `onMounted`/`onUnmounted`)
- Remove the global `<style>` block containing `.transparent-page` rules
- Add `background: #060818` to `.overlay-root` — matches `BracketVisualization.vue`'s root background
- Smoke mode unaffected: `Chart.vue` has its own `.chart-root { background: #06080e }` which continues to render correctly when embedded

### Logo + Genre Watermark

A compact element at the very top center of `.overlay-root` (always visible, `position: absolute; top: 0; left: 50%; transform: translateX(-50%)`):

- Logo image (~36px tall) + genre text below it in Anton SC ~10px, letter-spacing 0.22em
- Total height: ~55px
- `pointer-events: none`, `z-index: 45` (below judge panel at z-index 50, above panels)
- If no logo: genre text only
- Appears in both standard and smoke modes

### Timer (reworked)

**Old:** Progress bar + countdown text, wide centered bar at top, `width: 60%`

**New:**
- Countdown text only — Anton SC, `font-size: 28px`, no track/fill
- Position: `top-right corner` — `position: absolute; top: 12px; right: 20px; z-index: 100`
- Warning state (`timeLeft <= 10`): red color + pulse animation (existing keyframe reused)
- Entrance/exit: existing `Transition name="timer-enter"` kept, animation updated to slide from right edge

### Judge Panel — Resting Position (reworked)

**Current flow:**
1. Slam to vertical center (`judgeSlamCenter`)
2. Votes revealed
3. Retreat to top-center (`judgeRetreatTop` → `judge-at-top`)

**New flow:**
1. Slam to vertical center (`judgeSlamCenter`) — unchanged
2. Votes revealed — unchanged (1500ms reading pause)
3. Retreat to **beside winner name** using winner-aware animation:
   - Determine side from `msg.message` (0 = left wins, 1 = right wins)
   - Left wins → new class `judge-rest-right`: panel inner aligns `justify-content: flex-end`, transforms to `translateY(72vh) scale(0.78)` with `padding-right: 5vw`
   - Right wins → new class `judge-rest-left`: `justify-content: flex-start`, `translateY(72vh) scale(0.78)` with `padding-left: 5vw`
   - Animation duration: ~500ms, `cubic-bezier(0.34, 1.1, 0.64, 1)` (slight bounce)
4. `currentWinner` set → winner panels expand — unchanged timing

**New reactive state:** `judgeRestSide` ref (`'' | 'left' | 'right'`) — set alongside `currentWinner`

**Exit animation (in `updateBattlePair`):**
- Before resetting `judgeAnim`, if `judgeRestSide` is set:
  - Apply `judge-exit-right` or `judge-exit-left` class (~300ms fade + slide off same edge)
  - Wait 300ms, then proceed with existing cleanup (reset all refs)
- `judgeRestSide` cleared to `''` during the same reset block

**`judgePanelClass` computed update:**
```js
const judgePanelClass = computed(() => {
  if (isSmoke.value) return 'smoke-judge-always-on'
  if (judgeRestSide.value === 'right') return 'judge-rest-right'
  if (judgeRestSide.value === 'left')  return 'judge-rest-left'
  return judgeAnim.value
})
```

---

## 3. BattleControl.vue — Logo Upload UI

New "Event Logo" section in BattleControl (alongside existing overlay config controls):

- File picker (`accept="image/*"`) + upload button
- Preview of current logo (if set) — small `<img>` showing `overlayConfig.logoUrl`
- **Delete button** alongside the preview — calls `DELETE /battle/logo`, clears logo on all views
- On successful upload: `overlayConfig.logoUrl` updated locally + broadcast via WS

---

## 4. Backend Changes

### Migration

`V34__add_logo_url.sql`:
```sql
ALTER TABLE battle_genre_state ADD COLUMN logo_url VARCHAR(512);
```

### Entity & DTO

- Add `logoUrl` (String, nullable) to `BattleGenreState` entity
- Add `logoUrl` to `OverlayConfigDto` (request and response)

### Service

- `getOverlayConfig()` — include `logoUrl` in returned DTO
- `setOverlayConfig()` — include `logoUrl` in update + persist + WS broadcast
- New `uploadLogo(MultipartFile)` — save file (same storage as battler images), return URL, call `setOverlayConfig` with new `logoUrl`
- New `deleteLogo()` — set `logoUrl = null`, call `setOverlayConfig`

### Controller (`BattleController`)

- `POST /battle/logo-upload` — `@PreAuthorize("ADMIN or ORGANISER")`, delegates to `uploadLogo()`
- `DELETE /battle/logo` — `@PreAuthorize("ADMIN or ORGANISER")`, delegates to `deleteLogo()`
- Existing `POST /battle/overlay-config` already handles `logoUrl` once DTO is updated

### Frontend API (`api.js`)

- `uploadBattleLogo(eventName, file)` — `POST /battle/logo-upload`
- `deleteBattleLogo(eventName)` — `DELETE /battle/logo`

---

## Implementation Order (bottom-up)

1. DB migration (`V34`)
2. Entity → DTO → service → controller (logo CRUD)
3. `api.js` — two new functions
4. `BattleControl.vue` — logo upload/delete UI
5. `BracketVisualization.vue` — remove header + ticker, add logo banner, rework slot colors
6. `BattleOverlay.vue` — background, logo watermark, timer reposition, judge panel rework

---

## Out of Scope

- Logo upload for battler photos (unchanged)
- Chart.vue (unchanged — already has its own background)
- BattleJudge.vue (unchanged)
- Any changes to scoring logic or bracket data structures
