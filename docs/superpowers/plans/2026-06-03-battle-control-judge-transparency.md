# Battle Control Judge Vote Transparency Panel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real-time horizontal judge vote panel to `BattleControl.vue` that shows each judge's live vote, gates GET SCORE until all judges have voted, and upgrades the DECIDED-phase winner bar to a "CHAMPION LOCKED" display.

**Architecture:** Frontend-only change to a single file (`BattleControl.vue`). All data already flows through `battleJudges` ref and per-judge WebSocket subscriptions (`/topic/battle/vote/{judgeId}`). One new computed property (`voteCountDisplay`) is added; everything else reads existing state.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Tailwind CSS, existing design system utilities (`para-chip-sm`, `semantic-chip-success`, `type-label`, `type-body`, `section-rule`)

---

## File Map

| Action | Path | What changes |
|--------|------|-------------|
| Modify | `BES-frontend/src/views/BattleControl.vue` | New computed + 3 template changes |

No new files. No backend changes.

---

### Task 1: Add `voteCountDisplay` computed

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (after line 902, inside `<script setup>`)

- [ ] **Step 1: Locate insertion point**

Open `BattleControl.vue`. Find the `showFinalReveal` computed ending at line 902:
```js
const showFinalReveal = computed(() =>
  battlePhase.value === 'VOTING' &&
  isFinalInProgress.value &&
  allJudgesVoted.value &&
  tentativeWinner.value !== -1
)
```
The new computed goes immediately after this block.

- [ ] **Step 2: Insert `voteCountDisplay` computed**

After the closing `)` of `showFinalReveal` (line 902), add:
```js

const voteCountDisplay = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return {
    left:  judges.filter(j => j.vote === 0).length,
    right: judges.filter(j => j.vote === 1).length,
  }
})
```

- [ ] **Step 3: Verify no lint errors**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|warn" | head -20
```
Expected: no errors referencing `voteCountDisplay`.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: add voteCountDisplay computed for judge tally"
```

---

### Task 2: Add judge vote panel template

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template, after line 2488)

The panel goes between the winner announcement bar and the match pairs grid. It is always rendered — no phase guard — so the organiser sees judge status regardless of phase.

- [ ] **Step 1: Locate insertion point**

Find this comment in the template (around line 2489):
```html
      <!-- Match pairs (standard) — only shown when viewing the active round -->
```
The new panel is inserted **immediately before** this comment.

- [ ] **Step 2: Insert the judge vote panel**

Paste the following block before `<!-- Match pairs (standard) -->`:
```html
      <!-- Judge vote panel — real-time via per-judge WS subscriptions, always visible -->
      <div class="mb-4">
        <div class="section-rule mb-3">
          <span class="section-rule-label" style="font-size:9px;letter-spacing:0.22em">Judges</span>
          <div class="section-rule-line"></div>
        </div>

        <!-- Judge cards grid -->
        <template v-if="battleJudges?.judges?.length">
          <div
            class="grid gap-2 mb-3"
            :style="`grid-template-columns: repeat(${battleJudges.judges.length}, 1fr)`"
          >
            <div
              v-for="judge in battleJudges.judges"
              :key="judge.id"
              class="p-2 text-center"
              style="clip-path:polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%);border-left:2px solid"
              :style="judge.vote === -3
                ? 'border-color:rgba(245,158,11,0.5);background:rgba(245,158,11,0.06)'
                : judge.vote === 0
                  ? 'border-color:rgba(52,211,153,0.5);background:rgba(52,211,153,0.06)'
                  : 'border-color:rgba(59,130,246,0.5);background:rgba(59,130,246,0.06)'"
            >
              <div class="type-label text-content-muted mb-1" style="font-size:9px;letter-spacing:0.14em">
                {{ judge.judgeName }}
              </div>
              <div
                class="type-body"
                style="font-size:10px;letter-spacing:0.06em"
                :class="judge.vote === -3 ? 'text-amber-400' : judge.vote === 0 ? 'text-emerald-400' : 'text-blue-400'"
              >
                <template v-if="judge.vote === -3">⏳ WAITING</template>
                <template v-else-if="judge.vote === 0">{{ currentBattlePair?.[0] ?? 'LEFT' }}</template>
                <template v-else>{{ currentBattlePair?.[1] ?? 'RIGHT' }}</template>
              </div>
            </div>
          </div>

          <!-- Winner preview — shown when all judges have cast a vote -->
          <template v-if="allJudgesVoted">
            <!-- Clear winner -->
            <div
              v-if="tentativeWinner !== -1"
              class="semantic-chip-success px-3 py-2"
            >
              <div class="type-label text-emerald-400 mb-1" style="font-size:9px;letter-spacing:0.18em">
                WINNER PREVIEW (ORGANISER ONLY)
              </div>
              <div class="type-body text-emerald-400" style="font-size:13px">
                {{ tentativeWinner === 0 ? (currentBattlePair?.[0] ?? 'LEFT') : (currentBattlePair?.[1] ?? 'RIGHT') }}
              </div>
              <div class="type-label text-content-muted mt-1" style="font-size:9px">
                {{ voteCountDisplay.left }} – {{ voteCountDisplay.right }}
              </div>
            </div>
            <!-- Tie -->
            <div
              v-else
              class="px-3 py-2"
              style="border-left:3px solid #6b7280;background:rgba(107,114,128,0.08)"
            >
              <div class="type-label mb-1" style="font-size:9px;letter-spacing:0.18em;color:#9ca3af">
                WINNER PREVIEW
              </div>
              <div class="type-body" style="font-size:13px;color:#9ca3af">
                TIE — {{ voteCountDisplay.left }} – {{ voteCountDisplay.right }}
              </div>
              <div class="type-label text-content-muted mt-1" style="font-size:9px">Rematch required</div>
            </div>
          </template>
        </template>

        <!-- Empty state: no judges assigned -->
        <div
          v-else
          class="type-label text-content-muted px-3 py-2"
          style="font-size:10px;letter-spacing:0.14em;background:rgba(255,255,255,0.02);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
        >
          No judges assigned for this battle
        </div>
      </div>
```

- [ ] **Step 3: Build to verify no template errors**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```
Expected: clean build with no errors.

- [ ] **Step 4: Manual verify — start dev server and open BattleControl**

```bash
cd BES-frontend && npm run dev
```
Navigate to `/battle/control`. Check:
- "JUDGES" section rule appears below the winner bar in the Live Match card
- Judge cards are in a horizontal grid
- If no judges assigned: "No judges assigned" muted row shows

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: add real-time judge vote panel to Live Match card"
```

---

### Task 3: Gate GET SCORE button until all judges voted

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template, around line 2558)

- [ ] **Step 1: Locate the GET SCORE button**

Find this block (around line 2557):
```html
        <!-- VOTING: non-final, not all voted, or tie → Get Score / Rematch -->
        <button
          v-if="battlePhase === 'VOTING' && !showFinalReveal"
          @click="submitGetScore"
          class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
        </button>
```

- [ ] **Step 2: Replace with disabled-aware version**

Replace the block above with:
```html
        <!-- VOTING: non-final → Get Score / Rematch (disabled until all judges voted) -->
        <button
          v-if="battlePhase === 'VOTING' && !showFinalReveal"
          :disabled="!allJudgesVoted"
          @click="submitGetScore"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          :class="allJudgesVoted
            ? 'bg-accent'
            : 'text-content-muted cursor-not-allowed opacity-50'"
          :style="!allJudgesVoted ? 'background:rgba(255,255,255,0.04);border-color:rgba(255,255,255,0.08)' : ''"
          :title="allJudgesVoted ? '' : 'Waiting for all judges to vote'"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
        </button>
```

- [ ] **Step 3: Build to verify**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```
Expected: clean build.

- [ ] **Step 4: Manual verify**

With dev server running, open `/battle/control` in VOTING phase:
- Before all judges vote: GET SCORE button should be visually muted and unclickable (`:disabled` prevents click)
- After all judges vote (watch the judge panel update in real-time): GET SCORE button should become active white

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: disable GET SCORE button until all judges have voted"
```

---

### Task 4: Upgrade DECIDED-phase winner bar to champion locked display

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template, lines 2472–2488)

- [ ] **Step 1: Locate the winner announcement block**

Find this block (around line 2472):
```html
      <!-- Winner announcement -->
      <div
        class="px-4 py-3 mb-4"
        :class="{
          'semantic-chip-warning': winnerVariant === 'ongoing',
          'semantic-chip-warning': winnerVariant === 'wait',
          'semantic-chip-success': winnerVariant === 'winner',
          'border-l-3 border-gray-400 bg-gray-500/10': winnerVariant === 'tie',
        }"
      >
        <div
          class="w-2 h-2 rounded-full mb-1"
          :class="winnerVariant === 'winner' ? 'bg-emerald-400' : winnerVariant === 'tie' ? 'bg-gray-400' : 'bg-amber-400'"
          :style="winnerVariant === 'winner' ? 'box-shadow:0 0 8px rgba(52,211,153,0.8)' : winnerVariant === 'tie' ? '' : 'box-shadow:0 0 8px rgba(245,158,11,0.8)'"
        ></div>
        <span class="type-body text-content-primary">{{ winnerAnnouncement }}</span>
      </div>
```

- [ ] **Step 2: Replace with phase-conditional champion display**

Replace the entire block above with:
```html
      <!-- DECIDED: champion locked display -->
      <div
        v-if="battlePhase === 'DECIDED'"
        class="px-4 py-3 mb-4"
        style="border-left:4px solid #34d399;background:rgba(52,211,153,0.08)"
      >
        <div class="type-label text-emerald-400 mb-1" style="font-size:9px;letter-spacing:0.22em">
          ⭐ CHAMPION LOCKED
        </div>
        <div
          class="type-body text-emerald-400"
          style="font-size:18px;letter-spacing:0.12em;font-weight:bold;text-shadow:0 0 12px rgba(52,211,153,0.4)"
        >
          {{ genreChampions[selectedGenre] ?? currentGenreChampion ?? '—' }}
        </div>
        <div
          v-if="!revealActive"
          class="type-label text-content-muted mt-1"
          style="font-size:9px;letter-spacing:0.1em"
        >
          FINAL · ORGANISER ONLY — NOT REVEALED YET
        </div>
      </div>

      <!-- Winner announcement — all phases except DECIDED -->
      <div
        v-else
        class="px-4 py-3 mb-4"
        :class="{
          'semantic-chip-warning': winnerVariant === 'ongoing',
          'semantic-chip-warning': winnerVariant === 'wait',
          'semantic-chip-success': winnerVariant === 'winner',
          'border-l-3 border-gray-400 bg-gray-500/10': winnerVariant === 'tie',
        }"
      >
        <div
          class="w-2 h-2 rounded-full mb-1"
          :class="winnerVariant === 'winner' ? 'bg-emerald-400' : winnerVariant === 'tie' ? 'bg-gray-400' : 'bg-amber-400'"
          :style="winnerVariant === 'winner' ? 'box-shadow:0 0 8px rgba(52,211,153,0.8)' : winnerVariant === 'tie' ? '' : 'box-shadow:0 0 8px rgba(245,158,11,0.8)'"
        ></div>
        <span class="type-body text-content-primary">{{ winnerAnnouncement }}</span>
      </div>
```

- [ ] **Step 3: Build to verify**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```
Expected: clean build.

- [ ] **Step 4: Manual verify**

With dev server running, trigger DECIDED phase on `/battle/control`:
- Winner bar should show green left-border, "⭐ CHAMPION LOCKED" label, champion name with green glow, "NOT REVEALED YET" disclaimer
- After clicking "Reveal Champion": disclaimer disappears (`revealActive` becomes true)
- Navigating away from DECIDED (e.g. genre switch back to VOTING): normal amber/green winner bar returns

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: upgrade DECIDED phase winner bar to champion locked display"
```

---

### Task 5: End-to-end smoke test + push

- [ ] **Step 1: Full build check**

```bash
cd BES-frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in Xs` with no errors.

- [ ] **Step 2: Run existing tests**

```bash
cd BES-frontend && npm test 2>&1 | tail -20
```
Expected: all tests pass (no new tests were added — computed is trivial).

- [ ] **Step 3: Manual E2E walkthrough**

With dev server running (`npm run dev`), go through each phase on `/battle/control`:

| Phase | What to check |
|-------|--------------|
| IDLE | Judge panel visible, all showing ⏳ WAITING; normal winner bar shows |
| LOCKED | Same as IDLE |
| VOTING (some pending) | Some judges show amber WAITING, voted judges show battler name; GET SCORE disabled |
| VOTING (all voted, winner) | All judge cards show battler name; green winner preview banner shows; GET SCORE active |
| VOTING (all voted, tie) | All judge cards show battler name; gray TIE banner shows; GET SCORE active |
| REVEALED | Judge panel visible; votes from previous round may show |
| DECIDED | Green "CHAMPION LOCKED" bar with glowing champion name; "NOT REVEALED YET" shown; after Reveal Champion clicked, disclaimer hidden |

- [ ] **Step 4: Push and open PR**

```bash
git push -u origin feat/battle-division-state-persistence
gh pr create \
  --title "feat: real-time judge vote transparency panel in BattleControl" \
  --body "$(cat <<'EOF'
## Summary
- Adds horizontal judge vote panel to Live Match card (always visible, all phases)
- Shows each judge's vote as battler name (teal = left, blue = right, amber = waiting) in real time via existing per-judge WebSocket subscriptions
- Displays winner preview banner (name + tally) when all judges have voted; gray TIE state when split evenly
- Gates GET SCORE button — disabled with muted styling until `allJudgesVoted` is true
- Upgrades DECIDED-phase winner bar to green "CHAMPION LOCKED" display with glowing champion name and "NOT REVEALED YET" disclaimer

Closes #49

## Test plan
- [ ] IDLE/LOCKED: panel visible, all judges show WAITING
- [ ] VOTING partial: pending judges show amber WAITING, voted judges show battler name; GET SCORE disabled
- [ ] VOTING all voted (winner): winner preview banner appears with name + tally; GET SCORE enabled
- [ ] VOTING all voted (tie): gray TIE banner appears; GET SCORE enabled
- [ ] DECIDED: green champion locked bar, glowing name; disclaimer hides after Reveal Champion
- [ ] Build passes: `npm run build` clean
- [ ] Tests pass: `npm test` green

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
