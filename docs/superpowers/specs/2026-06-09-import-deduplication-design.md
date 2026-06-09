# Import Deduplication & Participant Identity Design

## Goal

Fix Google Sheets reimport so it never creates duplicates, never silently undoes in-app edits, and clearly reports what did and didn't happen.

## Context

Three participant entry paths must work consistently together:

| Path | Trigger | Purpose |
|------|---------|---------|
| **Sheet import** | Organiser clicks import on EventDetails | Bulk initial import |
| **Walk-in** | Organiser/Helper adds manually | On-the-day registration |
| **Update Details** | Organiser edits participant in table | Fix names, team composition |

The sheet is for bulk **initial** import. After that, edits happen in-app. Reimport's job is to pick up **only genuinely new rows** without disturbing existing data.

### Architecture Recap

```
Participant (global — one record across all events)
  └─ EventParticipant (per event — linked to one Participant)
       └─ EventGenreParticipant (per event+genre — linked to one EP + Genre)
            └─ EventGenreParticipantMember (per EGP — team members)
```

| Field | Entity | Scope | Proposed Mutability |
|-------|--------|-------|---------------------|
| `participantName` | `Participant` | Global | **Immutable** after first import/walk-in |
| `stageName` | `EventParticipant` | Per event | Editable |
| `displayName` | `EventParticipant` | Per event | Editable |
| `teamName` | `EventParticipant` + `EventGenreParticipant` | Per event / per event+genre | Editable |

---

## Design

### 1. Case-Insensitive Participant Lookup

**Problem:** `ParticipantRepo.findFirstByParticipantName` does exact match. "alice" ≠ "Alice" → duplicate. Combined with Anton SC font (all-uppercase appearance), users can't visually distinguish case in the UI.

**Fix:** Add `IgnoreCase` variant to the repo; both `ParticipantService.addParticpantService` (sheet import) and `addWalkInService` (walk-in) use it.

```java
// ParticipantRepo — new method
Optional<Participant> findFirstByParticipantNameIgnoreCase(String participantName);

// ParticipantService — both methods use IgnoreCase
public Participant addParticpantService(AddParticipantDto dto) {
    return repo.findFirstByParticipantNameIgnoreCase(dto.getParticipantName())
        .orElseGet(() -> repo.save(new Participant(dto.getParticipantName())));
}
```

**Affected files:** `ParticipantRepo.java`, `ParticipantService.java`

---

### 2. Immutable Participant Identity

**Problem:** `updateParticipant()` (called by the Update Details edit modal) currently does `participant.setParticipantName(trimmedName)` for both solo and team edits. This changes the global identity key, so reimport can't find the participant by their original sheet name → creates a duplicate.

**Fix:** `updateParticipant` must never mutate `participant.participantName`.

- **Solo:** Set `ep.displayName` and `ep.stageName` only. `participant.participantName` stays as-is.
- **Team:** Set `ep.stageName` from `memberNames[0]` (leader name). Set `ep.teamName`, `ep.displayName`, and per-EGP `displayName`/`teamName` from the edited team name. `participant.participantName` stays as-is.

```java
// BEFORE (solo):
participant.setParticipantName(trimmedName);  // ← REMOVE THIS
participantRepo.save(participant);
ep.setDisplayName(trimmedName);

// AFTER (solo):
ep.setDisplayName(trimmedName);
ep.setStageName(trimmedName);  // stageName is per-event, safe to update
eventParticipantRepo.save(ep);

// BEFORE (team):
participant.setParticipantName(leaderName);   // ← REMOVE THIS
participantRepo.save(participant);

// AFTER (team):
ep.setStageName(leaderName);  // stageName is per-event, safe to update
```

**Affected files:** `EventGenreParticpantService.updateParticipant()`

---

### 3. Stage-Name-Only Sheet Fallback

**Problem:** If a sheet has "Stage Name" but no "Name" column, the row is silently dropped because `RegistrationDtoMapper.mapRow()` returns an empty DTO when `nameIdx` is null.

**Fix:** Add a fallback chain in the mapper:
```
"Name" present → participantName = Name
  └─ Not present → "Stage Name" present → participantName = Stage Name
       └─ Not present → "Team Name" present → participantName = Team Name
            └─ Not present → skip row
```

**Affected files:** `RegistrationDtoMapper.mapRow()`

---

### 4. Walk-In Improvements

#### 4a. Team Member Dedup

**Problem:** `addWalkInToEventGenreParticipant` doesn't check for existing members before saving. If the same walk-in is submitted twice, the EGP is caught by the composite key check but members are saved unconditionally → duplicate `EventGenreParticipantMember` rows.

**Fix:** Add `existsBy*` guard before each member save, matching the sheet import pattern:

```java
if (!egpMemberRepo.existsByEventGenreParticipantAndMemberName(saved, memberName)) {
    egpMemberRepo.save(new EventGenreParticipantMember(saved, memberName));
}
```

#### 4b. Per-Genre Result Reporting

**Problem:** The walk-in form allows selecting multiple genres. The frontend loops over each genre calling `addWalkinToSystem` once per genre. But the backend always returns "Added walkin" even when the EGP already exists (silent reuse). The frontend only shows a binary "success" or "one or more failed" — the user doesn't know which genre was added vs already existed.

**Fix:** Backend returns a distinct response per outcome:

| Case | HTTP | Response Body |
|------|------|---------------|
| EGP created (new genre) | 201 | `{"status": "created", "genre": "Hip Hop"}` |
| EGP already exists | 200 | `{"status": "existing", "genre": "Popping", "message": "Already registered in Popping"}` |
| Validation error | 400 | `{"status": "error", "genre": "Popping", "message": "..."}` |

Both 200 and 201 are `res.ok`, so the frontend loop continues through all selected genres regardless of duplicates. The frontend collects per-genre results and shows a summary:

```
Participant processed
─────────────────────
✅ Added to Hip Hop (2v2 as Crew Beta)
ℹ️ Already in Popping — skipped
```

**Affected files:** `EventGenreParticpantService.addWalkInToEventGenreParticipant()`, `CreateParticipantForm.vue` (`submitNewEntry`)

---

### 5. Three-Tier Import Result Reporting

**Problem:** Many import outcomes are silently swallowed — genre mismatch, already-existing participants not being updated, zero genres parsed.

**Fix:** Expand `ImportResultDto` with severity-tagged detail items, distinguishing errors (blocked the row), warnings (something was skipped), and info (expected no-ops, successful additions for existing participants).

#### Backend: ImportResultDto Changes

```java
public class ImportResultDto {
    public int imported;
    public int existing;
    public int skipped;
    public List<DetailItem> errors   = new ArrayList<>();   // 🔴 blocked the row
    public List<DetailItem> warnings = new ArrayList<>();   // ⚠️ something skipped
    public List<DetailItem> info     = new ArrayList<>();   // ℹ️ expected no-ops, successful adds

    public static class DetailItem {
        public int row;
        public String name;
        public String reason;
        public String severity;  // "error" | "warning" | "info"
    }
}
```

#### What Gets Reported — Per Participant Row

| Condition | Severity | Message |
|-----------|----------|---------|
| `entryType` blocked by solo restriction | `error` | `"Solo entry not allowed for this division — ENTRY_TYPE required"` |
| No valid genre found in any category column | `error` | `"No valid genre found — participant not assigned to any division"` |
| Team member count vs format mismatch | `error` | `"Member count mismatch: 3v3 requires 2 additional member(s), got 1"` |
| Team name clash with existing EGP | `error` | `"Team name 'Crew Alpha' already exists in Popping under a different participant"` |
| Genre in sheet doesn't match any division | `warning` | `"Genre 'Hip Hop' didn't match any division — skipped"` |
| Participant already exists (EP level) | `info` | `"Alice already exists — stage name, team members, and removed genres were NOT updated. Use Update Details to edit."` |
| Case-insensitive name match | `info` | `"Sheet name 'alice' matched existing participant 'Alice'"` |
| Participant already in this genre | `info` | `"Alice already in Popping — skipped"` |
| New genre added for existing participant | `info` | `"Added Alice to Hip Hop"` |
| New participant imported | (counter) | Counted in `imported` |
| Walk-in member already exists | (silent skip) | No message needed |

#### Frontend: Modal Rendering

The import modal already accepts `errors`. Extend to accept `warnings` and `info` and render each tier with appropriate icon and color:

```
Import Complete
─────────────────────────────────
3 new participants added
5 already existed (not modified)
2 skipped due to errors

🔴 Errors (2)
  Row 5 "Bob": No valid genre found — participant not assigned
  Row 8 "Charlie": ENTRY_TYPE required for team division

⚠️ Warnings (1)
  Row 3 "Diana": "Hip Hop" didn't match any division — skipped

ℹ️ Info (4)
  Row 1 "Alice": Already exists — sheet changes not applied
  Row 1 "Alice": Added to Hip Hop
  Row 2 "alice": Matched existing participant "Alice"
  Row 4 "Eve": Already in Popping — skipped
```

**Affected files:** `RegistrationService.addParticipantToEvent()`, `ImportResultDto.java`, `EventDetails.vue` (`refreshParticipant`, `openModal`)

---

### 6. Frontend: Edit Modal Label Update

**Problem:** The edit modal labels the field "Name" but after this change it edits the display name, not the identity key. Users should know what they're changing.

**Fix:** Change the label from "Name" to "Display Name" for solo participants. For team, the label already says "Team Name" which is correct.

**Affected files:** `UpdateEventDetails.vue` edit modal template

---

## What Does NOT Change

- **Sheet import still skips updates for existing participants.** By design — the sheet is for initial bulk import; ongoing edits happen in-app. The difference is now users are **told** this happened.
- **Genres removed from the sheet are still NOT removed from DB.** This is intentional — removing a genre is destructive and should only happen via the UI with explicit confirmation.
- **New genres in the sheet for existing participants ARE still added.** The EGP-level `existsById` check handles this correctly: genuinely new genres create new EGPs; duplicates are skipped with an info message. A single person with two rows for two different genres/teams works as expected.
- **Team members on reimport are still not updated.** Changes go through Update Details.
- **No DB migration needed.** All changes are logic-only; no new columns or schema changes.

---

## Sheet Column Configuration (User-Facing)

After these changes, here's what users need to know about sheet setup:

| Column | Required? | Role |
|--------|-----------|------|
| **Name** | Required (unless Stage Name present) | Immutable identity key. For team, this is the leader/registrant. |
| **Stage Name** | Optional | Display name shown in UI. Falls back to Name if absent. Can differ per event. |
| **Team Name** | Optional (team only) | Team display name. Editable in-app. |
| **Member 2 Name**, etc. | Optional (team only) | Additional team members beyond the leader. |
| **Categories** | Required | Genre assignment. Format embedded (e.g., "Popping 1v1"). |

---

## Display Name Audit

**Background:** After making `participant.participantName` immutable, we must ensure no UI renders the raw DB `participantName` — all display should use `displayName`.

**Finding:** The backend already populates all DTO `participantName` fields with `displayName` values. The JSON field is confusingly named (it says `participantName` but contains `displayName` data), but the frontend already renders correct display values. No frontend changes needed.

### Backend DTO Population (all correct)

| Endpoint | DTO JSON Field | Backend Sets From |
|----------|---------------|-------------------|
| `GET /participants/{eventName}` | `participantName` | `egp.getDisplayName()` |
| `GET /verified-participant/{eventName}` | `name` | `ep.getDisplayName()` |
| Scores (ScoreService) | `participantName` | `egp.getDisplayName()` |
| `GET /results` | `participantName` | `ep.getDisplayName()` |
| Check-in list | `label` | EGP team name → stage → display → participant name (fallback chain) |
| Pickup crews | `displayName` | `egp.getDisplayName()` |
| Participant refs | `participantName` | `ep.getDisplayName()` |

### Frontend Pages Using `participantName` from API (all receive displayName data)

| Page | Key Usage | Verify |
|------|-----------|--------|
| Score.vue | Podium, scoreboard, tie-breaker | ✅ Data is displayName |
| AuditionList.vue | Judge cards, emcee rounds, auto-save | ✅ Data is displayName |
| BattleControl.vue | Participant pool, bracket seeding | ✅ Data is displayName |
| SwipeableCardsV2.vue | Scoring card name | ✅ Data is displayName |
| EmceeRoundView.vue | Round display | ✅ Data is displayName |
| PairScoreCards.vue | Score card | ✅ Data is displayName |
| Results.vue | Public results | ✅ Data is displayName |
| UpdateEventDetails.vue | Edit table | ✅ Data is displayName |
| EventDetails.vue | Participant lists | ✅ Data is displayName |
| FeedbackPopout.vue | Feedback header | ✅ Data is displayName |
| MiniScoreMenu.vue | Mini score | ✅ Data is displayName |
| CrewFormation.vue | Crew member display | ✅ Uses `displayName` field |

### Note: Auto-Save / localStorage Keys

`AuditionList.vue` (auto-save timers) and `Score.vue` (tie-breaker selections in localStorage) key by `participantName` (which is `displayName`). If a user edits a participant's display name, these keys break. This is **pre-existing behavior**, not introduced by this change. Not in scope for this fix.

---

## Verification Checklist

### Import / Reimport
- [ ] Import "Alice" → reimport same sheet → count shows 1 existing, 0 imported
- [ ] Import "Alice" → edit name to "Alice Wonderland" via Update Details → reimport → no duplicate, info says "Alice matched existing Alice Wonderland"
- [ ] Import with case variant "alice" → reimport with "Alice" → no duplicate
- [ ] Sheet with only "Stage Name" column → participant imports correctly
- [ ] Sheet genre doesn't match any division → warning in import result
- [ ] Sheet row with no valid genre → error in import result
- [ ] Same person, two rows, two different teams for two different genres → two EGPs created, one check-in entry with both genres

### Walk-In (Single + Multi-Genre)
- [ ] Walk-in Alice to Popping (new) → 201 Created
- [ ] Walk-in Alice to Popping again (duplicate) → 200 Existing with message "Already registered in Popping"
- [ ] Walk-in Alice with genres [Popping, Hip Hop] where Popping exists but Hip Hop is new → Popping reports "existing", Hip Hop reports "created", summary shows both
- [ ] Walk-in same team twice → no duplicate members
- [ ] Walk-in Alice to Hip Hop (different genre) → 201 Created, check-in list shows one Alice entry with genres=[Popping, Hip Hop]

### Update Details
- [ ] Edit solo name via Update Details → displayName changes, participantName stays unchanged in DB
- [ ] Edit team name via Update Details → teamName changes on EP + EGPs, participantName stays unchanged in DB

### Cross-Event
- [ ] Same person imported in two different events → one Participant record, two EventParticipants, different stageNames per event

### Cross-Path Consistency
- [ ] Sheet import Alice → walk-in Alice to same genre → walk-in reports "existing"
- [ ] Sheet import Alice → walk-in Alice to new genre → walk-in reports "created", both genres show under one check-in entry
- [ ] Sheet import Alice (team "Crew Alpha", Popping 2v2) → Add Genre for Alice to Hip Hop 2v2 as "Crew Beta" → one check-in entry with both genres

### Duplicate Prevention Strategy (Revised)

**Decision:** Do NOT lock the entire genre loop behind `if (isNew)`. That would break the "same person, two teams, two genres" sheet import use case. Instead, rely on the EGP-level `existsById` composite key check, which already prevents true duplicates at the `(eventId, eventGenreId, participantId)` level.

| Scenario | Check | Behavior | Message |
|----------|-------|----------|---------|
| New participant | `isNew=true` | Create EP + all EGPs | Counted in `imported` |
| Existing participant, genuinely new genre | `existsById` → false | Create new EGP | Info: "Added Alice to Hip Hop" |
| Existing participant, same genre | `existsById` → true | Skip | Info: "Alice already in Popping — skipped" |
| Genre deleted via UI, then reimported | `existsById` → false | Re-created | ⚠️ Warning: "Added Alice to Popping (was previously removed)" |

The deletion-then-reimport edge case is handled by a warning message rather than silently blocking — the organiser sees it and can either accept it or remove it again via the UI.

Implementation: the genre loop stays as-is for all participants. No `if (isNew)` gate. The `existsById` check handles deduplication. New `info`/`warning` messages make every action visible.
