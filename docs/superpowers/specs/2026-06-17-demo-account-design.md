# Demo Account System — Design Spec

**Date:** 2026-06-17
**Status:** Approved
**Scope:** Backend (Spring Boot) + Frontend (Vue 3)

---

## 1. Purpose

A self-service demo system that lets anyone experience Kyrove in their own isolated sandbox, without an admin setting up events or resetting data. Three event-day roles are covered: **Emcee**, **Judge**, and **Helper**. The organiser role is **excluded** from the demo to prevent abuse as a free event platform and because the organiser experience is mostly setup work rather than the exciting live feeling.

---

## 2. User Flow

```
Login Page
  │
  └── [Try Demo] button
        │
        ├── Passcode prompt modal
        │     │
        │     ├── Wrong passcode → "Invalid passcode" inline error
        │     │
        │     └── Correct passcode → Role Picker
        │           │
        │           ├── 🎤 Emcee
        │           ├── ⚖️ Judge
        │           └── 🛎️ Helper
        │
        └── POST /api/v1/demo/start { passcode, role }
              │
              ├── 200 → set session cookie, redirect to role landing
              ├── 401 → invalid passcode
              ├── 403 → demo disabled by admin
              └── 429 → rate limited (too many sandboxes)
```

**Return behavior:** If the browser already has a valid demo session cookie, clicking "Try Demo" detects the existing sandbox and offers "Continue" (return to current role) or "Start Fresh" (destroy old sandbox, create new one).

---

## 3. Architecture: Template Event → Per-Session Clone

### 3.1 Template Event

Seeded once on application startup. Serves as the immutable source for all clones.

```
Template Event: "Kyrove Demo"
├── Payment: not required
├── Judging mode: SOLO
├── Feedback: enabled
├── Results: not released
│
├── Category: "Hip Hop" (1v1, 20 participants, default single-score judging)
├── Category: "Popping" (1v1, 20 participants, custom 3-criteria judging)
│   └── Criteria: Musicality, Technique, Originality
│
├── Judges (3): DJ FLEX, B-Girl RAY, Kid Kazoo
│   └── All assigned to both categories
│
├── Participants: 20 per category (some overlap, some unique)
│   ├── All have audition numbers assigned
│   ├── All have stage names
│   ├── All have reference codes
│   └── All checked in (verified + payment done)
│
├── Scores: pre-filled — each judge has scored ~half the participants
│   └── Result: realistic mix of scored/unscored cards
│
└── Feedback: pre-filled feedback on scored participants

Session tokens are generated per clone, not stored on the template.
```

### 3.2 Per-Session Clone

When a user picks a role, `DemoService` deep-copies the template event under a new name:

```
Clone: "Kyrove Demo-{uuid8}"   (e.g., "Kyrove Demo-a3f9c2")
├── All 2 categories
├── All participants + EventParticipant + EventCategoryParticipant links
├── All judges + category assignments
├── All pre-filled scores
├── All pre-filled feedback + tags
└── 3 session tokens (one per demo role: EMCEE, JUDGE, HELPER)
    └── JUDGE token picks one of the 3 demo judges at random
```

**Clone transaction:** The entire clone runs in a single `@Transactional` block. If any step fails, nothing is persisted.

**Roles returned:**

| Role picked | Session type | Redirect |
|-------------|-------------|----------|
| Emcee | Token-based (`ROLE_EMCEE`) | `/emcee/session` |
| Judge | Token-based (`ROLE_JUDGE`) with linked judge | `/judge/session` |
| Helper | Token-based (`ROLE_HELPER`) | `/helper/session` |

The cloned session tokens are pre-authenticated — the user is logged in immediately with the token's role, no second step required.

---

## 4. Access Control

### 4.1 Passcode

- Stored in `app_config` table as key `demo_passcode`
- Initial value: randomly generated 8-character alphanumeric, printed to server logs on first startup
- Admin can **view** and **regenerate** the passcode from the Admin page
- `POST /api/v1/admin/demo/regenerate-passcode` → new random 8-char code, returns it, broadcasts via WebSocket (optional, admin-only screen anyway)

### 4.2 Enable/Disable Toggle

- Stored in `app_config` table as key `demo_enabled` (values: `"true"` / `"false"`)
- Default: `"true"` (or env var `DEMO_ENABLED` override on first seed)
- Admin can toggle from Admin page
- When disabled: `POST /api/v1/demo/start` returns `403 Forbidden`
- Toggle broadcast via WebSocket — frontend hides/shows "Try Demo" button live

### 4.3 Rate Limits

| Limit | Value | Enforcement |
|-------|-------|-------------|
| One sandbox per active session | Hard | Old sandbox must be destroyed before creating new one |
| Max 3 sandboxes per IP per 24h | Hard | Tracked in a small in-memory map |
| Max 10 concurrent demo sandboxes | Soft cap | Count active demo events; 11th gets 429 |

---

## 5. Backend Changes

### 5.1 New Files

| File | Purpose |
|------|---------|
| `controllers/DemoController.java` | `POST /api/v1/demo/start` — public endpoint |
| `services/DemoService.java` | Orchestrates clone, rate limiting, cleanup |
| `services/DemoDataSeeder.java` | `@PostConstruct` — seeds template event on startup if absent |
| `config/DemoRateLimitConfig.java` | In-memory IP tracking (ConcurrentHashMap) |

### 5.2 Modified Files

| File | Change |
|------|--------|
| `config/SecurityConfig.java` | Add `/api/v1/demo/**` to `permitAll` |
| `config/SessionExpiryListener.java` | On demo session expiry → call `DemoService.purgeSandbox(session)` |
| `services/AppConfigService.java` | Generalize to support generic key-value read/write (currently hardcoded to `accentColor`) |
| `controllers/AppConfigController.java` | Add `POST /api/v1/config/demo` for admin to manage passcode + toggle |
| `controllers/AdminController.java` | Alternative: add demo management endpoints here |
| `models/AppConfig.java` | No change needed — already generic key-value schema |

### 5.3 API Contract

```
POST /api/v1/demo/start
  Content-Type: application/json
  Body: {
    "passcode": "A3F9C2K1",
    "role": "EMCEE" | "JUDGE" | "HELPER"
  }

  Success 200:
  {
    "authenticated": true,
    "role": "JUDGE",
    "eventId": 456,
    "eventName": "Kyrove Demo-a3f9c2",
    "judgeId": 12,
    "judgeName": "DJ FLEX"
  }

  Error 401: { "error": "Invalid passcode" }
  Error 403: { "error": "Demo is currently disabled" }
  Error 429: { "error": "Too many demo sessions. Try again later." }
```

```
POST /api/v1/admin/demo/regenerate-passcode
  Authorization: ROLE_ADMIN

  Success 200:
  {
    "passcode": "B7D2E9F4"
  }
```

```
GET /api/v1/config/app
  (extended response)
  {
    "accentColor": "#ffffff",
    "demoEnabled": true
  }
```

### 5.4 DemoService.cloneTemplate()

Pseudocode:

```java
@Transactional
public DemoSession cloneTemplate(String role) {
    // 1. Load template event
    Event template = eventRepo.findByEventName("Kyrove Demo");

    // 2. Clone event with new name
    Event clone = new Event();
    clone.setEventName("Kyrove Demo-" + randomUuid8());
    clone.setPaymentRequired(template.isPaymentRequired());
    // ... copy all fields
    Event saved = eventRepo.save(clone);

    // 3. Clone categories
    for (EventCategory cat : template.getCategories()) {
        EventCategory clonedCat = cloneCategory(cat, saved);
        // Clone scoring criteria
        // Clone category-judge assignments
    }

    // 4. Clone participants + all junction records
    cloneParticipants(template, saved);

    // 5. Clone scores
    cloneScores(template, saved);

    // 6. Clone feedback
    cloneFeedback(template, saved);

    // 7. Generate session tokens for cloned event
    generateDemoTokens(saved, role);

    return buildSession(saved, role);
}
```

### 5.5 Cleanup

```java
// SessionExpiryListener — on session destroyed
if (session event name starts with "Kyrove Demo-") {
    eventRepo.deleteByEventName(eventName);  // cascade deletes everything
}

// Scheduled — every 6 hours
@Scheduled(fixedRate = 6 * 3600 * 1000)
public void purgeOrphanSandboxes() {
    // Delete any demo events older than 24h
    // (belt-and-suspenders for sessions that weren't cleanly destroyed)
    List<Event> orphans = eventRepo.findDemoEventsOlderThan(24h);
    orphans.forEach(e -> eventRepo.delete(e));
}
```

---

## 6. Frontend Changes

### 6.1 New / Modified Files

| File | Change |
|------|--------|
| `views/Login.vue` | Add "Try Demo" button, passcode modal, existing-session detection |
| `components/DemoRolePicker.vue` | New — 3 role cards with descriptions |
| `views/AdminPage.vue` | New "Demo Settings" section |
| `utils/api.js` | Add `startDemo(passcode, role)` and `regenerateDemoPasscode()` |
| `utils/auth.js` | Add demo session detection helper |
| `App.vue` | Handle `demoEnabled` from app config WebSocket |

### 6.2 Login Page — "Try Demo"

- Button styled as a secondary action below the login form
- Separated with a section rule: `— or —`
- Click → modal with passcode input
- If existing demo session detected → "Continue Demo" / "Start Fresh" prompt

### 6.3 Role Picker

Three equal cards in a row (stack on mobile):

```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│       🎤          │  │       ⚖️          │  │       🛎️          │
│     EMCEE         │  │     JUDGE         │  │    HELPER         │
│                   │  │                   │  │                   │
│  Run audition     │  │  Score            │  │  Check-in         │
│  rounds, view     │  │  participants,    │  │  participants,    │
│  scoreboard,      │  │  submit feedback, │  │  verify details,  │
│  announce results │  │  use keypad       │  │  see QR code      │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

Clicking a card triggers the API call and redirect.

### 6.4 Admin Page — Demo Settings

Section in the admin panel (visible to Admin only):

- **Demo enabled:** Toggle switch
- **Passcode:** Masked display (••••••••) with "Show" toggle and "Regenerate" button
- **Active sandboxes:** Count display (e.g., "2 demo sessions active")

---

## 7. Demo Template Data Specification

### 7.1 Event

- `eventName`: `"Kyrove Demo"`
- `paymentRequired`: `false`
- `judgingMode`: `"SOLO"`
- `feedbackEnabled`: `true`
- `resultsReleased`: `false`
- `releaseScore`: `false`
- `animTheme`: `"impact"`

### 7.2 Categories

**Hip Hop:**
- Format: `"1v1"`
- Judging: default (single overall score)
- 20 participants
- Scoring criteria: none (default mode)

**Popping:**
- Format: `"1v1"`
- Judging: custom criteria
- 20 participants
- Scoring criteria: Musicality (w=1), Technique (w=1), Originality (w=1)

### 7.3 Judges

| Name |
|------|
| DJ FLEX |
| B-Girl RAY |
| Kid Kazoo |

All assigned to both categories.

### 7.4 Participants

20 per category, with ~10 overlapping (appearing in both categories) and ~10 unique to each. Total: ~30 unique participants.

Each participant has:
- Stage name (e.g., "B-Boy Spinz", "Poppin' J", "Lil Flow")
- Reference code (auto-generated)
- Payment verified = true
- Display name (same as stage name)

Audition numbers: 1–20 per category, assigned in shuffle order.

### 7.5 Scores

Each judge has scored ~10 of the 20 participants per category (random distribution). Scores range from 5.0–9.5. This gives:
- ~50% scored / 50% unscored per category
- Realistic scoreboard with varying totals

### 7.6 Feedback

Pre-filled feedback on ~30% of scored participants using the default feedback tags. One or two tags + a brief note per feedback entry.

---

## 8. Database Migration

**V46:** Add `demo_passcode` and `demo_enabled` rows to `app_config`:

```sql
INSERT INTO app_config (key, value) VALUES ('demo_passcode', <random-8-char>)
    ON CONFLICT (key) DO NOTHING;
INSERT INTO app_config (key, value) VALUES ('demo_enabled', 'true')
    ON CONFLICT (key) DO NOTHING;
```

The random passcode in the migration is a placeholder — the `DemoDataSeeder` will overwrite it with a proper random value on first startup if it matches the placeholder.

No schema changes needed — `app_config` already supports arbitrary key-value pairs.

---

## 9. Testing

### Backend

- `DemoServiceTest` — clone integrity, transaction rollback on failure
- `DemoControllerIntegrationTest` — 200/401/403/429 responses
- `DemoDataSeederTest` — idempotency (calling seeder twice doesn't duplicate)
- `SessionExpiryListenerTest` — demo sandbox cascade-deleted on session expiry

### Frontend

- `DemoRolePicker.test.js` — renders 3 role cards, click emits correct role
- `Login.test.js` — "Try Demo" button visibility, passcode modal flow

---

## 10. Out of Scope

- Organiser role in demo (excluded to prevent abuse as free event platform)
- Battle features (PRO tier demo only — no MAX/battle access)
- Results portal demo (public route, no auth needed — anyone can already visit `/results`)
- Email sending from demo sandboxes (email is no longer used in the product)
- Google Sheets / Google Drive integration in demo sandboxes (organiser-only features, organiser excluded)
- Multi-language support for demo content
- Persistent sandboxes beyond 24h lifetime

## 11. Seeding the Template Event

The template event (`"Kyrove Demo"`) has a fixed, predictable name. This means:

- **Via Google Sheets:** An admin can import participants into `"Kyrove Demo"` just like any real event — the sheet needs a column matching the event name `"Kyrove Demo"`. This is the easiest way to seed 20+ participants per category with realistic names.
- **Via `DemoDataSeeder`:** Falls back to programmatic seeding if no data exists. The seeder checks if participants exist; if empty, it creates sample data programmatically.

The recommendation is to use Google Sheets for the initial seed (better names, faster setup), with the programmatic seeder as a fallback for fresh deployments where no sheet is configured.
