# BattleJudge Redesign

**Date:** 2026-05-27
**File:** `BES-frontend/src/views/BattleJudge.vue`
**Approach:** Option 2 — Full restructure (single-file rebuild)

---

## Goal

Rebuild `BattleJudge.vue` with a bold, impactful UI that:
- Matches `BattleOverlay.vue`'s dark design language
- Uses organiser-configured left/right colors from overlay config
- Gives judges a clear, persistent view of their own vote
- Works well on phones and tablets
- Survives page refresh without losing judge identity or vote state

---

## Layout

Portrait-first, full-height (`100dvh`). Four stacked regions:

```
┌─────────────────────────────────┐
│  HEADER                          │  ~56px
│  brand · phase pill · judge chip │
├─────────────────────────────────┤
│  NAMES BAR                       │  ~10% height
│  [LEFT NAME]    [RIGHT NAME]     │
├──────────────┬──────────────────┤
│              │                  │
│   LEFT ✊    │    RIGHT ✊      │  flex: 1 (~65%)
│              │                  │
├──────────────┴──────────────────┤
│         — TIE ✋ —              │  ~20% height
└─────────────────────────────────┘
```

- LEFT and RIGHT panels: `flex: 1` each, side by side
- TIE: full-width, fixed `~20%` of remaining height — a real button, not a strip
- No horizontal scrolling; no vertical scrolling

---

## Visual Language

Matches `BattleOverlay.vue`:

- **Background:** `#060a14`
- **Font:** Anton SC for direction labels (LEFT / RIGHT / TIE), Inter for everything else
- **LEFT panel:** radial gradient using `--left-color`; border glow on active/voted
- **RIGHT panel:** radial gradient using `--right-color`; border glow on active/voted
- **TIE panel:** neutral slate — never colored
- **Color bleeds:** subtle radial gradients in bottom corners (left-color bottom-left, right-color bottom-right)
- **No scanlines** — those are stream overlay only, not appropriate for a touch interface

---

## Colors — Overlay Config

On mount:
1. Call `getOverlayConfig()` → `{ showImages, leftColor, rightColor }`
2. Subscribe to `/topic/battle/overlay-config` WS for live updates

Apply as CSS custom properties on `.judge-root`:
```css
--left-color: <leftColor from config>   /* default: #dc2626 */
--right-color: <rightColor from config> /* default: #2563eb */
```

Colors affect: panel gradients, border glows, name bar tints, voted gradient wash, color bleeds, winner banner tint. TIE is always neutral.

---

## Judge Identity

### First load (no judge stored in localStorage)
- Bottom sheet slides up from the bottom over the vote panels
- Contains a 2-column grid of judge name chips (one per active judge)
- Tapping a name: dismisses sheet, saves to localStorage, shows judge chip in header
- localStorage keys: `battleJudgeId` (number), `battleJudgeName` (string)

### On refresh / revisit
- On mount, read `battleJudgeId` from localStorage
- If found: skip sheet, go straight to the voting screen with judge chip in header
- Cross-check: if stored ID is no longer in the active judges list, clear localStorage and re-show sheet

### Header judge chip
- Small pill: `VOTING AS: DIANA  ✕`
- Tap ✕ → clears localStorage, re-shows bottom sheet

---

## Vote Flow

### Tap-to-confirm (unchanged from current logic)
1. First tap → panel "arms" (pulses, shows "TAP AGAIN TO CONFIRM")
2. Second tap on same panel → vote confirmed, calls `battleJudgeVote(judgeId, side)`
3. Tapping a different panel while one is armed → arms the new one instead

### After confirming
- Voted panel: full glow + gradient wash floods bottom of panel + "✓ VOTE LOCKED" text
- Other panels: dimmed (low opacity, desaturated)
- TIE: dimmed if not voted, same glow/wash treatment if voted
- State persists until phase transitions to `LOCKED`

### Haptic feedback
- On confirm: `navigator.vibrate(40)` if supported — brief tactile acknowledgement

---

## Vote Persistence (Refresh-Proof)

### Writing
On vote confirm, write to localStorage:
```
key:   battleVote_${judgeId}_${leftName}_${rightName}
value: { vote: 0|1|-1, leftName, rightName, timestamp }
```

### Restoring on mount
1. Restore judge identity from localStorage
2. Subscribe to `/topic/battle/vote/${judgeId}` WS — backend value is source of truth
3. Also check localStorage for a matching vote key (same judgeId + leftName + rightName)
4. If backend vote is set (not `-3`): restore voted visual state from backend value
5. If backend is `-3` but localStorage has a match: restore from localStorage (backend may not have processed yet)
6. If mismatch: trust backend

### Clearing
- Vote localStorage key is cleared when phase transitions to `LOCKED` (new pair starting)
- Judge identity localStorage is **not** cleared on phase change — persists across rounds

---

## Phase States

| Phase | UI |
|-------|----|
| **IDLE** | Panels dimmed. Bottom sheet shown if no judge stored. Phase pill: "WAITING". No vote possible. |
| **LOCKED** | Panels visible but locked — semi-transparent overlay: "🔒 VOTING NOT OPEN". Vote from previous round cleared. Phase pill: amber "LOCKED". |
| **VOTING** | Panels fully active. If no vote: ready to tap. If voted (or restored from refresh): gradient wash + "✓ VOTE LOCKED". Phase pill: cyan pulsing "VOTING". |
| **REVEALED** | Winner banner slides up full-width. Shows winner name large + "WINS" (or "TIE"). Voted panel stays lit. Panels non-interactive. Phase pill: green "REVEALED". |

---

## REVEALED State Detail

- Full-width banner animates up from the bottom (slide + fade)
- Banner background: tinted with winning side's color (or neutral for tie)
- Content:
  - Winner: large name text + "WINS" label below
  - Tie: "TIE" centered
- Judge's voted panel remains glowing beneath the banner
- No interaction possible until phase moves to `LOCKED`

---

## WebSocket Subscriptions

Requires 6 separate clients (existing pattern — one subscription per client):

| Topic | Handler |
|-------|---------|
| `/topic/battle/phase` | Update `battlePhase`; on LOCKED: reset vote state |
| `/topic/battle/battle-pair` | Update `leftName`, `rightName`; clear vote localStorage key |
| `/topic/battle/score` | Update `revealedWinner` |
| `/topic/battle/judges` | Update `battleJudges`; re-show picker if current judge removed |
| `/topic/battle/vote/${judgeId}` | Restore vote state on connect — **subscribed only after judge identity is resolved** (localStorage or sheet selection) |
| `/topic/battle/overlay-config` | Live color updates |

---

## Responsive Behaviour

- **Phone portrait (360–430px wide):** Default layout. Names bar truncates with `text-overflow: ellipsis`.
- **Tablet portrait (768px+):** Panels scale up naturally (flex fills height). Font sizes use `clamp()`. Max width capped at `480px` centered.
- **Tablet landscape:** Layout unchanged — still portrait orientation locked via CSS (`max-height: 100dvh`, `overflow: hidden`).
- Touch targets: LEFT/RIGHT panels are always `≥ 44px` wide; TIE is always `≥ 64px` tall.

---

## Branch Strategy

New branch: `feature/battle-judge-redesign`
- `git fetch origin`
- `git rebase origin/master` before starting
- Single commit per logical unit (identity, vote state, phase states, colors)
- Force-push to remote branch after rebase

When switching to this branch in future: always `git fetch && git rebase origin/master` first.
