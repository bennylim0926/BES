# 7-to-Smoke Overlay Redesign

## Goal

Redesign the 7-to-Smoke overlay (`Chart.vue` / `BattleOverlay.vue` smoke mode) to match the boldness and visual style of the existing 1v1 battle overlay — same fonts, color system, judge card UI, and animation language.

---

## Scope

Two files are involved:
- `BES-frontend/src/views/Chart.vue` — the `/battle/chart` public view (OBS source or projector)
- `BES-frontend/src/views/BattleOverlay.vue` — the smoke-mode section within the 1v1 overlay (conditionally rendered when `overlayMode === 'smoke'`)

Both share the same visual design. The implementation lives primarily in `Chart.vue`.

---

## Design Decisions

### Layout

**Option C — Floating pill.** No separate bottom strip. A frosted-glass pill is absolutely positioned at the bottom-center of the chart area. It shows `LEFT NAME VS RIGHT NAME · X PTS · Y PTS`. The chart fills the full height.

### Bar Chart

- One vertical bar per participant, growing from the bottom.
- Bar height = `(points / 7) * 100%` of the bar-wrap height.
- **Active left** (position 0 in queue): red gradient using `--left-color`; glow box-shadow.
- **Active right** (position 1 in queue): blue gradient using `--right-color`; glow box-shadow.
- **Waiting** (all others): neutral gray `#181818 → #2a2a2a`, no glow.
- VS chip between the active pair columns.
- Gap/spacer between active pair and rest of queue.
- 7 score dots per fighter beneath the bar; filled dot = point earned. Active dots use team color, waiting dots use muted white.
- Name label below dots. Active names use team color with glow-only text-shadow `0 0 12px color-mix(in srgb, var(--color) 80%, transparent)`. No hard pixel shadow at this size.

### Floating Pill

```
[ VIBRAZE  VS  KRAZIX  ·  3 PTS · 4 PTS ]
```

- `position: absolute; bottom: 10px; left: 50%; transform: translateX(-50%)`
- `background: rgba(0,0,0,0.65); backdrop-filter: blur(10px); border-radius: 99px`
- Names use `--left-color` / `--right-color` mix
- Anton SC font for names and VS; Inter for PTS label

### Color System

Identical to the existing 1v1 overlay. CSS custom properties on the overlay root:

```css
--left-color: #dc2626;   /* default red */
--right-color: #2563eb;  /* default blue */
```

Driven by `overlayConfig` from `BattleControl.vue` via WebSocket (same mechanism as 1v1).

### Queue Order

Left-to-right column order mirrors `rounds` array from BattleControl. Index 0 = active left, index 1 = active right, index 2+ = waiting queue in order.

### Terminology

- UI text: "PTS" not "SMOKE" (e.g. `3 PTS · 4 PTS`, `TAKES THE POINT`)
- Format name stays "7 TO SMOKE" in the header badge

---

## Animations

### FLIP Sliding (queue reorder)

When a match ends and the queue shifts, columns slide to their new positions instead of teleporting:

1. Snapshot `getBoundingClientRect().left` for every `.col` before DOM update.
2. Re-render at new positions with `transition: none`.
3. `requestAnimationFrame` → apply inverse `translateX` to each col (so they appear to still be in old position).
4. Second `requestAnimationFrame` → clear inline transform with `transition: transform 0.55s cubic-bezier(0.4,0,0.2,1)`. Columns animate to new positions.

### Score Pop (point gained)

When a fighter's score increases, their bar plays a bounce + brightness flash:

```css
@keyframes scorePop {
  0%   { transform: scaleY(1) scaleX(1); filter: brightness(1); }
  18%  { transform: scaleY(1.07) scaleX(1.04); filter: brightness(2.5); }
  38%  { transform: scaleY(0.96) scaleX(1.02); filter: brightness(1.5); }
  58%  { transform: scaleY(1.02) scaleX(1.01); filter: brightness(1.1); }
  100% { transform: scaleY(1) scaleX(1); filter: brightness(1); }
}
```

A radial glow burst (`glowBurst` keyframe) also radiates from the bar top using the team color.

### Bar Height Transition

`transition: height 0.6s cubic-bezier(0.22, 1, 0.36, 1)` on `.bar`. Score pop animation fires after height transition.

---

## Result Overlay

Shown after the organiser announces the result (`REVEALED` phase). Displayed over the chart.

### Win

```
[ VIBRAZE ]          (large Anton SC, team color, hard shadow 4px 4px 0 var(--color) + glow)
TAKES THE POINT      (subtitle, muted)

[ Judge cards ]
```

### Tie

```
IT'S A TIE           (large Anton SC, white)
BOTH EARN A POINT    (subtitle)

[ Judge cards — tie style ]
```

### Judge Card Container

Matches 1v1 `BattleOverlay.vue` exactly:

```css
.judge-inner {
  background: linear-gradient(135deg,
    color-mix(in srgb, var(--left-color) 12%, rgba(6,8,18,0.94)),
    color-mix(in srgb, var(--right-color) 12%, rgba(6,8,18,0.94))
  );
  backdrop-filter: blur(24px);
  border-radius: 14px;
  border: 1px solid rgba(255,255,255,0.12);
  box-shadow: 0 24px 80px rgba(0,0,0,0.75), 0 0 0 1px rgba(255,255,255,0.04) inset;
  padding: 10px 20px 14px;
}
```

### Judge Card (per judge)

Parallelogram shape via `clip-path`:

```css
clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
```

- Voted left: red tint, red border glow
- Voted right: blue tint, blue border glow
- Tie: neutral, no arrows, just dimmed name + `TIE` badge

### Vote Track Bar

Single gradient bar (not pips) showing vote split:

```css
.vote-track { height: 10px; background: rgba(255,255,255,0.1); border-radius: 9999px; }
.fill-left  { background: linear-gradient(90deg, color-mix(in srgb, var(--left-color) 70%, black), var(--left-color)); }
.fill-right { background: linear-gradient(90deg, color-mix(in srgb, var(--right-color) 70%, black), var(--right-color)); }
```

### Direction Arrows

```css
.vote-arrow-left  { clip-path: polygon(100% 0%, 0% 50%, 100% 100%); }
.vote-arrow-right { clip-path: polygon(0% 0%, 100% 50%, 0% 100%); }
```

Lit arrow uses team color + `filter: drop-shadow(0 0 8px var(--color))`.

### Judge Card Entrance Animation

Judge container slams onto screen at 120% scale, then springs to 100% (matching the 1v1 VS chip entrance). After a pause, scales down and moves to the top of the screen.

### Auto-dismiss

Result overlay auto-dismisses after ~4 seconds (same as 1v1).

---

## Champion Overlay

When any fighter reaches 7 points, a full-screen takeover replaces the normal result overlay.

```
[ FIGHTER NAME ]     (champSlam animation — scale in from 2.5x, spring to 1x)
7 TO SMOKE CHAMPION  (subtitle fades in after name)
```

Background: radial glow burst using `--champ-color` (winner's team color).

```css
@keyframes champSlam {
  0%   { transform: scale(2.5) translateY(-20px); opacity: 0; filter: blur(12px); }
  55%  { transform: scale(0.96) translateY(0);   opacity: 1; filter: blur(0); }
  72%  { transform: scale(1.04); }
  85%  { transform: scale(0.99); }
  100% { transform: scale(1); }
}
```

This does NOT auto-dismiss — it persists until the organiser transitions away.

---

## Queue Logic (reference — already correct in BattleControl.vue)

- **Left wins**: winner stays at position 0, loser moves to end of queue.
- **Right wins**: winner moves to position 0, loser moves to end.
- **Tie**: both go to end of queue, next two take over. No score change.

Champion check runs after every score update: `if (score >= 7) → showChampion()`.

---

## Files to Touch

| File | Change |
|------|--------|
| `BES-frontend/src/views/Chart.vue` | Full redesign — new bar chart layout, FLIP animation, score pop, result overlay, champion overlay |
| `BES-frontend/src/views/BattleOverlay.vue` | Smoke mode section: swap old UI for new `<SmokeChart>` component (or inline) |

If Chart.vue grows large, extract reusable pieces:
- `SmokeBarChart.vue` — the bar chart + floating pill
- `SmokeResultOverlay.vue` — win/tie result overlay
- `SmokeChampionOverlay.vue` — champion takeover

---

## Out of Scope

- Backend changes (none needed — uses existing WebSocket state)
- BattleControl.vue logic changes (queue/tie logic is already correct)
- New API endpoints
