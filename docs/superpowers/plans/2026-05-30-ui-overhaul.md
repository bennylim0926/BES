# BES UI Overhaul — Cinematic Battle Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify every BES screen under the cinematic battle design language — Anton SC typography, `--accent-color` token system, parallelogram shapes, and broadcast chrome — while keeping `BattleOverlay.vue`, `BracketVisualization.vue`, and `Chart.vue` untouched.

**Architecture:** The token layer (`base.css`) is updated first so every downstream view inherits new defaults. `App.vue` gets the cinematic navbar and global scanlines. A new `AppConfig` backend entity stores the accent color and broadcasts changes via WebSocket so all clients update live.

**Tech Stack:** Vue 3 (Composition API), Tailwind v4 (`base.css @theme`), Spring Boot 3, Spring Data JPA, STOMP WebSocket, Flyway (V20 migration), H2 for tests.

**Do NOT change:** `BattleOverlay.vue`, `BracketVisualization.vue`, `Chart.vue`, `BattleJudge.vue`.

---

## File Structure

### New files
| File | Purpose |
|------|---------|
| `BES/src/main/resources/db/migration/V20__add_app_config.sql` | Create `app_config` table with seed row |
| `BES/src/main/java/com/example/BES/models/AppConfig.java` | JPA entity for key-value app config |
| `BES/src/main/java/com/example/BES/respositories/AppConfigRepository.java` | Spring Data repo |
| `BES/src/main/java/com/example/BES/services/AppConfigService.java` | Business logic (get/save accent) |
| `BES/src/main/java/com/example/BES/controllers/AppConfigController.java` | `GET/POST /api/v1/config/app` |
| `BES/src/test/java/com/example/BES/controllers/AppConfigControllerIntegrationTest.java` | Integration tests |

### Modified files — Backend
| File | Change |
|------|--------|
| `BES/src/main/java/com/example/BES/config/SecurityConfig.java` | Permit `GET /api/v1/config/app`; restrict `POST` to ADMIN |

### Modified files — Frontend
| File | Change |
|------|--------|
| `BES-frontend/src/assets/base.css` | New token layer, Anton SC, accent tokens, parallelogram utilities |
| `BES-frontend/src/App.vue` | Cinematic navbar, scanlines, accent fetch + WS subscribe |
| `BES-frontend/src/utils/api.js` | Add `getAppConfig`, `postAppConfig` |
| `BES-frontend/src/views/Login.vue` | Display-tier hero, parallelogram form |
| `BES-frontend/src/views/MainMenu.vue` | Parallelogram quick-action cards, section rules |
| `BES-frontend/src/views/EventSelector.vue` | Parallelogram event cards, corner bars |
| `BES-frontend/src/views/Events.vue` | Parallelogram rows, section rules |
| `BES-frontend/src/components/EventCard.vue` | Parallelogram card shape, corner bars |
| `BES-frontend/src/views/EventDetails.vue` | Section rules, parallelogram stat boxes |
| `BES-frontend/src/views/UpdateEventDetails.vue` | Parallelogram rows, semantic chips |
| `BES-frontend/src/views/AuditionList.vue` | Context bar + filter chips → parallelogram |
| `BES-frontend/src/views/Score.vue` | Number/stat tier for scores, parallelogram rows |
| `BES-frontend/src/views/BattleControl.vue` | Section rules, bracket seed slots → parallelogram |
| `BES-frontend/src/views/AdminPage.vue` | Theme Config section with color picker |
| `BES-frontend/src/views/Results.vue` | Parallelogram result rows |
| `BES-frontend/src/views/ResultsQR.vue` | Parallelogram result rows |
| `BES-frontend/src/components/EmceeRoundView.vue` | Anton SC labels, section rules |
| `BES-frontend/src/components/FeedbackPopout.vue` | Parallelogram chips, Anton SC |
| `BES-frontend/src/components/Timer.vue` | Number/stat tier |
| `BES-frontend/src/components/MiniScoreMenu.vue` | Parallelogram chips |
| `BES-frontend/src/components/ReusableDropdown.vue` | Parallelogram options |
| `BES-frontend/src/components/SwipeableCardsV2.vue` | Parallelogram score card |
| `BES-frontend/src/components/PairScoreCards.vue` | Parallelogram stat layout |
| `BES-frontend/src/views/ActionDoneModal.vue` | Corner bars on modal, semantic chip variants |

---

## Task 1: base.css — Design Token Foundation

**Files:**
- Modify: `BES-frontend/src/assets/base.css`

This is the most critical task. Every downstream view inherits from it. Complete it before touching any view.

Key changes to make, section by section:

- [ ] **Step 1: Update Google Fonts import** — Remove Outfit (no longer used); keep Inter and JetBrains Mono.

Replace line 1:
```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;700&display=swap');
```

- [ ] **Step 2: Update `@theme` typography tokens**

Replace the `/* Typography */` block inside `@theme` (lines ~46-50):
```css
/* Typography */
--font-sans:    'Anton SC', sans-serif;
--font-body:    'Inter', ui-sans-serif, system-ui, sans-serif;
--font-eagle:   'EagleHorizonP', sans-serif;
--font-anton:   'Anton SC', sans-serif;
--font-source:  'JetBrains Mono', 'Source Code Pro', monospace;
```
(`--font-heading` is removed. `--font-body` is Inter, available but not the default.)

- [ ] **Step 3: Add accent CSS custom properties** — Insert after the closing `}` of `@theme`, before the `@layer base` block:

```css
/* ─────────────────────────────────────────
   Runtime Accent Color Tokens
   JS sets --accent-server on <html> (not --accent-color directly).
   CSS cascades --accent-color from --accent-server.
   [data-theme="light"] overrides --accent-color via CSS cascade
   (CSS rules win over inline styles on ancestor elements when
   the property is set on a different custom property).
───────────────────────────────────────── */
:root {
  --accent-server:  #ffffff;
  --accent-color:   var(--accent-server);
  --accent-muted:   color-mix(in srgb, var(--accent-color) 25%, transparent);
  --accent-subtle:  color-mix(in srgb, var(--accent-color) 7%, transparent);
}
```

Then in Step 10, add `--accent-color: rgba(0, 0, 0, 0.78)` to the **existing** `[data-theme="light"]` block — do NOT create a second `[data-theme="light"]` block. There should be exactly one such block in the file.
```

- [ ] **Step 4: Update `body` base rule** — change `leading-relaxed` to `leading-tight`. Anton SC is a display font; 1.625 line-height creates excessive gaps.

In `@layer base`, replace:
```css
@apply font-sans bg-surface-950 text-content-primary antialiased leading-relaxed min-h-screen;
```
with:
```css
@apply font-sans bg-surface-950 text-content-primary antialiased leading-tight min-h-screen;
```

- [ ] **Step 5: Update `h1-h6` base rule** — Remove Outfit (`font-heading`); Anton SC is now the default via `font-sans`.

Replace the `h1, h2, h3, h4, h5, h6` block:
```css
h1, h2, h3, h4, h5, h6 {
  @apply font-sans tracking-wide uppercase text-content-primary;
}
```

- [ ] **Step 6: Add accent CSS utilities** — Add inside the `@layer utilities` block, after the existing layout utilities:

```css
/* ── Accent token utilities ─────────────────────────────────── */
.text-accent   { color: var(--accent-color); }
.bg-accent     { background: var(--accent-color); }
.border-accent { border-color: var(--accent-color); }
.glow-accent   { box-shadow: 0 0 14px var(--accent-muted); }
```

- [ ] **Step 7: Update `.card` and `.card-hover`** — Replace rounded corners with parallelogram clip-path.

Replace the existing `.card` and `.card-hover` blocks:
```css
.card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}

.card-hover {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  transition: background 0.2s, border-color 0.2s;
}
.card-hover:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--accent-muted);
  box-shadow: 0 0 14px var(--accent-muted);
}
```

- [ ] **Step 8: Update `.badge-*`** — Replace `rounded-full` with parallelogram clip-path.

Replace the badge block:
```css
.badge,
.badge-primary,
.badge-secondary,
.badge-success,
.badge-warning,
.badge-danger,
.badge-neutral {
  @apply inline-flex items-center px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wider;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}

.badge-primary   { background: rgba(255,255,255,0.08); color: var(--accent-color); border: 1px solid var(--accent-muted); }
.badge-secondary { @apply bg-accent-100 text-accent-500; }
.badge-success   { background: rgba(52,211,153,0.10); color: #34d399; border: 1px solid rgba(52,211,153,0.2); }
.badge-warning   { background: rgba(245,158,11,0.08); color: #f59e0b; border: 1px solid rgba(245,158,11,0.2); }
.badge-danger    { background: rgba(239,68,68,0.10);  color: #ef4444; border: 1px solid rgba(239,68,68,0.2); }
.badge-neutral   { background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.55); border: 1px solid rgba(255,255,255,0.07); }
```

- [ ] **Step 9: Add cinematic chrome utilities** — Add inside `@layer utilities`:

```css
/* ── Parallelogram chip (rows, list items, stat boxes) ──────── */
.para-chip {
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
}
.para-chip-sm {
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
}

/* ── Section rule (label + hairline) ───────────────────────── */
.section-rule {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-rule-line {
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.07);
}
.section-rule-label {
  font-family: 'Anton SC', sans-serif;
  font-size: 10px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.28);
  white-space: nowrap;
}

/* ── Corner accent bars (panels, modals) ───────────────────── */
.corner-bar-tl {
  position: absolute; top: 0; left: 0;
  width: 2px; height: 28%;
  background: linear-gradient(to bottom, var(--accent-color), transparent);
  pointer-events: none;
}
.corner-bar-bl {
  position: absolute; bottom: 0; left: 0;
  height: 2px; width: 30%;
  background: linear-gradient(to right, var(--accent-color), transparent);
  pointer-events: none;
}

/* ── Color bleed (page root only) ──────────────────────────── */
.color-bleed {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(ellipse 50% 45% at 0% 100%, var(--accent-subtle), transparent 70%),
    radial-gradient(ellipse 40% 40% at 100% 100%, color-mix(in srgb, var(--accent-color) 4%, transparent), transparent 70%);
}

/* ── Glowing status dot ─────────────────────────────────────── */
.glow-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--accent-color);
  box-shadow: 0 0 8px var(--accent-muted);
  flex-shrink: 0;
}

/* ── Semantic state chips (left border + dot) ───────────────── */
.semantic-chip-error {
  border-left: 3px solid #ef4444;
  border-top: 1px solid rgba(239,68,68,0.2);
  border-right: 1px solid rgba(239,68,68,0.2);
  border-bottom: 1px solid rgba(239,68,68,0.2);
  background: rgba(239,68,68,0.10);
  border-radius: 0;
}
.semantic-chip-warning {
  border-left: 3px solid #f59e0b;
  border-top: 1px solid rgba(245,158,11,0.2);
  border-right: 1px solid rgba(245,158,11,0.2);
  border-bottom: 1px solid rgba(245,158,11,0.2);
  background: rgba(245,158,11,0.08);
}
.semantic-chip-success {
  border-left: 3px solid #34d399;
  border-top: 1px solid rgba(52,211,153,0.2);
  border-right: 1px solid rgba(52,211,153,0.2);
  border-bottom: 1px solid rgba(52,211,153,0.2);
  background: rgba(52,211,153,0.08);
}

/* ── Typography tier utilities ──────────────────────────────── */
.type-display {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(36px, 5vw, 56px);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #ffffff;
  line-height: 1;
  text-shadow: 2px 2px 0 var(--accent-muted);
}
.type-page-title {
  font-family: 'Anton SC', sans-serif;
  font-size: 28px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #f0f0f0;
  line-height: 1;
  text-shadow: 1px 1px 0 var(--accent-muted);
}
.type-section {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.55);
  line-height: 1;
}
.type-body {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.75);
  line-height: 1.1;
}
.type-label {
  font-family: 'Anton SC', sans-serif;
  font-size: 10px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.35);
  line-height: 1;
}
.type-stat {
  font-family: 'Anton SC', sans-serif;
  font-size: 42px;
  letter-spacing: 0.02em;
  color: #ffffff;
  line-height: 1;
}
```

- [ ] **Step 10: Update `[data-theme="light"]` block** — Two edits to make:

**10a** — Find the existing `[data-theme="light"] { ... }` block (around line 510 in the current file) and add this property inside it, after the existing `--color-content-disabled` line:
```css
/* Accent — CSS wins because JS only sets --accent-server (see :root block above) */
--accent-color: rgba(0, 0, 0, 0.78);
```

**10b** — After all existing `[data-theme="light"] .class { }` rules at the bottom of the file, append the following new component-specific light-mode rules (following the existing pattern in the file):
```css
/* Parallelogram chip surfaces on light */
[data-theme="light"] .para-chip,
[data-theme="light"] .para-chip-sm {
  background: rgba(0, 0, 0, 0.03);
  border-color: rgba(0, 0, 0, 0.08);
}

[data-theme="light"] .card,
[data-theme="light"] .card-hover {
  background: rgba(0, 0, 0, 0.03);
  border-color: rgba(0, 0, 0, 0.08);
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 4px 16px rgba(0,0,0,0.04) !important;
}
[data-theme="light"] .card-hover:hover { border-color: var(--accent-muted); }

[data-theme="light"] .section-rule-line { background: rgba(0, 0, 0, 0.1); }
[data-theme="light"] .section-rule-label { color: rgba(0, 0, 0, 0.35); }

[data-theme="light"] .badge-neutral {
  background: rgba(0, 0, 0, 0.04);
  color: rgba(0, 0, 0, 0.55);
  border-color: rgba(0, 0, 0, 0.08);
}

[data-theme="light"] .scanlines { opacity: 0.5; }
[data-theme="light"] .color-bleed { display: none; }

[data-theme="light"] .corner-bar-tl,
[data-theme="light"] .corner-bar-bl {
  background: linear-gradient(to bottom, rgba(0,0,0,0.4), transparent);
}

[data-theme="light"] .type-display    { color: #0d0d0d; text-shadow: 2px 2px 0 rgba(0,0,0,0.08); }
[data-theme="light"] .type-page-title { color: #1a1a1a; text-shadow: 1px 1px 0 rgba(0,0,0,0.06); }
[data-theme="light"] .type-section    { color: rgba(0,0,0,0.55); }
[data-theme="light"] .type-body       { color: rgba(0,0,0,0.75); }
[data-theme="light"] .type-label      { color: rgba(0,0,0,0.40); }
[data-theme="light"] .type-stat       { color: #0d0d0d; }
```

- [ ] **Step 11: Update `.stat-card` and `.stat-value`** — Parallelogram shape; Number/stat tier for value:

Replace the existing `.stat-card` and `.stat-value` blocks:
```css
.stat-card {
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  @apply flex flex-col items-center justify-center p-5 text-center gap-1 relative;
}

.stat-value {
  @apply type-stat tabular-nums;
}

.stat-label {
  @apply type-label;
}
```

- [ ] **Step 12: Update `.input-base`** — Anton SC, parallelogram, accent focus ring:

Replace the existing `.input-base`:
```css
.input-base {
  width: 100%;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 0.625rem 1rem;
  font-family: 'Anton SC', sans-serif;
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.88);
}
.input-base::placeholder {
  color: rgba(255, 255, 255, 0.25);
  letter-spacing: 0.1em;
}
.input-base:focus {
  outline: none;
  border-color: var(--accent-muted);
  box-shadow: 0 0 8px var(--accent-subtle);
}
```

- [ ] **Step 13: Update focus-visible ring** — Use accent color instead of red:

```css
*:focus-visible {
  outline: 2px solid var(--accent-muted);
  outline-offset: 2px;
}
```

- [ ] **Step 14: Run dev server and verify basics work**

```bash
cd BES-frontend && npm run dev
```
Open http://localhost:5173. Verify: body font is now Anton SC (all caps), no broken layout.

- [ ] **Step 15: Run tests**

```bash
cd BES-frontend && npm test
```
Expected: all existing tests pass (CSS changes don't affect logic tests).

- [ ] **Step 16: Commit**

```bash
git add BES-frontend/src/assets/base.css
git commit -m "style: rewrite base.css with Anton SC, accent tokens, parallelogram utilities"
```

---

## Task 2: api.js — App Config API helpers

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add `getAppConfig` and `postAppConfig` functions** — Append to the end of `api.js`:

```js
export const getAppConfig = async () => {
  const res = await fetch('/api/v1/config/app', { credentials: 'include' })
  return res.json()
}

export const postAppConfig = async (accentColor) => {
  const res = await fetch('/api/v1/config/app', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accentColor })
  })
  return res.json()
}
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add getAppConfig and postAppConfig API helpers"
```

---

## Task 3: App.vue — Cinematic Navbar + Scanlines + Accent

**Files:**
- Modify: `BES-frontend/src/App.vue`

- [ ] **Step 1: Update script — add accent color logic**

At the top of `<script setup>`, add to imports:
```js
import { createClient, deactivateClient, subscribeToChannel } from './utils/websocket'
import { getAppConfig } from './utils/api'
```

Add new refs after existing refs:
```js
const accentServer = ref('#ffffff')
const wsAccentClient = ref(null)
```

Add the `applyAccent` function after `applyTheme`:
```js
function applyAccent(color) {
  accentServer.value = color
  document.documentElement.style.setProperty('--accent-server', color)
}
```

Update `applyTheme` to handle accent properly:
```js
function applyTheme(t) {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem('bes-theme', t)
}
```
(The accent CSS custom property cascade handles light mode automatically once `--accent-server` is set and `[data-theme="light"]` overrides `--accent-color`.)

Update `onMounted` to fetch accent config and subscribe:
```js
onMounted(async () => {
  applyTheme(theme.value)
  try {
    const res = await whoami()
    authStore.login(res)
  } catch {
    // not authenticated — ok
  }
  try {
    const cfg = await getAppConfig()
    if (cfg?.accentColor) applyAccent(cfg.accentColor)
  } catch {
    // server not ready — keep default
  }
  const c = createClient()
  wsAccentClient.value = c
  subscribeToChannel(c, '/topic/app-config', (msg) => {
    if (msg?.accentColor) applyAccent(msg.accentColor)
  })
})
```

Add `onUnmounted` cleanup — first update the `vue` import at line 1 of the script to add `onUnmounted`:
```js
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
```

Then add the cleanup:
```js
onUnmounted(() => {
  deactivateClient(wsAccentClient.value)
})
```

- [ ] **Step 2: Add scanlines overlay div** — Add as the very first child inside `<template>`, before `<nav>`:

```html
<!-- Global scanlines overlay -->
<div
  class="scanlines fixed inset-0 z-[9999] pointer-events-none"
  style="background: repeating-linear-gradient(to bottom, transparent 0px, transparent 3px, rgba(0,0,0,0.045) 3px, rgba(0,0,0,0.045) 4px);"
  aria-hidden="true"
></div>
```

- [ ] **Step 3: Rewrite the `<nav>` template** — Replace the entire `<nav>` element with the cinematic version. Keep all computed props (role, isAuthenticated, activeEvent, etc.) — only the template changes.

```html
<nav
  v-if="!hideNav"
  class="fixed top-0 left-0 w-full z-40 bg-surface-900/95 border-b border-[rgba(255,255,255,0.07)] transition-all duration-300"
>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
    <div class="grid grid-cols-[auto_1fr_auto] items-center h-16 gap-4">

      <!-- Left: BES wordmark + glowing dot -->
      <router-link to="/" class="flex items-center gap-2.5 group flex-shrink-0">
        <div class="glow-dot"></div>
        <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">BES</span>
      </router-link>

      <!-- Center: Primary nav as parallelogram chips -->
      <div class="hidden md:flex items-center justify-center gap-2">
        <router-link to="/" v-slot="{ isActive }">
          <span
            class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
            :class="isActive
              ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
              : 'text-content-muted hover:text-content-primary'"
          >Home</span>
        </router-link>
        <router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
          <span
            class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
            :class="isActive
              ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
              : 'text-content-muted hover:text-content-primary'"
          >Events</span>
        </router-link>
        <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
          <span
            class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
            :class="isActive
              ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
              : 'text-content-muted hover:text-content-primary'"
          >Admin</span>
        </router-link>
      </div>

      <!-- Right: event chip + role + theme + logout -->
      <div class="hidden md:flex items-center gap-2">

        <!-- Active event dropdown -->
        <div v-if="isAuthenticated && activeEvent" class="relative">
          <button
            @click="eventMenuOpen = !eventMenuOpen"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-secondary hover:text-content-primary transition-all duration-200 max-w-[200px]"
          >
            <span class="truncate">{{ activeEvent.name }}</span>
            <i class="pi pi-chevron-down text-[10px] flex-shrink-0 opacity-50 transition-transform duration-200"
               :class="eventMenuOpen ? 'rotate-180' : ''"></i>
          </button>

          <Transition
            enter-active-class="transition duration-150 ease-out"
            enter-from-class="opacity-0 translate-y-1"
            enter-to-class="opacity-100 translate-y-0"
            leave-active-class="transition duration-100 ease-in"
            leave-from-class="opacity-100 translate-y-0"
            leave-to-class="opacity-0 translate-y-1"
          >
            <div v-if="eventMenuOpen"
              class="absolute right-0 top-full mt-2 w-52 z-50 bg-surface-800 border border-[rgba(255,255,255,0.07)] shadow-[0_8px_32px_rgba(0,0,0,0.5)] overflow-hidden relative"
              style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)">
              <div class="corner-bar-tl"></div>
              <div class="px-3 py-2.5 border-b border-[rgba(255,255,255,0.07)]">
                <p class="type-label text-content-muted mb-0.5">Current Event</p>
                <p class="type-body text-content-primary truncate">{{ activeEvent.name }}</p>
              </div>
              <div class="py-1">
                <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                  @click="goToEventDetails"
                  class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                  Event Details
                </button>
                <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'"
                  @click="goToSection('Audition List')"
                  class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                  Audition
                </button>
                <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                  @click="goToSection('Update Event Details')"
                  class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                  Participants
                </button>
                <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE'"
                  @click="goToSection('Score')"
                  class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                  Scoreboard
                </button>
                <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                  @click="goToSection('Battle Control')"
                  class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                  Battle
                </button>
              </div>
              <div class="border-t border-[rgba(255,255,255,0.07)] py-1">
                <button @click="changeEvent"
                  class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                  Change Event
                </button>
              </div>
            </div>
          </Transition>
          <div v-if="eventMenuOpen" class="fixed inset-0 z-40" @click="eventMenuOpen = false"></div>
        </div>

        <div v-if="isAuthenticated" class="h-4 w-px bg-[rgba(255,255,255,0.12)]"></div>

        <!-- Role badge -->
        <span v-if="isAuthenticated && roleDisplay"
          class="badge-neutral type-label px-2 py-0.5">
          {{ roleDisplay.label }}
        </span>

        <!-- Theme toggle -->
        <button @click="toggleTheme"
          class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
          <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
        </button>

        <router-link v-if="!isAuthenticated" to="/login">
          <span class="px-4 py-1.5 para-chip type-label text-surface-900 bg-accent cursor-pointer">Login</span>
        </router-link>

        <button v-if="isAuthenticated"
          @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
          class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-red-400 hover:bg-red-950 transition-all duration-200">
          <i class="pi pi-sign-out text-sm"></i>
        </button>
      </div>

      <!-- Mobile Hamburger -->
      <button @click="isOpen = !isOpen"
        class="md:hidden inline-flex items-center justify-center w-9 h-9 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-colors focus:outline-none">
        <i class="pi text-lg" :class="isOpen ? 'pi-times' : 'pi-bars'"></i>
      </button>

    </div>
  </div>

  <!-- Mobile Dropdown -->
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="opacity-0 -translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 -translate-y-2"
  >
    <div v-show="isOpen" class="md:hidden border-t border-[rgba(255,255,255,0.07)] bg-surface-900/98">
      <div class="px-3 py-3 space-y-0.5">
        <router-link to="/" v-slot="{ isActive }">
          <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
            :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
            Home
          </span>
        </router-link>
        <router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
          <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
            :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
            Events
          </span>
        </router-link>
        <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
          <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
            :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
            Admin
          </span>
        </router-link>

        <template v-if="isAuthenticated && activeEvent">
          <div class="px-4 pt-3 pb-1 section-rule">
            <span class="section-rule-label">Current Event — {{ activeEvent.name }}</span>
            <div class="section-rule-line"></div>
          </div>
          <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" @click="goToEventDetails"
            class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
            Event Details
          </button>
          <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'" @click="goToSection('Audition List')"
            class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
            Audition
          </button>
          <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" @click="goToSection('Update Event Details')"
            class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
            Participants
          </button>
          <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE'" @click="goToSection('Score')"
            class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
            Scoreboard
          </button>
          <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" @click="goToSection('Battle Control')"
            class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
            Battle
          </button>
          <button @click="changeEvent"
            class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-muted hover:text-content-primary transition-colors">
            Change Event
          </button>
        </template>
      </div>

      <div class="px-3 py-3 border-t border-[rgba(255,255,255,0.07)]">
        <div v-if="isAuthenticated" class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span v-if="roleDisplay" class="badge-neutral type-label">{{ roleDisplay.label }}</span>
            <button @click="toggleTheme"
              class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
              <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
            </button>
          </div>
          <button @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
            class="flex items-center gap-2 px-4 py-2 type-label text-red-400 bg-red-950 hover:bg-red-900 transition-colors cursor-pointer"
            style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
            Logout
          </button>
        </div>
        <router-link v-else to="/login">
          <span class="flex items-center justify-center px-4 py-2.5 type-label para-chip text-surface-900 bg-accent w-full cursor-pointer">Login</span>
        </router-link>
      </div>
    </div>
  </Transition>
</nav>
```

- [ ] **Step 4: Verify in browser**

```bash
cd BES-frontend && npm run dev
```
Check: cinematic navbar, glowing dot next to BES, parallelogram event dropdown, scanlines visible.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/App.vue
git commit -m "feat: cinematic navbar, global scanlines, accent color WS subscribe"
```

---

## Task 4: Backend — AppConfig entity, migration, service, controller

**Files:**
- Create: `BES/src/main/resources/db/migration/V20__add_app_config.sql`
- Create: `BES/src/main/java/com/example/BES/models/AppConfig.java`
- Create: `BES/src/main/java/com/example/BES/respositories/AppConfigRepository.java`
- Create: `BES/src/main/java/com/example/BES/services/AppConfigService.java`
- Create: `BES/src/main/java/com/example/BES/controllers/AppConfigController.java`
- Create: `BES/src/test/java/com/example/BES/controllers/AppConfigControllerIntegrationTest.java`
- Modify: `BES/src/main/java/com/example/BES/config/SecurityConfig.java`

- [ ] **Step 1: Write the integration test first (TDD)**

Create `BES/src/test/java/com/example/BES/controllers/AppConfigControllerIntegrationTest.java`:
```java
package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AppConfigControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void getAppConfig_unauthenticated_returnsDefault() throws Exception {
        mockMvc.perform(get("/api/v1/config/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#ffffff"));
    }

    @Test
    public void postAppConfig_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/config/app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accentColor\":\"#ff0000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void postAppConfig_admin_savesColor() throws Exception {
        mockMvc.perform(post("/api/v1/config/app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accentColor\":\"#06b6d4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#06b6d4"));

        mockMvc.perform(get("/api/v1/config/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#06b6d4"));
    }

    @Test
    @WithMockUser(username = "organiser", roles = {"ORGANISER"})
    public void postAppConfig_organiser_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/config/app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accentColor\":\"#ff0000\"}"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails** (controller doesn't exist yet):

```bash
cd BES && mvn test -Dtest=AppConfigControllerIntegrationTest
```
Expected: 404 Not Found for all tests (endpoint doesn't exist).

- [ ] **Step 3: Create the Flyway migration**

Create `BES/src/main/resources/db/migration/V20__add_app_config.sql`:
```sql
CREATE TABLE app_config (
    id      BIGSERIAL PRIMARY KEY,
    key     VARCHAR(100) NOT NULL UNIQUE,
    value   TEXT         NOT NULL
);

INSERT INTO app_config (key, value) VALUES ('accentColor', '#ffffff');
```

- [ ] **Step 4: Create the JPA entity**

Create `BES/src/main/java/com/example/BES/models/AppConfig.java`:
```java
package com.example.BES.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String key;

    @Column(nullable = false)
    private String value;
}
```

- [ ] **Step 5: Create the repository**

Create `BES/src/main/java/com/example/BES/respositories/AppConfigRepository.java`:
```java
package com.example.BES.respositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.AppConfig;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {
    Optional<AppConfig> findByKey(String key);
}
```

- [ ] **Step 6: Create the service**

Create `BES/src/main/java/com/example/BES/services/AppConfigService.java`:
```java
package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.BES.models.AppConfig;
import com.example.BES.respositories.AppConfigRepository;

@Service
public class AppConfigService {

    private static final String ACCENT_KEY = "accentColor";
    private static final String ACCENT_DEFAULT = "#ffffff";

    @Autowired
    private AppConfigRepository repo;

    public String getAccentColor() {
        return repo.findByKey(ACCENT_KEY)
                   .map(AppConfig::getValue)
                   .orElse(ACCENT_DEFAULT);
    }

    public String saveAccentColor(String color) {
        AppConfig cfg = repo.findByKey(ACCENT_KEY)
                            .orElse(new AppConfig(null, ACCENT_KEY, ACCENT_DEFAULT));
        cfg.setValue(color);
        repo.save(cfg);
        return color;
    }
}
```

- [ ] **Step 7: Create the controller**

Create `BES/src/main/java/com/example/BES/controllers/AppConfigController.java`:
```java
package com.example.BES.controllers;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.BES.services.AppConfigService;

@RestController
@RequestMapping("/api/v1/config")
public class AppConfigController {

    @Autowired
    private AppConfigService service;

    @Autowired
    private SimpMessagingTemplate messaging;

    @GetMapping("/app")
    public ResponseEntity<Map<String, String>> getAppConfig() {
        String color = service.getAccentColor();
        return ResponseEntity.ok(Map.of("accentColor", color));
    }

    @PostMapping("/app")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> postAppConfig(@RequestBody Map<String, String> body) {
        String color = body.get("accentColor");
        if (color == null || color.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accentColor is required"));
        }
        String saved = service.saveAccentColor(color);
        messaging.convertAndSend("/topic/app-config", Map.of("accentColor", saved));
        return ResponseEntity.ok(Map.of("accentColor", saved));
    }
}
```

- [ ] **Step 8: Update SecurityConfig** — Permit `GET /api/v1/config/app` publicly; POST is protected by `@PreAuthorize` on the controller.

In `SecurityConfig.java`, add a line before `.anyRequest().authenticated()`:
```java
.requestMatchers(HttpMethod.GET, "/api/v1/config/app").permitAll()
```

The full `authorizeHttpRequests` block after the change:
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/api/v1/battle/**").permitAll()
    .requestMatchers("/ws/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/v1/results").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/v1/config/app").permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated())
```

Also add the `HttpMethod` import if not present:
```java
import org.springframework.http.HttpMethod;
```

- [ ] **Step 9: Run tests to confirm they pass**

```bash
cd BES && mvn test -Dtest=AppConfigControllerIntegrationTest
```
Expected: all 4 tests PASS.

- [ ] **Step 10: Run full test suite**

```bash
cd BES && mvn test
```
Expected: all tests pass.

- [ ] **Step 11: Commit**

```bash
git add BES/src/main/resources/db/migration/V20__add_app_config.sql \
        BES/src/main/java/com/example/BES/models/AppConfig.java \
        BES/src/main/java/com/example/BES/respositories/AppConfigRepository.java \
        BES/src/main/java/com/example/BES/services/AppConfigService.java \
        BES/src/main/java/com/example/BES/controllers/AppConfigController.java \
        BES/src/main/java/com/example/BES/config/SecurityConfig.java \
        BES/src/test/java/com/example/BES/controllers/AppConfigControllerIntegrationTest.java
git commit -m "feat: AppConfig entity, V20 migration, GET/POST /api/v1/config/app with WS broadcast"
```

---

## Task 5: Login.vue — Cinematic Login Screen

**Files:**
- Modify: `BES-frontend/src/views/Login.vue`

Login is the first impression of the new design. The left panel gets a Display-tier "BES" hero; the right panel gets a parallelogram form with accent border-left.

- [ ] **Step 1: Replace the template** — Keep `<script setup>` unchanged. Replace `<template>` entirely:

```html
<template>
  <div class="min-h-screen flex overflow-hidden bg-surface-950 relative">

    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <!-- ── Left panel: BES hero ───────────────────────────────── -->
    <div class="hidden lg:flex lg:w-[52%] relative flex-col justify-between p-14 overflow-hidden">
      <div class="absolute inset-0 bg-surface-900"></div>
      <div class="corner-bar-tl" style="height: 40%"></div>
      <div class="corner-bar-bl" style="width: 40%"></div>

      <!-- Wordmark -->
      <div class="relative z-10 flex items-center gap-2.5">
        <div class="glow-dot"></div>
        <span class="type-body tracking-[0.12em]">BES</span>
      </div>

      <!-- Hero -->
      <div class="relative z-10">
        <div class="type-display mb-6">BES</div>
        <div class="section-rule mb-6">
          <div class="section-rule-line"></div>
        </div>
        <p class="type-body text-content-secondary leading-relaxed max-w-md">
          The all-in-one platform for street dance battle events — registration, judging, real-time battles.
        </p>
        <div class="flex flex-wrap gap-2 mt-8">
          <span class="badge-neutral px-3 py-1.5">Event Management</span>
          <span class="badge-neutral px-3 py-1.5">Audition Control</span>
          <span class="badge-neutral px-3 py-1.5">Battle System</span>
        </div>
      </div>

      <!-- Footer -->
      <div class="relative z-10 type-label text-content-muted">
        &copy; {{ new Date().getFullYear() }} BES Platform
      </div>
    </div>

    <!-- ── Right panel: Login form ───────────────────────────── -->
    <div class="flex-1 flex items-center justify-center p-8 relative z-10">
      <div class="w-full max-w-sm relative">
        <!-- Corner bars on form panel -->
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <div class="p-8 bg-surface-900 border border-[rgba(255,255,255,0.07)]"
          style="clip-path: polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">

          <div class="mb-8">
            <div class="type-page-title mb-1">Sign In</div>
            <p class="type-label text-content-muted">Battle Event System</p>
          </div>

          <form @submit.prevent="submitLogin" class="space-y-4">
            <div>
              <label class="type-label text-content-muted block mb-2">Username</label>
              <input
                v-model="username"
                type="text"
                placeholder="Username"
                class="input-base w-full"
                autocomplete="username"
              />
            </div>
            <div>
              <label class="type-label text-content-muted block mb-2">Password</label>
              <div class="relative">
                <input
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="Password"
                  class="input-base w-full pr-10"
                  autocomplete="current-password"
                />
                <button type="button" @click="showPassword = !showPassword"
                  class="absolute right-3 top-1/2 -translate-y-1/2 type-label text-content-muted hover:text-content-primary transition-colors">
                  <i class="pi" :class="showPassword ? 'pi-eye-slash' : 'pi-eye'"></i>
                </button>
              </div>
            </div>

            <button
              type="submit"
              :disabled="isLoading"
              class="w-full py-3 type-body bg-accent text-surface-900 transition-all duration-200 disabled:opacity-50 mt-2"
              style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)">
              {{ isLoading ? 'Signing in...' : 'Sign In' }}
            </button>
          </form>
        </div>
      </div>
    </div>

    <!-- Error modal -->
    <ActionDoneModal
      :show="showModal"
      :title="modalTitle"
      :variant="modalVariant"
      @accept="handleAccept"
      @close="showModal = false"
    >
      <p class="type-body text-content-secondary">{{ modalMessage }}</p>
    </ActionDoneModal>
  </div>
</template>
```

- [ ] **Step 2: Verify in browser** — Navigate to http://localhost:5173/login. Expect: dark left panel with large "BES" display text, parallelogram form on the right, cinematic layout.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/Login.vue
git commit -m "style: cinematic login screen with display-tier hero and parallelogram form"
```

---

## Task 6: MainMenu.vue — Cinematic Quick-Action Home

**Files:**
- Modify: `BES-frontend/src/views/MainMenu.vue`

- [ ] **Step 1: Replace the template** — Keep `<script setup>` unchanged. Replace `<template>`:

```html
<template>
  <div class="page-container relative">
    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <div class="relative z-10">
      <!-- Page header -->
      <div class="mb-8">
        <div class="type-page-title mb-1">Home</div>
        <p class="type-label text-content-muted">{{ roleDisplay?.label ?? 'Welcome' }}</p>
      </div>

      <!-- Quick actions section -->
      <div class="section-rule mb-6">
        <span class="section-rule-label">Quick Actions</span>
        <div class="section-rule-line"></div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">

        <!-- Events card (Admin / Organiser) -->
        <router-link
          v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
          to="/events"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-calendar text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Events</div>
          <p class="type-label text-content-muted">Manage and browse events</p>
        </router-link>

        <!-- Audition List -->
        <router-link
          v-if="activeEvent"
          :to="{ name: 'Audition List' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-list text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Audition</div>
          <p class="type-label text-content-muted">Score and timer controls</p>
        </router-link>

        <!-- Participants (Admin / Organiser) -->
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER')"
          :to="{ name: 'Update Event Details' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-users text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Participants</div>
          <p class="type-label text-content-muted">Manage registrations and judges</p>
        </router-link>

        <!-- Scoreboard (Admin / Organiser / Emcee) -->
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE')"
          :to="{ name: 'Score' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-chart-bar text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Scoreboard</div>
          <p class="type-label text-content-muted">Live leaderboard</p>
        </router-link>

        <!-- Battle Control (Admin / Organiser) -->
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER')"
          :to="{ name: 'Battle Control' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-bolt text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Battle</div>
          <p class="type-label text-content-muted">Bracket and match control</p>
        </router-link>

        <!-- Admin (Admin only) -->
        <router-link
          v-if="role === 'ROLE_ADMIN'"
          to="/admin"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-cog text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Admin</div>
          <p class="type-label text-content-muted">Genres, judges, theme config</p>
        </router-link>

      </div>

      <!-- No event selected hint -->
      <div v-if="!activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE')"
        class="mt-8 p-4 semantic-chip-warning flex items-start gap-3">
        <div class="w-2 h-2 rounded-full bg-amber-400 box-shadow-[0_0_6px_rgba(245,158,11,0.8)] mt-0.5 flex-shrink-0"></div>
        <div>
          <div class="type-body text-amber-400 mb-1">No Active Event</div>
          <p class="type-label text-content-muted">
            Select an event to access audition, scoring, and battle features.
          </p>
          <router-link :to="{ name: 'EventSelector' }" class="type-label text-accent underline mt-2 inline-block">
            Select Event →
          </router-link>
        </div>
      </div>

    </div>
  </div>
</template>
```

At the top of `<script setup>`, add if not present:
```js
import { computed } from 'vue'
import { useAuthStore } from '@/utils/auth'

const authStore = useAuthStore()
const role = computed(() => authStore.user?.role?.[0]?.authority ?? '')
const activeEvent = computed(() => authStore.activeEvent)
const roleDisplay = computed(() => {
  const labels = { ROLE_ADMIN: 'Admin', ROLE_ORGANISER: 'Organiser', ROLE_JUDGE: 'Judge', ROLE_EMCEE: 'Emcee' }
  const label = labels[role.value]
  return label ? { label } : null
})
```

- [ ] **Step 2: Verify** — Navigate to `/`. Expect: parallelogram quick-action cards with corner bars, section rule, color bleed.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/MainMenu.vue
git commit -m "style: cinematic MainMenu with parallelogram quick-action cards"
```

---

## Task 7: EventSelector.vue + Events.vue + EventCard.vue

**Files:**
- Modify: `BES-frontend/src/views/EventSelector.vue`
- Modify: `BES-frontend/src/views/Events.vue`
- Modify: `BES-frontend/src/components/EventCard.vue`

- [ ] **Step 1: Update EventSelector.vue** — Read the current template. Apply these targeted changes:

a) Wrap the page root `<div>` with `relative` and add `<div class="color-bleed"></div>` as first child.

b) Replace the page title element with:
```html
<div class="type-page-title mb-1">Select Event</div>
<p class="type-label text-content-muted">{{ events.length }} available</p>
```

c) Replace the event card grid items. Each event card chip should be:
```html
<button
  v-for="event in events"
  :key="event.id"
  @click="selectEvent(event)"
  class="card-hover p-5 text-left relative group w-full"
>
  <div class="corner-bar-tl"></div>
  <div class="type-body mb-1">{{ event.name }}</div>
  <div class="type-label text-content-muted">{{ event.date ?? 'No date set' }}</div>
</button>
```

d) Replace section-divider `border-b` elements with `.section-rule`:
```html
<div class="section-rule mb-4">
  <span class="section-rule-label">Available Events</span>
  <div class="section-rule-line"></div>
</div>
```

- [ ] **Step 2: Update Events.vue** — Read the current template. Apply these targeted changes:

a) Add `color-bleed` div to page root.

b) Replace page title with `type-page-title` class.

c) Replace each event list row with a parallelogram chip:
```html
<div class="para-chip px-4 py-3 flex items-center gap-4 mb-2">
  <span class="type-body">{{ event.name }}</span>
  <span class="badge-neutral ml-auto">{{ event.status }}</span>
</div>
```

d) Add section rules before grouped sections.

- [ ] **Step 3: Update EventCard.vue** — Read the current component. Apply:

a) Replace card container `class` — remove `rounded-2xl`, apply parallelogram:
```html
<div class="card-hover p-5 relative group w-full" style="...">
```
Note: EventCard has an absolute-positioned action panel. Since `clip-path` clips all children, keep the card's outer div WITHOUT `clip-path` but WITH a `::before` pseudo-element for the visual parallelogram background. OR: Verify that the action panel is positioned outside the card in the parent — in that case, `card-hover` with clip-path is safe.

Check in `Events.vue` how EventCard is used. If the action panel overlays from outside the card boundary, use `card-hover` with clip-path. If the panel overflows the card, use this instead for EventCard's container:
```html
<div class="relative bg-[rgba(255,255,255,0.04)] border border-[rgba(255,255,255,0.07)] p-5 transition-all duration-200 hover:border-[color:var(--accent-muted)] hover:bg-[rgba(255,255,255,0.06)] group">
  <div class="corner-bar-tl"></div>
```

b) Replace event name heading with `type-body` class.

c) Replace any `font-heading`, `font-semibold`, `rounded-full` badge classes with cinematic equivalents.

- [ ] **Step 4: Verify** — Navigate to `/events` and `/event/select`. Check parallelogram cards, section rules.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/EventSelector.vue \
        BES-frontend/src/views/Events.vue \
        BES-frontend/src/components/EventCard.vue
git commit -m "style: cinematic EventSelector, Events, EventCard"
```

---

## Task 8: EventDetails.vue — Section Rules and Stat Boxes

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue` (1546 lines — targeted changes only)

This is a large file. Make targeted search-and-replace changes:

- [ ] **Step 1: Add color-bleed to page root** — Find the outermost `<div class="page-container ...">` and add as the very first child inside it:
```html
<div class="color-bleed"></div>
```

- [ ] **Step 2: Replace `border-b border-surface-700` section dividers** — Find all occurrences (likely 5-8) of:
```html
<h2 class="... font-heading ...">Section Title</h2>
```
And the adjacent `border-b` divider. Replace with section-rule pattern:
```html
<div class="section-rule mb-4">
  <span class="section-rule-label">Section Title</span>
  <div class="section-rule-line"></div>
</div>
```

- [ ] **Step 3: Replace stat boxes** — Find all `stat-card` elements and ensure they use `type-stat` for the value and `type-label` for the label:
```html
<div class="stat-card">
  <div class="type-stat">{{ value }}</div>
  <div class="type-label text-content-muted mt-1">Label</div>
</div>
```

- [ ] **Step 4: Replace page title** — Find the main `h1` or page title element:
```html
<div class="type-page-title mb-1">{{ eventName }}</div>
```

- [ ] **Step 5: Replace `rounded-2xl` card containers for genre accordion items** — Find genre cards and apply:
```html
<div class="para-chip p-4 mb-3 relative">
```

- [ ] **Step 6: Replace `rounded-full` badges** — Find all `badge-*` class elements that use `rounded-full` and remove `rounded-full` (the updated `.badge-*` in base.css already applies parallelogram clip-path).

- [ ] **Step 7: Run dev and verify** — Open an event's details page. Check section rules, stat boxes, no rounded corners.

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "style: cinematic EventDetails — section rules, parallelogram stat boxes"
```

---

## Task 9: UpdateEventDetails.vue — Parallelogram Rows + Semantic Chips

**Files:**
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue`

- [ ] **Step 1: Read the file** — Run:
```bash
cat BES-frontend/src/views/UpdateEventDetails.vue
```

- [ ] **Step 2: Apply changes** — This is 215 lines. Make targeted changes:

a) Add `color-bleed` to page root.

b) Add page title with `type-page-title`.

c) Replace table rows with parallelogram chips. Each participant row should be:
```html
<div class="para-chip px-4 py-3 flex items-center gap-3 mb-2">
  <span class="type-body">{{ participant.stageName || participant.name }}</span>
  <!-- status chips use semantic-chip-* classes -->
  <span v-if="participant.verified" class="badge-success ml-auto">Verified</span>
  <span v-else class="badge-warning">Pending</span>
</div>
```

d) Replace section dividers with `.section-rule`.

e) Error/warning banners: replace with `.semantic-chip-error` or `.semantic-chip-warning`.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/UpdateEventDetails.vue
git commit -m "style: cinematic UpdateEventDetails — parallelogram rows, semantic chips"
```

---

## Task 10: AuditionList.vue — Context Bar + Filter Chips

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

- [ ] **Step 1: Apply changes** — Key targeted changes:

a) Context bar (top): Convert to parallelogram chip with `type-label` text:
```html
<div class="para-chip px-4 py-2 flex items-center gap-4 mb-4">
  <div class="glow-dot"></div>
  <span class="type-label text-content-muted">{{ activeEvent?.name }}</span>
  <span class="badge-neutral ml-auto">{{ participants.length }} registered</span>
</div>
```

b) Filter chips: Replace `rounded-full` filter buttons with parallelogram:
```html
<button
  v-for="genre in genres"
  :key="genre.id"
  @click="selectGenre(genre)"
  class="para-chip-sm px-3 py-1 type-label transition-all duration-150"
  :class="selectedGenre?.id === genre.id
    ? 'text-accent border-[color:var(--accent-muted)]'
    : 'text-content-muted hover:text-content-primary'"
>{{ genre.name }}</button>
```

c) Section rules before "Scored" / "Unscored" groups.

d) Participant rows → `para-chip` with `type-body` name.

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "style: cinematic AuditionList — parallelogram context bar, filter chips"
```

---

## Task 11: Score.vue — Number/Stat Tier for Scores

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (1029 lines — targeted changes)

- [ ] **Step 1: Apply targeted changes:**

a) Add `color-bleed` to page root.

b) Podium / top-3 score values → `type-stat` class:
```html
<div class="type-stat">{{ score.totalScore }}</div>
<div class="type-label text-content-muted mt-1">{{ score.participantName }}</div>
```

c) Leaderboard rows → `para-chip`:
```html
<div class="para-chip px-4 py-3 flex items-center gap-4 mb-2">
  <span class="type-stat text-[24px]">{{ rank }}</span>
  <span class="type-body">{{ entry.name }}</span>
  <span class="type-stat text-[20px] ml-auto">{{ entry.score }}</span>
</div>
```

d) Section rules before "Top 3", "All Scores", "By Judge" sections.

e) Stat boxes (total registered, genres) → `stat-card` with `type-stat` value.

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "style: cinematic Score — number/stat tier, parallelogram leaderboard rows"
```

---

## Task 12: BattleControl.vue — Section Rules + Bracket Slots

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (1736 lines — targeted changes)

- [ ] **Step 1: Apply targeted changes:**

a) Add `color-bleed` to page root.

b) All `border-b border-surface-700` section dividers → `.section-rule`.

c) Bracket seed slot elements → `para-chip`:
```html
<div class="para-chip px-3 py-2 flex items-center gap-2">
  <span class="type-label text-content-muted">{{ slotIndex + 1 }}</span>
  <span class="type-body">{{ slot.name ?? 'Empty' }}</span>
</div>
```

d) Phase indicator chips (IDLE / LOCKED / VOTING / REVEALED) → `.semantic-chip-*`:
- IDLE → `badge-neutral`
- LOCKED → `badge-warning`
- VOTING → `badge-primary` (accent)
- REVEALED → `badge-success`

e) Page title → `type-page-title`.

f) Section labels → `type-section` with section-rule.

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "style: cinematic BattleControl — section rules, parallelogram bracket slots"
```

---

## Task 13: AdminPage.vue — Theme Config Color Picker

**Files:**
- Modify: `BES-frontend/src/views/AdminPage.vue`

- [ ] **Step 1: Add imports to `<script setup>`**

Add to existing imports:
```js
import { getAppConfig, postAppConfig } from '@/utils/api'
```

Add new refs after existing refs:
```js
const accentColor = ref('#ffffff')
const accentApplying = ref(false)

const presetColors = [
  { label: 'White / Silver', value: '#ffffff' },
  { label: 'Gold', value: '#f59e0b' },
  { label: 'Cyan', value: '#06b6d4' },
  { label: 'Purple', value: '#8b5cf6' },
  { label: 'Orange', value: '#f97316' },
  { label: 'Emerald', value: '#10b981' },
]
```

Add `onMounted` fetch (extend existing `onMounted` or create one):
```js
// inside onMounted:
try {
  const cfg = await getAppConfig()
  if (cfg?.accentColor) accentColor.value = cfg.accentColor
} catch { /* keep default */ }
```

Add apply function:
```js
const applyAccentToAll = async () => {
  accentApplying.value = true
  try {
    await postAppConfig(accentColor.value)
    // parent App.vue will pick up the WS broadcast
  } catch {
    openModal('Error', 'Failed to apply accent color. Please try again.', 'error')
  } finally {
    accentApplying.value = false
  }
}
```

- [ ] **Step 2: Add Theme Config section to the template** — Find the end of the last section in the template and add a new section before the closing `</div>`:

```html
<!-- ── Theme Config ────────────────────────────────────────────────── -->
<div class="section-rule mb-6 mt-10">
  <span class="section-rule-label">Theme Config</span>
  <div class="section-rule-line"></div>
</div>

<div class="card p-6 relative max-w-lg">
  <div class="corner-bar-tl"></div>
  <div class="corner-bar-bl"></div>

  <div class="mb-4">
    <div class="type-label text-content-muted mb-2">Current Accent</div>
    <div
      class="w-full h-12 para-chip flex items-center justify-center type-label"
      :style="{ background: accentColor, color: '#111' }"
    >{{ accentColor }}</div>
  </div>

  <div class="mb-4">
    <div class="type-label text-content-muted mb-2">Presets</div>
    <div class="flex flex-wrap gap-2">
      <button
        v-for="preset in presetColors"
        :key="preset.value"
        @click="accentColor = preset.value"
        class="w-8 h-8 transition-transform hover:scale-110"
        style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
        :style="{ background: preset.value, outline: accentColor === preset.value ? '2px solid rgba(255,255,255,0.6)' : 'none', outlineOffset: '2px' }"
        :title="preset.label"
      ></button>
    </div>
  </div>

  <div class="mb-4 flex items-center gap-3">
    <div class="type-label text-content-muted flex-shrink-0">Custom</div>
    <input
      type="color"
      v-model="accentColor"
      class="w-10 h-8 cursor-pointer border-0 bg-transparent"
    />
    <span class="type-label text-content-muted font-source">{{ accentColor }}</span>
  </div>

  <button
    @click="applyAccentToAll"
    :disabled="accentApplying"
    class="w-full py-2 type-body bg-accent text-surface-900 disabled:opacity-50 transition-all"
    style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)">
    {{ accentApplying ? 'Applying...' : 'Apply to All Clients' }}
  </button>
</div>
```

- [ ] **Step 3: Apply cinematic styling to existing sections** — AdminPage has genre, judge, image, and feedback sections. For each:

a) Replace section headers `<h2 class="font-heading ...">` with:
```html
<div class="section-rule mb-4">
  <span class="section-rule-label">Genres</span>
  <div class="section-rule-line"></div>
</div>
```

b) Replace list row cards with `para-chip` containers.

c) Replace `rounded-full` badges with `badge-*` (parallelogram already applied by updated base.css).

- [ ] **Step 4: Verify** — Navigate to `/admin`. Check Theme Config section with color picker. Select a preset — it should update the swatch. Click "Apply to All Clients" — accent should update across the app live.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/AdminPage.vue
git commit -m "feat: AdminPage Theme Config section with accent color picker and WS broadcast"
```

---

## Task 14: Results.vue + ResultsQR.vue

**Files:**
- Modify: `BES-frontend/src/views/Results.vue`
- Modify: `BES-frontend/src/views/ResultsQR.vue`

- [ ] **Step 1: Apply to Results.vue** — Targeted changes:

a) Add `color-bleed` to page root.

b) Page title → `type-page-title`.

c) Reference code input → `input-base` (already styled; verify no regressions).

d) Result rows → `para-chip` with score in `type-stat`:
```html
<div class="para-chip px-4 py-3 flex items-center gap-4 mb-2">
  <span class="type-stat text-[22px]">{{ result.rank }}</span>
  <div class="flex-1">
    <div class="type-body">{{ result.name }}</div>
    <div class="type-label text-content-muted">{{ result.genre }}</div>
  </div>
  <span class="type-stat text-[22px]">{{ result.score }}</span>
</div>
```

e) Section rules between sections.

- [ ] **Step 2: Apply to ResultsQR.vue** — Same parallelogram result rows + color bleed + page title pattern.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/Results.vue BES-frontend/src/views/ResultsQR.vue
git commit -m "style: cinematic Results and ResultsQR — parallelogram rows, number/stat tier"
```

---

## Task 15: Shared Components

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue`
- Modify: `BES-frontend/src/components/FeedbackPopout.vue`
- Modify: `BES-frontend/src/components/Timer.vue`
- Modify: `BES-frontend/src/components/MiniScoreMenu.vue`
- Modify: `BES-frontend/src/components/ReusableDropdown.vue`
- Modify: `BES-frontend/src/components/SwipeableCardsV2.vue`
- Modify: `BES-frontend/src/components/PairScoreCards.vue`

Read each file before editing. Apply these universal patterns:

- [ ] **Step 1: EmceeRoundView.vue** — Section rules for round separators. Participant status badges → `badge-success` / `badge-warning` (parallelogram from base.css). Participant name → `type-body`. Count label → `type-stat`.

- [ ] **Step 2: FeedbackPopout.vue** — Tag chips → `para-chip-sm type-label`. Section headers → `section-rule`. Submit button → `bg-accent` parallelogram.

- [ ] **Step 3: Timer.vue** — Time display → `type-stat` (42px Anton SC). Label → `type-label`. Start/stop buttons → `para-chip bg-accent type-body` for primary, `para-chip type-label` for secondary.

- [ ] **Step 4: MiniScoreMenu.vue** — Each score option → `para-chip-sm type-label` hover state with `text-accent`.

- [ ] **Step 5: ReusableDropdown.vue** — Dropdown trigger → `para-chip type-label`. Options list → `bg-surface-800 border border-[rgba(255,255,255,0.07)]`. Each option → `type-body hover:bg-[rgba(255,255,255,0.04)]`.

- [ ] **Step 6: SwipeableCardsV2.vue** — Card outer container: keep existing width constraints (`w-[97%] p-2` per CLAUDE.md — do NOT increase padding). Replace `rounded-2xl` with parallelogram but keep the card compact for mobile judge UX. Score number → `type-stat`. Participant name → `type-body`.

**IMPORTANT for SwipeableCardsV2:** Per CLAUDE.md, the judge scoring card has strict size constraints: `py-4` keypad buttons, `w-[97%]` card width, `p-2` card padding. Do not increase these regardless of the design language change. The parallelogram clip-path can be applied but sizing must remain untouched.

- [ ] **Step 7: PairScoreCards.vue** — Score values → `type-stat`. Participant name → `type-body`. VS divider → `section-rule`. Card containers → `para-chip`.

- [ ] **Step 8: ActionDoneModal.vue** — This modal is used across every view for confirmations and logout. Read the file then apply:

a) Add `relative` to the modal container and add corner bars:
```html
<div class="relative bg-surface-800 border border-[rgba(255,255,255,0.07)] p-6"
     style="clip-path: polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">
  <div class="corner-bar-tl"></div>
  <div class="corner-bar-bl"></div>
```

b) Modal title → `type-page-title`.

c) For warning/error variants, wrap with the corresponding semantic chip class:
```html
<!-- Variant warning -->
<div class="semantic-chip-warning p-4 mb-4 flex items-start gap-3">
  <div class="w-2 h-2 rounded-full bg-amber-400 flex-shrink-0 mt-1" style="box-shadow: 0 0 6px rgba(245,158,11,0.8)"></div>
  <slot></slot>
</div>
<!-- Variant error -->
<div class="semantic-chip-error p-4 mb-4 flex items-start gap-3">
  <div class="w-2 h-2 rounded-full bg-red-400 flex-shrink-0 mt-1" style="box-shadow: 0 0 6px rgba(239,68,68,0.8)"></div>
  <slot></slot>
</div>
```

d) Action buttons → parallelogram shape. Confirm/accept → `bg-accent para-chip type-label`. Cancel/close → `para-chip type-label text-content-muted`.

- [ ] **Step 9: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue \
        BES-frontend/src/components/FeedbackPopout.vue \
        BES-frontend/src/components/Timer.vue \
        BES-frontend/src/components/MiniScoreMenu.vue \
        BES-frontend/src/components/ReusableDropdown.vue \
        BES-frontend/src/components/SwipeableCardsV2.vue \
        BES-frontend/src/components/PairScoreCards.vue
git commit -m "style: cinematic shared components — Anton SC, parallelogram chips, section rules"
```

---

## Final Verification

- [ ] **Step 1: Run full frontend test suite**
```bash
cd BES-frontend && npm test
```
Expected: all tests pass.

- [ ] **Step 2: Run full backend test suite**
```bash
cd BES && mvn test
```
Expected: all tests pass.

- [ ] **Step 3: Docker smoke test**
```bash
docker-compose up --build --no-cache
```
Open https://localhost. Verify: Anton SC everywhere, parallelogram shapes, cinematic navbar, scanlines, color bleed on page roots. Log in as admin and test accent color picker — all tabs should update live via WebSocket.

- [ ] **Step 4: Light mode check** — Toggle light mode. Verify: scanlines at half opacity, color bleed hidden, accent inverts to dark automatically.

- [ ] **Step 5: Do NOT change** — Confirm `BattleOverlay.vue`, `BracketVisualization.vue`, `Chart.vue` are unchanged: `git diff HEAD -- BES-frontend/src/views/BattleOverlay.vue BES-frontend/src/views/BracketVisualization.vue BES-frontend/src/views/Chart.vue` should show no diff.
