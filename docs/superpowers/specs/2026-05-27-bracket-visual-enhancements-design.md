# Bracket Visual Enhancements — Design Spec

**Date:** 2026-05-27
**Branch:** feature/battle-judge-redesign
**Scope:** BracketVisualization.vue, BattleOverlay.vue, BattleControl.vue

---

## Overview

Three layered visual enhancements to the live bracket system:

1. **Color theming** — bracket slots use the overlay config's `leftColor`/`rightColor`, with gold for winners
2. **Advance animation** — bolder, more dramatic animation when a winner moves to the next round slot
3. **Champion reveal** — a triggered full-screen announcement for each genre's champion, dismissible from BattleControl

---

## 1. Color Theming

### Behavior

| Slot state | Visual |
|---|---|
| Active match, left side | `leftColor` from overlay config (background + border + glow) |
| Active match, right side | `rightColor` from overlay config |
| Won this round (result locked) | Gold — `rgba(245,158,11,…)` — regardless of which side they were on |
| Lost this round | Dimmed to ~40% opacity, no strikethrough, no extra label |
| Advancing to next round (before that match is active) | Resets to `leftColor` or `rightColor` based on their new position in the next match |
| Upcoming / not yet seeded | Neutral dark — `rgba(255,255,255,0.04)` border |

### Winner indicator

A small parallelogram **WIN** badge chip rendered to the right of the name, inside the slot. Styled as:
- Background: `rgba(245,158,11,0.25)`
- Border: `1px solid rgba(245,158,11,0.5)`
- Text: `#fbbf24`, `font-family: Inter`, `font-size: 9px`, `font-weight: 900`, `letter-spacing: 0.12em`
- Shape: `clip-path: polygon(3px 0%, 100% 0%, calc(100%-3px) 100%, 0% 100%)` — parallelogram
- Counter-skewed `skewX(5deg)` to sit upright inside the slot's `skewX(-5deg)` transform

### Color source

- `BracketVisualization.vue` subscribes to `/topic/battle/overlay-config` (same as `BattleOverlay.vue`)
- On mount, calls `getOverlayConfig()` to get initial config
- Stores as `overlayConfig = ref({ leftColor: '#dc2626', rightColor: '#2563eb' })`
- CSS custom properties `--left-color` and `--right-color` applied to `.bracket-root` so slot styles can inherit

### Cyan removal

All existing `#06b6d4` cyan in `BracketVisualization.vue` is replaced with white/silver neutral (`rgba(255,255,255,…)`) — ticker labels, round headers, connectors, any accent elements.

---

## 2. Advance Animation

Triggered when `bracketState` updates and a winner advances to a new slot in the next round.

### Sequence

1. **Ring burst on source slot** — an expanding ring `border` animates outward from the winning slot and fades out (`scale(1) → scale(1.6)`, `opacity: 1 → 0`, duration ~400ms). Gold color.
2. **Lightning streak** — a horizontal gold `div` expands left-to-right across the gap between source and destination slots (`width: 0 → full gap`, duration ~180ms). Gradient: `transparent → rgba(245,158,11,0.9) → #fff`.
3. **Streak fades** — streak opacity transitions to 0 (~150ms after reaching full width).
4. **Destination slot ignites** — destination slot transitions to the battler's new position color (left or right config color) with a brief `box-shadow` flash (~400ms settling).

### Implementation notes

- Source and destination slot positions are found via `getBoundingClientRect()` relative to the bracket container
- The ring and streak `div`s are absolutely positioned inside the bracket container, removed after animation completes
- Animation is non-blocking (does not delay state updates)
- If the next round slot is not yet visible (e.g. waiting for the other semi to finish), the animation still plays to the slot's position — the slot just remains "pending" until the other battler is seeded

---

## 3. Champion Reveal

### Trigger flow

1. Organiser clicks **"REVEAL CHAMPION"** for a specific genre in `BattleControl.vue`
2. Backend (or direct WebSocket broadcast) emits a new event: `champion-reveal` on topic `/topic/battle/champion-reveal`
3. Payload: `{ genreId, genreName, championName }`
4. Both `BracketVisualization.vue` and `BattleOverlay.vue` subscribe to this topic and show the reveal animation
5. Organiser clicks **"DISMISS"** (same area in BattleControl) — emits `champion-dismiss` on `/topic/battle/champion-reveal` with `{ dismiss: true }`
6. Both screens clear the overlay; bracket is fully visible again with the final match showing the WIN badge on the champion's slot

### Animation (Option 1 — Full-screen name slam)

Displayed as an absolutely-positioned overlay over the bracket/overlay content:

1. **Background darkens** — overlay fades in (`opacity: 0 → 1`, 300ms), background `#060818` with a radial gold glow `radial-gradient(ellipse 60% 50% at 50% 55%, rgba(245,158,11,0.12) 0%, transparent 70%)`
2. **Genre label** — appears top-center, fade + slide up (`opacity: 0 → 1, translateY(10px) → 0`, delay 100ms)
3. **"CHAMPION" label** — gold tint, fade + slide up, delay 250ms
4. **Name slams in** — Anton SC, large (~52px on bracket, larger on overlay), `scale(0.7) translateY(20px) → scale(1) translateY(0)` with `cubic-bezier(0.175, 0.885, 0.32, 1.275)` (overshoot bounce), delay 400ms. White text with `text-shadow: 0 0 40px rgba(245,158,11,0.6), 0 0 80px rgba(245,158,11,0.3)`
5. **Gold underbar** — a thin horizontal line expands to 200px width beneath the name, delay 700ms

### Multi-genre workflow

- Each genre has its own "REVEAL CHAMPION" / "DISMISS" button in BattleControl
- Organiser can run all finals first, then reveal champions one by one at the end
- The bracket stores the winner in `bracketState` (localStorage + backend) — no re-voting required; reveal is a pure display trigger
- Only one genre reveal can be active at a time — triggering a second genre's reveal automatically dismisses the previous one

### BattleControl UI change

In the genre list within BattleControl, each genre row that has a completed final match gets:
- **"REVEAL CHAMPION"** button — enabled only when the final match has a winner
- Changes to **"DISMISS"** while a reveal is active for that genre
- Button style: same as other BattleControl action buttons (dark pill, not a prominent CTA — this is a behind-the-scenes control)

### New WebSocket event

| Direction | Topic | Payload |
|---|---|---|
| Server → clients | `/topic/battle/champion-reveal` | `{ genreId, genreName, championName, dismiss: false }` |
| Server → clients | `/topic/battle/champion-reveal` | `{ dismiss: true }` |

Backend: new endpoint `POST /api/v1/battle/champion-reveal` accepting `{ genreId, dismiss }`. Backend looks up the winner from bracket state if `dismiss: false`, broadcasts the event. No persistence needed — purely ephemeral display state.

---

## Files Changed

| File | Change |
|---|---|
| `BES-frontend/src/views/BracketVisualization.vue` | Overlay config subscription, color theming, cyan removal, advance animation, champion reveal overlay |
| `BES-frontend/src/views/BattleOverlay.vue` | Champion reveal overlay (subscribe + animation) |
| `BES-frontend/src/views/BattleControl.vue` | "REVEAL CHAMPION" / "DISMISS" buttons per genre |
| `BES-frontend/src/utils/api.js` | `revealChampion(genreId)` and `dismissChampion()` fetch functions |
| `BES/src/main/java/com/example/BES/controllers/BattleController.java` | New `POST /api/v1/battle/champion-reveal` endpoint |
| `BES/src/main/java/com/example/BES/services/` | Logic to look up bracket winner and broadcast via WebSocket |

---

## Out of Scope

- No changes to voting logic, bracket seeding, or match state machine
- No database schema changes (champion reveal is ephemeral)
- No changes to BattleJudge, Chart, or other battle views
