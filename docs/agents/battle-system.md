# Battle System — permissions, persistence, pitfalls

> Loaded on demand via CLAUDE.md routing. **If the task touches battle mode in any way** (bracket, 7-to-Smoke, phases, votes, overlay, BattleControl/BattleService/LiveMatchPanel, "data not persisting") **read this whole file before opening any source file.** Last verified against code: 2026-07-03.

## 🔴 BEFORE diving into code — answer these 6 questions

When someone reports a battle bug or requests a battle feature, do not open a file until you can answer all of these (ask the user if you can't):

| # | Question | Why It Matters |
|---|----------|---------------|
| 1 | **Which format?** 7-to-Smoke, regular bracket, or both? | Entirely different data structures (`[{name,score}]` vs `{Top16:[[L,R,W],...]}`), different state machines, different UI |
| 2 | **Which role?** Admin, Organiser (PRO/MAX), or Emcee? | Most bugs are role-specific. PRO organisers are blocked at `TierAccessService`; Emcee has restricted endpoints and a separate UI code path in `LiveMatchPanel` |
| 3 | **One event with both formats?** Do they switch categories? | The save-then-load cycle on category switch is the #1 source of state leaks (see Pitfall #0) |
| 4 | **Single tab or multiple?** Any other clients connected? | `activeCategoryName` is a global server field — multiple tabs race on it. Emcees run different categories concurrently, so cross-tab activity is the norm |
| 5 | **What phase is the battle in?** IDLE / LOCKED / VOTING / REVEALED / DECIDED? | Different phases take different code paths in `jumpToRecoveredPair`, `hydrateFromState`, and the template |
| 6 | **Page refresh or server restart?** | Page refresh = frontend re-hydrates from REST. Server restart = `@PostConstruct` loads last active category from DB |

**If the answer to any of these is unclear or "both," investigate each path separately.** The formats diverge in `broadcastBracket`, `setWinner` vs `update7toSmokeMatch`, `hydrateFromState` format gates, and the template sections.

## Endpoint Permission Matrix

**When debugging battle issues, check this first.** Most "data not persisting" or "feature not working" bugs are either (a) missing `EMCEE` on a `@PreAuthorize` annotation, or (b) the organiser is on PRO tier — `TierAccessService.requireBattleAccess()` returns 403 for non-MAX before authorisation even runs.

| Endpoint | Method | Admin | Organiser | Emcee | What It Saves |
|----------|--------|:---:|:---:|:---:|---------------|
| `/battle/bracket` | POST | ✅ | ✅ | ✅ | Bracket state (rounds, winners, topSize) |
| `/battle/score` | POST | ✅ | ✅ | ✅ | Judge vote tally → winner, phase=REVEALED |
| `/battle/phase` | POST | ✅ | ✅ | ✅ | Battle phase transitions |
| `/battle/battle-pair` | POST | ✅ | ✅ | ✅ | Current battler pair |
| `/battle/battle-pair` | DELETE | ✅ | ✅ | ✅ | Clear current pair |
| `/battle/smoke` | POST | ✅ | ✅ | ✅ | 7-to-Smoke battlers list |
| `/battle/revote` | POST | ✅ | ✅ | ✅ | Reset all judge votes |
| `/battle/champion-reveal` | POST | ✅ | ✅ | ✅ | Champion reveal broadcast to overlay/bracket |
| `/battle/judge` | POST | ✅ | ✅ | ❌ | Add battle judge |
| `/battle/judge` | DELETE | ✅ | ✅ | ❌ | Remove battle judge |
| `/battle/judge/weightage` | POST | ✅ | ✅ | ❌ | Update judge weight |
| `/battle/upload` | POST | ✅ | ✅ | ❌ | Upload images |
| `/battle/overlay-config` | POST | ✅ | ✅ | ❌ | Overlay colors/visibility |
| `/battle/active-category` | POST | ✅ | ✅ | ✅ | Switch active event+category |

**Every time you add a new battle endpoint, explicitly decide whether Emcee needs it.** Default to including `'EMCEE'` unless the operation is setup/admin-only. Also wire it through `TierAccessService.requireBattleAccess()` so PRO organisers get 403 instead of partial access. Then add the row to this table.

**This table is NOT exhaustive** — `BattleController` has ~38 mappings (timer, format-timer, battle-mode, image/logo upload+delete, vote, resolved-participants, tie-breaker-state, …). For any endpoint not listed, derive the truth directly:
```bash
grep -n -B2 -A2 "@PreAuthorize" BES/src/main/java/com/example/BES/controllers/BattleController.java
```
Note: `/battle/resolved-participants` and `/battle/tie-breaker-state` also allow **HELPER** — a role this table has no column for. And **zero grep hits for an endpoint means it has no `@PreAuthorize` at all** (no role restriction at the annotation level — true for ~13 of the ~38 mappings, e.g. `/battle/vote`, GET `/state`), not that the endpoint doesn't exist.

## Persistence Architecture

### Two data stores (both in the same `battle_category_state` DB row)

| Store | Backend Field | API | Updated By | Used For |
|-------|-------------|-----|------------|----------|
| `bracketState` | `bracketState` (Map) | POST `/bracket` | `broadcastBracket()` in frontend | Standard bracket rounds+winners + smoke `topSize`/initial array |
| `battlers` | `battlers` (List) | POST `/smoke` | `updateSmokeList()` in frontend, `setScoreService()` in backend | 7-to-Smoke queue order + scores |

**Critical:** Both are saved to the same DB row via `persistActiveState()`. They must stay in sync. `broadcastBracket()` writes BOTH (smoke path only). `updateSmokePair()` writes only `battlers`. `setScoreService()` writes only `battlers`.

### Refresh state restoration

On page refresh, `BattleControl.vue` restores state through two paths:

1. **Recovery path** (`jumpToRecoveredPair`): taken when `battlePhase !== 'IDLE' && currentPair.left` is non-empty. Restores active battle position.
2. **Hydrate path** (`hydrateFromState`): taken when IDLE or no active pair. Restores bracket data only.

Both paths receive state from `GET /battle/state` (REST) and `/topic/battle/state` (WebSocket). The diff guard (`lastAppliedState`) prevents duplicate processing.

### Format detection (7-to-Smoke vs Standard)

- **Frontend:** `isSmoke` = `topSize === 7`. Set by category watcher when category name contains "7 to smoke".
- **Backend:** `categoryFormat` loaded from `EventCategory.format` field. Included in `/battle/state` response. *(The JSON key was renamed `genreFormat` → `categoryFormat` in #149 — old frontend code reading `genreFormat` silently sees undefined.)*
- **`hydrateFromState`:** uses `applySmoke`/`applyStd` hard gates to prevent cross-format data corruption.

## Common Pitfalls

### ⚠️ 0. One event = both formats coexisting (read this first)

**An event can have both 7-to-Smoke and regular battle categories simultaneously.** The user switches between them via the category selector in `LiveMatchPanel`. This is the single most important architectural fact — every other pitfall flows from it.

**The core problem:** `battlePhase`, `champion`, `bracketState`, and `battlers` in `BattleService.java` are **single in-memory fields shared across ALL categories** — NOT per-category structures. When the frontend switches categories:

```
1. persistActiveState()          → saves current in-memory state to OUTGOING category's DB row
2. activeCategoryName = new
3. loadCategoryStateIntoMemory() → overwrites in-memory state from INCOMING category's DB row
```

This save-then-load cycle is the critical path. If anything goes wrong (race condition, missing permission, stale WS message, format mismatch), data from one format **leaks into the other's DB row**. For every change, ask:

- "What happens to this state when the user switches categories?"
- "Is this field scoped to the current category or global?"
- "Does this mutation include a format guard (`isSmoke`/`applySmoke`)?"

### 1. "Data not persisting on refresh" → check permissions first
```
→ Is the user an Emcee?
→ Check Network tab for 403 responses on POST /bracket, POST /champion-reveal
→ Verify @PreAuthorize on the relevant BattleController endpoint includes 'EMCEE'
```

### 2. Silent failures in API calls
Most API functions in `api.js` catch errors with `console.error(e)` but **don't surface them to the UI** and **don't check HTTP status codes**. A 403 returns a Response object that `res.ok` would reject, but callers rarely check. When debugging, always check the browser's Network tab.

### 3. Global backend state shared across categories
`battlePhase`, `champion`, `bracketState`, `battlers` in `BattleService.java` get overwritten by `loadCategoryStateIntoMemory()` on category switch. If two browser tabs have different categories active, the last tab to call `setActiveCategory()` wins the `activeCategoryName` race. DB rows are correctly separated per `(event_name, category_name)`; in-memory state is a single slot.

### 4. `watch(topSize)` clears `currentBattle`/`currentTop`/`currentRound`
These are always reset at the end of the watcher callback. Programmatic changes set `skipSizeChangeClear=true` to suppress this, but if the watcher fires **async after hydration** (triggered by a topSize change in `hydrateFromState`), it can wipe just-restored state. Always guard these clears with the `programmatic` flag.

### 5. 7-to-Smoke `broadcastBracket` must be called after battle operations
`updateSmokePair()` was only calling `updateSmokeList()` (saving battlers), not `setBracketState()` (saving bracket). This left `bracketState.rounds` stale after queue rotation. Now `updateSmokePair` calls `broadcastBracket()` which syncs both.

### 6. `updateSmokeList` was fire-and-forget in `broadcastBracket`
The `await` was missing, causing `updateSmokeList` and `setBracketState` to race on the same DB row. Both write via `persistActiveState()`. Now properly awaited.

### 7. Emcee template parity
When adding UI controls, check both the Organiser section (`!isReadonly`) AND the Emcee section (`isReadonly`) in `LiveMatchPanel.vue`. The two sections are maintained separately and drift — buttons present for Organiser may be missing for Emcee.

## Debugging flow (follow this order — do not skip to step 5)

1. **Network tab:** any 403 or failed requests?
2. **Backend `@PreAuthorize`:** does the user's role have access? (matrix above)
3. **Frontend console:** any caught errors?
4. **DB state:** what does `GET /battle/state` actually return?
5. **Code logic:** only after ruling out 1–4

## Cross-references

- Tier gating pattern and deferred follow-ups (STOMP auth gap, permissive blank-eventName paths): memory `project_organiser_tier_phase.md`
- Battle phase machine + tier gating summary: `docs/agents/routes-and-roles.md`
- Relevant design docs: `docs/superpowers/specs/2026-06-25-battle-audition-submode-design.md`, `2026-06-03-*` battle specs
