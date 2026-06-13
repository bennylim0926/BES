# App Rename: BES → Kyrove

> **Status:** Design approved · Pending implementation plan
> **Date:** 2026-06-13

## Problem

**BES (Battle Event System)** is too generic. It doesn't differentiate in search, doesn't evoke the product's identity, and the `.com` domain is expensive. A rename is needed before the product grows further — the longer we wait, the more expensive the switch becomes.

## New Name: Kyrove

**Kyrove** (pronounced *ky-ROVE*, two syllables, six letters).

### Etymology

```
Kairos (καιρός)  →  Ky
Groove           →  rove
                  ─────
                  Kyrove
```

- **Kairos** — the ancient Greek word for *the right, critical, opportune moment*. Not chronological time (Chronos), but qualitative time — the moment that matters, the instant where everything aligns.
- **Groove** — the pocket, the rhythm, the state of flow. In street dance culture, "the groove" is where everything lives — the music, the movement, the feeling.

Together: **the moment in the groove.** The instant where preparation meets execution. Everything aligned, in rhythm, at the right time.

### Why It Works

| Lens | Assessment |
|------|-----------|
| **Descriptive** | The Kairos root carries the idea of timing, control, and precision — what the tool gives organisers. The Groove root carries dance authenticity and flow. |
| **Memorable** | Coined word. Six letters. Two syllables. Zero existing brands. Instantly ownable. |
| **Dance culture** | Groove is embedded in the name's DNA. The street dance audience feels it without it being explained. |
| **Organiser appeal** | Kairos → timing, the right moment, precision. Speaks to the organiser's job: making sure everything happens when it should. |
| **Verb potential** | "Kyrove your event." Natural verb form. Built-in marketing language. |
| **Logo potential** | K and V are strong angular letters. Pairs naturally with the existing parallelogram design system. |
| **Search** | Unique word. Zero competition for SEO. Once indexed, Kyrove *is* the search result. |

### The Tagline

> **Kyrove** — *Every moment in the groove.*

Or shorter variants for different contexts:

| Context | Tagline |
|---------|---------|
| Hero / homepage | Every moment in the groove. |
| Meta description | Kyrove: the all-in-one platform for dance battle events. Registration, scoring, battle control, results — every moment in the groove. |
| Social / short | Your event. One groove. |
| Elevator pitch | Kyrove runs your entire dance battle event — registration through results — so every moment lands in the groove. |

## Domain Strategy

### Primary: `kyrove.com`

DNS shows no records — likely available. Purchase immediately if confirmed available via registrar.

### Fallback TLDs (in priority order)

| TLD | Why | Use case |
|-----|-----|----------|
| `kyrove.app` | App-first positioning. Modern. Google-registered TLD carries trust. | Primary fallback if `.com` is unavailable. |
| `kyrove.events` | Literally describes the product category. Perfect semantic fit. | Strong alternative, slightly long for typing. |
| `kyrove.io` | Tech-forward. Familiar to early adopters. Short. | Good if `.app` and `.events` are unavailable. |

### Avoid

- `.live` — used by a direct competitor. Don't share a TLD with the competition.
- `.org` — wrong signal (implies non-profit)
- Hyphenated domains (`ky-rove.com`) — hard to say, hard to type, looks cheap

### Recommendation

Attempt to register all three: `kyrove.com`, `kyrove.app`, `kyrove.events`. Redirect the alternates to the primary. This prevents squatters and protects the brand.

## Visual & Brand Implications

### Parallelogram System Compatibility

Kyrove's angular letters (K, V) pair naturally with the existing parallelogram clip-path design system. The K and V can be extracted as logo marks — two sharp angles that mirror the parallelogram shape.

### Accent Color

The existing runtime-configurable accent color system (`--accent-color`) requires no change. Kyrove works with any color — the brand is the name, not a palette.

### Typography

Existing Anton SC typography remains. KYROVE in Anton SC with `letter-spacing: 0.06em` — it's designed for this treatment.

### Cinematic Chrome Elements

All six existing elements (scanlines, parallelogram chips, corner accent bars, section rules, color bleed, topbar) are name-agnostic. No visual system changes required.

## What Changes

| Layer | Change | Effort |
|-------|--------|--------|
| `README.md` | Title + description | Trivial |
| `CLAUDE.md` | Project name references | Trivial |
| `BES-frontend/index.html` | `<title>` tag | Trivial |
| `BES-frontend/src/` | App name in UI (navbar, login, etc.) | Small — extract to a single config constant |
| `BES/src/main/resources/` | Application name in properties | Trivial |
| Email templates | Sender name, subject lines | Small |
| Docker config | Image names, labels | Trivial |
| GitHub repo | Rename? Or keep `BES` as code name? | Decision needed |
| Domain | Register + configure DNS + Nginx | Small |

### What Doesn't Change

- **Code internals**: package names (`com.example.BES`), class names, variable names — no change. The code name stays `BES` internally. Only user-facing surface changes.
- **Database**: No schema changes.
- **API routes**: No URL changes.
- **Git history**: Preserved as-is.

## Rollout

### Phase 1: Secure the name
1. Register `kyrove.com` (and alternates)
2. Set up DNS → point to existing server
3. Update Nginx config for new domain

### Phase 2: Rebrand the surface
1. Extract app name to a single frontend config constant
2. Update `<title>`, navbar, login page, email templates
3. Keep "Powered by BES" as a subtle footer note during transition

### Phase 3: Communicate
1. Update README and docs
2. If any existing users: email announcement
3. Social / word of mouth

## Open Decisions

1. **GitHub repo name**: Rename to `kyrove` or keep `BES` as the internal code name? Recommendation: keep `BES` as the repo name (it's already the code name throughout the codebase) and add `kyrove` as the product name in README.
2. **Backend package**: Keep `com.example.BES` indefinitely — Java package renames are high-risk, low-reward.
3. **Logo mark**: Design the K/V parallelogram mark — out of scope for this spec, but noted.
