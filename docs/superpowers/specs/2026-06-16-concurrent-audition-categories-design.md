# Concurrent Audition Categories Design

**Date:** 2026-06-16
**Status:** Approved

## Problem

The audition display system is scoped to the event level, not the category level. This means:

1. The backend holds one `AuditionDisplayStateDto` per event in memory — concurrent emcees overwrite each other's state.
2. The WebSocket topic `/topic/audition/{eventName}/display` is shared across all categories — all display screens receive every update regardless of which category they are monitoring.
3. The timer is embedded in the shared state, so whoever publishes last wins.
4. Emcees can switch categories mid-session inside `EmceeRoundView`, creating accidental display updates.

## Solution: Category-Scoped State (Approach A)

Scope the state store key and WebSocket topic to `eventName + categoryName`. Each category gets its own independent slot. Add a category picker to `AuditionDisplay.vue`'s operator panel so display screens can subscribe to a specific category's channel. Enforce category selection before the emcee enters `EmceeRoundView`.

---

## Backend Changes

### `EventAuditionDisplayService`

**State store key:** `eventName` → `eventName:categoryName`

```java
// Before
ConcurrentHashMap<String, AuditionDisplayStateDto> stateStore  // key = eventName

// After
ConcurrentHashMap<String, AuditionDisplayStateDto> stateStore  // key = eventName + ":" + categoryName
```

Helper to derive the composite key:
```java
private String stateKey(String eventName, String categoryName) {
    return eventName + ":" + categoryName;
}
```

Both `getState(eventName, categoryName)` and `updateState(dto)` use the composite key. The `categoryName` for `updateState` is read from the incoming DTO body (it is already a required field).

### `EventAuditionDisplayController`

Both endpoints gain a required `category` query parameter:

```
GET  /api/v1/event/audition-display?event={eventName}&category={categoryName}
POST /api/v1/event/audition-display?event={eventName}&category={categoryName}
```

The POST derives the composite key from the DTO body's `categoryName` field (the `?category` param is passed for consistency but the body is authoritative). The GET uses the `?category` param directly.

### WebSocket Broadcast Topic

```
Before: /topic/audition/{eventName}/display
After:  /topic/audition/{eventName}/{categoryName}/display
```

`categoryName` is URL-encoded consistently on both the publisher (backend) and subscriber (frontend) sides. The backend URL-encodes the category name when constructing the topic string before broadcasting.

---

## Frontend Changes

### `api.js`

```js
// Before
getAuditionDisplayState(eventName)
postAuditionDisplayState(dto)

// After
getAuditionDisplayState(eventName, categoryName)
// → GET /api/v1/event/audition-display?event=X&category=Y

postAuditionDisplayState(dto)
// → POST /api/v1/event/audition-display?event=X&category=Y
//   category derived from dto.categoryName
```

### `AuditionList.vue` — Category Picker Gate

Add a `selectedCategory` ref (initially `null`). The view conditionally renders:

- **`selectedCategory === null`** → show category picker screen (list of all categories for the active event)
- **`selectedCategory !== null`** → show `EmceeRoundView` locked to that category

The existing inline `switchCategory()` call and any mid-session category switching logic is removed. To change category, the emcee presses "Back" from the EmceeRoundView header, which resets `selectedCategory` to `null`.

Category picker screen layout:
```
AUDITION — [Event Name]

SELECT CATEGORY

  [ Hip-Hop         → ]
  [ Breaking        → ]
  [ Popping         → ]
```

Each row shows category name and format. Tapping navigates into EmceeRoundView for that category.

### `EmceeRoundView.vue`

Two call-site changes — no logic changes:

1. `onMounted`: `getAuditionDisplayState(props.eventName, props.categoryName)` — pass category so recovery loads the right slot.
2. `publishState` → `postAuditionDisplayState(buildStatePayload(...))` — `api.js` derives the category from the DTO's `categoryName` field (already populated from props).

**Background color:** inline style `background: #060818` → `background: #111111` (surface-900, theme base).

### `AuditionDisplay.vue`

**Category selection logic:**

1. On mount, read `?category=X` from the URL. If present, set `selectedCategory` and subscribe to `/topic/audition/{eventName}/{category}/display` immediately (no login required — this is the OBS path).
2. If no URL param and not logged in → standby screen with message "Open Display Settings to select a category."
3. If logged in (operator), load all categories for the event via `getCategoriesByEvent` (already called on mount).

**Dynamic re-subscription** — a `watch` on `selectedCategory`:

```js
watch(selectedCategory, async (newCat, oldCat) => {
  if (currentSubscription) currentSubscription.unsubscribe()
  stopLocalTimer()
  state.value = null

  if (!newCat) return

  const initial = await getAuditionDisplayState(eventName.value, newCat)
  if (initial) applyState(initial)

  currentSubscription = subscribeToChannel(
    client.value,
    `/topic/audition/${eventName.value}/${encodeURIComponent(newCat)}/display`,
    applyState
  )

  // Update URL so operator can copy it into OBS
  const url = new URL(window.location.href)
  url.searchParams.set('category', newCat)
  history.pushState({}, '', url)
})
```

**Operator panel — category dropdown (new, above existing color row):**

```
┌─ Display Settings ─────────────────────┐
│  MONITORING CATEGORY                   │
│  [ Hip-Hop              ▼ ]            │
│                                        │
│  Audition Number Color                 │
│  [■] #f59e0b  [×]                      │
└────────────────────────────────────────┘
```

- Dropdown lists all event categories (from existing `eventCategories` ref).
- Selecting a category updates `selectedCategory`, triggering the watcher above.
- The `activeCategoryEntry` computed (used by the color picker) continues to work — it matches against `selectedCategory` instead of `state.value?.categoryName`.

**Background color:** `.display-root { background: #060818 }` → `background: #111111`.

---

## Data Flow: Two Concurrent Emcees

```
Emcee A (Hip-Hop)                    Emcee B (Breaking)
      │                                     │
      │ POST ?category=Hip-Hop              │ POST ?category=Breaking
      ▼                                     ▼
stateStore["Event:Hip-Hop"]      stateStore["Event:Breaking"]
      │                                     │
      │ broadcast                           │ broadcast
      ▼                                     ▼
/topic/audition/Event/           /topic/audition/Event/
  Hip-Hop/display                  Breaking/display
      │                                     │
      ▼                                     ▼
Display screen A                   Display screen B
(?category=Hip-Hop)               (?category=Breaking)
```

Zero shared state. Zero interference. Each timer is embedded in its own category's state slot and counts down independently on each display screen.

---

## OBS Setup Workflow

1. Operator loads `/audition/display?event=EventName` on their laptop (logged in).
2. Opens the operator panel (bottom-right cog), picks "Hip-Hop" from the category dropdown.
3. URL updates to `/audition/display?event=EventName&category=Hip-Hop`.
4. Operator copies that URL into OBS browser source. Done.
5. OBS loads the page without a session — the `?category=Hip-Hop` param auto-subscribes without login.

---

## Background Color Standardisation

Both emcee-facing views that used the dark navy `#060818` (a broadcast-display colour that leaked into operator UI) are updated to `#111111` (surface-900, the theme's standard page base):

| File | Location | Before | After |
|------|----------|--------|-------|
| `EmceeRoundView.vue` | Template inline style, line 184 | `#060818` | `#111111` |
| `AuditionDisplay.vue` | `.display-root` CSS, line 260 | `#060818` | `#111111` |

---

## Files Changed

| File | Type of change |
|------|---------------|
| `EventAuditionDisplayService.java` | Composite state store key |
| `EventAuditionDisplayController.java` | Add `?category` param to GET + POST |
| `BES-frontend/src/utils/api.js` | Update `getAuditionDisplayState`, `postAuditionDisplayState` |
| `BES-frontend/src/views/AuditionList.vue` | Category picker gate, remove inline switcher |
| `BES-frontend/src/components/EmceeRoundView.vue` | Thread category through API calls, fix background |
| `BES-frontend/src/views/AuditionDisplay.vue` | Category subscription, operator panel dropdown, fix background |

No database migrations required. No new DTOs. No new routes.
