# Documentation Task: Write Plans

---

## File 1: FEATURE_5_BRACKET_VISUALIZATION.md

### Content to write:

```markdown
# Feature 5: Bracket Visualization — Implementation Plan

## Context
BattleControl.vue manages the full bracket lifecycle (seeding, match progression,
winner advancement) but state only lives in the organiser's browser localStorage.
Spectators, participants, and emcees have no way to see the bracket. This adds a
live public view at /battle/bracket that mirrors BattleControl in real time via WebSocket.

## Architecture
BattleControl.vue → POST /api/v1/battle/bracket → BattleService (in-memory)
                                                         ↓ broadcast
                                              /topic/battle/bracket
                                                         ↓
                                          BracketVisualization.vue (public, no login)

## Backend Changes
1. New DTO: dtos/battle/SetBracketStateDto.java — fields: topSize (String), rounds (Object)
2. BattleService — add bracketState field, getBracketState(), setBracketStateService(dto)
   broadcasts to /topic/battle/bracket
3. BattleController — GET /api/v1/battle/bracket (for page refresh) + POST (stores + broadcasts)

## Frontend Changes
4. api.js — add setBracketState(rounds, topSize), getBracketState()
5. BattleControl.vue — add broadcastBracket() helper; call after:
   - setWinner()
   - clearWinner()
   - updateMatch()
   - applyToFirstRound()
   - clearLocalStorage()
6. router/index.js — add public route /battle/bracket → BracketVisualization.vue
7. BracketVisualization.vue (NEW) — public page, subscribes to:
   - /topic/battle/bracket       → re-render full bracket
   - /topic/battle/battle-pair   → highlight active match (cyan glow)
   - /topic/battle/score         → trigger winner animation

## BracketVisualization UI Design
- Dark background: bg-surface-900
- Horizontal tree: Top{N} → Top{N/2} → ... → Top2 (Final), left to right
- Each column = one round; each card: [left] vs [right]
- Active match: border-primary-500 shadow-lg shadow-primary-500/20 (cyan glow)
- Winner name: text-primary-500 font-bold | Loser: text-surface-500 line-through
- Winner animation: scale-up burst on name → 800ms → winner slides into next slot
  (Vue <Transition name="slide-in"> on new slot entry)
- Connector lines: thin SVG/CSS lines between rounds (border-surface-600)
- Smoke mode (topSize=7): queue list view with scores, not tree
- On load: call getBracketState() REST as fallback before WS message

## Verification
1. Seed bracket in BattleControl → open /battle/bracket in another tab → names visible
2. Click "Initiate Battle" → bracket page highlights that match in cyan
3. Get Score → winner animates, advances to next round; highlight shifts on "Next Pair"
4. Open on separate phone → same live state
5. Refresh /battle/bracket → loads via REST fallback
6. Smoke mode → queue view shown
```

---

## File 2: FEATURE_6_BATTLE_FEEDBACK.md

### Content to write:

```markdown
# Feature 6: Battle Feedback — Implementation Plan

## Context
BattleJudge.vue handles voting only. After a winner is announced, judges have
no structured way to leave feedback for either battler. This adds a popup feedback
panel triggered by the winner announcement, reusing the FeedbackTag system.

## Problem
- BattleJudge.vue cannot see the current pair (no /topic/battle/battle-pair subscription)
- No post-battle feedback mechanism exists
- Feedback model (AuditionFeedback) exists but is tied to EventGenreParticipant (audition context)
- Battles are in-memory (battler names, no EGP FK) — need a separate BattleFeedback entity

## Proposed UX
When /topic/battle/score fires with winner (0 or 1):
1. BattleJudge.vue shows a full-screen feedback popup
2. Popup has two sections: Winner (green border) + Loser (neutral border)
3. Each section: battler name + FeedbackTag chips grouped by FeedbackTagGroup
4. Judge taps 1–3 tags per section (optional)
5. Optional free-text note per section
6. "Submit Feedback" → POST both sections; popup closes
7. "Skip" → popup closes, nothing saved; score/vote already persisted

## Current Pair Visibility Fix (prerequisite)
- BattleJudge.vue must subscribe to /topic/battle/battle-pair on mount
- Store leftName / rightName in state
- Display subtly in header: "NOW: [Left] vs [Right]"

## Data Model: BattleFeedback (new entity)
BattleFeedback:
  id            Long (PK, auto)
  judge         Judge (FK → judge)
  battlerName   String   ← store name string (in-memory battles have no EGP FK)
  battleResult  String   ← "winner" or "loser"
  tags          Set<FeedbackTag> (ManyToMany → battle_feedback_tag join table)
  note          String (nullable)
  createdAt     LocalDateTime

DB Migration (V{next}__add_battle_feedback.sql):
  CREATE TABLE battle_feedback (
    id            BIGSERIAL PRIMARY KEY,
    judge_id      BIGINT NOT NULL REFERENCES judge(judge_id),
    battler_name  VARCHAR(255) NOT NULL,
    battle_result VARCHAR(50),
    note          TEXT,
    created_at    TIMESTAMP
  );
  CREATE TABLE battle_feedback_tag (
    feedback_id BIGINT REFERENCES battle_feedback(id),
    tag_id      BIGINT REFERENCES feedback_tag(id),
    PRIMARY KEY (feedback_id, tag_id)
  );

## Backend Changes
1. New model: BattleFeedback.java
2. New repo: BattleFeedbackRepo.java
3. New DTOs: SubmitBattleFeedbackDto (judgeId, battlerName, battleResult, tagIds[], note)
             GetBattleFeedbackDto
4. New service: BattleFeedbackService.java — submitFeedback(dto), getFeedbackByBattler(name)
5. BattleController — add POST /api/v1/battle/feedback, GET /api/v1/battle/feedback?battler={name}

## Frontend Changes
6. api.js — add submitBattleFeedback(dto), reuse existing endpoint for FeedbackTagGroups
7. BattleJudge.vue:
   - Subscribe to /topic/battle/battle-pair → store leftName / rightName
   - Subscribe to /topic/battle/score → when winner 0 or 1 → show feedback popup
   - Render FeedbackTagGroup chips in popup
   - On submit → POST /api/v1/battle/feedback for both battlers
   - On skip → dismiss popup

## Verification
1. BattleControl announces winner → BattleJudge popup appears immediately
2. Popup shows winner (green border) + loser with correct names
3. FeedbackTagGroup chips render; multiple selectable
4. Submit → rows in battle_feedback + battle_feedback_tag tables
5. Skip → popup closes, no rows inserted
6. Battler name pair visible in BattleJudge header during battle
```

---

## File 3: BATTLE_FLOW_ROBUSTNESS.md

### Content to write:

```markdown
# Battle Flow Robustness — Implementation Plan

## Context
The current battle flow (BattleControl → judge votes in BattleJudge → reveal score)
works if handled with care but has no guardrails. Problems:
- Judges have no signal for when voting is open — can accidentally vote early or late
- BattleJudge voting buttons are always active (no phase-gating)
- "Get Score" and "Reset Bracket" can be accidentally pressed
- No clear phase display for Emcee / anyone watching the control interface
- After a tie the re-initiation is not clearly communicated to judges

## Goal
Add a battle phase state machine that gates every interface. The organiser (BattleControl)
controls phase transitions explicitly. Each interface (BattleJudge, BattleOverlay) reacts
to phase changes automatically — zero coordination needed by voice.

## Phase State Machine

LOCKED → VOTING → REVEALED → LOCKED (next pair)
   ↑                              |
   └──────── Reset Bracket ───────┘ (→ IDLE)

| Phase    | Who sees what |
|----------|---------------|
| IDLE     | Setup/bracket building phase. All voting locked |
| LOCKED   | Pair announced. Overlay shows battler names. Judges see "NEXT UP: A vs B" but cannot vote |
| VOTING   | Organiser opened voting. Judges can vote. BattleControl shows per-judge vote status |
| REVEALED | Score revealed. Winner shown. Judges locked. Organiser sees "Next Pair" button only |

## Phase Transitions (all triggered from BattleControl)
- "Start Match" (initiateBattlePair) → automatically sets phase to LOCKED
- New "Open Voting" button → sets phase to VOTING (replaces current always-available voting)
- "Get Score" → available only in VOTING; on success → auto-sets phase to REVEALED
- "Next Pair" → available only in REVEALED; on click → sets phase to LOCKED for next pair
- Tie result → phase stays VOTING (judge re-votes; "Rematch" is just re-opening voting)
- "Reset Bracket" → requires confirmation dialog → sets phase to IDLE

## Backend Changes
1. BattleService — add battlePhase field (String: "IDLE"/"LOCKED"/"VOTING"/"REVEALED")
   Default: "IDLE"
   Auto-transitions:
     - setBattlerPair() called → force phase to "LOCKED" + broadcast
     - setScoreService() returns winner (0 or 1) → force phase to "REVEALED" + broadcast
     - setScoreService() returns -1 (tie) → keep VOTING, broadcast special tie message
2. New endpoint: POST /api/v1/battle/phase { phase: "VOTING" | "LOCKED" | "IDLE" }
   (REVEALED is only set by the backend on score reveal — not manually settable)
3. GET /api/v1/battle/phase → returns current phase (for reconnect)
4. All phase changes broadcast to /topic/battle/phase

## Frontend Changes — BattleControl.vue
5. Subscribe to /topic/battle/phase on mount; store in battlePhase ref
6. On mount: GET /api/v1/battle/phase to hydrate on refresh
7. Button visibility rules:
   - "Open Voting" button: visible only when battlePhase === 'LOCKED'
     → calls POST /api/v1/battle/phase { phase: 'VOTING' }
   - "Get Score" button: visible only when battlePhase === 'VOTING'
   - "Next Pair" button: visible only when battlePhase === 'REVEALED'
   - "Previous" button: visible only when battlePhase === 'REVEALED' (allows backing up)
   - "Start Round" (initiateBattlePair): always available when not in VOTING/REVEALED
8. "Reset Bracket" → requires confirmation modal before proceeding; sets phase to IDLE
9. Phase status badge in "Live Match" card header (colored pill: LOCKED/VOTING/REVEALED)

## Frontend Changes — BattleJudge.vue
10. Subscribe to /topic/battle/phase on mount; store in battlePhase ref
11. Subscribe to /topic/battle/battle-pair on mount; store leftName/rightName
12. On mount: GET /api/v1/battle/phase + GET /api/v1/battle/battle-pair for reconnect
13. Phase-gated UI:
    - IDLE: Full-screen overlay "Waiting for battle setup..."
      (dark, centered text, no voting buttons visible)
    - LOCKED: Show battler names prominently ("NEXT UP: A vs B")
      Buttons visible but disabled with lock icon + "Waiting for voting to open"
    - VOTING: Full interaction. Buttons active. Pulse animation on buttons.
      Shows "VOTING OPEN" status chip. Current pair names shown.
    - REVEALED: Buttons disabled. Winner shown ("A WINS" banner).
      If this judge's vote matched winner → "✓ Correct call" indicator.
14. Tie handling: phase stays VOTING, overlay shows "TIE — revote"

## Frontend Changes — BattleOverlay.vue (stream display)
15. Subscribe to /topic/battle/phase
16. LOCKED: Show battler intro animation ("NEXT UP" card)
17. VOTING: Show the normal judge panel (slide down as it does now)
18. REVEALED: Show winner + score; hide judge vote indicators after 3s

## Recoverable Actions
- Organiser accidentally clicks "Get Score" before judges voted:
  Backend check: if any judge vote === -3 (default) → return -3, phase stays VOTING
- Organiser needs to undo a winner: "clearWinner" available in bracket view (already exists)
- Organiser needs to re-open voting: only possible if phase === LOCKED
  (can re-initiate same pair to go back to LOCKED, then Open Voting again)
- Wrong pair started: while in LOCKED phase, operator can still click a different
  "Start Round" to reset to a new pair → goes back to LOCKED with new names

## New WebSocket Topic
/topic/battle/phase → { phase: "IDLE" | "LOCKED" | "VOTING" | "REVEALED" }

## Verification
1. Open BattleControl + BattleJudge side by side
2. Click "Start Round" → BattleJudge shows names + "Waiting for voting to open"
   (buttons visible but disabled)
3. Click "Open Voting" → BattleJudge buttons activate with pulse; VOTING chip shown
4. Judge votes in BattleJudge → vote status visible in BattleControl
5. Click "Get Score" → winner revealed; BattleJudge locks; BattleControl shows "Next Pair"
6. Tie: BattleJudge shows "TIE — revote"; buttons stay active; no "Next Pair" visible
7. Click "Reset Bracket" → confirmation modal appears; cancelling does nothing
8. Refresh BattleJudge mid-battle → phase and pair names restored via REST
```
