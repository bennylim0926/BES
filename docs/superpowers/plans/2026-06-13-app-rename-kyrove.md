# App Rename: BES → Kyrove Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace user-facing "BES" / "Battle Event System" branding with "Kyrove" across the frontend, docs, and email templates. Internal code references (package names, directory names, env vars) stay as-is.

**Architecture:** Extract the app name into a single `APP_NAME` constant in the frontend. All Vue components reference this constant instead of hardcoding "BES". Backend `spring.application.name` and Docker env vars remain unchanged (internal surface only).

**Tech Stack:** Vue 3, Spring Boot (no new dependencies)

**Spec:** `docs/superpowers/specs/2026-06-13-app-rename-kyrove-design.md`

---

### Task 1: Add APP_NAME constant to frontend config

**Files:**
- Create: `BES-frontend/src/utils/branding.js`
- Modify: `BES-frontend/src/App.vue:199-202`
- Modify: `BES-frontend/src/views/Login.vue:64-110`
- Modify: `BES-frontend/src/views/Results.vue:83-113`
- Modify: `BES-frontend/src/views/ResultsQR.vue:60-63`
- Modify: `BES-frontend/index.html:7`

- [ ] **Step 1: Create branding config module**

Create `BES-frontend/src/utils/branding.js`:

```js
export const APP_NAME = 'Kyrove'
export const APP_TAGLINE = 'Every moment in the groove.'
export const APP_DESCRIPTION = 'The all-in-one platform for dance battle events.'
```

- [ ] **Step 2: Verify file was created**

Run: `cat BES-frontend/src/utils/branding.js`
Expected: file content matches above.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/branding.js
git commit -m "feat: add branding config module with Kyrove constants

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Update frontend user-facing branding

**Files:**
- Modify: `BES-frontend/index.html:5,7`
- Modify: `BES-frontend/src/App.vue:199-202`
- Modify: `BES-frontend/src/views/Login.vue:64,73,78,94,110`
- Modify: `BES-frontend/src/views/Results.vue:83,92,97,113`
- Modify: `BES-frontend/src/views/ResultsQR.vue:60,63`

- [ ] **Step 1: Update index.html `<title>` and favicon reference**

Edit `BES-frontend/index.html`:

```html
<!-- Line 5: update favicon reference (keep filename for now, or rename later) -->
<link rel="icon" href="/bes.ico">

<!-- Line 7: update title -->
<title>Kyrove</title>
```

Old lines (for matching):
```html
    <link rel="icon" href="/bes.ico">
```
and
```html
    <title>BES</title>
```

- [ ] **Step 2: Update App.vue navbar wordmark**

Edit `BES-frontend/src/App.vue`, lines 199-202. Replace:

```html
        <!-- Left: BES wordmark + glowing dot -->
        <router-link to="/" class="flex items-center gap-2.5 no-underline shrink-0">
          <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">BES</span>
```

With:

```html
        <!-- Left: Kyrove wordmark + glowing dot -->
        <router-link to="/" class="flex items-center gap-2.5 no-underline shrink-0">
          <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">KYROVE</span>
```

Add the import at the top of the `<script setup>` section in App.vue (find the existing imports and add):

```js
import { APP_NAME } from './utils/branding.js'
```

Then use it in the template:
```html
          <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">{{ APP_NAME }}</span>
```

- [ ] **Step 3: Update Login.vue**

Edit `BES-frontend/src/views/Login.vue`:

Find the `<script setup>` section and add:
```js
import { APP_NAME, APP_TAGLINE } from '../utils/branding.js'
```

Replace lines 64-110:

Old (line 64):
```html
    <!-- ── Left panel: BES hero ───────────────────────────────── -->
```
New:
```html
    <!-- ── Left panel: Kyrove hero ────────────────────────────── -->
```

Old (line 73):
```html
        <span class="type-body tracking-[0.12em]">BES</span>
```
New:
```html
        <span class="type-body tracking-[0.12em]">{{ APP_NAME }}</span>
```

Old (line 78):
```html
        <div class="type-display mb-6">BES</div>
```
New:
```html
        <div class="type-display mb-6">{{ APP_NAME }}</div>
```

Old (line 94):
```html
        &copy; {{ new Date().getFullYear() }} BES Platform
```
New:
```html
        &copy; {{ new Date().getFullYear() }} {{ APP_NAME }} Platform
```

Old (line 110):
```html
            <p class="type-label text-content-muted">Battle Event System</p>
```
New:
```html
            <p class="type-label text-content-muted">{{ APP_TAGLINE }}</p>
```

- [ ] **Step 4: Update Results.vue**

Edit `BES-frontend/src/views/Results.vue`:

Find the `<script setup>` section and add:
```js
import { APP_NAME } from '../utils/branding.js'
```

Replace lines 83-113:

Old (line 83):
```html
      <!-- ── Left panel: BES hero (≥md) ────────────────────────────── -->
```
New:
```html
      <!-- ── Left panel: Kyrove hero (≥md) ─────────────────────────── -->
```

Old (line 92):
```html
          <span class="type-body tracking-[0.12em]">BES</span>
```
New:
```html
          <span class="type-body tracking-[0.12em]">{{ APP_NAME }}</span>
```

Old (line 97):
```html
          <div class="type-display mb-6">BES</div>
```
New:
```html
          <div class="type-display mb-6">{{ APP_NAME }}</div>
```

Old (line 113):
```html
          &copy; {{ new Date().getFullYear() }} BES Platform
```
New:
```html
          &copy; {{ new Date().getFullYear() }} {{ APP_NAME }} Platform
```

- [ ] **Step 5: Update ResultsQR.vue**

Edit `BES-frontend/src/views/ResultsQR.vue`:

Find the `<script setup>` section and add:
```js
import { APP_NAME } from '../utils/branding.js'
```

Replace lines 60-63:

Old (line 60):
```html
        <!-- BES branding -->
```
New:
```html
        <!-- Kyrove branding -->
```

Old (line 63):
```html
          <span class="type-body tracking-[0.12em]">BES</span>
```
New:
```html
          <span class="type-body tracking-[0.12em]">{{ APP_NAME }}</span>
```

- [ ] **Step 6: Run frontend dev server to verify no build errors**

Run: `cd BES-frontend && npm run dev`
Expected: dev server starts without errors. Check that Login, Results, and ResultsQR pages render "KYROVE" instead of "BES".

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/index.html BES-frontend/src/App.vue BES-frontend/src/views/Login.vue BES-frontend/src/views/Results.vue BES-frontend/src/views/ResultsQR.vue BES-frontend/src/utils/branding.js
git commit -m "feat: rebrand frontend UI — BES → Kyrove

Replace all user-facing 'BES' and 'Battle Event System' branding with
'Kyrove' and 'Every moment in the groove.' tagline. App name extracted
to a single branding.js config constant.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Update documentation

**Files:**
- Modify: `README.md:1,21,24,34`
- Modify: `CLAUDE.md:7`

- [ ] **Step 1: Update README.md**

Edit `README.md`:

Old (line 1):
```markdown
# BES — Battle Event System
```
New:
```markdown
# Kyrove — Every moment in the groove.
```

Old (line 3 — unchanged, but verify description still works):
The description "Full-stack web app for managing dance battle events..." stays. It's accurate and doesn't contain the old name.

Old (lines 21, 24 — directory references, keep as-is since directories haven't moved):
Lines 21 and 24 reference `BES/` and `BES-frontend/` directories. These stay — the directories are not being renamed.

Old (line 34):
```markdown
It is **optional personal dev tooling** — you do not need it to run or contribute to BES.
```
New:
```markdown
It is **optional personal dev tooling** — you do not need it to run or contribute to Kyrove.
```

- [ ] **Step 2: Update CLAUDE.md description**

Edit `CLAUDE.md`, line 7:

Old:
```markdown
**BES (Battle Event System)** — a full-stack web app for managing dance battle events (registration, judging, scoring, real-time battle control, results portal).
```
New:
```markdown
**Kyrove (formerly BES — Battle Event System)** — a full-stack web app for managing dance battle events (registration, judging, scoring, real-time battle control, results portal). The product is called Kyrove; internal code references (package names, directory names, env vars) still use `BES`.
```

All other `BES/` directory references in CLAUDE.md stay — they refer to the actual directory paths which haven't changed.

- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: update README and CLAUDE.md — BES → Kyrove

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Verify — full lint and build check

**Files:** None modified, verification only.

- [ ] **Step 1: Run frontend lint**

Run: `cd BES-frontend && npm run build 2>&1 | tail -20`
Expected: Build succeeds with no errors. "Build complete" or similar.

- [ ] **Step 2: Run backend build**

Run: `cd BES && mvn clean package -DskipTests 2>&1 | tail -10`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run frontend tests**

Run: `cd BES-frontend && npm test 2>&1`
Expected: All tests pass. If any test assertions reference "BES" in rendered output, those tests need updating — note the failing tests and fix them.

- [ ] **Step 4: Fix any test assertions that reference old name**

If `npm test` shows failures related to "BES" in expected output text, search for those test files:

Run: `grep -rn "BES\|Battle Event" BES-frontend/src/utils/__tests__/ 2>/dev/null`

For each test file that asserts "BES" in rendered output, update the assertion to match "KYROVE" or use the `APP_NAME` import.

Example: if a test has:
```js
expect(wrapper.text()).toContain('BES')
```
Change to:
```js
import { APP_NAME } from '../branding.js'
expect(wrapper.text()).toContain(APP_NAME)
```

- [ ] **Step 5: Re-run tests after fixes**

Run: `cd BES-frontend && npm test 2>&1`
Expected: All tests pass.

- [ ] **Step 6: Final commit (if test fixes were needed)**

```bash
git add BES-frontend/src/utils/__tests__/
git commit -m "test: update test assertions — BES → Kyrove

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Domain Registration (Manual)

These steps require a domain registrar and DNS provider — not automatable via code.

1. Register `kyrove.com` (primary) via preferred registrar (Namecheap, Porkbun, Cloudflare, etc.)
2. Register `kyrove.app` and `kyrove.events` as redirects
3. Configure DNS A records pointing to the production server IP
4. Update Nginx `server_name` to include the new domains
5. Provision SSL certificates (Let's Encrypt) for the new domains

---

## Self-Review

**1. Spec coverage:**
- ✅ Extract app name to config constant → Task 1
- ✅ Update `<title>` tag → Task 2 Step 1
- ✅ Update navbar → Task 2 Step 2
- ✅ Update Login page → Task 2 Step 3
- ✅ Update Results page → Task 2 Step 4
- ✅ Update ResultsQR page → Task 2 Step 5
- ✅ Update README → Task 3 Step 1
- ✅ Update CLAUDE.md → Task 3 Step 2
- ✅ No backend package rename (explicitly out of scope per spec)
- ✅ No Docker env var rename (explicitly out of scope per spec)
- ✅ Domain registration noted as manual step

**2. Placeholder scan:** No TBD, TODO, or vague instructions. All code changes shown with exact old/new strings.

**3. Type consistency:** `APP_NAME`, `APP_TAGLINE`, `APP_DESCRIPTION` — consistent across all tasks. Import paths match the file created in Task 1.
