# AuditionList UI/UX Revamp — Design Spec

**Date:** 2026-05-29  
**Scope:** `AuditionList.vue`, `SwipeableCardsV2.vue`, `PairScoreCards.vue`, `EmceeRoundView.vue`, `Timer.vue`  
**Status:** Approved

---

## 1. Goal

Improve visual boldness, mobile/tablet responsiveness (portrait + landscape), and clarity of purpose for both Judge and Emcee roles — without changing any existing business logic or data flow.

---

## 2. Design Language

Inspired by `BattleOverlay.vue` and `BracketVisualization.vue`:

| Token | Value |
|-------|-------|
| Background | `#060818` (deep near-black) |
| Surface card | `#0d1225` |
| Border | `rgba(255,255,255,0.08)` |
| Judge accent | `#f59e0b` (gold) — never red |
| Emcee accent | `#ffffff` (white) |
| Body font | Inter |
| Display/number font | Anton SC (`font-anton`) |
| Heading font | Outfit (`font-heading`) |

**Parallelogram buttons:** `clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%)` for keypad digit buttons — same skew aesthetic as BattleOverlay.

---

## 3. Judge View — SwipeableCardsV2 (SOLO mode)

### 3.1 Card Structure

Each card = one participant. Cards scroll horizontally with `scroll-snap-type: x mandatory`.

**Portrait layout (single column):**

The context bar is rendered by `AuditionList.vue` above the `<SwipeableCardsV2>` component (see Section 6). The card starts from the participant header:

```
── AuditionList renders ──────────────────
  BES 2026 · Hip-Hop · SOLO · Judge  [⚙]  ← context bar (collapsed filter)
── SwipeableCardsV2 renders ─────────────
┌─────────────────────────────┐
│ #04  JORDAN SMITH           │  ← Anton SC audition number + stage name
│ Genre · Rd 4/12             │  ← sub-info
│ ● ● ○ ○ ○  (swipe dots)     │
├─────────────────────────────┤
│ SCORE  7 . 5                │  ← large Anton SC score display
├─────────────────────────────┤
│  [1][2][3]  ← gold parallelogram digit buttons
│  [4][5][6]
│  [7][8][9]
│     [0]
├─────────────────────────────┤
│ [Reset]  [Submit ✓]  [Jump] │  ← sticky bottom bar
└─────────────────────────────┘
```

**Landscape layout (split pane):**
```
┌──────────────────┬──────────────┐
│ #04              │  [1][2][3]   │
│ JORDAN SMITH     │  [4][5][6]   │
│ Genre · Rd 4/12  │  [7][8][9]   │
│                  │     [0]      │
│ SCORE  7 . 5     │              │
│                  │ [Feedback]   │
│ [feedback tags]  │              │
└──────────────────┴──────────────┘
│ [Reset]     [Submit ✓]    [Jump]│  ← sticky bottom bar spans full width
```

### 3.2 Multi-Criteria Mode

When `judgingMode === 'CUSTOM'`, a tab row replaces the single score display:

```
[ Technique ] [ Musicality ] [ Performance ]
              ↑ active tab
SCORE  7 . 5  (for active criterion)
```

- Tabs styled with gold underline for active criterion
- Aggregate shown in the bottom bar: `AVG 7.2`

### 3.3 Decimal Input

Whole number keypad and decimal keypad remain as-is functionally. Styling updated to gold parallelogram buttons.

### 3.4 Feedback Button

Inline on card (portrait: below score display; landscape: in left panel). A green dot `●` appears on the button after feedback has been submitted for that participant.

### 3.5 Sticky Bottom Bar

Always visible, three actions:
- **Reset** — clears current score (existing behavior)
- **Submit ✓** — submits score, advances to next card (existing behavior)  
- **Jump** — scrolls to first unscored participant

---

## 4. Judge View — PairScoreCards (PAIR mode)

Pair = two participants audition together. They receive **individual scores**. No VS language anywhere.

### 4.1 Selector Tab Pattern (replaces stacked scroll)

```
┌──────────────────────────────┐
│ [#03 ALEX CHEN ●] [#04 MARIA]│  ← two tabs, tap to switch active
├──────────────────────────────┤
│ SCORE  7 . 5  (active)       │  ← score belongs to active tab's participant
├──────────────────────────────┤
│  [1][2][3]
│  [4][5][6]
│  [7][8][9]
│     [0]
├──────────────────────────────┤
│ [Feedback]                   │  ← applies to active participant
├──────────────────────────────┤
│ [Reset]  [Submit ✓]  [Jump]  │
└──────────────────────────────┘
```

**Green dot `●`** on a tab = feedback already given for that participant.

**Active tab** = gold underline + slightly brighter text. Inactive = muted.

**No scrolling in portrait or landscape.** Both participants are always reachable by tapping their tab.

### 4.2 Landscape Layout

Same as SOLO landscape but tabs appear in the left panel above the score display.

```
┌──────────────────┬──────────────┐
│ [#03 ●] [#04]    │  [1][2][3]   │
│                  │  [4][5][6]   │
│ SCORE  7 . 5     │  [7][8][9]   │
│ ALEX CHEN        │     [0]      │
│                  │ [Feedback]   │
└──────────────────┴──────────────┘
│ [Reset]     [Submit ✓]    [Jump]│
```

---

## 5. Emcee View — EmceeRoundView

### 5.1 Reading Order (bottom to top)

All layouts share the same reading order: most urgent at bottom, fading upward.

```
Round+4  #08 SAM PARK        ← faded, low opacity
Round+3  #07 ALEX WU         ← faded
Round+2  #06 MAYA REYES      ← faded
Round+3  #05 KAI TANAKA  UP NEXT ← slightly brighter
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
● NOW ON STAGE   Rd 4 / 12
  #04  JORDAN SMITH          ← full brightness, Anton SC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  38              ← Anton SC timer number, very large
  OF 60S
  [↑Up] [30] [60] [90]       ← preset bar
```

Implementation: `flex-direction: column-reverse` on the queue container. The DOM order is `[Round+4, Round+3, Round+2, Up Next]` — reversed visually so Up Next renders immediately above NOW.

### 5.2 Navigation

Swipe left/right to move between rounds (existing `onTouchStart/Move/End` logic, 60px threshold). **No Prev/Next buttons** — swipe only.

### 5.3 Judge Name

Only rendered when `slot.judgeName` is non-null/non-empty. No placeholder shown.

### 5.4 PAIR Mode

Pair slots show two names linked by `&`:

```
● NOW ON STAGE   Rd 2 / 6
  #03  ALEX CHEN
    &
  #04  MARIA LEE
```

Queue rows also show both names per pair slot.

### 5.5 Landscape Layout

```
┌──────────────┬─────────────────────────┐
│              │  Round+3  #07 ALEX WU   │ ← faded queue, bottom-to-top
│   38         │  Round+2  #06 MAYA      │
│   OF 60S     │  UP NEXT  #05 KAI   ▲  │
│              ├─────────────────────────┤
│ [↑Up][30]    │ ● NOW  Rd 4/12          │
│ [60] [90]    │   #04  JORDAN SMITH     │
│              │   ← swipe →             │
└──────────────┴─────────────────────────┘
```

Left column: Anton SC timer + presets. Right column: queue (bottom-to-top within column) + NOW card.

### 5.6 Timer States

| State | Visual |
|-------|--------|
| Running | White Anton SC number |
| ≤5s remaining | Red pulse animation on number |
| Finished | Number shows 0, flash effect |
| Count-up (↑Up) | Green tint on number |

---

## 6. AuditionList.vue — Filter Panel Behaviour

The existing filter panel (genre, judging mode, role filters) collapses to a **slim context bar** as soon as the scoring component is visible (i.e., genre and role are both selected and cards/emcee view are rendered):

```
BES 2026 · Hip-Hop · SOLO · Judge         [⚙ Filters]
```

Trigger: a `hasActiveSession` computed ref — `true` when `selectedGenre` and `userRole` are both set. When `true`, render the slim bar instead of the full panel. Tapping `[⚙ Filters]` toggles the panel open temporarily (overlay/drawer), then collapses again on close.

Filter panel remains fully expanded on first load before genre/role are selected (existing behavior).

---

## 7. What Does NOT Change

- All API calls, WebSocket subscriptions, and scoring logic in `AuditionList.vue`
- `computeAggregate()` weighted scoring in `SwipeableCardsV2`
- `feedbackGiven` Map tracking in `AuditionList`
- `IntersectionObserver` for `currentIndex` in `SwipeableCardsV2`
- `isActive(pairIdx)` gating in `PairScoreCards`
- Touch swipe detection in `EmceeRoundView`
- Timer countdown/count-up logic in `Timer.vue`
- All existing props/emits interfaces — only internal templates and styles change

---

## 8. Files Changed

| File | Change |
|------|--------|
| `SwipeableCardsV2.vue` | Full template + style redesign (gold accent, Anton SC, parallelogram buttons, landscape split-pane, sticky bottom bar, context bar) |
| `PairScoreCards.vue` | Replace stacked cards with selector tab pattern; feedback dot on tabs; landscape split-pane |
| `EmceeRoundView.vue` | Column-reverse queue, remove Prev/Next buttons, judge name conditional, PAIR `&` display |
| `Timer.vue` | Anton SC number, timer moved to bottom of EmceeRoundView (no structural change to Timer itself — placement change in EmceeRoundView template) |
| `AuditionList.vue` | Filter panel → slim context bar toggle during scoring |
