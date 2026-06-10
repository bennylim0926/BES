# EmceeRoundView Nav Button Footer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three non-interactive, misleading `← Prev / swipe / Next →` span labels at the bottom of the EmceeRoundView card with two large parallelogram `<button>` elements that correctly trigger `goPrev()` and `goNext()`.

**Architecture:** Single-file change in `EmceeRoundView.vue` — swap the footer markup and add scoped CSS for `.nav-btn`. No logic changes; the existing `goNext`/`goPrev` functions and swipe gesture are reused as-is.

**Tech Stack:** Vue 3 (script setup), scoped CSS, PrimeIcons (`pi pi-chevron-left/right`)

---

### Task 1: Replace footer markup and add nav-btn CSS

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue:202-206` (template) and the `<style scoped>` block

- [ ] **Step 1: Replace the footer div (lines 202–206)**

Open `BES-frontend/src/components/EmceeRoundView.vue`. Find:

```html
            <div class="flex items-center justify-between px-4 pb-1.5">
              <span class="type-label text-content-muted">← Prev</span>
              <span class="type-label text-content-muted">swipe</span>
              <span class="type-label text-content-muted">Next →</span>
            </div>
```

Replace with:

```html
            <div class="flex items-center gap-1.5 px-2.5 pb-2 pt-1">
              <button
                class="nav-btn"
                :disabled="currentRound <= 1"
                @pointerdown.stop
                @click="goPrev"
                aria-label="Previous round"
              >
                <i class="pi pi-chevron-left text-xs"></i>
                <span class="type-label">Prev</span>
              </button>
              <button
                class="nav-btn"
                :disabled="currentRound >= totalRounds"
                @pointerdown.stop
                @click="goNext"
                aria-label="Next round"
              >
                <span class="type-label">Next</span>
                <i class="pi pi-chevron-right text-xs"></i>
              </button>
            </div>
```

- [ ] **Step 2: Add `.nav-btn` CSS to the scoped style block**

In the `<style scoped>` section (after the existing `.queue-item` rule, before the closing `</style>`), add:

```css
/* ── Nav buttons ──────────────────────────────────────────────────────── */
.nav-btn {
  flex: 1;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.65);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.nav-btn:not(:disabled):active {
  background: var(--accent-muted);
  color: var(--accent-color);
}
.nav-btn:disabled {
  opacity: 0.2;
  pointer-events: none;
  cursor: not-allowed;
}
```

- [ ] **Step 3: Run the dev server and verify**

```bash
cd BES-frontend && npm run dev
```

Open `http://localhost:5173` and navigate to `/event/audition-list` as an Emcee or Admin with an active event.

Check:
- Two equal-width buttons appear at the bottom of the NOW card
- `PREV` button is dimmed (opacity ~20%) on round 1
- `NEXT` button is dimmed on the last round
- Tapping `NEXT` advances to the next round correctly
- Tapping `PREV` goes back correctly
- Swiping left/right on the card body still navigates (gesture unchanged)
- No accidental navigation fires when tapping the buttons (pointer isolation)

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue
git commit -m "ui: replace confusing swipe labels with nav buttons on EmceeRoundView

Closes #122"
```
