# Battle Overlay Theme System — Design Spec

**Date:** 2026-06-11
**Status:** Design — implementation pending
**Origin:** GitHub issue #134
**Scope:** `BattleOverlay.vue` only (BracketVisualization, Chart, BattleJudge excluded)

---

## 1. Context

### 1.1 What exists today

`BattleOverlay.vue` already has an event-scoped animation theme system with two themes:

- **IMPACT** (`animTheme: 'impact'`) — current baseline. Fast slam-in, single shake, 1500ms vote pause
- **HYPE** (`animTheme: 'hype'`) — dramatic build-up. Longer pauses (2500ms vote pause), second shake burst, 800ms final pause

These differ only in *timing* — they share the same visual language (translateX slam, scale-from-6 VS, CSS keyframe jitter, clip-path glitch). All animation primitives are CSS-only with no canvas, no GSAP, no external dependencies.

The overlay supports two layout variants via `overlayConfig.showImages`:
- **With image**: battler photo on left/right panels
- **Without image**: large name typography in place of photo

Battle phases progress: `IDLE → LOCKED → VOTING → REVEALED → DECIDED`. Pair changes trigger `updateBattlePair()` which runs the entrance animation.

### 1.2 Why we're expanding

Issue #134 asked for "AfterEffects-quality" polish across ~30 layered effects. Discussion narrowed this into a coherent multi-theme system instead of a list of disconnected polish items. Each theme is a *visual personality* with one motif carrying the work — not a stack of effects competing for attention.

### 1.3 Reference direction

Visual reference: LINEUP9 OPENSTYLE FINAL intro (Korean dance battle broadcast).

Key elements observed in the reference:
- Cool palette (cyan, steel, white) — no warm colors except spark accents
- Chrome / glass 3D type treatment (title, VS, name labels)
- Vertical light beam dropping → fracturing into branching lightning
- Battlers emerge from heavy motion blur + chromatic aberration → settle to crisp focus
- Rolling smoke at the base, particle bokeh in the background
- Battlers composited on real venue (crowd in soft focus)
- Subtle chromatic RGB-split as constant polish, not transition

The reference inspired the **Lightning** theme. Other themes apply the same *structural philosophy* (one motif, atmosphere always alive, type as character) to different moods.

---

## 2. Shared system

Read this section once. Every theme builds on these primitives.

### 2.1 Theme registry

Extends the existing `animTheme` config field. Five themes ship as the full set; only two are required for v1 (see Build Order).

```
animTheme: 'lightning' | 'fire' | 'ice' | 'ink' | 'cosmic'
```

Existing values `'impact'` and `'hype'` map to `'lightning'` during migration (or are kept as separate sub-modes — implementer's choice, low stakes). The theme registry is a lookup table from theme key to:
- Timing constants (entrance length, vote pause, etc.)
- Default accent color
- Per-theme primitive flags (which atmosphere layers are active)

### 2.2 Customizable accent

Single CSS custom property: `--overlay-accent`. Configurable per event via `OverlayConfig`. Default differs per theme:

| Theme | Default accent | Notes |
|---|---|---|
| Lightning | `#00d4ff` (cyan) | Tints all electric elements |
| Fire | `#ff6b1a` (ember orange) | Tints core flame color; derived darks shift via `color-mix` |
| Ice | `#a8d8ff` (glacial blue) | Tints crystalline elements |
| Ink | `#ffffff` (bone white) | Pure monochrome — accent is just intensity |
| Cosmic | `#b76eff` (nebula violet) | Tints nebula clouds; starfield stays white |

Derived tokens (auto-computed via `color-mix`):
- `--accent-muted` = 25% accent
- `--accent-subtle` = 7% accent
- `--accent-glow` = 60% accent with bloom

Team-color split (left vs right) is preserved via the existing `leftColor`/`rightColor` config fields. For most themes, team colors tint the *winner reveal* and *loser exit* sides, while the central column (VS, dividers, transitions) uses the theme accent.

### 2.3 With-image vs without-image variants

Both required for every theme. The presence of a battler photo changes what carries the visual weight:

| Variant | What carries the panel | Theme adaptation |
|---|---|---|
| With image | The photo, with chromatic aberration + cool/warm grade per theme | Atmosphere wraps around photo; smoke/embers/snow/ink drift in front of and behind the photo |
| Without image | Large stylized name (Anton SC with theme type treatment) | Name itself gets the chrome/forged/frost/brush/stardust treatment — name *is* the panel |

Document any layout shifts between variants per theme (most are minor — name moves to where the photo would be).

### 2.4 Extended entrance animation timeline

The "round card" interstitial lives inside `runEntrance()`. It is **not a new phase** — it is part of the entrance animation. Same trigger (`updateBattlePair`), same downstream state (LOCKED → VOTING).

| t (ms) | Beat | What's on screen | Notes |
|---|---|---|---|
| 0 | Atmosphere held | Theme's idle atmospheric layer (bokeh / embers / snow / ink / starfield) | Already running between matches |
| 150 | Title fade-in | Event name (small label tier, top-center) | Anton SC, theme-tinted |
| 400 | Genre + Round assembly | Headline letterform: genre name + round label, theme-specific reveal | Per-theme: lightning traces / fire chars / ice crystallizes / ink brush / stardust |
| 1200 | Title hold | Sustains. Audience reads it | Atmosphere keeps breathing |
| 2200 | Transition strike | Bridge from title → battlers (theme-specific) | Lightning: beam fractures · Fire: flames part · Ice: title shatters · Ink: brush slash · Cosmic: warp tunnel |
| 2400 | Battlers emerge | Pair appears via theme entrance | Theme-specific |
| 2900 | VS forms | VS letterform assembles in center | Theme-specific |
| 3400 | Settled hero | Clean readable composition, atmosphere continues breathing | Steady state — phase moves to LOCKED |

**Total: ~3.4s standard entrance.** Finals add ~600ms for the per-theme finals setpiece (see §3 each theme).

Cost estimate: 8–15 pair intros per event × 3.4s = ~40 seconds of broadcast pacing over a session.

### 2.5 Round card (interstitial)

Pure animation-layer addition. No backend, no WebSocket, no new phase.

**Data sources (all already available):**
- Event name: `eventName` ref (from route query param)
- Genre name: `activeGenreName` ref (from `/topic/battle/state`)
- Round label: derive from `state.bracket.rounds` by finding which round contains the current pair
- isFinal: already in pair message (`msg.isFinal`)

**Round label mapping (bracket-style, per decision):**
| Bracket round key | Label shown |
|---|---|
| `Top16` | `TOP 16` |
| `Top8` | `TOP 8` |
| `Top4` | `TOP 4` |
| `Top2`, isFinal = false | `TOP 2` |
| `Top2`, isFinal = true | `GRAND FINAL` |
| Smoke (`topSize === 7`) | Round label hidden; show event + genre only |

**Decisions confirmed:**
- Genre line: **always shown**
- Round label: **bracket-style** (TOP X), not broadcast-style (QUARTERFINAL)
- Final: `isFinal=true` replaces TOP 2 with GRAND FINAL **and** triggers per-theme finals setpiece

**Implementation guidance:**
- Lives inside `runEntrance()` — respect the existing `animToken` increment so a quick genre-switch or pair-reset cancels in-flight title-card cleanly
- Empty pair clear (`!msg.left && !msg.right` branch) must also reset any in-flight title-card state
- IDLE-phase deferred-entrance path (`pendingEntrance = true`) calls `runEntrance()` later — title card lives inside, so this path is automatically covered
- Per-event opt-out: `overlayConfig.showRoundCard` (boolean, default true). Hides the title beat entirely (entrance reverts to ~400ms baseline)

### 2.6 Technical primitives

Used across themes — pick the right primitive for the effect, don't reinvent.

| Primitive | Use cases | Notes |
|---|---|---|
| SVG `feTurbulence` + `mask` | Smoke, heat haze, snow drift, ink swirls, nebula clouds | One filter, animated turbulence seed for organic motion |
| SVG `feTurbulence` + `feDisplacementMap` | Heat distortion (fire), gravitational lens (cosmic), ink bleed | Distorts the rendering of elements behind |
| SVG `stroke-dasharray` draw-on | Lightning bolt traces, brush strokes, frost crawling, lightning round-card reveal | Plain `<path>` with animated dash offset |
| SVG `feSpecularLighting` | Type bevel for chrome VS, forged-steel VS, etched-glass VS | Real 3D lighting on text, not CSS shadow stack |
| SVG `feGaussianBlur` + `feColorMatrix` | Glow / bloom on hot elements | Used sparingly — bloom is expensive |
| CSS `text-shadow` stack + `background-clip: text` | Body text hierarchy (Anton SC stays flat) | Anton SC has only one weight; layering provides depth |
| Particle composable (~50 lines, no deps) | Embers, dust bokeh, snow, ink splatter, stardust | Plain divs with `v-for` on a reactive array, removed after animation ends |
| CSS clip-path | Frost crystallization, ink reveal masks, lightning fracture wipes | Already used in current overlay |

**Hard constraints (from issue #134, kept):**
- CSS / SVG / JS only
- No `<canvas>`, no GSAP, no animation libraries, no npm adds
- All new effects scoped to `BattleOverlay.vue`
- All decorative elements `aria-hidden="true"`

### 2.7 Performance budget

- **Target**: 60fps on 2020 MacBook Pro at 1920×1080
- **Max 2 simultaneous filter properties** on full-overlay elements during a single beat
- **No `box-shadow` on large animated elements** — use `filter: drop-shadow` or `opacity` only
- **Particle counts**: 15 max ambient, 30 max burst. Removed from DOM after animation ends
- **Pause ambient effects** during one-shot setpieces (judge entrance, winner reveal) — otherwise compounds with always-on chrome layers and drops frames
- **Reduced-motion**: respect `prefers-reduced-motion` — disable atmosphere particle layers and heavy filters; keep functional state transitions

### 2.8 Decisions locked in

| Decision | Choice |
|---|---|
| Round label style | Bracket-style: `TOP 16`, `TOP 8`, `TOP 4`, `TOP 2`, `GRAND FINAL` |
| Genre line on round card | Always shown |
| isFinal special treatment | Per-theme finals setpiece (see §3) |
| Entrance length (standard) | ~3.4s |
| Entrance length (finals) | ~4s+ (per-theme) |
| Layout variants | With-image AND without-image required for every theme |
| Accent color | Per-event configurable via `OverlayConfig`; one CSS variable drives all theme-tinted elements |
| Smoke (7-to-smoke) mode | Round label hidden on title card; show event + genre only |
| Round card opt-out | `overlayConfig.showRoundCard` boolean, default true |

---

## 3. The 5 themes

Each theme described with the same headers for scannability. Concrete enough that a fresh session can implement without re-asking design questions; abstract enough that specific keyframe values are tuned during implementation.

---

### 3.1 Lightning

> **Arena broadcast** — sudden impact, explosive, sport/esports vibe.

**Signature**: Chrome glass type, vertical strikes, cool dust bokeh.

**Palette**: Cyan / steel / white. Default accent `#00d4ff`. Brief warm sparks at impact moments only.

**Type treatment**: 
- Headlines (VS, round card title): SVG `<text>` with `feSpecularLighting` for real chrome bevel + cyan rim glow
- Body (event name, names, judges): CSS Anton SC + text-shadow stack with cyan rim glow

**Atmosphere (always on)**:
- Slow vertical light columns drifting (SVG gradients, 8s cycle)
- Cool dust bokeh particles (~12 visible, slow downward drift)
- Constant subtle chromatic RGB-split on all chrome elements (1–2px offset)

**Entrance** (battlers arriving):
- Battlers arrive blurred + chromatic-split heavy → snap to crisp focus
- Heavy motion blur smear during travel (CSS `filter: blur(8px)` resolving to 0)
- No bounce — clean stop

**Impact moment** (replaces stage shake):
- Single bright bolt strikes L→R across the screen (SVG `<path>` with `stroke-dasharray` draw-on, ~80ms)
- Brief whole-overlay brightness spike (`filter: brightness(1.15)` for 80ms)

**Voting ambient**: Low-amplitude lightning crackles around VS letterform (small bolts, opacity ~0.3, randomized timing every 800–1500ms).

**Judge panel**: Arrives as a spotlight beam from above that resolves into the panel. Edges have chrome treatment. Judge name cards = chrome chips with cyan rim.

**Vote reveal**: Each card "struck" with a small lightning bolt from VS to the card (SVG `<path>` draw-on, ~120ms per card, staggered). Vote arrow lights up like a circuit completing.

**Winner reveal**:
- Multiple bolts radiate outward from winner's name (3–5 SVG paths, draw-on then fade)
- Winner panel emits upward light column (linear-gradient from base, pulses 3×)
- Winner label gets bloom flare (radial bloom behind text)

**Loser exit**: Desaturate (`filter: saturate(0.2) brightness(0.5)`) over 200ms before hard-cut translateX off-edge.

**Pair-change transition**: Vertical bolt drops, expands to white-out, retreats to reveal new pair (recurring beam motif).

**Finals special**:
- Round card reads `GRAND FINAL`
- Multiple bolts strike simultaneously from all corners, converging on center
- Sustained crackle behind the type for ~1s before clearing
- VS entrance has extra arc layers radiating outward

**Round card treatment**: Letters materialize from a single white-cyan bolt that traces each letter's outline (SVG stroke-dasharray draw-on per letter), then settle to chrome with rim glow.

**With-image vs without-image**:
- With image: chromatic aberration on photo edges, cool color grade
- Without image: name gets the full chrome bevel treatment (SVG `<text>` with `feSpecularLighting`), name *is* the panel

**Primary technique**: SVG `<path>` for bolts (paths can be hand-authored as constants — a library of 6–8 bolt shapes cycled), SVG `feSpecularLighting` for chrome bevel.

**Open questions**:
- Bolt shape library: hand-author or generate procedurally? (Recommend hand-author — looks better, only 6–8 needed)
- Chromatic aberration intensity: subtle (~1px) or dramatic (~3px)? Tune in implementation

---

### 3.2 Fire

> **Underground ritual** — slow burn, ritual tension, cypher/Mortal Kombat vibe.

**Signature**: Forged steel type with ember cracks, embers rising, heat haze.

**Palette**: Ember orange / molten gold / charcoal / deep ember red. Default accent `#ff6b1a`.

**Type treatment**:
- Headlines (VS, round card title): SVG `<text>` with `feSpecularLighting` (warm light source) for molten-metal bevel + ember crack overlay (SVG paths with bright orange glow)
- Body: CSS Anton SC + warm text-shadow stack

**Atmosphere (always on)**:
- Warm glow seeping up from the bottom edge (radial gradient, breathing 4s cycle)
- Subtle heat distortion across entire frame (SVG `feTurbulence` + `feDisplacementMap`, 8s cycle)
- Embers drift upward continuously (~12–15 visible, glowing divs, random horizontal sway, fade as they rise)

**Entrance** (battlers arriving):
- Flame intensity briefly swells
- Dark vertical column "parts" the fire in center, widens outward to left and right
- Battlers fade up from inside receding flames: silhouette → heat-shimmer veil → crisp portrait
- Heat distortion lingers around them for ~600ms, then settles
- **Total entrance: ~1.5s** (deliberately slow)

**Impact moment** (replaces stage shake):
- Ember burst from VS — particles explode outward, fade as they rise
- Warm light wash pulses across whole overlay (firelight cast)
- Heat distortion intensifies for ~200ms

**Voting ambient**:
- Fire intensifies subtly
- VS's ember cracks pulse brighter, slower heartbeat (~0.5 Hz)
- Ember density doubles

**Judge panel**: Slides in like forged metal pulled from fire — leaves heat shimmer trail. Edges glow ember-orange on arrival, gradually cool to dark steel with faint glow. Judge name cards look like branded leather (slight char effect at letter edges).

**Vote reveal**: Each card ignites — small flame licks up from bottom edge. Vote arrow appears as char marks expanding into shape. Brief flare, then settles to ember glow.

**Winner reveal**:
- Winner side erupts: fire intensifies dramatically on their half (flames rise to ~60vh, embers shower upward)
- Winner panel fills with sustained fire wash that doesn't die out
- Winner name letterforms go red-hot (bright molten core) then settle to molten glow with cracks

**Loser exit**: Cools to ash — desaturates to grey, embers on their side extinguish one by one, smoke drifts away from them. Panel goes dark steel without the glow.

**Pair-change transition**: Fire rises from bottom and consumes whole frame (full ember wash for ~400ms). Pull back — flames recede, new pair has emerged from inside.

**Finals special**:
- Round card reads `GRAND FINAL`, wreathed in fire that doesn't burn out
- Ember density 3×
- Slow wall of flame rises behind the type
- F's serif burns brightest (signature beat)
- Sustained ~2s

**Round card treatment**: Text appears as char marks burning into a wood/leather plane (CSS clip-path reveal with char/glow edge), edges glow ember then cool to forged metal with crack overlay.

**With-image vs without-image**:
- With image: warm color grade on photo, heat shimmer around battler outlines
- Without image: name treated as forged metal (SVG bevel + ember crack overlay), name *is* the burning iron brand

**Primary technique**: SVG `feTurbulence` + `feDisplacementMap` for heat distortion + flame mask; particle composable for embers; SVG `feSpecularLighting` (warm light source) for forged-steel bevel.

**Open questions**:
- Flame rendering: animated SVG turbulence-distorted gradient, or pre-authored SVG flame paths? Turbulence is more organic but harder to direct
- Ember color drift: do embers cool to red as they rise, or stay constant orange? (Recommend cool to red — more realistic, costs nothing)

---

### 3.3 Ice

> **Crystalline calm** — slow, geometric, theatrical.

**Signature**: Pale glacial blue, crystalline geometry, frost creeping in.

**Palette**: Glacial blue / pale frost white / deep ice navy. Default accent `#a8d8ff`.

**Type treatment**:
- Headlines: SVG `<text>` with `feSpecularLighting` (cold light source) for etched-glass bevel — clear letterforms with frosted internal cracks, refractive light shimmer
- Body: CSS Anton SC + cool blue text-shadow

**Atmosphere (always on)**:
- Slow snow drift downward (~15 small flakes, random horizontal sway)
- Faint cold breath haze at frame edges (radial gradient, subtle)
- Frost patterns at corners (static SVG, slow opacity breath)

**Entrance** (battlers arriving):
- Frame frosts inward from all corners (SVG mask with stroke-dasharray crawl, ~800ms)
- Frost patterns meet at center, then shatter outward (clip-path with shard fragments)
- Battlers revealed crisply behind the shattered frost
- Ice shards rain down for ~400ms after shatter

**Impact moment** (replaces stage shake):
- Ice shards explode outward in geometric patterns (10–12 SVG polygon shards), then dissipate as snow

**Voting ambient**:
- Frost slowly creeps in further at frame edges
- VS's internal cracks shimmer (refractive light pulse, slow)
- Snow density slightly increases

**Judge panel**: Slides in encased in a thin layer of ice that cracks and falls away on settle. Judge name cards have frost-edge treatment.

**Vote reveal**: Each card frosts over briefly, then frost shatters revealing the vote (clip-path reveal with shard fragments).

**Winner reveal**:
- Winner side: bright ice bloom — refractive light explosion, ice crystals spiral outward then settle
- Winner panel fills with crystalline pattern (SVG geometric overlay)
- Winner name etched into glass — bright refractive shimmer, then settles to crystalline glow

**Loser exit**: Freezes solid — desaturates to pale grey-blue, ice creeps over the panel, then shatters off-edge (translateX with shard particle fallout).

**Pair-change transition**: Whole frame frosts inward, ice plate covers everything, then shatters revealing new pair. Geometric, dramatic.

**Finals special**:
- Round card reads `GRAND FINAL`, encased in a solid ice block
- Ice block slowly cracks (animated SVG crack paths drawing on)
- Then shatters dramatically into shards that rain down behind the entering pair
- Sustained ~2.5s — most theatrical setpiece in the system

**Round card treatment**: Letters crystallize from nothing — geometric facets assembling, frost spreading around them. Settled state: etched glass with refractive shimmer.

**With-image vs without-image**:
- With image: cool color grade, frost at photo edges
- Without image: name etched in glass (SVG `<text>` with `feSpecularLighting` + internal crack overlay)

**Primary technique**: SVG clip-path for shard geometry, SVG `stroke-dasharray` for frost crawl, SVG `feSpecularLighting` for etched glass.

**Open questions**:
- Shard shapes: pre-authored library (6–8 shapes) or procedural polygon generation? (Recommend pre-authored)
- Frost crawl pattern: organic curves or geometric crystal lattice? (Recommend lattice — fits the theme better)

---

### 3.4 Ink

> **Menacing minimalism** — black + white, restrained, martial-arts vibe.

**Signature**: Pure black + bone white. No color accent. Ink-in-water swirls, brush strokes, smoke.

**Palette**: Pure black / bone white. Default accent `#ffffff` (intensity, not hue).

**Type treatment**:
- Headlines: SVG `<text>` rendered with brush-stroke calligraphy effect — letters drawn with stroke-dasharray draw-on (variable stroke width for brush feel), slight edge bleed via `feGaussianBlur`
- Body: CSS Anton SC, no shadows (clean), white on black

**Atmosphere (always on)**:
- Black ink curls drifting through frame (SVG turbulence-distorted gradient blobs, very slow)
- Occasional ink drip from top edge (one every ~6s, random position)
- No particle bokeh — restraint is the point

**Entrance** (battlers arriving):
- Frame fills with swirling ink wash (SVG turbulence mask, ~800ms)
- Ink recedes/retracts revealing battlers as if pulled out of darkness
- Ink splatter on arrival (small splat marks around battlers, fade)

**Impact moment** (replaces stage shake):
- Single brush-stroke slash across the screen (white on black, SVG path with stroke-dasharray draw-on, ~120ms)
- Thin and decisive

**Voting ambient**:
- Ink curls intensify slightly
- VS letterform shimmers subtly (subtle opacity oscillation, no glow)

**Judge panel**: Brush-paints itself in (SVG mask reveal with brush texture). Judge name cards look like inked seals (Asian seal/chop aesthetic).

**Vote reveal**: Each card revealed by a single brush stroke wiping across it. Vote arrow appears as a quick brush mark.

**Winner reveal**:
- Winner side gets a bold ink wash (large brush sweep covering background)
- Winner name calligraphed in with thick brush stroke (dramatic reveal)
- No glow, no particles — pure form

**Loser exit**: Fades to grey, then ink covers from one side (brush wipe out).

**Pair-change transition**: Ink wash floods frame from one corner, covers everything, then recedes from opposite corner revealing new pair. Like turning a page.

**Finals special**:
- Round card preceded by slow ink drip that becomes the title (drip lands, blooms outward as ink, forms `GRAND FINAL` calligraphy)
- Most minimalist finals setpiece — meditative, opposite of pageantry
- Sustained ~2s

**Round card treatment**: Text brush-paints itself on stroke by stroke (SVG stroke-dasharray on calligraphy paths). Settled state: bold inked characters with slight edge bleed.

**With-image vs without-image**:
- With image: monochrome filter on photo (`filter: grayscale(1) contrast(1.2)`), ink overlay at edges
- Without image: name in calligraphy SVG, full brush-stroke treatment

**Primary technique**: SVG path with stroke-dasharray for brush strokes, SVG turbulence + mask for ink wash, SVG `feGaussianBlur` for ink bleed edges.

**Open questions**:
- Calligraphy authoring: do we hand-author SVG paths for each letter of Anton SC, or use a brush-texture mask over standard text? Hand-authored looks better but is more upfront work
- True monochrome or single accent allowed? (Recommend strict monochrome — keep the theme's identity. Team colors can show as tinted ink wash in winner reveal only)

---

### 3.5 Cosmic

> **Reverent / epic** — slow, vast, sci-fi space-opera vibe.

**Signature**: Deep space black, nebula colors, parallax starfield.

**Palette**: Deep space black / nebula violet / magenta / gold accents / white stars. Default accent `#b76eff`.

**Type treatment**:
- Headlines: SVG `<text>` with stardust assembly reveal — letters made of glowing particles + subtle nebula gradient fill
- Body: CSS Anton SC + soft violet glow

**Atmosphere (always on)**:
- Parallax starfield: 3 depth layers drifting at different speeds (slow, medium, fast — far/mid/near)
- Faint nebula clouds drifting (SVG turbulence-masked radial gradients, very slow)
- Occasional shooting star (every ~8s, random angle)

**Entrance** (battlers arriving):
- Frame zooms through a tunnel of stars at warp speed (radial particle streak effect — stars stretch into lines pointing outward, ~600ms)
- Slows and battlers fade in from the starfield
- Stars resume normal drift

**Impact moment** (replaces stage shake):
- Gravitational lens distortion: brief radial warp of the frame (SVG `feDisplacementMap` with radial falloff, ~200ms)
- Like a black hole pulse

**Voting ambient**:
- Nebula clouds pulse slowly
- Stars twinkle more actively
- VS letterform has subtle nebula gradient shift

**Judge panel**: Materializes from a particle swirl — stardust converges to form the panel shape. Judge name cards have starfield backgrounds.

**Vote reveal**: Each card lights up like a star igniting — small bloom expansion + particle shower.

**Winner reveal**:
- Winner side: supernova bloom — massive radial light explosion + particle shower
- Winner panel fills with nebula gradient
- Winner name made of bright stardust, slowly settles

**Loser exit**: Fades into the void — desaturates, stars fade around them, panel goes pure black, then slides off.

**Pair-change transition**: Warp tunnel — stars stretch into lines as we "travel through space" briefly, then slow to reveal new pair. Same primitive as entrance but used as transition.

**Finals special**:
- Round card heralded by a supernova bloom behind the type (radial light explosion that fades to nebula)
- Big, slow, reverent — "this is the moment" energy
- Sustained ~2.5s

**Round card treatment**: Text assembles from drifting particles — a thousand stardust particles swirl into letterform shapes. Settled state: glowing letters with nebula gradient fill.

**With-image vs without-image**:
- With image: cool grade with magenta tint in shadows, soft glow around photo
- Without image: name made of stardust particles, slow shimmer

**Primary technique**: SVG turbulence for nebula, particle composable for stars (multi-layer for parallax), SVG `feDisplacementMap` for warp distortion, SVG `feGaussianBlur` + `feColorMatrix` for bloom.

**Open questions**:
- Starfield density: how many stars per layer? (Performance budget will likely cap ~40 visible total across all layers)
- Nebula colors: stick to violet/magenta, or allow accent to shift the dominant nebula hue? (Recommend the latter — gives organisers a tuning knob)

---

## 4. Build order

**Phase 1 — Foundation + 2 themes (recommended first PR or pair of PRs):**
1. Theme registry refactor — extract existing IMPACT/HYPE into the new structure
2. Shared primitives — SVG filter library, particle composable, type-treatment system
3. Round card system — `runEntrance()` extension, data wiring, label mapping
4. **Lightning** theme (most opposite to existing baseline, validates the system)
5. **Fire** theme (most opposite to Lightning, proves the theme is genuinely swappable)

**Phase 2 — Theme 3:**
6. **Ice** theme (third most distinct mood, ships if Phase 1 proved the system)

**Phase 3 — Deferred until event need:**
7. **Ink** theme (build when there's a confirmed event that wants the restrained vibe)
8. **Cosmic** theme (build when there's a confirmed event that wants the epic vibe)

Rationale: Ink and Cosmic are designed in this spec so the work isn't lost, but they're not implementation priorities until proven demand. Saves 2 themes' worth of build + maintenance cost.

---

## 5. Files expected to change

| Path | Change |
|---|---|
| `BES-frontend/src/views/BattleOverlay.vue` | Extensive — template additions for theme layers, scoped CSS per theme, script for theme registry and runEntrance extension |
| `BES-frontend/src/components/OverlayConfigForm.vue` (or wherever `overlayConfig` is edited in BattleControl) | Theme selector dropdown, accent color picker, `showRoundCard` toggle |
| Backend `Event` model + Flyway migration | Add `animTheme` column if not already present (verify — current `animTheme` may already be persisted). New columns: `overlayAccentColor`, `showRoundCard` |
| `BES-frontend/src/utils/api.js` | Likely no changes — overlay config endpoint already exists |
| **New file**: `BES-frontend/src/utils/overlayParticles.js` (or similar) | Small composable, ~50 lines |

---

## 6. What this spec does NOT change

Explicitly out of scope:

- Battle phase state machine (`IDLE → LOCKED → VOTING → REVEALED → DECIDED`) — untouched
- Backend battle endpoints (no new endpoints, no signature changes)
- WebSocket topics (no new subscriptions, no new message shapes)
- Bracket data structures (no schema changes to `bracketState`/`battlers`)
- Permission matrix in `BattleController` (no new endpoints means no new `@PreAuthorize` reviews)
- `BracketVisualization.vue`, `BattleJudge.vue`, `Chart.vue` (excluded per #134)
- 7-to-Smoke logic (theme system applies to smoke mode visually; round label hides as noted in §2.5)

---

## 7. Open questions / parking lot

| # | Question | When to resolve |
|---|---|---|
| 1 | Should existing IMPACT/HYPE map to Lightning automatically, or keep them as separate timing sub-modes within Lightning? | At Phase 1 implementation |
| 2 | Is there an `animTheme` column already in the DB, or does Phase 1 need to add it? | Verify before Phase 1 |
| 3 | Bolt shape library hand-authoring effort — how many bolt shapes are enough for variety? (Estimate 6–8) | Lightning implementation |
| 4 | Calligraphy paths for Ink — author per letter or use brush mask over text? | Ink implementation (deferred) |
| 5 | Theme switching mid-event — does the overlay handle a live theme swap gracefully, or only at IDLE? | Phase 1 design |
| 6 | Reduced-motion fallback — minimum viable experience per theme (which layers must stay vs disable)? | Phase 1 implementation |
| 7 | Theme preview in BattleControl — do organisers need a preview pane before going live? | Phase 1 UX decision |
| 8 | Per-genre theme override vs event-wide theme — current `animTheme` is event-scoped per recent #133; should themes follow the same scoping or go to per-genre? | Phase 1 design |

---

## 8. References

- Issue #134: original ask
- PR #133: existing IMPACT/HYPE theme system (architectural precedent)
- LINEUP9 OPENSTYLE FINAL: visual reference video (cool palette + chrome + lightning)
- `docs/superpowers/specs/2026-05-30-ui-overhaul-design.md`: app-wide design language (Anton SC, parallelogram chips, cinematic chrome — overlay themes inherit type stack)
- `docs/superpowers/specs/2026-05-27-battle-overlay-improvements-design.md`: earlier overlay improvement spec
