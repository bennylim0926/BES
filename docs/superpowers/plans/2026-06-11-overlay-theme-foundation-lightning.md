# Overlay Theme Foundation + Lightning Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the multi-theme overlay foundation (theme registry, accent color, round-card interstitial, particle/bolt primitives) and ship the Lightning theme — Phase 1 of issue #134, Fire deferred to a follow-up PR.

**Architecture:** Theme registry + round-label derivation live in a new `utils/overlayThemes.js` (pure, unit-testable). One-shot/ambient visuals live in two presentational components (`OverlayRoundCard.vue`, `LightningFx.vue`) driven by props/nonces from `BattleOverlay.vue`, which keeps all state. Per-theme visuals are CSS keyed off the existing `data-anim-theme` root attribute. Backend gains two persisted Event columns (`overlay_accent_color`, `show_round_card`) flowing through the existing `/battle/overlay-config` endpoint + WS topic — no new endpoints, no phase-machine changes.

**Tech Stack:** Vue 3 `<script setup>`, scoped CSS, inline SVG (`feSpecularLighting`, `stroke-dasharray` draw-on), Vitest, Spring Boot + Flyway + JUnit/MockMvc. No canvas, no GSAP, no new npm deps.

**Spec:** `docs/superpowers/specs/2026-06-11-battle-overlay-themes.md` (committed in Task 0)

**Key design decisions (resolving spec §7 open questions):**

| Q | Decision |
|---|---|
| Q1 IMPACT/HYPE | Kept as **legacy themes** (unchanged visuals/timing). `lightning` is a new third value. No data migration. Lets the user A/B old vs new. |
| Q2 animTheme column | Already exists (V36). New migration is **V37** for the two new columns. |
| Q5 live theme swap | Already works: overlay re-reads `overlayConfig` from WS; `data-anim-theme` flips CSS instantly. Mid-entrance swap is acceptable (organisers switch at IDLE). |
| Q6 reduced motion | `prefers-reduced-motion`: particle composable spawns nothing; CSS block disables fx layers/blur (Task 10). |
| Q7 preview pane | Deferred — user evaluates on the real overlay. |
| Q8 scoping | Event-scoped, same as #133 (`Event` row fields). |
| Round card for legacy themes | Round card only plays for non-legacy themes (`timing.titleTotal > 0`). IMPACT/HYPE behavior is byte-for-byte unchanged. |
| Smoke mode | No round card, no fx layer in Phase 1 (Chart.vue excluded per convention). `deriveRoundLabel` already returns `''` for smoke. |
| Restore paths | Mount/hydrate restores with phase VOTING/REVEALED/DECIDED pass `{ skipTitle: true }` so OBS refresh doesn't replay a 2.4s title card mid-battle. |
| Entrance cancel | New `entranceToken` (separate from `animToken`) — the LOCKED handler increments `animToken` as part of the *normal* new-pair flow, so it cannot be used to abort the title card. `entranceToken` increments only in `updateBattlePair` (new pair / empty pair). |

---

## File Structure

| Path | Change | Responsibility |
|---|---|---|
| `BES/src/main/resources/db/migration/V37__add_overlay_theme_config.sql` | Create | Two new event columns |
| `BES/src/main/java/com/example/BES/models/Event.java` | Modify | `overlayAccentColor`, `showRoundCard` fields |
| `BES/src/main/java/com/example/BES/dtos/battle/SetOverlayConfigDto.java` | Modify | Accept `lightning`, accent hex, round-card boolean |
| `BES/src/main/java/com/example/BES/services/BattleService.java` | Modify | Persist + return new fields in overlay config |
| `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java` | Modify | New overlay-config tests |
| `BES-frontend/src/utils/overlayThemes.js` | Create | Theme registry, `resolveTheme`, `deriveRoundLabel` |
| `BES-frontend/src/utils/overlayBolts.js` | Create | Hand-authored bolt path library |
| `BES-frontend/src/utils/overlayParticles.js` | Create | Ambient/burst particle composable |
| `BES-frontend/src/utils/__tests__/overlayThemes.test.js` | Create | Registry + label unit tests |
| `BES-frontend/src/utils/__tests__/overlayParticles.test.js` | Create | Composable unit tests |
| `BES-frontend/src/components/overlay/OverlayRoundCard.vue` | Create | Round-card interstitial (template + per-theme CSS) |
| `BES-frontend/src/components/overlay/LightningFx.vue` | Create | Atmosphere + one-shot bolt/flash layer |
| `BES-frontend/src/views/BattleOverlay.vue` | Modify | Registry wiring, entrance stages, themed CSS variants |
| `BES-frontend/src/views/BattleControl.vue` | Modify | Theme buttons from registry, accent picker, round-card toggle |
| `CLAUDE.md` | Modify | Latest-migration pointer |

---

### Task 0: Commit the design spec

**Files:**
- Add: `docs/superpowers/specs/2026-06-11-battle-overlay-themes.md` (already exists, untracked)

- [ ] **Step 0.1: Commit**

```bash
git add docs/superpowers/specs/2026-06-11-battle-overlay-themes.md
git commit -m "docs: add battle overlay theme system design spec (#134)"
```

---

### Task 1: Backend — persist accent + round-card toggle, accept `lightning`

**Files:**
- Test: `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java`
- Create: `BES/src/main/resources/db/migration/V37__add_overlay_theme_config.sql`
- Modify: `BES/src/main/java/com/example/BES/models/Event.java` (after `animTheme`, ~line 39)
- Modify: `BES/src/main/java/com/example/BES/dtos/battle/SetOverlayConfigDto.java`
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java:377-410`
- Modify: `CLAUDE.md` (migration pointer line)

- [ ] **Step 1.1: Write the failing tests**

Add to `BattleControllerIntegrationTest.java` next to the existing overlay-config tests (~line 380). The test needs an `Event` row because the new fields persist on `Event` — check the class's existing `@Autowired` block and add `EventRepository` if not present (`import com.example.BES.respositories.EventRepository;` — note the repo package is spelled `respositories`).

```java
@Test
@WithMockUser(roles = "ORGANISER")
public void testSetOverlayConfig_persistsThemeAccentAndRoundCard() throws Exception {
    Event ev = new Event();
    ev.setEventName("overlay-theme-test");
    eventRepository.save(ev);

    String body = "{\"showImages\":true,\"leftColor\":\"#dc2626\",\"rightColor\":\"#2563eb\"," +
            "\"eventName\":\"overlay-theme-test\",\"animTheme\":\"lightning\"," +
            "\"overlayAccentColor\":\"#00d4ff\",\"showRoundCard\":false}";

    mockMvc.perform(post("/api/v1/battle/overlay-config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.animTheme").value("lightning"))
            .andExpect(jsonPath("$.overlayAccentColor").value("#00d4ff"))
            .andExpect(jsonPath("$.showRoundCard").value(false));

    // Persisted — GET returns the same values
    mockMvc.perform(get("/api/v1/battle/overlay-config?event=overlay-theme-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.animTheme").value("lightning"))
            .andExpect(jsonPath("$.overlayAccentColor").value("#00d4ff"))
            .andExpect(jsonPath("$.showRoundCard").value(false));
}

@Test
@WithMockUser(roles = "ORGANISER")
public void testSetOverlayConfig_rejectsUnknownTheme() throws Exception {
    String body = "{\"showImages\":true,\"leftColor\":\"#dc2626\",\"rightColor\":\"#2563eb\",\"animTheme\":\"disco\"}";
    mockMvc.perform(post("/api/v1/battle/overlay-config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isBadRequest());
}

@Test
@WithMockUser(roles = "ORGANISER")
public void testSetOverlayConfig_rejectsInvalidAccentColor() throws Exception {
    String body = "{\"showImages\":true,\"leftColor\":\"#dc2626\",\"rightColor\":\"#2563eb\",\"overlayAccentColor\":\"cyan\"}";
    mockMvc.perform(post("/api/v1/battle/overlay-config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isBadRequest());
}

@Test
@WithMockUser
public void testGetOverlayConfig_includesThemeDefaults() throws Exception {
    mockMvc.perform(get("/api/v1/battle/overlay-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.animTheme").exists())
            .andExpect(jsonPath("$.showRoundCard").value(true));
}
```

Required imports if missing: `com.example.BES.models.Event`, `com.example.BES.respositories.EventRepository`.

- [ ] **Step 1.2: Run tests to verify they fail**

Run from `BES/`: `mvn test -Dtest=BattleControllerIntegrationTest`
Expected: the four new tests FAIL (unknown JSON path / 200-vs-400 mismatches); existing tests still pass.

- [ ] **Step 1.3: Create migration V37**

`BES/src/main/resources/db/migration/V37__add_overlay_theme_config.sql`:

```sql
ALTER TABLE event ADD COLUMN overlay_accent_color VARCHAR(7);
ALTER TABLE event ADD COLUMN show_round_card BOOLEAN NOT NULL DEFAULT TRUE;
```

- [ ] **Step 1.4: Add Event fields**

In `Event.java`, directly after the `animTheme` field:

```java
    @Column(name = "overlay_accent_color", length = 7)
    private String overlayAccentColor;

    @Column(name = "show_round_card", nullable = false)
    private boolean showRoundCard = true;
```

(`@Data` generates `getOverlayAccentColor`/`setOverlayAccentColor`/`isShowRoundCard`/`setShowRoundCard`.)

- [ ] **Step 1.5: Extend SetOverlayConfigDto**

Replace the `animTheme` field and add two fields + getters (follow the file's existing manual-getter style; it has no setters and deserialization works — do not add setters):

```java
    @Pattern(regexp = "^(impact|hype|lightning)$", message = "animTheme must be one of: impact, hype, lightning")
    private String animTheme;

    // Empty string clears the override (falls back to theme default accent)
    @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "overlayAccentColor must be a valid 6-digit hex color")
    private String overlayAccentColor;

    private Boolean showRoundCard;
```

```java
    public String getOverlayAccentColor() { return overlayAccentColor; }
    public Boolean getShowRoundCard()     { return showRoundCard; }
```

- [ ] **Step 1.6: Extend BattleService overlay config read/write**

Replace the body of `getOverlayConfig(String eventName)` (BattleService.java:377-385):

```java
    public Map<String, Object> getOverlayConfig(String eventName) {
        EventBattleState s = stateFor(eventName);
        Map<String, Object> cfg = new HashMap<>(s.overlayConfig);
        cfg.put("logoUrl", s.logoUrl);
        Optional<Event> ev = eventRepo.findByEventNameIgnoreCase(eventName);
        String theme = ev.map(Event::getAnimTheme).orElse(null);
        cfg.put("animTheme", theme != null ? theme : "impact");
        cfg.put("overlayAccentColor", ev.map(Event::getOverlayAccentColor).orElse(null));
        cfg.put("showRoundCard", ev.map(Event::isShowRoundCard).orElse(true));
        return cfg;
    }
```

(`HashMap` tolerates the null accent value. Add `import java.util.Optional;` if missing.)

In `setOverlayConfigService` (BattleService.java:394-410), replace the `if (dto.getAnimTheme() != null) { ... }` block with:

```java
        if (dto.getAnimTheme() != null || dto.getOverlayAccentColor() != null || dto.getShowRoundCard() != null) {
            eventRepo.findByEventNameIgnoreCase(eventName).ifPresent(ev -> {
                if (dto.getAnimTheme() != null) ev.setAnimTheme(dto.getAnimTheme());
                if (dto.getOverlayAccentColor() != null) {
                    ev.setOverlayAccentColor(dto.getOverlayAccentColor().isEmpty() ? null : dto.getOverlayAccentColor());
                }
                if (dto.getShowRoundCard() != null) ev.setShowRoundCard(dto.getShowRoundCard());
                eventRepo.save(ev);
            });
        }
```

- [ ] **Step 1.7: Run tests to verify they pass**

Run from `BES/`: `mvn test -Dtest=BattleControllerIntegrationTest`
Expected: ALL tests pass (new + existing).

- [ ] **Step 1.8: Update CLAUDE.md migration pointer**

In `CLAUDE.md`, replace the line:
`- Latest migration: V33__add_format_timer.sql → next is V34__...`
with:
`- Latest migration: V37__add_overlay_theme_config.sql → next is V38__...`

- [ ] **Step 1.9: Commit**

```bash
git add BES/src/main/resources/db/migration/V37__add_overlay_theme_config.sql \
  BES/src/main/java/com/example/BES/models/Event.java \
  BES/src/main/java/com/example/BES/dtos/battle/SetOverlayConfigDto.java \
  BES/src/main/java/com/example/BES/services/BattleService.java \
  BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java \
  CLAUDE.md
git commit -m "feat: persist overlay accent color + round-card toggle, accept lightning theme (#134)"
```

---

### Task 2: Theme registry + round-label derivation (pure utils, TDD)

**Files:**
- Create: `BES-frontend/src/utils/overlayThemes.js`
- Test: `BES-frontend/src/utils/__tests__/overlayThemes.test.js`

- [ ] **Step 2.1: Write the failing tests**

`BES-frontend/src/utils/__tests__/overlayThemes.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { OVERLAY_THEMES, resolveTheme, deriveRoundLabel } from '../overlayThemes'

describe('resolveTheme', () => {
  it('returns the named theme', () => {
    expect(resolveTheme('lightning').accent).toBe('#00d4ff')
    expect(resolveTheme('lightning').legacy).toBe(false)
  })
  it('falls back to impact for unknown/empty keys', () => {
    expect(resolveTheme('disco')).toBe(OVERLAY_THEMES.impact)
    expect(resolveTheme(undefined)).toBe(OVERLAY_THEMES.impact)
  })
  it('legacy themes keep current timing and never show the title card', () => {
    expect(resolveTheme('impact').timing).toMatchObject({ vsShakeDelay: 340, votePause: 1500, finalPause: 400, secondShake: false, titleTotal: 0, vsDelay: 0 })
    expect(resolveTheme('hype').timing).toMatchObject({ vsShakeDelay: 420, votePause: 2500, finalPause: 800, secondShake: true, titleTotal: 0, vsDelay: 0 })
  })
  it('lightning follows the spec entrance timeline', () => {
    expect(resolveTheme('lightning').timing).toMatchObject({ titleTotal: 2400, vsDelay: 500, finalsExtra: 600 })
  })
})

describe('deriveRoundLabel', () => {
  const rounds = {
    Top16: [['A', 'B', 'A'], ['C', 'D', '']],
    Top8:  [['A', 'C', ''], ['', '', '']],
    Top4:  [],
    Top2:  [['X', 'Y', '']],
  }
  const base = { rounds, isFinal: false, isSmoke: false }

  it('maps bracket round keys to TOP N labels', () => {
    expect(deriveRoundLabel({ ...base, leftName: 'C', rightName: 'D' })).toBe('TOP 16')
    expect(deriveRoundLabel({ ...base, leftName: 'A', rightName: 'C' })).toBe('TOP 8')
    expect(deriveRoundLabel({ ...base, leftName: 'X', rightName: 'Y' })).toBe('TOP 2')
  })
  it('returns GRAND FINAL when isFinal regardless of rounds', () => {
    expect(deriveRoundLabel({ ...base, leftName: 'X', rightName: 'Y', isFinal: true })).toBe('GRAND FINAL')
    expect(deriveRoundLabel({ rounds: null, leftName: 'X', rightName: 'Y', isFinal: true, isSmoke: false })).toBe('GRAND FINAL')
  })
  it('returns empty string for smoke mode', () => {
    expect(deriveRoundLabel({ ...base, leftName: 'A', rightName: 'B', isSmoke: true })).toBe('')
  })
  it('returns empty string when pair not found or data missing', () => {
    expect(deriveRoundLabel({ ...base, leftName: 'Z', rightName: 'Q' })).toBe('')
    expect(deriveRoundLabel({ rounds: null, leftName: 'A', rightName: 'B', isFinal: false, isSmoke: false })).toBe('')
    expect(deriveRoundLabel({ ...base, leftName: '', rightName: '' })).toBe('')
  })
  it('ignores non-array and non-Top keys', () => {
    const weird = { Top8: 'oops', notes: [['A', 'B', '']], Top4: [['A', 'B', '']] }
    expect(deriveRoundLabel({ rounds: weird, leftName: 'A', rightName: 'B', isFinal: false, isSmoke: false })).toBe('TOP 4')
  })
})
```

- [ ] **Step 2.2: Run tests to verify they fail**

Run from `BES-frontend/`: `npx vitest run src/utils/__tests__/overlayThemes.test.js`
Expected: FAIL — module not found.

- [ ] **Step 2.3: Implement `overlayThemes.js`**

```js
// Theme registry for BattleOverlay. Each theme = timing constants + default
// accent + legacy flag. Legacy themes (impact/hype, PR #133) keep the original
// slam/shake visuals and never show the round-card interstitial (titleTotal 0).
//
// Timing keys (ms):
//   titleTotal   round-card beat length; 0 = skip card entirely
//   finalsExtra  added hold when isFinal (GRAND FINAL setpiece)
//   vsDelay      battlers-on-screen → VS rush-in gap (spec t=2400 → t=2900)
//   vsShakeDelay VS rush start → impact moment (bolt/shake)
//   votePause    judges-votes-visible hold before winner
//   finalPause   pre-winner pause when isFinal (no judge panel)
//   secondShake  legacy HYPE double shake burst
export const OVERLAY_THEMES = {
  impact: {
    label: 'IMPACT', legacy: true, accent: '#ffffff',
    timing: { vsShakeDelay: 340, votePause: 1500, finalPause: 400, secondShake: false, titleTotal: 0, vsDelay: 0, finalsExtra: 0 },
  },
  hype: {
    label: 'HYPE', legacy: true, accent: '#ffffff',
    timing: { vsShakeDelay: 420, votePause: 2500, finalPause: 800, secondShake: true, titleTotal: 0, vsDelay: 0, finalsExtra: 0 },
  },
  lightning: {
    label: 'LIGHTNING', legacy: false, accent: '#00d4ff',
    timing: { vsShakeDelay: 340, votePause: 1500, finalPause: 400, secondShake: false, titleTotal: 2400, vsDelay: 500, finalsExtra: 600 },
  },
}

export const resolveTheme = (key) => OVERLAY_THEMES[key] || OVERLAY_THEMES.impact

// Bracket-style round label per spec §2.5. Returns '' when the label should
// be hidden (smoke mode, unknown pair, no bracket data).
export function deriveRoundLabel({ rounds, leftName, rightName, isFinal, isSmoke }) {
  if (isSmoke) return ''
  if (isFinal) return 'GRAND FINAL'
  if (!rounds || !leftName || !rightName) return ''
  for (const [key, list] of Object.entries(rounds)) {
    const m = /^Top(\d+)$/.exec(key)
    if (!m || !Array.isArray(list)) continue
    if (list.some(match => Array.isArray(match) && match[0] === leftName && match[1] === rightName)) {
      return `TOP ${m[1]}`
    }
  }
  return ''
}
```

- [ ] **Step 2.4: Run tests to verify they pass**

Run: `npx vitest run src/utils/__tests__/overlayThemes.test.js`
Expected: PASS (all).

- [ ] **Step 2.5: Commit**

```bash
git add BES-frontend/src/utils/overlayThemes.js BES-frontend/src/utils/__tests__/overlayThemes.test.js
git commit -m "feat: overlay theme registry + bracket round-label derivation (#134)"
```

---

### Task 3: Particle composable + bolt path library (TDD)

**Files:**
- Create: `BES-frontend/src/utils/overlayParticles.js`
- Create: `BES-frontend/src/utils/overlayBolts.js`
- Test: `BES-frontend/src/utils/__tests__/overlayParticles.test.js`

- [ ] **Step 3.1: Write the failing tests**

`BES-frontend/src/utils/__tests__/overlayParticles.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useOverlayParticles } from '../overlayParticles'
import { BOLT_PATHS, randomBolt } from '../overlayBolts'

const mockMatchMedia = (matches) => {
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches }))
}

describe('useOverlayParticles', () => {
  beforeEach(() => { vi.useFakeTimers(); mockMatchMedia(false) })
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

  it('spawnAmbient adds persistent particles with factory data', () => {
    const { particles, spawnAmbient } = useOverlayParticles()
    spawnAmbient(3, (i) => ({ x: i * 10 }))
    expect(particles.value).toHaveLength(3)
    expect(particles.value[1].x).toBe(10)
    expect(particles.value.every(p => p.ambient)).toBe(true)
    vi.advanceTimersByTime(60000)
    expect(particles.value).toHaveLength(3) // ambient never auto-removes
  })

  it('burst auto-removes its particles after ttl, leaving ambient intact', () => {
    const { particles, spawnAmbient, burst } = useOverlayParticles()
    spawnAmbient(2, () => ({}))
    burst(5, () => ({}), 800)
    expect(particles.value).toHaveLength(7)
    vi.advanceTimersByTime(799)
    expect(particles.value).toHaveLength(7)
    vi.advanceTimersByTime(1)
    expect(particles.value).toHaveLength(2)
  })

  it('clear removes everything and cancels timers', () => {
    const { particles, spawnAmbient, burst, clear } = useOverlayParticles()
    spawnAmbient(2, () => ({}))
    burst(3, () => ({}), 500)
    clear()
    expect(particles.value).toHaveLength(0)
    vi.advanceTimersByTime(1000) // no throw, no resurrection
    expect(particles.value).toHaveLength(0)
  })

  it('spawns nothing under prefers-reduced-motion', () => {
    mockMatchMedia(true)
    const { particles, spawnAmbient, burst } = useOverlayParticles()
    spawnAmbient(5, () => ({}))
    burst(5, () => ({}), 500)
    expect(particles.value).toHaveLength(0)
  })

  it('particles get unique ids', () => {
    const { particles, spawnAmbient } = useOverlayParticles()
    spawnAmbient(4, () => ({}))
    const ids = particles.value.map(p => p.id)
    expect(new Set(ids).size).toBe(4)
  })
})

describe('overlayBolts', () => {
  it('exposes at least 6 hand-authored paths', () => {
    expect(BOLT_PATHS.length).toBeGreaterThanOrEqual(6)
    BOLT_PATHS.forEach(d => expect(d).toMatch(/^M/))
  })
  it('randomBolt returns a member of the library', () => {
    for (let i = 0; i < 20; i++) expect(BOLT_PATHS).toContain(randomBolt())
  })
})
```

- [ ] **Step 3.2: Run tests to verify they fail**

Run: `npx vitest run src/utils/__tests__/overlayParticles.test.js`
Expected: FAIL — modules not found.

- [ ] **Step 3.3: Implement `overlayBolts.js`**

```js
// Hand-authored lightning bolt paths in a 0-100 viewBox (spec §3.1: a small
// library cycled at random reads better than procedural generation).
// Paths 0-1, 4-5 travel left→right; 2-3 travel top→bottom.
// Render with pathLength="240" so stroke-dasharray draw-on works uniformly.
export const BOLT_PATHS = [
  'M0 46 L9 41 L17 53 L28 44 L34 58 L47 47 L55 56 L68 42 L77 52 L88 45 L100 49',
  'M0 52 L12 49 L20 60 L31 50 L42 62 L51 48 L63 57 L74 46 L85 55 L100 50',
  'M50 0 L46 14 L55 22 L44 36 L53 47 L43 61 L52 73 L45 86 L50 100',
  'M48 0 L53 11 L42 25 L51 38 L40 52 L50 66 L41 79 L49 90 L46 100',
  'M0 38 L14 44 L25 35 L33 50 L46 40 L57 52 L70 38 L82 49 L100 42',
  'M0 60 L11 52 L23 63 L37 49 L45 60 L59 46 L71 58 L86 47 L100 55',
]

export const randomBolt = () => BOLT_PATHS[Math.floor(Math.random() * BOLT_PATHS.length)]
```

- [ ] **Step 3.4: Implement `overlayParticles.js`**

```js
import { onBeforeUnmount, getCurrentInstance, ref } from 'vue'

let nextId = 0

// Particle composable (spec §2.6): plain reactive array rendered with v-for.
// spawnAmbient = persistent looping particles (CSS animates them forever).
// burst = one-shot particles removed from the DOM after ttlMs (perf budget §2.7).
// Under prefers-reduced-motion both are no-ops.
export function useOverlayParticles() {
  const particles = ref([])
  const timers = new Set()
  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

  const spawnAmbient = (count, factory) => {
    if (reduced) return
    for (let i = 0; i < count; i++) {
      particles.value.push({ id: nextId++, ambient: true, ...factory(i) })
    }
  }

  const burst = (count, factory, ttlMs) => {
    if (reduced) return
    const ids = new Set()
    for (let i = 0; i < count; i++) {
      const p = { id: nextId++, ambient: false, ...factory(i) }
      ids.add(p.id)
      particles.value.push(p)
    }
    const t = setTimeout(() => {
      particles.value = particles.value.filter(p => !ids.has(p.id))
      timers.delete(t)
    }, ttlMs)
    timers.add(t)
  }

  const clear = () => {
    timers.forEach(clearTimeout)
    timers.clear()
    particles.value = []
  }

  if (getCurrentInstance()) onBeforeUnmount(clear)

  return { particles, spawnAmbient, burst, clear }
}
```

(`getCurrentInstance()` guard lets the composable run in Vitest without a component context.)

- [ ] **Step 3.5: Run tests to verify they pass**

Run: `npx vitest run src/utils/__tests__/overlayParticles.test.js`
Expected: PASS. Then run the full suite: `npm test` — everything green.

- [ ] **Step 3.6: Commit**

```bash
git add BES-frontend/src/utils/overlayParticles.js BES-frontend/src/utils/overlayBolts.js \
  BES-frontend/src/utils/__tests__/overlayParticles.test.js
git commit -m "feat: overlay particle composable + lightning bolt path library (#134)"
```

---

### Task 4: BattleControl — theme selector from registry, accent picker, round-card toggle

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (script ~lines 38, 303-318, 1905, 1939; template ~lines 2702-2725)

No unit test (view file; repo convention tests utils only). Verified manually in Step 4.5 and end-to-end in Task 10.

- [ ] **Step 4.1: Extend the overlayConfig defaults (3 places)**

Line 38 — replace:

```js
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb', logoUrl: null, animTheme: 'impact', overlayAccentColor: null, showRoundCard: true })
```

Lines ~1905 and ~1939 — both hydrate merges currently read `overlayConfig.value = { animTheme: 'impact', ...savedConfig }`. Replace both with:

```js
    if (savedConfig?.showImages !== undefined) overlayConfig.value = { animTheme: 'impact', overlayAccentColor: null, showRoundCard: true, ...savedConfig }
```

- [ ] **Step 4.2: Registry-driven theme options + accent model (script)**

Add to the imports: `import { OVERLAY_THEMES } from '@/utils/overlayThemes'`

Below `selectAnimTheme` (~line 318) add:

```js
const themeOptions = Object.entries(OVERLAY_THEMES).map(([key, t]) => ({ key, label: t.label }))
const accentDefault = computed(() => (OVERLAY_THEMES[overlayConfig.value.animTheme] || OVERLAY_THEMES.impact).accent)
// Color input needs a concrete hex; null override falls back to the theme default
const accentModel = computed({
  get: () => overlayConfig.value.overlayAccentColor || accentDefault.value,
  set: (v) => { overlayConfig.value.overlayAccentColor = v },
})
const resetAccent = () => {
  overlayConfig.value.overlayAccentColor = ''  // '' tells the backend to clear the override
  pushOverlayConfig()
}
```

Update `pushOverlayConfig` validation so a null accent passes but a malformed typed hex fails — replace the existing guard block with:

```js
const pushOverlayConfig = async () => {
  if (!HEX_RE.test(overlayConfig.value.leftColor) || !HEX_RE.test(overlayConfig.value.rightColor)) {
    overlayConfigError.value = 'Colors must be a valid hex (e.g. #dc2626)'
    return
  }
  const accent = overlayConfig.value.overlayAccentColor
  if (accent && !HEX_RE.test(accent)) {
    overlayConfigError.value = 'Accent must be a valid hex (e.g. #00d4ff)'
    return
  }
  overlayConfigError.value = ''
  await setOverlayConfig(overlayConfig.value, selectedEvent.value)
}
```

(`setOverlayConfig` in api.js spreads the whole config object into the POST body — no api.js change needed.)

- [ ] **Step 4.3: Template — theme buttons from registry**

Replace the two hardcoded IMPACT/HYPE buttons inside `.anim-theme-group` (~lines 2704-2724) with:

```html
            <div class="anim-theme-group" role="radiogroup" aria-label="Animation theme">
              <button
                v-for="opt in themeOptions"
                :key="opt.key"
                type="button"
                role="radio"
                :aria-checked="overlayConfig.animTheme === opt.key"
                :class="['anim-theme-btn', { 'is-active': overlayConfig.animTheme === opt.key }]"
                @click="selectAnimTheme(opt.key)"
              >
                {{ opt.label }}
              </button>
            </div>
```

- [ ] **Step 4.4: Template — accent row + round-card toggle**

Insert directly after the Animation Theme `.overlay-setting-row` (before the Event Logo row):

```html
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Overlay Accent</span>
            <div class="overlay-color-group">
              <input
                type="color"
                v-model="accentModel"
                @change="pushOverlayConfig"
                class="overlay-color-swatch"
                title="Overlay accent color"
              />
              <input
                type="text"
                v-model="accentModel"
                @change="pushOverlayConfig"
                maxlength="7"
                :placeholder="accentDefault"
                aria-label="Overlay accent hex value"
                class="overlay-hex-input"
              />
              <button
                v-if="overlayConfig.overlayAccentColor"
                type="button"
                class="para-chip-sm px-3 py-1.5 type-label"
                @click="resetAccent"
              >
                Reset
              </button>
            </div>
          </div>
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Round Card</span>
            <label class="overlay-toggle">
              <input
                type="checkbox"
                v-model="overlayConfig.showRoundCard"
                @change="pushOverlayConfig"
                aria-label="Show round card interstitial before each battle"
              />
              <span class="overlay-toggle-track"></span>
            </label>
          </div>
```

- [ ] **Step 4.5: Build + manual smoke check**

Run from `BES-frontend/`: `npm run build`
Expected: build succeeds. Then `npm run dev`, open Battle Control → Overlay Settings: three theme buttons render (IMPACT/HYPE/LIGHTNING), accent swatch shows `#00d4ff` when LIGHTNING selected with no override, Reset appears only after picking a custom accent, Round Card toggle persists across reload (Network tab: POST `/battle/overlay-config` carries `animTheme`, `overlayAccentColor`, `showRoundCard`; GET returns them).

- [ ] **Step 4.6: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: theme selector, accent picker and round-card toggle in overlay settings (#134)"
```

---

### Task 5: BattleOverlay foundation — registry wiring, entrance stages, round-card mount

Goal of this task: the **mechanics** (timing, mounting, cancellation, restore paths) with a plain-styled round card. Lightning visual treatment comes in Tasks 6-9. After this task, IMPACT/HYPE behave exactly as before; LIGHTNING shows a simple fade-in/out card then the standard entrance.

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue` (script + template)
- Create: `BES-frontend/src/components/overlay/OverlayRoundCard.vue` (neutral version)

- [ ] **Step 5.1: Create neutral `OverlayRoundCard.vue`**

```vue
<script setup>
import { computed } from 'vue'

const props = defineProps({
  eventName:   { type: String, default: '' },
  genreName:   { type: String, default: '' },
  roundLabel:  { type: String, default: '' },
  isFinal:     { type: Boolean, default: false },
  // Beat (ms) at which the exit/strike transition begins (spec t=2200; finals 2800)
  strikeDelay: { type: Number, default: 2200 },
})

// Genre is the headline; fall back to event name when genre is missing
const headline = computed(() => props.genreName || props.eventName)
const showEventLabel = computed(() => !!props.eventName && !!props.genreName)
const headlineChars = computed(() => headline.value.toUpperCase().split(''))
</script>

<template>
  <div
    class="round-card"
    :class="{ 'rc-final': isFinal }"
    :style="{ '--rc-strike': strikeDelay + 'ms' }"
    aria-hidden="true"
  >
    <div class="rc-scrim"></div>
    <div v-if="showEventLabel" class="rc-event">{{ eventName }}</div>
    <div class="rc-headline">
      <span
        v-for="(ch, i) in headlineChars"
        :key="i"
        class="rc-ch"
        :style="{ animationDelay: `calc(400ms + ${i * 45}ms)` }"
      >{{ ch === ' ' ? ' ' : ch }}</span>
    </div>
    <div v-if="roundLabel" class="rc-round">{{ roundLabel }}</div>
  </div>
</template>

<style scoped>
.round-card {
  position: absolute; inset: 0; z-index: 70;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 14px;
  pointer-events: none;
  animation: rcExit 200ms ease-in var(--rc-strike) forwards;
}
/* Readability scrim — radial, never a full fill (OBS transparency stays usable) */
.rc-scrim {
  position: absolute; inset: 0;
  background: radial-gradient(ellipse at center, rgba(0,0,0,0.45), transparent 75%);
}
.rc-event {
  position: relative;
  font-family: 'Anton SC', sans-serif;
  font-size: 14px; letter-spacing: 0.22em; text-transform: uppercase;
  color: rgba(255,255,255,0.85);
  text-shadow: 0 2px 8px rgba(0,0,0,0.7);
  opacity: 0;
  animation: rcFadeIn 350ms ease-out 150ms forwards;
}
.rc-headline {
  position: relative;
  display: flex; flex-wrap: wrap; justify-content: center;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(48px, 7vw, 92px);
  letter-spacing: 0.06em; text-transform: uppercase; line-height: 1;
  color: #fff;
  text-shadow: 0 4px 18px rgba(0,0,0,0.8);
}
.rc-ch { opacity: 0; animation: rcChIn 320ms cubic-bezier(0.16, 1, 0.3, 1) forwards; }
.rc-round {
  position: relative;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(20px, 2.6vw, 32px);
  letter-spacing: 0.18em; text-transform: uppercase;
  color: rgba(255,255,255,0.92);
  text-shadow: 0 2px 10px rgba(0,0,0,0.7);
  opacity: 0;
  animation: rcFadeIn 350ms ease-out 700ms forwards;
}
@keyframes rcFadeIn { to { opacity: 1; } }
@keyframes rcChIn {
  from { opacity: 0; transform: translateY(18px); filter: blur(6px); }
  to   { opacity: 1; transform: translateY(0);    filter: blur(0); }
}
@keyframes rcExit { to { opacity: 0; } }
</style>
```

- [ ] **Step 5.2: BattleOverlay script — registry, accent, stage state**

In `BattleOverlay.vue`:

**Imports** — add:

```js
import { resolveTheme, deriveRoundLabel } from '@/utils/overlayThemes'
import OverlayRoundCard from '@/components/overlay/OverlayRoundCard.vue'
```

**Replace** the `THEME_TIMING` const + `themeTiming` computed (lines 18-24) with:

```js
// ── Animation theme (registry in utils/overlayThemes.js) ───────────────────
const activeTheme  = computed(() => resolveTheme(overlayConfig.value.animTheme))
const themeTiming  = computed(() => activeTheme.value.timing)
const isLightning  = computed(() => overlayConfig.value.animTheme === 'lightning')
const overlayAccent = computed(() => overlayConfig.value.overlayAccentColor || activeTheme.value.accent)
const showRoundCard = computed(() => overlayConfig.value.showRoundCard !== false)
```

Update the line-15 default ref to include the new keys:

```js
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb', logoUrl: null, animTheme: 'impact', overlayAccentColor: null, showRoundCard: true })
```

**Add** entrance-stage state below the `animToken` declaration (~line 137):

```js
// entranceToken: cancels an in-flight runEntrance (title card included) when a
// new pair / empty pair arrives. Separate from animToken because the LOCKED
// phase handler increments animToken as part of the NORMAL new-pair flow —
// using animToken here would kill the title card the moment LOCKED lands.
let entranceToken = 0
// entranceStage: 'battle' (panels visible) | 'title' (round card interstitial)
const entranceStage = ref('battle')
const titleRoundLabel = ref('')
// Latest bracket rounds — fed by mount state, /topic/battle/state and /topic/battle/bracket.
// Used only to derive the round-card label; never mutated.
const bracketRounds = ref(null)
```

- [ ] **Step 5.3: Rewrite `runEntrance` with the title beat**

Replace the whole `runEntrance` (lines 177-188) with:

```js
// ── Entrance animation ─────────────────────────────────────────────────────
// Spec §2.4 timeline. Legacy themes have titleTotal=0/vsDelay=0 and reproduce
// the original behavior exactly (rush-in immediately, shake at vsShakeDelay).
const runEntrance = async (opts = {}) => {
  const myEntrance = ++entranceToken
  const live = () => !unmounted && entranceToken === myEntrance
  await useDelay().wait(50) // allow DOM to clear previous animation classes
  if (!live()) return
  hideJudgeDecision.value = true
  const t = themeTiming.value

  const wantTitle = !opts.skipTitle && showRoundCard.value && !isSmoke.value && t.titleTotal > 0
  if (wantTitle) {
    titleRoundLabel.value = deriveRoundLabel({
      rounds: bracketRounds.value,
      leftName: leftName.value, rightName: rightName.value,
      isFinal: isFinal.value, isSmoke: isSmoke.value,
    })
    vsAnim.value = 'vs-pre'
    entranceStage.value = 'title'
    await useDelay().wait(t.titleTotal + (isFinal.value ? t.finalsExtra : 0))
    if (!live()) return
  }
  entranceStage.value = 'battle'

  if (t.vsDelay > 0) {
    vsAnim.value = 'vs-pre' // battlers emerge first; VS forms vsDelay later (spec t=2400→2900)
    await useDelay().wait(t.vsDelay)
    if (!live()) return
  }
  vsAnim.value = 'rush-in'
  await useDelay().wait(t.vsShakeDelay) // VS hits undershoot trough
  if (!live()) return
  await impactFx()
}

// Impact moment (spec §3.1): lightning swaps the stage shake for a bolt strike
// + brightness flash (wired to LightningFx in Task 7 via boltNonce).
const boltNonce = ref(0)
const impactFx = async () => {
  if (isLightning.value) {
    boltNonce.value++
    return
  }
  stageShaking.value = true
  await useDelay().wait(120)
  if (unmounted) return
  stageShaking.value = false
}
```

- [ ] **Step 5.4: Wire cancellation + restore paths**

**Empty-pair branch** of `updateBattlePair` (~line 219, `if (!msg.left && !msg.right) {`) — add at the top of the branch:

```js
    entranceToken++              // cancel any in-flight title card
    entranceStage.value = 'battle'
    titleRoundLabel.value = ''
```

**Signature + entrance call** — change `const updateBattlePair = async (msg) => {` to `const updateBattlePair = async (msg, opts = {}) => {` and the final lines (~305-309) to:

```js
  if (battlePhase.value === 'IDLE') {
    pendingEntrance = true
  } else {
    await runEntrance(opts)
  }
}
```

(`pendingEntrance` path intentionally drops `opts`: IDLE→LOCKED is a live battle start, where the title card SHOULD play.)

**updateScore shake sites** (~lines 371-386) — replace the two `stageShaking` blocks with `impactFx()` calls, preserving HYPE's double burst:

```js
    // Shake on slam landing (~350ms into animation)
    await useDelay().wait(350)
    if (!ok()) return
    await impactFx()

    // HYPE: second shake burst for extra drama
    if (themeTiming.value.secondShake) {
      await useDelay().wait(140)
      if (!ok()) return
      await impactFx()
    }
```

(Original inline waits were 120/100ms shakes; `impactFx` standardizes on 120ms — visually identical burst.)

**Restore paths get `skipTitle`** so an OBS refresh mid-battle doesn't replay a 2.4s card:

- `onMounted` (~line 510): `if (pair) await updateBattlePair(pair, { skipTitle: ['VOTING', 'REVEALED', 'DECIDED'].includes(state?.battlePhase) })`
- `hydrateOverlayFromState` (~line 71): `await updateBattlePair(state.currentPair, { skipTitle: ['VOTING', 'REVEALED', 'DECIDED'].includes(state.battlePhase) })`
- Smoke→standard format-switch handler (~line 548): `if (state?.currentPair?.left) await updateBattlePair(state.currentPair, { skipTitle: true })`

**bracketRounds feed** — three places:

- `onMounted` after `const state = await getBattleState(...)` (~line 499): `if (state?.bracket?.rounds) bracketRounds.value = state.bracket.rounds`
- `hydrateOverlayFromState` top (after the snapshot guard, ~line 50): `if (state.bracket?.rounds) bracketRounds.value = state.bracket.rounds`
- bracket topic handler (~line 537, after `if (!msg) return`): `if (msg.rounds) bracketRounds.value = msg.rounds`

- [ ] **Step 5.5: Template — accent var, round-card mount, vs-pre**

**Root element** (line 661-666) — add the accent var and pass nonces later; for now:

```html
  <div
    class="overlay-root"
    :class="{ 'stage-shake': stageShaking }"
    :data-anim-theme="overlayConfig.animTheme || 'impact'"
    :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor, '--overlay-accent': overlayAccent }"
  >
```

**Structural decorators** (lines 722-727) — hide during the title beat:

```html
    <template v-if="!isSmoke && entranceStage !== 'title'">
      <div class="center-divider" aria-hidden="true"></div>
      <div class="scanlines"      aria-hidden="true"></div>
      <div class="color-bleed color-bleed-left"  aria-hidden="true"></div>
      <div class="color-bleed color-bleed-right" aria-hidden="true"></div>
    </template>
```

**Round card branch** — inside the standard-battle template, change the blank/panels fork (lines 806-813) to a three-way:

```html
      <!-- Blank announcement — no active pair -->
      <div v-if="isBlank" class="blank-announce" role="status" aria-live="polite">
        <div class="blank-ring"></div>
        <div class="blank-label">WAITING</div>
        <div class="blank-sub">NEXT BATTLE COMING UP</div>
      </div>

      <!-- Round card interstitial — title beat of the entrance (spec §2.5) -->
      <OverlayRoundCard
        v-else-if="entranceStage === 'title'"
        :event-name="eventName"
        :genre-name="activeGenreName"
        :round-label="titleRoundLabel"
        :is-final="isFinal"
        :strike-delay="2200 + (isFinal ? themeTiming.finalsExtra : 0)"
      />

      <!-- Battler panels -->
      <template v-else>
```

(The judge panel `v-if` needs no change: `hideJudgeDecision` is true during the title beat, so it stays off-screen.)

**vs-pre CSS** — add next to `.vs-gone` (~line 1459):

```css
.vs-pre { opacity: 0; }
```

**Derived accent tokens** — add to `.overlay-root` rule (~line 1007):

```css
  --overlay-accent-muted:  color-mix(in srgb, var(--overlay-accent, #ffffff) 25%, transparent);
  --overlay-accent-subtle: color-mix(in srgb, var(--overlay-accent, #ffffff) 7%, transparent);
  --overlay-accent-glow:   color-mix(in srgb, var(--overlay-accent, #ffffff) 60%, transparent);
```

- [ ] **Step 5.6: Verify — tests, build, legacy regression**

Run: `npm test` and `npm run build` from `BES-frontend/` — green.
Manual (dev server, two tabs: control + `/battle/overlay?event=<name>`):
1. Theme IMPACT: start a battle → identical to current behavior (slam + VS rush + shake, no card).
2. Theme LIGHTNING: start a battle → plain card fades in (event label, genre headline letters, `TOP N`), exits at ~2.4s, panels slam, VS rushes 500ms later. `GRAND FINAL` on a final pair, card holds 600ms longer.
3. Round Card toggle OFF → LIGHTNING entrance skips the card.
4. Refresh overlay mid-VOTING → no card replay, pair restores directly.
5. Clear pair mid-card (organiser reset) → card vanishes, WAITING shows.

- [ ] **Step 5.7: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue BES-frontend/src/components/overlay/OverlayRoundCard.vue
git commit -m "feat: entrance title-card stage machinery + theme registry wiring in overlay (#134)"
```

---

### Task 6: OverlayRoundCard — Lightning treatment

Chrome letterforms with bolt-flash assembly, beam strike-out, finals setpiece (spec §3.1 "Round card treatment" + "Finals special").

**Files:**
- Modify: `BES-frontend/src/components/overlay/OverlayRoundCard.vue`

- [ ] **Step 6.1: Add bolt imports + theme class hook**

In the card's script, add:

```js
import { BOLT_PATHS } from '@/utils/overlayBolts'
// Four corner bolts for the finals setpiece, drawn converging toward center
const cornerBolts = [BOLT_PATHS[2], BOLT_PATHS[3], BOLT_PATHS[0], BOLT_PATHS[5]]
```

The card inherits `data-anim-theme` via CSS from the overlay root — but scoped styles can't see ancestor attributes directly, so use `:global` nesting for the lightning variants (pattern below) OR simpler: bind the class from the existing prop-free context by adding a `themeKey` prop. **Chosen: prop.** Add to `defineProps`:

```js
  themeKey: { type: String, default: 'impact' },
```

and to the root div class binding: `:class="[`rc-${themeKey}`, { 'rc-final': isFinal }]"`.

In `BattleOverlay.vue`, pass it: `:theme-key="overlayConfig.animTheme || 'impact'"`.

- [ ] **Step 6.2: Template additions (beam + finals bolts)**

After `.rc-round`, add:

```html
    <!-- Strike-out beam — bridges title → battlers (spec t=2200) -->
    <div v-if="themeKey === 'lightning'" class="rc-beam"></div>
    <!-- Finals: bolts converge from corners + sustained crackle -->
    <svg
      v-if="themeKey === 'lightning' && isFinal"
      class="rc-corner-bolts"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
    >
      <path v-for="(d, i) in cornerBolts" :key="i" :d="d" pathLength="240"
            class="rc-corner-bolt" :style="{ animationDelay: `${600 + i * 90}ms` }"
            :transform="`rotate(${[45, 135, 225, 315][i]} 50 50)`" />
    </svg>
```

- [ ] **Step 6.3: Lightning CSS**

Append to the card's `<style scoped>`:

```css
/* ══ LIGHTNING treatment ══════════════════════════════════════ */
/* Chrome letterforms: gradient fill + cyan rim; letters materialize with a
   white-hot flash that settles to chrome (spec: bolt traces → settle). */
.rc-lightning .rc-headline { text-shadow: none; }
.rc-lightning .rc-ch {
  background: linear-gradient(180deg, #ffffff 0%, #dff6ff 38%, #8fd6ee 50%, #eafbff 62%, #9adcf5 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 10px var(--overlay-accent-muted));
  animation-name: rcChZap;
}
@keyframes rcChZap {
  0%   { opacity: 0; transform: translateY(14px) scale(1.18); filter: blur(8px) brightness(3); }
  45%  { opacity: 1; filter: blur(1px) brightness(2.2) drop-shadow(0 0 14px var(--overlay-accent)); }
  100% { opacity: 1; transform: translateY(0) scale(1); filter: blur(0) brightness(1) drop-shadow(0 0 10px var(--overlay-accent-muted)); }
}
.rc-lightning .rc-event { color: var(--overlay-accent); text-shadow: 0 0 12px var(--overlay-accent-muted); }
.rc-lightning .rc-round {
  color: rgba(255,255,255,0.95);
  text-shadow: 0 0 14px var(--overlay-accent-muted), 0 2px 10px rgba(0,0,0,0.7);
}
/* Vertical beam drops at the strike beat, flares, and the card exits behind it */
.rc-beam {
  position: absolute; left: 50%; top: 0; width: 4px; height: 100%;
  transform: translateX(-50%) scaleY(0); transform-origin: top;
  background: linear-gradient(to bottom, #ffffff, var(--overlay-accent) 60%, transparent);
  filter: drop-shadow(0 0 12px var(--overlay-accent));
  animation: rcBeamDrop 160ms cubic-bezier(0.7, 0, 1, 1) var(--rc-strike) forwards;
}
@keyframes rcBeamDrop { to { transform: translateX(-50%) scaleY(1); } }
/* Finals: corner bolts draw on, crackle sustained behind the type (spec ~1s) */
.rc-corner-bolts { position: absolute; inset: 0; width: 100%; height: 100%; }
.rc-corner-bolt {
  fill: none; stroke: #eafcff; stroke-width: 1.4;
  filter: drop-shadow(0 0 6px var(--overlay-accent));
  stroke-dasharray: 240; stroke-dashoffset: 240;
  animation: rcBoltDraw 180ms linear forwards, rcBoltCrackle 900ms 220ms steps(2, jump-none) infinite;
}
@keyframes rcBoltDraw    { to { stroke-dashoffset: 0; } }
@keyframes rcBoltCrackle { 0%, 100% { opacity: 0.9; } 50% { opacity: 0.35; } }
```

- [ ] **Step 6.4: Verify + commit**

`npm run build` green. Dev-server check: LIGHTNING card letters flash-assemble to chrome, beam drops at 2.2s, finals shows corner bolts + crackle and holds to 3.0s. IMPACT/HYPE never mount the card.

```bash
git add BES-frontend/src/components/overlay/OverlayRoundCard.vue BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: lightning round-card treatment — chrome assembly, strike beam, finals setpiece (#134)"
```

---

### Task 7: LightningFx layer + entrance/VS/voting treatments

Atmosphere (light columns + bokeh), impact bolt + brightness flash, winner radiate bolts, chrome VS, voting crackle, battler entrance blur/chromatic variants.

**Files:**
- Create: `BES-frontend/src/components/overlay/LightningFx.vue`
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

- [ ] **Step 7.1: Create `LightningFx.vue`**

```vue
<script setup>
import { ref, watch, onMounted } from 'vue'
import { useOverlayParticles } from '@/utils/overlayParticles'
import { randomBolt, BOLT_PATHS } from '@/utils/overlayBolts'

const props = defineProps({
  // false pauses ambient layers during one-shot setpieces (perf budget §2.7)
  active:      { type: Boolean, default: true },
  // true while phase is VOTING — low-amplitude crackle around the VS
  voting:      { type: Boolean, default: false },
  // increment = impact moment: one bolt across the screen + brightness flash
  strikeNonce: { type: Number, default: 0 },
  // increment = winner reveal bolts; side: 0 = left half, 1 = right half
  winnerNonce: { type: Number, default: 0 },
  winnerSide:  { type: Number, default: -1 },
})

const { particles, spawnAmbient } = useOverlayParticles()

onMounted(() => {
  // Cool dust bokeh, slow downward drift (spec: ~12 visible)
  spawnAmbient(12, () => ({
    x: Math.random() * 100,
    size: 3 + Math.random() * 5,
    dur: 9 + Math.random() * 8,
    delay: -(Math.random() * 12),
    op: 0.12 + Math.random() * 0.25,
  }))
})

// One-shot strike bolt (impact moment)
const strikeBolt = ref(null)
let strikeTimer = null
watch(() => props.strikeNonce, () => {
  strikeBolt.value = { d: randomBolt(), id: props.strikeNonce }
  clearTimeout(strikeTimer)
  strikeTimer = setTimeout(() => { strikeBolt.value = null }, 420)
})

// Winner radiate bolts (3-5 per spec; 4 used)
const winnerBolts = ref(null)
let winnerTimer = null
watch(() => props.winnerNonce, () => {
  if (props.winnerSide !== 0 && props.winnerSide !== 1) return
  winnerBolts.value = {
    id: props.winnerNonce,
    side: props.winnerSide,
    paths: [BOLT_PATHS[2], BOLT_PATHS[3], BOLT_PATHS[2], BOLT_PATHS[3]],
  }
  clearTimeout(winnerTimer)
  winnerTimer = setTimeout(() => { winnerBolts.value = null }, 900)
})

const crackleA = BOLT_PATHS[0]
const crackleB = BOLT_PATHS[4]
</script>

<template>
  <div class="lfx" aria-hidden="true">
    <!-- Ambient atmosphere — always on, paused during setpieces -->
    <div class="lfx-ambient" :data-paused="!active">
      <div class="lfx-col lfx-col-a"></div>
      <div class="lfx-col lfx-col-b"></div>
      <div class="lfx-col lfx-col-c"></div>
      <div
        v-for="p in particles"
        :key="p.id"
        class="lfx-bokeh"
        :style="{
          left: p.x + '%',
          width: p.size + 'px',
          height: p.size + 'px',
          opacity: p.op,
          animationDuration: p.dur + 's',
          animationDelay: p.delay + 's',
        }"
      ></div>
    </div>

    <!-- Voting ambient — low-amplitude crackle near the VS -->
    <svg v-if="voting" class="lfx-crackle" viewBox="0 0 100 100" preserveAspectRatio="none">
      <path :d="crackleA" pathLength="240" class="lfx-crackle-bolt lfx-crackle-a" />
      <path :d="crackleB" pathLength="240" class="lfx-crackle-bolt lfx-crackle-b" />
    </svg>

    <!-- Impact strike — bolt across the screen + brightness spike -->
    <template v-if="strikeBolt">
      <svg :key="strikeBolt.id" class="lfx-strike" viewBox="0 0 100 100" preserveAspectRatio="none">
        <path :d="strikeBolt.d" pathLength="240" class="lfx-strike-bolt" />
      </svg>
      <div :key="'f' + strikeBolt.id" class="lfx-flash"></div>
    </template>

    <!-- Winner radiate bolts over the winner's half -->
    <svg
      v-if="winnerBolts"
      :key="'w' + winnerBolts.id"
      class="lfx-winner"
      :class="winnerBolts.side === 0 ? 'lfx-winner-left' : 'lfx-winner-right'"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
    >
      <path
        v-for="(d, i) in winnerBolts.paths"
        :key="i"
        :d="d"
        pathLength="240"
        class="lfx-winner-bolt"
        :transform="`rotate(${[30, 100, 170, 240][i]} 50 50)`"
        :style="{ animationDelay: `${i * 70}ms` }"
      />
    </svg>
  </div>
</template>

<style scoped>
.lfx { position: absolute; inset: 0; pointer-events: none; z-index: 5; }
.lfx-ambient { position: absolute; inset: 0; }
.lfx-ambient[data-paused="true"] * { animation-play-state: paused; }

/* Slow vertical light columns (spec: 8s cycle) */
.lfx-col {
  position: absolute; top: -10%; height: 120%; width: 140px;
  background: linear-gradient(to bottom, transparent, var(--overlay-accent-subtle) 35%, transparent 80%);
  animation: lfxColDrift 8s ease-in-out infinite alternate;
}
.lfx-col-a { left: 12%; }
.lfx-col-b { left: 47%; animation-delay: -3s; }
.lfx-col-c { left: 78%; animation-delay: -5.5s; }
@keyframes lfxColDrift {
  from { transform: translateX(-30px); opacity: 0.5; }
  to   { transform: translateX(30px);  opacity: 1; }
}

/* Dust bokeh — slow downward drift, loops forever */
.lfx-bokeh {
  position: absolute; top: -5vh; border-radius: 50%;
  background: #eafcff; filter: blur(1px);
  animation-name: lfxBokehFall; animation-timing-function: linear; animation-iteration-count: infinite;
}
@keyframes lfxBokehFall { to { transform: translateY(115vh); } }

/* Voting crackle — uneven opacity spikes feel randomized without JS timers */
.lfx-crackle { position: absolute; left: 38%; top: 30%; width: 24%; height: 40%; }
.lfx-crackle-bolt {
  fill: none; stroke: var(--overlay-accent); stroke-width: 1;
  filter: drop-shadow(0 0 4px var(--overlay-accent));
  opacity: 0;
}
.lfx-crackle-a { animation: lfxCrackleA 2.6s linear infinite; }
.lfx-crackle-b { animation: lfxCrackleB 3.4s linear infinite; }
@keyframes lfxCrackleA { 0%, 28%, 36%, 100% { opacity: 0; } 30%, 34% { opacity: 0.3; } 71%, 74% { opacity: 0.22; } 76% { opacity: 0; } }
@keyframes lfxCrackleB { 0%, 52%, 60%, 100% { opacity: 0; } 54%, 58% { opacity: 0.3; } 12%, 15% { opacity: 0.18; } 17% { opacity: 0; } }

/* Impact strike bolt — draw-on then fade (spec ~80-140ms) */
.lfx-strike { position: absolute; inset: 0; width: 100%; height: 100%; }
.lfx-strike-bolt {
  fill: none; stroke: #eafcff; stroke-width: 1.6;
  filter: drop-shadow(0 0 8px var(--overlay-accent));
  stroke-dasharray: 240; stroke-dashoffset: 240;
  animation: lfxBoltDraw 140ms linear forwards, lfxBoltFade 240ms 150ms ease-out forwards;
}
/* Brightness spike — white wash via opacity, not a root filter (perf §2.7) */
.lfx-flash {
  position: absolute; inset: 0; background: #fff; opacity: 0;
  animation: lfxFlash 180ms ease-out forwards;
}
@keyframes lfxFlash { 0% { opacity: 0; } 25% { opacity: 0.16; } 100% { opacity: 0; } }
@keyframes lfxBoltDraw { to { stroke-dashoffset: 0; } }
@keyframes lfxBoltFade { to { opacity: 0; } }

/* Winner radiate bolts — confined to the winner's half */
.lfx-winner { position: absolute; top: 0; height: 100%; width: 50%; }
.lfx-winner-left  { left: 0; }
.lfx-winner-right { left: 50%; }
.lfx-winner-bolt {
  fill: none; stroke: #eafcff; stroke-width: 1.4;
  filter: drop-shadow(0 0 8px var(--overlay-accent));
  stroke-dasharray: 240; stroke-dashoffset: 240;
  animation: lfxBoltDraw 160ms linear forwards, lfxBoltFade 420ms 200ms ease-out forwards;
}
</style>
```

- [ ] **Step 7.2: Mount the layer + winner trigger in BattleOverlay**

**Script** — add import `import LightningFx from '@/components/overlay/LightningFx.vue'` and below `impactFx`:

```js
// Winner reveal fx — fires whenever a winner becomes 0/1 (live score, champion
// reveal, REVEALED restore). fxPaused stops ambient layers during the setpiece.
const winnerNonce = ref(0)
const fxPaused = ref(false)
let fxPauseTimer = null
watch(currentWinner, (side) => {
  if (side !== 0 && side !== 1) return
  if (!isLightning.value) return
  winnerNonce.value++
  fxPaused.value = true
  clearTimeout(fxPauseTimer)
  fxPauseTimer = setTimeout(() => { fxPaused.value = false }, 1800)
})
const fxActive = computed(() => !fxPaused.value && judgeAnim.value !== 'judge-slam-center')
```

**Template** — directly after the glitch overlay div (line 719):

```html
    <!-- Lightning theme fx layer — standard mode only -->
    <LightningFx
      v-if="isLightning && !isSmoke"
      :active="fxActive"
      :voting="showVotingIndicator && !isBlank"
      :strike-nonce="boltNonce"
      :winner-nonce="winnerNonce"
      :winner-side="currentWinner"
    />
```

- [ ] **Step 7.3: Chrome VS + filter defs**

**Template** — add a zero-size defs SVG just inside `.overlay-root` (before the timer Transition):

```html
    <svg v-if="isLightning" width="0" height="0" style="position:absolute" aria-hidden="true">
      <defs>
        <filter id="chrome-bevel" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur in="SourceAlpha" stdDeviation="1.6" result="blur" />
          <feSpecularLighting in="blur" surfaceScale="3" specularConstant="0.9"
                              specularExponent="14" lighting-color="#cfeeff" result="spec">
            <feDistantLight azimuth="235" elevation="42" />
          </feSpecularLighting>
          <feComposite in="spec" in2="SourceAlpha" operator="in" result="specMasked" />
          <feMerge>
            <feMergeNode in="SourceGraphic" />
            <feMergeNode in="specMasked" />
          </feMerge>
        </filter>
      </defs>
    </svg>
```

**CSS** — append a lightning section to BattleOverlay's styles:

```css
/* ══ LIGHTNING THEME ══════════════════════════════════════════ */
/* Chrome VS: gradient glass fill + real specular bevel + cyan rim */
[data-anim-theme="lightning"] .vs-text {
  background: linear-gradient(180deg, #ffffff 0%, #dff6ff 38%, #8fd6ee 50%, #eafbff 62%, #9adcf5 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: url(#chrome-bevel) drop-shadow(0 0 14px var(--overlay-accent-glow));
}
/* Constant subtle RGB split on chrome elements (spec: 1-2px, always on) */
[data-anim-theme="lightning"] .name-text,
[data-anim-theme="lightning"] .name-giant {
  text-shadow:
    1.5px 0 0 rgba(255, 40, 90, 0.35),
    -1.5px 0 0 var(--overlay-accent-muted),
    0 2px 10px rgba(0, 0, 0, 0.65);
}
/* Battler photos: cool grade */
[data-anim-theme="lightning"] .battler-img {
  filter: saturate(0.9) contrast(1.08) brightness(1.02);
}
/* Entrance: arrive blurred + bright, snap to crisp — no bounce (spec §3.1) */
[data-anim-theme="lightning"] .slam-in-left {
  animation: lightArriveLeft 620ms cubic-bezier(0.16, 1, 0.3, 1) both;
}
[data-anim-theme="lightning"] .slam-in-right {
  animation: lightArriveRight 620ms cubic-bezier(0.16, 1, 0.3, 1) both;
}
@keyframes lightArriveLeft {
  0%   { transform: translateX(-60%); opacity: 0; filter: blur(14px) brightness(1.6); }
  55%  { opacity: 1; filter: blur(6px) brightness(1.25); }
  100% { transform: translateX(0); opacity: 1; filter: blur(0) brightness(1); }
}
@keyframes lightArriveRight {
  0%   { transform: translateX(60%); opacity: 0; filter: blur(14px) brightness(1.6); }
  55%  { opacity: 1; filter: blur(6px) brightness(1.25); }
  100% { transform: translateX(0); opacity: 1; filter: blur(0) brightness(1); }
}
```

**Important check:** read the existing `leftSlamIn`/`rightSlamIn` keyframes (~line 1562-1574) first — the lightning variants must end on the same final transform the panels rest at (translateX(0)). If the originals include extra properties in their final frame, mirror them.

**Finals VS arcs** (spec §3.1 finals: "VS entrance has extra arc layers radiating outward") — add `'vs-final': isFinal && isLightning` to the vs-badge class binding (line ~857):

```html
      <div
        class="vs-badge"
        :class="[vsAnim, { 'vs-gone': currentWinner !== -2 && vsAnim !== 'knock-left' && vsAnim !== 'knock-right', 'vs-final': isFinal && isLightning }]"
        aria-hidden="true"
      >
```

and CSS:

```css
/* Finals: expanding arc rings radiate from the VS on entrance */
[data-anim-theme="lightning"] .vs-final::before,
[data-anim-theme="lightning"] .vs-final::after {
  content: '';
  position: absolute; inset: -10px;
  border-radius: 50%;
  border: 2px solid var(--overlay-accent-muted);
  pointer-events: none;
  animation: vsArc 900ms ease-out 2 forwards;
}
[data-anim-theme="lightning"] .vs-final::after { animation-delay: 250ms; }
@keyframes vsArc {
  from { transform: scale(0.6); opacity: 0.9; }
  to   { transform: scale(2.4); opacity: 0; }
}
```

- [ ] **Step 7.4: Verify + commit**

`npm test` + `npm run build` green. Dev check (LIGHTNING): ambient columns drift + bokeh falls between battles and during battles; entrance panels resolve from blur with no bounce; VS is chrome with cyan rim; bolt + white flash replaces the stage shake at VS landing; during VOTING small crackles flicker near the VS; IMPACT theme shows none of this (regression).

```bash
git add BES-frontend/src/components/overlay/LightningFx.vue BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: lightning fx layer — atmosphere, impact bolt, chrome VS, voting crackle (#134)"
```

---

### Task 8: Judge panel + vote reveal — Lightning variants

Spotlight-beam arrival, chrome chips, zap card reveal, circuit arrows (spec §3.1 "Judge panel" + "Vote reveal").

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue` (CSS only)

- [ ] **Step 8.1: Read the existing judge keyframes**

Read `judgeSlamCenter` (~line 1620) and note its exact final-frame transform (the panel's resting position/scale). The lightning variant below MUST end on the same values — adjust `judgeBeamIn`'s 100% frame to match what you find.

- [ ] **Step 8.2: Append judge CSS**

```css
/* Judge panel arrives as a light beam from above that resolves into the panel */
[data-anim-theme="lightning"] .judge-slam-center {
  animation: judgeBeamIn 560ms cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
@keyframes judgeBeamIn {
  0%   { transform: translate(-50%, -120vh) scaleY(1.5); opacity: 0; filter: blur(10px) brightness(2.4); }
  60%  { opacity: 1; filter: blur(2px) brightness(1.5); }
  100% { transform: translate(-50%, -50%) scale(1.06); opacity: 1; filter: blur(0) brightness(1); }
  /* ^ 100% frame: copy the exact end transform from the existing judgeSlamCenter */
}
/* Chrome chips with cyan rim (no box-shadow on animated elements — perf §2.7) */
[data-anim-theme="lightning"] .judge-card {
  background: linear-gradient(165deg, rgba(255,255,255,0.10), rgba(160,220,240,0.05) 55%, rgba(255,255,255,0.08));
  border: 1px solid var(--overlay-accent-muted);
}
/* Vote reveal: each card "struck" — white-hot zap settling to chrome,
   reuses the existing per-card animationDelay stagger */
[data-anim-theme="lightning"] .judge-card.card-burst {
  animation: cardZap 420ms cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes cardZap {
  0%   { opacity: 0; transform: scale(0.92); filter: brightness(2.6) blur(3px); }
  45%  { opacity: 1; filter: brightness(1.6) blur(0); }
  100% { opacity: 1; transform: scale(1); filter: brightness(1); }
}
/* Vote arrows light up like a circuit completing */
[data-anim-theme="lightning"] .arrow-lit-left,
[data-anim-theme="lightning"] .arrow-lit-right {
  filter: drop-shadow(0 0 6px var(--overlay-accent));
}
[data-anim-theme="lightning"] .vote-arrow { transition: filter 160ms ease-out 60ms; }
```

- [ ] **Step 8.3: Verify + commit**

Dev check (LIGHTNING, non-final battle): reveal score → judge panel descends as a bright blurred beam and resolves centered (same resting spot as IMPACT — no jump when it later drifts to the winner side); cards zap in staggered; voted arrows glow. IMPACT regression: unchanged slam.

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: lightning judge panel beam arrival + zap vote reveal (#134)"
```

---

### Task 9: Winner reveal, loser exit, pair-change beam — Lightning variants

Spec §3.1 "Winner reveal" / "Loser exit" / "Pair-change transition". Radiating bolts already fire from Task 7's `winnerNonce` watcher — this task adds the panel-side visuals.

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue` (CSS only)

- [ ] **Step 9.1: Read the existing exit keyframes**

Read `hardCutLeft`/`hardCutRight` (~lines 1598-1608) and the `.slide-left-out`/`.slide-right-out` class rules to confirm duration and easing; the lightning variants keep the same total off-screen translation.

- [ ] **Step 9.2: Append winner/loser/transition CSS**

```css
/* Winner panel: upward light column pulsing from the base (3 pulses) */
[data-anim-theme="lightning"] .panel-winner::after {
  content: '';
  position: absolute; left: 50%; bottom: 0;
  width: 240px; height: 70vh;
  transform: translateX(-50%);
  background: linear-gradient(to top, var(--overlay-accent-muted), transparent 75%);
  pointer-events: none;
  animation: winnerColumn 800ms ease-in-out 3;
  opacity: 0;
}
@keyframes winnerColumn { 0%, 100% { opacity: 0.15; } 50% { opacity: 0.85; } }
/* Bloom flare behind the winner label */
[data-anim-theme="lightning"] .winner-label { position: relative; }
[data-anim-theme="lightning"] .winner-label::before {
  content: '';
  position: absolute; inset: -30px -60px;
  background: radial-gradient(ellipse at center, var(--overlay-accent-glow), transparent 70%);
  z-index: -1;
  animation: bloomIn 700ms ease-out both;
}
@keyframes bloomIn { from { opacity: 0; transform: scale(0.6); } to { opacity: 1; transform: scale(1); } }
/* Loser exit: desaturate 200ms, then hard cut off-edge (spec) */
[data-anim-theme="lightning"] .slide-left-out {
  animation: lightCutLeft 480ms cubic-bezier(0.7, 0, 0.84, 0) forwards;
}
[data-anim-theme="lightning"] .slide-right-out {
  animation: lightCutRight 480ms cubic-bezier(0.7, 0, 0.84, 0) forwards;
}
@keyframes lightCutLeft {
  0%   { transform: translateX(0); filter: saturate(1) brightness(1); }
  42%  { transform: translateX(0); filter: saturate(0.2) brightness(0.5); }
  100% { transform: translateX(-115%); filter: saturate(0.2) brightness(0.5); }
}
@keyframes lightCutRight {
  0%   { transform: translateX(0); filter: saturate(1) brightness(1); }
  42%  { transform: translateX(0); filter: saturate(0.2) brightness(0.5); }
  100% { transform: translateX(115%); filter: saturate(0.2) brightness(0.5); }
}
/* Pair-change: glitch overlay becomes a vertical beam → white-out → retreat
   (recurring beam motif; same `glitching` state drives both themes) */
[data-anim-theme="lightning"] .glitch-overlay {
  background: none;
  animation: none;
}
[data-anim-theme="lightning"] .glitch-overlay::before {
  content: '';
  position: absolute; left: 50%; top: 0; width: 6px; height: 100%;
  transform: translateX(-50%) scaleY(0); transform-origin: top;
  background: linear-gradient(to bottom, #ffffff, var(--overlay-accent) 70%, transparent);
  filter: drop-shadow(0 0 10px var(--overlay-accent));
  animation: beamDrop 140ms cubic-bezier(0.7, 0, 1, 1) forwards;
}
[data-anim-theme="lightning"] .glitch-overlay::after {
  content: '';
  position: absolute; inset: 0;
  background: #fff; opacity: 0;
  animation: beamWhiteout 320ms 120ms cubic-bezier(0.3, 0, 0.2, 1) forwards;
}
@keyframes beamDrop { to { transform: translateX(-50%) scaleY(1); } }
@keyframes beamWhiteout { 0% { opacity: 0; } 35% { opacity: 0.9; } 100% { opacity: 0; } }
```

**Layering check:** the winner column `::after` must sit behind the name text. If it overlaps oddly, give `.name-overlay`/`.name-center-wrap` `position: relative; z-index: 2` under the lightning selector.

- [ ] **Step 9.3: Verify + commit**

Dev check (LIGHTNING): reveal winner → radiate bolts on winner half (Task 7), light column pulses 3×, bloom behind TAKES IT/WINNER, loser grays out then hard-cuts; click Next → vertical beam drops, white-out, new pair's title card. Glitch flicker still shows on IMPACT (regression).

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: lightning winner reveal, loser exit and beam pair transition (#134)"
```

---

### Task 10: Reduced motion, perf pass, full verification, PR

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue` (CSS)
- Modify: `BES-frontend/src/components/overlay/OverlayRoundCard.vue` (CSS)

- [ ] **Step 10.1: Reduced-motion CSS**

Append to BattleOverlay styles (the particle composable already no-ops — this covers pure-CSS layers):

```css
@media (prefers-reduced-motion: reduce) {
  [data-anim-theme="lightning"] .lfx,
  [data-anim-theme="lightning"] .vs-crackle { display: none !important; }
  [data-anim-theme="lightning"] .slam-in-left,
  [data-anim-theme="lightning"] .slam-in-right,
  [data-anim-theme="lightning"] .judge-slam-center,
  [data-anim-theme="lightning"] .slide-left-out,
  [data-anim-theme="lightning"] .slide-right-out { animation-duration: 1ms !important; }
}
```

And to OverlayRoundCard styles:

```css
@media (prefers-reduced-motion: reduce) {
  .rc-beam, .rc-corner-bolts { display: none; }
  .rc-ch, .rc-event, .rc-round { animation-duration: 1ms; animation-delay: 0ms; }
}
```

(Note: `.lfx` is inside a scoped child component; in BattleOverlay's scoped CSS target it as `:deep(.lfx)` — or place these two display rules inside LightningFx/OverlayRoundCard's own styles, which is simpler and preferred.)

- [ ] **Step 10.2: Perf sanity pass**

With LIGHTNING active, open Chrome DevTools → Performance, record an entrance + reveal at 1920×1080:
- No long frames > 32ms during ambient idle
- Max 2 filter properties animating on any full-screen element at once (flash uses opacity, columns use opacity/transform — compliant)
- DOM particle count ≤ 15 idle (12 bokeh), bursts removed after their ttl

If columns or bokeh cause paint storms, halve column width or bokeh count — both are single-line tweaks.

- [ ] **Step 10.3: Full regression run**

```bash
cd BES-frontend && npm test && npm run build
cd ../BES && mvn test
```
Expected: all green.

- [ ] **Step 10.4: End-to-end verification checklist (dev server)**

| # | Scenario | Expect |
|---|---|---|
| 1 | IMPACT full battle (start → vote → reveal → next) | Pixel-identical to pre-branch behavior |
| 2 | HYPE reveal | Double shake intact |
| 3 | LIGHTNING entrance, images ON | Card → beam → blurred arrival → chrome VS → bolt+flash |
| 4 | LIGHTNING entrance, images OFF | Name-giant carries chromatic split; layout sane |
| 5 | LIGHTNING finals pair | GRAND FINAL card, corner bolts, +600ms hold, no judge panel, winner straight after finalPause |
| 6 | Round Card toggle OFF | LIGHTNING goes straight to panels |
| 7 | Accent changed to e.g. #ff9d00 live | Columns/bolts/VS rim/card re-tint without refresh |
| 8 | OBS-style refresh during VOTING | No card replay; crackle resumes; votes intact |
| 9 | Genre switch mid-card | Card cancels cleanly, new genre's state loads |
| 10 | Pair cleared mid-card | WAITING shows, no stuck card |
| 11 | 7-to-Smoke genre | Chart unchanged, no fx layer, no card |
| 12 | Champion reveal after refresh | Winner bolts + column fire once |

- [ ] **Step 10.5: Push + PR**

```bash
git push -u origin feat/overlay-theme-foundation-lightning
gh pr create --title "feat: overlay theme foundation + Lightning theme (#134 phase 1)" --body "$(cat <<'EOF'
## Summary
- Theme registry + accent color + round-card interstitial foundation for the multi-theme overlay system (spec: docs/superpowers/specs/2026-06-11-battle-overlay-themes.md)
- Lightning theme: chrome type, bolt strikes, light-beam transitions, dust-bokeh atmosphere
- New Event columns `overlay_accent_color` / `show_round_card` (V37) flowing through the existing overlay-config endpoint
- IMPACT/HYPE preserved as legacy themes — zero behavior change

Part of #134 (phase 1 — Fire theme follows in a separate PR).

## Test plan
- [ ] `mvn test` — overlay-config persistence + validation
- [ ] `npm test` — registry, round-label derivation, particle composable
- [ ] Manual checklist in docs/superpowers/plans/2026-06-11-overlay-theme-foundation-lightning.md Task 10.4
EOF
)"
```

(PR references #134 but must NOT close it — Fire/Ice phases remain.)

---

## Deferred (tracked, not in this PR)

| Item | Where it lands |
|---|---|
| Fire theme | Next PR (spec §3.2) — registry + DTO regex gain `fire` |
| Smoke-mode atmosphere/title treatment | With Fire or later — Chart.vue conventions need their own decision |
| Theme preview pane in BattleControl | Spec §7 Q7 — only if organisers ask |
| Ice / Ink / Cosmic | Spec phases 2-3 |

## Self-review notes (spec → task coverage)

- §2.1 registry → T2 · §2.2 accent + derived tokens → T1/T5 · §2.3 both layout variants → T7 (name-giant + img rules) · §2.4 timeline → T5 timing constants · §2.5 round card data/label/opt-out/cancel → T2/T5 · §2.6 primitives → T3/T6/T7 · §2.7 perf budget → T7 (opacity flash, no box-shadow) + T10 · §3.1 all twelve Lightning beats → T5-T9 · §5 file list → File Structure table · §6 no-logic-change → no endpoint/phase/WS edits anywhere.
- Type consistency: `timing` keys (`vsShakeDelay/votePause/finalPause/secondShake/titleTotal/vsDelay/finalsExtra`) used identically in T2 registry, T5 runEntrance, T5 template strike-delay. `boltNonce/winnerNonce/currentWinner` prop names match LightningFx props. `deriveRoundLabel` object-arg shape matches T2 tests and T5 call site.
