# Organiser Tier System — Design Spec

**Date:** 2026-06-14
**Status:** Approved (pending implementation plan)
**Author:** brainstormed with Claude

## Summary

Introduce two organiser tiers — **Pro** and **Max** — to gate access to the battle system. Pro tier sees every Kyrove feature *except* anything battle-related. Max tier sees everything. Admin can flip an organiser between tiers at any time. Tier is enforced both in the frontend (UX) and the backend (security).

## Motivation

Battle is the most operationally complex feature in Kyrove: bracket seeding, phase state machine, OBS overlays, real-time WebSocket sync, multiple roles touching battle endpoints. Organisers running plain audition events don't need it and shouldn't be billed for it. Introducing a tier lets us monetise battle as a premium feature without splitting the codebase.

## Scope

### In scope
- New `tier` column on `Account` (`PRO` | `MAX`)
- Single backend `TierAccessService` enforcing the rule
- 403 enforcement on all battle endpoints + EventGenre format setter
- Frontend lock UI on every battle entry point with a reusable `<UpgradeToMaxModal>`
- Admin UI: Tier column + Pro/Max dropdown in the Organisers table on `/admin`
- Migration: existing organisers → `MAX`; new accounts → `PRO`

### Out of scope (v1)
- Self-serve billing / payment integration
- Per-event tier override
- Tier change audit log
- Email notifications on tier change
- WebSocket broadcast of tier changes
- More than two tiers

## Rules

### Effective tier resolution

| Logged-in role | Effective tier comes from |
|---|---|
| Admin | Always Max (admin sees everything) |
| Organiser | Own `account.tier` |
| Emcee / Judge / Helper | Resolved from active event's organisers — Max if any assigned organiser is Max, otherwise Pro |

### What "battle access" gates

Everything battle. The list:

**Frontend routes / entry points**
- `/battle/control` — bracket seeding, phases, live match control
- `/battle/judge` — judge voting screen
- `/battle/overlay`, `/battle/bracket`, `/battle/chart` — public broadcast displays (these are no-auth routes; gating is at the data-fetch layer — Pro events have no battle data so they naturally render empty, but the routes themselves stay public for OBS sources)
- Battle link in event-chip nav dropdown
- Battle quick-action on `MainMenu`

**Frontend UI surfaces in shared pages**
- EventGenre format selector (the 7-to-Smoke vs Standard choice on EventDetails)
- Battle judges + weightages management section
- Battle guests (`EventGenreBattleGuest`) section

**Backend endpoints**
- All endpoints on `BattleController` (~15 endpoints — bracket, score, phase, battle-pair, smoke, revote, champion-reveal, judge management, image upload, overlay-config, active-genre)
- The EventGenre format setter (locks 7-to-Smoke writes for Pro)

### Behaviour on downgrade

When admin downgrades an organiser Max → Pro:
- Existing battle data (`battle_genre_state` rows, bracket state, smoke lists, votes, tie-breakers) **stays untouched in the DB**.
- The organiser and their event's Emcees/Judges/Helpers immediately lose access on next page interaction (whoami refresh).
- If admin re-upgrades to Max later, the bracket state is exactly where it was left.
- This is access-control, not data destruction. Reversible by design.

### Multi-organiser events

For events with multiple assigned organisers:
- **Organiser logins:** each organiser sees according to their own tier. A Pro co-organiser cannot use battle even if a Max co-organiser exists on the same event.
- **Emcee/Judge/Helper logins:** resolved from event's assigned organisers — Max wins. One Max organiser unlocks battle for the event's working staff.

## Data Model

### Migration: `V40__add_organiser_tier.sql`

```sql
ALTER TABLE account
  ADD COLUMN tier VARCHAR(10) NOT NULL DEFAULT 'PRO'
  CHECK (tier IN ('PRO', 'MAX'));

-- Backfill: everyone already in the system keeps current functionality
UPDATE account
  SET tier = 'MAX'
  WHERE role = 'ROLE_ORGANISER';
```

### Entity update: `Account.java`

```java
@Column(nullable = false, length = 10)
private String tier = "PRO";  // 'PRO' | 'MAX'
```

The field is only semantically meaningful when `role = ROLE_ORGANISER`. Other roles ignore it (their access is resolved from the event).

## Backend

### New service: `TierAccessService`

Single source of truth for the tier rule. Every battle-related code path calls this service. No scattered `@PreAuthorize` SpEL with tier logic.

```java
@Service
public class TierAccessService {

  // Throws 403 ResponseStatusException if denied
  public void requireBattleAccess(Authentication auth, String eventName) {
    if (!hasBattleAccess(auth, eventName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Battle features require Max tier");
    }
  }

  public boolean hasBattleAccess(Authentication auth, String eventName) {
    Account user = accountService.fromAuth(auth);
    if (isAdmin(user)) return true;
    if (isOrganiser(user)) return "MAX".equals(user.getTier());
    // Emcee / Judge / Helper — resolve from event owners
    return eventService.getAssignedOrganisers(eventName).stream()
      .anyMatch(o -> "MAX".equals(o.getTier()));
  }
}
```

### Enforcement points

1. **`BattleController`** — every endpoint extracts the event name from request DTO/path and calls `tierAccessService.requireBattleAccess(...)` before doing work.
2. **EventGenre format setter** (on `EventController` or `EventGenreService`) — same gate; rejects 7-to-Smoke writes for Pro organisers.
3. **`/api/v1/auth/whoami`** — response gains:
   ```json
   { ...existing whoami fields,
     "tier": "PRO" | "MAX" | null }   // null for non-organiser roles
   ```
   `battleEnabled` is NOT returned by whoami. The frontend composable computes it:
   - Admin → `true`
   - Organiser → `tier === 'MAX'`
   - Emcee/Judge/Helper → call a small endpoint `GET /api/v1/event/{name}/battle-enabled` when an active event is set; cache result; treat as `false` if no active event yet.

   This keeps whoami stateless and avoids encoding event context into the auth response.

### New admin endpoint

```
POST /api/v1/admin/organisers/tier
Body: { "accountId": Long, "tier": "PRO" | "MAX" }
```
- `@PreAuthorize("hasRole('ADMIN')")`
- Updates `account.tier`
- Returns updated `GetOrganiserDto` (with tier field)

`GetOrganiserDto` gains a `tier` field. `getAllOrganisers()` returns it.

## Frontend

### Composable: `utils/useTierAccess.js`

```js
export function useTierAccess() {
  const authStore = useAuthStore()
  const eventStore = useEventStore()  // or whatever holds active event
  return {
    tier: computed(() => authStore.user?.tier),
    battleEnabled: computed(() => {
      const role = authStore.user?.role?.[0]?.authority
      if (role === 'ROLE_ADMIN') return true
      if (role === 'ROLE_ORGANISER') return authStore.user.tier === 'MAX'
      // Emcee / Judge / Helper — resolve from active event
      return eventStore.activeEventBattleEnabled === true
    }),
  }
}
```

`activeEventBattleEnabled` is populated when the active event is set (alongside the existing `setActiveEvent`) by calling `GET /api/v1/event/{name}/battle-enabled`.

### Component: `<UpgradeToMaxModal>`

Reusable. Parallelogram clip-path card. Oswald via `--font-sans`, sentence case. Uses `--accent-color` for the glow, not red (informational, not error).

```
┌─────────────────────────────────────────┐
│  Battle system is a Max-tier feature    │  ← type-name-lg, sentence case
├─────────────────────────────────────────┤
│  Battles, brackets, judge voting,       │  ← type-prose
│  OBS overlays, and 7-to-Smoke are       │
│  unlocked on the Max tier.              │
│                                         │
│  Contact your admin to upgrade.         │
│                                         │
│      [ Close ]   [ Contact admin ]      │  ← Contact admin → mailto: or copy
└─────────────────────────────────────────┘
```

### Lock UI placement

| Location | Pro behaviour |
|---|---|
| Event-chip dropdown "Battle" link | Visible with lock icon; click opens `<UpgradeToMaxModal>` (no router push) |
| `MainMenu` battle quick-action card | Lock icon; click opens modal |
| `/battle/control` route guard | Redirect to `/events` and open modal |
| `/battle/judge` route guard | Redirect to home and open modal |
| `EventDetails` genre format dropdown | "7-to-Smoke" option shows lock icon, disabled, tooltip "Max tier required" |
| Battle judges section in EventDetails | Section header shows lock badge; controls disabled; "Upgrade" CTA in section |
| Battle guests section in EventDetails | Same as above |

Max users never see lock icons or upgrade copy. Absence of locks is the signal.

### Router guard update

In `router/index.js`, the `beforeEach` guard gains a step: if route is `/battle/control` or `/battle/judge` and `!user.battleEnabled`, redirect to a sensible home + set a Pinia flag that opens the upgrade modal on the destination.

## Admin UI

### `AdminPage.vue` — Organisers panel

Add a **Tier** column to the existing organisers table:

```
| Username       | Tier     | Events | Credits | Actions          |
|----------------|----------|--------|---------|------------------|
| dance_jam_2026 | [ Max ▾ ]│ 3      │ 5       │ Assign · Delete  │
| local_battle   | [ Pro ▾ ]│ 1      │ 2       │ Assign · Delete  │
```

- Tier cell is a parallelogram-clipped dropdown with Pro and Max options.
- Selection change fires `POST /api/v1/admin/organisers/tier`.
- Toast confirmation: "Set <username> to Max" / "Set <username> to Pro".
- Filter chip row above the table: `All · Max · Pro` for scanning.

No new admin page. No bulk action. Single-row inline edit.

## Design system compliance

All new UI follows the current Kyrove design system:
- **Font:** Oswald via `--font-sans` (NOT Anton SC — Anton SC is reserved for the three broadcast displays: BattleOverlay, BracketVisualization, Chart).
- **Case:** Sentence case via the new `type-prose` / `type-name-*` tiers.
- **Shape:** Parallelogram clip-path on cards, chips, dropdowns.
- **Accent:** `--accent-color` runtime-configurable.
- **Semantic state pattern:** Lock badge uses 3px solid left border + subtle tint + glowing dot. Lock colour is `--accent-color` (informational), not red (red is reserved for errors).

## Acceptance Criteria

1. `Account.tier` column exists, defaults `'PRO'`, all existing organisers backfilled to `'MAX'`.
2. `GET /api/v1/auth/whoami` returns `tier` and `battleEnabled`.
3. All `BattleController` endpoints + EventGenre format setter return 403 when caller fails the tier gate. Single `TierAccessService` owns the rule.
4. Every frontend battle entry point shows lock-state UI for Pro users and opens `<UpgradeToMaxModal>` on click.
5. Direct URL navigation to `/battle/control` or `/battle/judge` while Pro redirects and opens the upgrade modal.
6. Admin sees Tier column in Organisers table on `/admin`; flipping the dropdown updates the DB and the organiser's effective access on next whoami refresh.
7. Max → Pro downgrade preserves existing battle data (no destructive writes).
8. Emcees/Judges/Helpers working an event whose assigned organisers are all Pro see the same lock UI as a Pro organiser would.

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Scattered tier checks rot over time | Every gate routes through `TierAccessService`. PR review checklist: new battle endpoint? → must call `requireBattleAccess`. |
| Pro organiser mid-event after downgrade | Data preserved; admin can re-upgrade instantly. Document this in the admin dropdown tooltip. |
| API client writes 7-to-Smoke directly bypassing UI | Backend format setter rejects 403. Frontend lock is hint; backend is the gate. |
| Emcee/Judge/Helper confusion when event has mixed-tier organisers | Resolution rule (Max wins) is explicit and documented. Surfacing "this event's tier is X" in UI is out of scope for v1; their effective access is what matters. |

## Files Affected (rough estimate)

**Backend (~11 files)**
- `db/migration/V40__add_organiser_tier.sql` (new)
- `models/Account.java`
- `services/TierAccessService.java` (new)
- `services/AccountService.java`
- `services/EventService.java` (helper: `getAssignedOrganisers`)
- `controllers/BattleController.java` (gate every endpoint)
- `controllers/EventController.java` (gate format setter + new `GET /event/{name}/battle-enabled`)
- `controllers/AuthController.java` (whoami returns `tier`)
- `controllers/AdminController.java` (new tier endpoint)
- `dtos/admin/GetOrganiserDto.java` + new `UpdateOrganiserTierDto.java`

**Frontend (~12 files)**
- `utils/useTierAccess.js` (new)
- `utils/api.js` (new `setOrganiserTier`)
- `utils/auth.js` (store `tier`, `battleEnabled` on user)
- `components/UpgradeToMaxModal.vue` (new)
- `App.vue` (event-chip Battle link lock)
- `views/MainMenu.vue` (battle quick-action lock)
- `views/EventDetails.vue` (format selector, battle judges, battle guests sections)
- `views/AdminPage.vue` (Tier column)
- `router/index.js` (battle route guards)
- + small touches on `BattleControl.vue`, `BattleJudge.vue` if they need to handle direct-load case

## Implementation Order (bottom-up)

1. DB migration → entity field → service → admin endpoint → whoami response
2. Backend gate (`TierAccessService`) wired into every battle endpoint
3. Frontend composable + modal component
4. Lock UI rollout across battle entry points
5. Admin tier dropdown
6. Backend tests for gate behaviour; frontend smoke tests for lock UI

Each step is independently committable.
