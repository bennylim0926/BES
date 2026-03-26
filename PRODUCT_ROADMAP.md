# BES Product Roadmap

## Context

BES already handles the full event lifecycle (registration → audition → battle), which puts it ahead of most tools that solve only one slice. The goal is to close the remaining gaps that cause real friction for organisers, judges, and participants — without over-engineering.

This document covers six proposed improvements, ordered by priority and dependency.

---

## Feature 1: Audition Feedback System (Judge Side)

### The Problem

Judges want to give feedback, but typing it out mid-audition is not feasible — their attention must stay on the dancer. The solution must work within the ~10 seconds between performers.

### Proposed UX: Quick-Tap Tag System

Integrated directly into the existing scoring flow. After a judge submits scores for a participant, a lightweight feedback panel appears (does not block or disrupt, can be skipped):

**Two rows of tag chips, tap to toggle:**

| Strengths (green) | Areas to Improve (amber) |
|-------------------|--------------------------|
| Musicality | Work on musicality |
| Creative movement | Foundation needs work |
| Unique style | More variety |
| Solid foundation | Stage presence |
| Good energy | Cleaner transitions |
| Stage presence | Connect to the music |
| Clean execution | Inconsistent execution |

- Judge taps 1–3 from each row. Total interaction: ~10 seconds
- One optional free-text field (not required) for judges who want to add a specific note
- "Save" or "Skip" — skipping leaves feedback blank, score is still saved
- Tags are genre-aware: breaking/popping/locking can have different default tag sets (linked to the dynamic criteria feature)

### What Gets Stored

Add a separate `AuditionFeedback` entity linked to `EventGenreParticipant` + `Judge`, so feedback and scores are decoupled. Fields: `feedbackTags` (string array), `feedbackNote` (nullable text).

### Why This Works

- Zero typing required for the core flow (tags only)
- Judges who want to say more can, but aren't forced to
- Tags are genre-relevant and map directly to what judges already think about
- Naturally extends to battle feedback (same component, different tag set)

---

## Feature 2: Participant Results Portal (No Login)

### The Problem

After the event, participants want to see their scores and feedback. Currently there's no way to do this without organiser access. Participants go ask judges directly, which doesn't scale.

### Proposed UX: Reference Code Portal

**Registration side:**
- When a participant is added to an event (import or walk-in), generate a unique **8-character alphanumeric reference code** (e.g. `BK2F-9XPQ`) stored on `EventParticipant`
- This code is included in the confirmation email alongside a **QR code** linking to the results portal
- Walk-in participants receive a printed slip or organiser shows them the QR from the screen

**Results portal (`/results` — public, no login):**
- Participant enters their reference code or scans QR
- They see:
  - Their scores per aspect (per judge if multiple judges)
  - The feedback tags from each judge
  - Optional judge notes
  - Genre and event name
- Results are only visible after the organiser **releases** results for that event (toggle in event settings)

**What this solves:**
- No login needed — lowers the barrier to zero
- Not tied to audition number (which can change or differ across events)
- Organiser controls timing of release (e.g. release after all auditions done)
- Participants feel valued — they leave with something tangible

### Technical Notes

- Add `referenceCode` (short alphanumeric) to `EventParticipant`
- Add `resultsReleased` boolean to `Event`
- New public endpoint: `GET /api/v1/results?ref={code}` — returns scores + feedback if results are released
- New frontend route: `/results` — simple page, no auth guard

---

## Feature 3: Dynamic Scoring Criteria

### The Problem

Every scoring aspect is currently a fixed string (e.g. "Musicality") with a fixed scale (0–10). Different events and genres require different criteria — breaking and popping have fundamentally different judging philosophies. Without this, BES only works for the one format it was built for.

### What to Build

- Organiser defines custom scoring aspects per **event** and optionally overrides per **genre**
- Each aspect has: `name`, `maxScore`, optional `weight` (for weighted average)
- Judge interface dynamically reflects the criteria for the current genre
- Default fallback: single "Score" aspect, max 10 — for simple events that don't need this
- Feedback tags (Feature 1) can optionally be linked to defined aspects

### Business Value

- Unlocks popping, locking, krump, waacking, vogueing, choreography showcases
- Other organisers running different styles can use BES without customisation requests
- Directly enables feedback tags to be aspect-aware (e.g. "Strong Musicality" ties to the Musicality aspect)

---

## Feature 4: Post-Audition Team/Crew Formation

### The Problem

Individuals register and audition separately. After auditions close, participants sometimes want to form pickup crews for the battle round. Right now this is impossible — teams must be registered upfront or not at all.

### What to Build

- After auditions close, organiser can group individual qualified participants into a named team/crew
- Team gets a name; members remain linked to their original individual registrations
- Battle system supports team vs team (the battle pair is two teams, not two individuals)
- Teams shown on battle overlay and bracket

### Why This Matters

Pick-up crew formation is standard in b-boy events. "Top 8 qualify, form your own 2v2" is a real format. No other tool supports this gracefully.

---

## Feature 5: Full Bracket Visualization (Public, Live)

### The Problem

Currently the battle control shows only the current pair. Participants don't know who they face next. Spectators can't follow the tournament. There's no lasting record.

### What to Build

- Visual elimination bracket tree that updates live as battles are resolved
- Public URL — no login, spectators open on phones
- Shows: completed matches with winners, current match highlighted, upcoming matches
- Works for both 1v1 and team battles (Feature 4)
- Bracket persists after the event as a historical record

### Business Value

- Spectators share the bracket link → organic reach
- Reduces "when do I battle?" questions
- Gives events a professional feel comparable to major international competitions
- Post-event result artifact

---

## Feature 6: Battle Feedback (Extension of Feature 1)

Same tag-based approach as Feature 1, but triggered after a judge submits their vote in a battle.

- Tag set is outcome-aware: positive tags describe why the winner stood out
- Optional note
- Qualified participants who advance can see: audition feedback + battle feedback in the results portal
- Tied to reference code portal (Feature 2) — same portal, more data

> Build this only after Feature 1 is validated in production.

---

## Build Order & Dependencies

```
Feature 1 (Feedback Tags)
  └── Feature 2 (Results Portal) — feedback must exist to display
      └── Feature 6 (Battle Feedback) — extends Features 1+2

Feature 3 (Dynamic Criteria) — standalone, no dependencies
  └── (Later) Feedback tags become criteria-aware

Feature 4 (Team Formation) — requires battle system familiarity
  └── Feature 5 (Bracket Viz) — extends to show teams in bracket

Feature 5 (Bracket Visualization) — partially standalone (1v1 works now)
```

**Suggested sequence:**

| # | Feature | Reason |
|---|---------|--------|
| 1 | Dynamic Scoring Criteria | Foundational — enables all other event types |
| 2 | Feedback Tags (Audition) | Quick win, high judge satisfaction |
| 3 | Results Portal | Completes the participant feedback loop |
| 4 | Bracket Visualization | Spectator engagement |
| 5 | Team Formation | More complex data model changes |
| 6 | Battle Feedback | Only after Feature 1 is validated |

---

## Out of Scope (Explicitly Deferred)

| Feature | Reason |
|---------|--------|
| Participant self-registration portal | Current import + walk-in flow works; public form is a separate product surface |
| Check-in QR codes | No-show rate is acceptable at current event scale |
| Post-event certificates | Useful but lower urgency |
| Voice/audio feedback | Noisy environment, transcription complexity, not worth it vs. tag system |

---

## Verification (per feature)

- **F1**: Judge completes scoring → feedback panel appears → tags saved → viewable in admin
- **F2**: Register participant → receive email with code → organiser releases results → participant enters code → sees scores + tags
- **F3**: Define criteria for event/genre → judge sees correct aspects in scoring UI → scores saved correctly
- **F4**: Organiser groups participants into crew → battle control accepts crew vs crew → overlay shows team names
- **F5**: Complete a battle → bracket updates live → public URL reflects result from another device
- **F6**: Judge votes in battle → feedback panel appears → winner views it in results portal
