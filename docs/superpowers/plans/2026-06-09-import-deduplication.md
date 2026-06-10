# Import Deduplication & Participant Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix sheet reimport so it never creates duplicates, never silently undoes in-app edits, and clearly reports what happened. Unify participant identity across import, walk-in, and edit paths.

**Architecture:** Make `participant.participantName` an immutable identity key (set once, never changed). Use case-insensitive lookup. Add three-tier result reporting (errors/warnings/info) to both sheet import and walk-in flows. The backend already populates DTO `participantName` fields with `displayName` values — no display-layer changes needed.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Vue 3, no DB migration required.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `BES/.../respositories/ParticipantRepo.java` | Modify | Add `IgnoreCase` query |
| `BES/.../services/ParticipantService.java` | Modify | Use `IgnoreCase` in both methods |
| `BES/.../services/EventGenreParticpantService.java` | Modify | Fix `updateParticipant` identity mutation, walk-in member dedup, per-genre result |
| `BES/.../controllers/EventController.java` | Modify | Walk-in endpoint returns per-genre status |
| `BES/.../mapper/RegistrationDtoMapper.java` | Modify | Stage-Name-only fallback |
| `BES/.../dtos/ImportResultDto.java` | Modify | Add warnings/info fields |
| `BES/.../services/RegistrationService.java` | Modify | Add info/warning messages during import |
| `BES-frontend/src/views/EventDetails.vue` | Modify | Update `openModal` + modal template for three tiers |
| `BES-frontend/src/components/CreateParticipantForm.vue` | Modify | Per-genre walk-in result summary |
| `BES-frontend/src/views/UpdateEventDetails.vue` | Modify | "Name" → "Display Name" label |

---

### Task 1: Case-Insensitive Participant Lookup

**Files:**
- Modify: `BES/src/main/java/com/example/BES/respositories/ParticipantRepo.java`
- Modify: `BES/src/main/java/com/example/BES/services/ParticipantService.java`

- [ ] **Step 1: Add `IgnoreCase` query method to ParticipantRepo**

Replace the existing file content with:

```java
package com.example.BES.respositories;

import org.springframework.stereotype.Repository;
import com.example.BES.models.Participant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface ParticipantRepo extends JpaRepository<Participant, Long>{
    Optional<Participant> findFirstByParticipantName(String participantName);
    Optional<Participant> findFirstByParticipantNameIgnoreCase(String participantName);
}
```

- [ ] **Step 2: Update ParticipantService to use case-insensitive lookup**

Replace the file content with:

```java
package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.models.Participant;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class ParticipantService {
    @Autowired
    ParticipantRepo repo;

    public Participant addParticpantService(AddParticipantDto dto){
        return repo.findFirstByParticipantNameIgnoreCase(dto.getParticipantName()).orElseGet(() -> {
            Participant participant = new Participant();
            participant.setParticipantName(dto.getParticipantName());
            return repo.save(participant);
        });
    }

    public Participant addWalkInService(AddWalkInDto dto){
        Participant participant = repo.findFirstByParticipantNameIgnoreCase(dto.name).orElse(new Participant());
        if(participant.getParticipantName() == null){;
            participant.setParticipantName(dto.name);
            participant = repo.save(participant);
        }
        return participant;
    }
}
```

- [ ] **Step 3: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/respositories/ParticipantRepo.java \
        BES/src/main/java/com/example/BES/services/ParticipantService.java
git commit -m "feat: case-insensitive participant lookup for import and walk-in"
```

---

### Task 2: Immutable Participant Identity (Stop Mutating `participantName` on Edit)

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java:606-686`

- [ ] **Step 1: Replace the `updateParticipant` method**

Replace lines 674-685 (the `else` / solo block) so `participantName` is never mutated. Also fix lines 651-657 (team leader name) to not mutate `participantName`.

Read the current method at lines 606-686, then apply these two edits:

**Edit A — Team leader name (lines 651-657):** Remove `participant.setParticipantName(leaderName)` and `participantRepo.save(participant)`:

```java
            // Update leader name (index 0) — keeps participant.participantName and ep.stageName in sync
            // so both the edit modal and check-in card show the same value
            if (dto.memberNames != null && !dto.memberNames.isEmpty()) {
                String leaderName = dto.memberNames.get(0).trim();
                if (!leaderName.isEmpty()) {
                    ep.setStageName(leaderName);
                }
            }
```

**Edit B — Solo block (lines 674-685):** Remove `participant.setParticipantName` and `participantRepo.save`:

```java
        } else {
            if (ep != null) {
                ep.setDisplayName(trimmedName);
                ep.setStageName(trimmedName);
                eventParticipantRepo.save(ep);
            }
            for (EventGenreParticipant egp : egps) {
                egp.setDisplayName(trimmedName);
            }
            repo.saveAll(egps);
        }
```

- [ ] **Step 2: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java
git commit -m "fix: stop mutating participantName on edit — preserve immutable identity key"
```

---

### Task 3: Stage-Name-Only Sheet Fallback

**Files:**
- Modify: `BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java:20-36`

- [ ] **Step 1: Add fallback chain in `mapRow`**

Replace the name index resolution (lines 22-33) with a fallback chain:

```java
        Integer nameIdx = colIndexMap.get(SheetHeader.NAME);
        Integer stageNameIdx = colIndexMap.get(SheetHeader.STAGE_NAME);
        Integer teamNameIdx = colIndexMap.get(SheetHeader.TEAM_NAME);

        // Fallback chain: Name → Stage Name → Team Name → skip row
        if (nameIdx == null) {
            if (stageNameIdx != null) {
                nameIdx = stageNameIdx;
            } else if (teamNameIdx != null) {
                nameIdx = teamNameIdx;
            } else {
                return dto; // no identity column — skip row
            }
        }

        if (row.size() <= nameIdx) return dto;

        dto.setParticipantName(row.get(nameIdx));

        // Stage name: dedicated column if present and different from identity, otherwise identity doubles as stage name
        if (stageNameIdx != null && row.size() > stageNameIdx && !row.get(stageNameIdx).isBlank()) {
            dto.setStageName(row.get(stageNameIdx));
        } else {
            dto.setStageName(row.get(nameIdx));
        }
```

Note: The original fallback on line 28-30 (`if (nameIdx == null && teamNameIdx != null) nameIdx = teamNameIdx`) is now incorporated into the chain above. Remove the old standalone fallback.

- [ ] **Step 2: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java
git commit -m "feat: add Stage-Name-only and Team-Name-only fallback for sheet import"
```

---

### Task 4: Walk-In Improvements (Member Dedup + Per-Genre Result)

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java:86-131`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java:340-358`

- [ ] **Step 1: Add member dedup guard and return status in `addWalkInToEventGenreParticipant`**

Replace lines 96-129 of the method. The method now returns a `Map<String, String>` instead of `EventGenreParticipant`:

```java
    public Map<String, String> addWalkInToEventGenreParticipant(
            Participant p, String genre, EventParticipant ep,
            String judgeName, String entryMode, String teamName, List<String> teamMembers) {

        EventGenre eg = eventGenreRepo.findByEventAndName(ep.getEvent(), genre).orElse(null);
        if (eg == null) throw new IllegalArgumentException("Division not found: " + genre);
        if ("solo".equalsIgnoreCase(entryMode) && !eg.isSoloAllowed())
            throw new IllegalArgumentException("Solo entries are not allowed for this division: " + genre);
        Judge j = judgeRepo.findFirstByName(judgeName).orElse(null);
        EventGenreParticipantId id = new EventGenreParticipantId(ep.getEvent().getEventId(), eg.getId(), p.getParticipantId());
        EventGenreParticipant egp = repo.findById(id).orElse(null);
        boolean isNew = egp == null;
        if (isNew) {
            egp = new EventGenreParticipant();
            egp.setId(id);
            egp.setJudge(j);
            egp.setEvent(ep.getEvent());
            egp.setParticipant(p);
            egp.setEventGenre(eg);

            String genreFormat = eg.getFormat();
            boolean isTeamFormat = isTeamFormat(genreFormat);
            boolean isTeamEntry = isTeamFormat && !"solo".equalsIgnoreCase(entryMode);

            if (isTeamEntry) {
                validateTeamEntry(genreFormat, teamName, teamMembers);
                egp.setFormat(genreFormat);
                egp.setTeamName(teamName);
                egp.setDisplayName(teamName);
            } else {
                egp.setFormat(isTeamFormat ? null : genreFormat);
                egp.setDisplayName(ep.getDisplayName() != null ? ep.getDisplayName() : p.getParticipantName());
            }
        }
        EventGenreParticipant saved = repo.save(egp);

        if (!"solo".equalsIgnoreCase(entryMode) && isTeamFormat(saved.getFormat())
                && teamMembers != null) {
            for (String memberName : teamMembers) {
                if (memberName != null && !memberName.isBlank()
                        && !egpMemberRepo.existsByEventGenreParticipantAndMemberName(saved, memberName)) {
                    egpMemberRepo.save(new EventGenreParticipantMember(saved, memberName));
                }
            }
        }
        Map<String, String> result = new java.util.HashMap<>();
        result.put("status", isNew ? "created" : "existing");
        result.put("genre", genre);
        return result;
    }
```

- [ ] **Step 2: Update the controller to return per-genre result**

Replace lines 343-352 of `EventController.java`:

```java
    public ResponseEntity<?> addWalkInToSystem(@Valid @RequestBody AddWalkInDto dto) {
        try {
            Participant p = participantService.addWalkInService(dto);
            EventParticipant ep = eventParticipantService.addNewWalkInInEventService(p, dto.eventName);
            Map<String, String> result = eventGenreParticipantService.addWalkInToEventGenreParticipant(
                p, dto.genre, ep, dto.judgeName, dto.entryMode, dto.teamName, dto.teamMembers);
            Map<String, Object> walkinMsg = new java.util.HashMap<>();
            walkinMsg.put("eventName", dto.eventName);
            messagingTemplate.convertAndSend("/topic/walkin/", walkinMsg);
            HttpStatus status = "created".equals(result.get("status")) ? HttpStatus.CREATED : HttpStatus.OK;
            return new ResponseEntity<>(result, status);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new java.util.HashMap<>();
            error.put("status", "error");
            error.put("genre", dto.genre);
            error.put("message", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error adding walk-in", e);
            Map<String, String> error = new java.util.HashMap<>();
            error.put("status", "error");
            error.put("message", "Error adding participant");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }
```

Note: The return type changes from `ResponseEntity<String>` to `ResponseEntity<?>`.

- [ ] **Step 3: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: walk-in member dedup + per-genre created/existing result"
```

---

### Task 5: Three-Tier Import Result DTO

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/ImportResultDto.java`

- [ ] **Step 1: Replace ImportResultDto with severity-tagged detail items**

```java
package com.example.BES.dtos;

import java.util.ArrayList;
import java.util.List;

public class ImportResultDto {
    public int imported;
    public int existing;
    public int skipped;
    public List<DetailItem> errors   = new ArrayList<>();
    public List<DetailItem> warnings = new ArrayList<>();
    public List<DetailItem> info     = new ArrayList<>();

    public static class DetailItem {
        public int row;
        public String name;
        public String reason;
        public String severity;

        public DetailItem(int row, String name, String reason, String severity) {
            this.row = row;
            this.name = name;
            this.reason = reason;
            this.severity = severity;
        }
    }

    /** Convenience — for backward compatibility with code still using SkippedRow style */
    public void addError(int row, String name, String reason) {
        errors.add(new DetailItem(row, name, reason, "error"));
    }

    public void addWarning(int row, String name, String reason) {
        warnings.add(new DetailItem(row, name, reason, "warning"));
    }

    public void addInfo(int row, String name, String reason) {
        info.add(new DetailItem(row, name, reason, "info"));
    }
}
```

- [ ] **Step 2: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/ImportResultDto.java
git commit -m "feat: three-tier import result DTO with errors/warnings/info"
```

---

### Task 6: Add Info/Warning Messages During Import

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/RegistrationService.java:86-184`

- [ ] **Step 1: Update `addParticipantToEvent` to use new DTO and add messages**

This task modifies the import loop to:
1. Use `addError`/`addWarning`/`addInfo` convenience methods
2. Add info when: participant already exists, case-insensitive match, genre skipped, new genre added
3. Add warning when: genre in sheet doesn't match any division
4. Add error when: no valid genres parsed for the row

Replace lines 88-182 (the participant loop body). Key changes marked with comments:

```java
        for (AddParticipantDto participant : importable) {
            String participantName = participant.getParticipantName();
            try {
                boolean hasTeamFormatGenre = hasTeamFormatGenre(participant.getGenres(), allDivisions);

                if (hasTeamFormatGenre) {
                    String entryType = participant.getEntryType();
                    boolean soloBlocked = isSoloBlockedForAnyGenre(participant.getGenres(), allDivisions);
                    if ((entryType == null || entryType.isBlank()) && soloBlocked) {
                        result.addError(rowNumber, participantName,
                            "Solo entry not allowed for this division — ENTRY_TYPE required");
                        result.skipped++;
                        rowNumber++;
                        continue;
                    }
                    if ("team".equals(entryType)) {
                        String format = getTeamFormat(participant.getGenres(), allDivisions);
                        validateTeamEntry(format, participant.getTeamName(), participant.getMemberNames());
                    }
                }

                // Check for case-insensitive match
                Participant exactMatch = participantRepo.findFirstByParticipantName(participantName).orElse(null);
                Participant toAddParticipant = participantService.addParticpantService(participant);
                if (exactMatch == null && toAddParticipant != null
                        && !toAddParticipant.getParticipantName().equals(participantName)) {
                    result.addInfo(rowNumber, participantName,
                        "Sheet name '" + participantName + "' matched existing participant '"
                        + toAddParticipant.getParticipantName() + "'");
                }

                EventParticipant ep = eventParticipantRepo
                    .findByEventAndParticipant(event, toAddParticipant).orElse(null);

                boolean isNew = ep == null;
                if (isNew) {
                    ep = new EventParticipant();
                    ep.setParticipant(toAddParticipant);
                    ep.setEvent(event);
                    ep.setStageName(participant.getStageName());
                    ep.setDisplayName(resolveDisplayName(participant));
                    ep.setResidency(participant.getResidency());
                    ep.setGenre(participant.getGenres() != null ? String.join(", ", participant.getGenres()) : "");
                    ep.setPaymentVerified(!event.isPaymentRequired());
                    ep.setScreenshotUrl(participant.getScreenshotUrl());
                    ep.setReferenceCode(ReferenceCodeUtil.generate());
                    eventParticipantRepo.save(ep);
                } else {
                    result.addInfo(rowNumber, participantName,
                        "Already exists — stage name, team members, and removed genres were NOT updated. Use Update Details to edit.");
                }

                if (participant.getGenres() != null && !participant.getGenres().isEmpty()) {
                    boolean anyGenreMatched = false;
                    for (String genreName : participant.getGenres()) {
                        EventGenre eg = findMatchingDivision(allDivisions, genreName);
                        if (eg == null) {
                            result.addWarning(rowNumber, participantName,
                                "Genre '" + genreName + "' didn't match any division — skipped");
                            continue;
                        }
                        anyGenreMatched = true;
                        EventGenreParticipantId id = new EventGenreParticipantId(
                            event.getEventId(), eg.getId(), toAddParticipant.getParticipantId());
                        if (eventGenreParticipantRepo.existsById(id)) {
                            result.addInfo(rowNumber, participantName,
                                "Already in " + eg.getName() + " — skipped");
                            continue;
                        }
                        EventGenreParticipant egp = new EventGenreParticipant();
                        egp.setId(id);
                        egp.setEvent(event);
                        egp.setEventGenre(eg);
                        egp.setParticipant(toAddParticipant);

                        String effectiveFormat = eg.getFormat();
                        boolean isTeamFormat = isTeamFormat(effectiveFormat);
                        boolean isTeamEntry = isTeamFormat && "team".equals(participant.getEntryType());

                        if (isTeamEntry) {
                            String teamDisplayName = participant.getTeamName();
                            long clash = eventGenreParticipantRepo.countByDisplayNameForOtherParticipant(
                                event.getEventId(), eg.getId(), teamDisplayName,
                                toAddParticipant.getParticipantId());
                            if (clash > 0) {
                                result.addError(rowNumber, participantName,
                                    "Team name '" + teamDisplayName + "' already exists in " + eg.getName()
                                    + " under a different participant");
                                continue;
                            }
                            egp.setFormat(effectiveFormat);
                            egp.setTeamName(teamDisplayName);
                            egp.setDisplayName(teamDisplayName);
                        } else {
                            egp.setFormat(isTeamFormat ? null : effectiveFormat);
                            egp.setDisplayName(orElse(participant.getStageName(), participant.getParticipantName()));
                        }

                        EventGenreParticipant savedEgp = eventGenreParticipantRepo.save(egp);

                        if (!isNew) {
                            result.addInfo(rowNumber, participantName,
                                "Added to " + eg.getName());
                        }

                        if (isTeamEntry && participant.getMemberNames() != null) {
                            for (String memberName : participant.getMemberNames()) {
                                if (memberName != null && !memberName.isBlank()
                                        && !egpMemberRepo.existsByEventGenreParticipantAndMemberName(savedEgp, memberName)) {
                                    egpMemberRepo.save(new EventGenreParticipantMember(savedEgp, memberName));
                                }
                            }
                        }
                    }
                    if (!anyGenreMatched) {
                        result.addError(rowNumber, participantName,
                            "No valid genre found — participant not assigned to any division");
                        result.skipped++;
                    }
                }
                if (isNew) result.imported++; else result.existing++;

            } catch (IllegalArgumentException e) {
                result.addError(rowNumber, participantName, e.getMessage());
                result.skipped++;
            }
            rowNumber++;
        }
```

- [ ] **Step 2: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/RegistrationService.java
git commit -m "feat: add info/warning/error messages throughout import loop"
```

---

### Task 7: Frontend — Three-Tier Import Modal

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue:17-21` (refs), `142-148` (openModal), `390-400` (refreshParticipant), `2077-2087` (modal template)

- [ ] **Step 1: Add warning and info refs**

Replace lines 17-21:

```javascript
const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("success")
const modalErrors = ref([])
const modalWarnings = ref([])
const modalInfo = ref([])
```

- [ ] **Step 2: Update `openModal` to accept warnings and info**

Replace lines 142-148:

```javascript
const openModal = (title, message, variant = 'success', errors = [], warnings = [], info = []) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  modalErrors.value = errors
  modalWarnings.value = warnings
  modalInfo.value = info
  showModal.value = true
}
```

- [ ] **Step 3: Update `handleAccept` to clear new refs**

Replace lines 63-65:

```javascript
  showModal.value = false
  modalErrors.value = []
  modalWarnings.value = []
  modalInfo.value = []
```

- [ ] **Step 4: Update `refreshParticipant` to pass warnings and info**

Replace lines 391-400:

```javascript
      createEventResponse.json().then(result => {
        const r = typeof result === 'string' ? JSON.parse(result) : result
        const imported = r?.imported ?? r?.IMPORTED ?? 0
        const existing = r?.existing ?? 0
        const skipped = r?.skipped ?? r?.SKIPPED ?? 0
        const errors = r?.errors ?? r?.ERRORS ?? []
        const warnings = r?.warnings ?? []
        const info = r?.info ?? []
        let msg = `${imported} new participant${imported !== 1 ? 's' : ''} added`
        if (existing > 0) msg += `, ${existing} already existed`
        if (skipped > 0) msg += `, ${skipped} skipped`
        const hasDetails = errors.length > 0 || warnings.length > 0 || info.length > 0
        openModal('Import Complete', msg, hasDetails ? 'warning' : 'success', errors, warnings, info)
      })
```

- [ ] **Step 5: Update modal template to render three tiers**

Replace lines 2077-2087:

```html
    <p class="type-body text-content-secondary">{{ modalMessage }}</p>
    <div v-if="modalErrors.length > 0" class="mt-3 space-y-1 max-h-48 overflow-y-auto">
      <div class="type-label text-xs text-red-400/80 mb-1">Errors</div>
      <div
        v-for="(e, i) in modalErrors"
        :key="'err-' + i"
        class="type-label text-xs text-red-400/80 bg-red-400/5 border-l-2 border-red-400/40 px-2 py-1 normal-case"
      >Row {{ e.row }}: <span class="text-content-secondary">{{ e.name }}</span> — {{ e.reason }}</div>
    </div>
    <div v-if="modalWarnings.length > 0" class="mt-3 space-y-1 max-h-48 overflow-y-auto">
      <div class="type-label text-xs text-amber-400/80 mb-1">Warnings</div>
      <div
        v-for="(w, i) in modalWarnings"
        :key="'warn-' + i"
        class="type-label text-xs text-amber-400/80 bg-amber-400/5 border-l-2 border-amber-400/40 px-2 py-1 normal-case"
      >Row {{ w.row }}: <span class="text-content-secondary">{{ w.name }}</span> — {{ w.reason }}</div>
    </div>
    <div v-if="modalInfo.length > 0 && modalInfo.length <= 10" class="mt-3 space-y-1 max-h-48 overflow-y-auto">
      <div class="type-label text-xs text-content-muted mb-1">Info</div>
      <div
        v-for="(inf, i) in modalInfo"
        :key="'info-' + i"
        class="type-label text-xs text-content-muted bg-white/2 border-l-2 border-white/10 px-2 py-1 normal-case"
      >Row {{ inf.row }}: <span class="text-content-secondary">{{ inf.name }}</span> — {{ inf.reason }}</div>
    </div>
    <div v-if="modalInfo.length > 10" class="mt-2 type-label text-xs text-content-muted normal-case px-2">
      ... and {{ modalInfo.length - 10 }} more info items
    </div>
```

- [ ] **Step 6: Build frontend to verify**

Run: `cd BES-frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: three-tier import modal with errors, warnings, and info"
```

---

### Task 8: Frontend — Walk-In Per-Genre Result Summary

**Files:**
- Modify: `BES-frontend/src/components/CreateParticipantForm.vue:89-120`

- [ ] **Step 1: Update `submitNewEntry` to collect per-genre results**

Replace lines 89-119:

```javascript
const submitNewEntry = async () => {
  if (name.value.trim() === "") {
    showError.value = true
    return
  }
  if (createTable.genres.length === 0) {
    showNoDivisionError.value = true
    return
  }
  const results = { created: [], existing: [], failed: [] }
  for (const g of createTable.genres) {
    const mode = entryModes[g] || 'team'
    const members = mode === 'solo' ? [] : (teamMemberNames[g] || []).filter(m => m.trim() !== "")
    const tName = mode === 'solo' ? '' : (teamNames[g] || '')
    try {
      const res = await addWalkinToSystem(name.value, props.event, g, selectedJudge.value, members, tName, mode)
      if (!res || !res.ok) {
        results.failed.push(g)
      } else {
        const body = await res.json().catch(() => null)
        if (body?.status === 'created') results.created.push(g)
        else results.existing.push(g)
      }
    } catch {
      results.failed.push(g)
    }
  }
  name.value = ""
  createTable.genres = []
  Object.keys(entryModes).forEach(k => { delete entryModes[k]; delete teamNames[k]; delete teamMemberNames[k] })
  selectedJudge.value = ""
  emit("createNewEntry")
  if (results.failed.length > 0) {
    walkinResult.value = results
    showSubmitError.value = true
  } else if (results.created.length === 0 && results.existing.length > 0) {
    // Everything was already registered
    walkinResult.value = results
    showAllExisting.value = true
  } else {
    walkinResult.value = results
    showSuccess.value = true
  }
}
```

- [ ] **Step 2: Add new state refs**

Add after line 24 (after `showSubmitError`):

```javascript
const walkinResult = ref({ created: [], existing: [], failed: [] })
const showAllExisting = ref(false)
```

- [ ] **Step 3: Update the success modal to show per-genre summary**

Replace lines 300-309 (the "Participant Added" modal):

```html
  <ActionDoneModal
    :show="showSuccess"
    title="Participant Processed"
    variant="info"
    acceptLabel="Done"
    @accept="() => { showSuccess = false; $emit('close') }"
    @close="() => { showSuccess = false; $emit('close') }"
  >
    <div class="space-y-2">
      <p v-if="walkinResult.created.length > 0" class="type-body text-emerald-400">
        ✅ Added to: {{ walkinResult.created.join(', ') }}
      </p>
      <p v-if="walkinResult.existing.length > 0" class="type-body text-content-muted">
        ℹ️ Already in: {{ walkinResult.existing.join(', ') }}
      </p>
    </div>
  </ActionDoneModal>
```

- [ ] **Step 4: Add the "all existing" modal**

Add after the success modal:

```html
  <ActionDoneModal
    :show="showAllExisting"
    title="Already Registered"
    variant="info"
    acceptLabel="Done"
    @accept="() => { showAllExisting = false; $emit('close') }"
    @close="() => { showAllExisting = false; $emit('close') }"
  >
    <p class="type-body text-content-muted">
      ℹ️ {{ walkinResult.existing.join(', ') }} — already registered.
    </p>
  </ActionDoneModal>
```

- [ ] **Step 5: Update the submit error modal to show per-genre detail**

Replace lines 311-319 (the "Submission Failed" modal):

```html
  <ActionDoneModal
    :show="showSubmitError"
    title="Submission Failed"
    variant="error"
    @accept="showSubmitError = false"
    @close="showSubmitError = false"
  >
    <div class="space-y-2">
      <p v-if="walkinResult.created.length > 0" class="type-body text-emerald-400">
        ✅ Added to: {{ walkinResult.created.join(', ') }}
      </p>
      <p v-if="walkinResult.existing.length > 0" class="type-body text-content-muted">
        ℹ️ Already in: {{ walkinResult.existing.join(', ') }}
      </p>
      <p v-if="walkinResult.failed.length > 0" class="type-body text-red-400">
        ❌ Failed: {{ walkinResult.failed.join(', ') }}
      </p>
    </div>
  </ActionDoneModal>
```

- [ ] **Step 6: Build frontend to verify**

Run: `cd BES-frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/components/CreateParticipantForm.vue
git commit -m "feat: walk-in per-genre result summary (created/existing/failed)"
```

---

### Task 9: Frontend — Edit Modal Label Change

**Files:**
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue:388-396`

- [ ] **Step 1: Change "Name" label to "Display Name" for solo**

Replace lines 388-396:

```html
            <label class="field-label">
              {{ editTarget?.isTeam ? 'Team Name' : 'Display Name' }}
              <span class="required">*</span>
            </label>
            <input
              v-model="editName"
              class="field-input"
              :placeholder="editTarget?.isTeam ? 'Team name' : 'Display name'"
            />
```

- [ ] **Step 2: Build frontend to verify**

Run: `cd BES-frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/UpdateEventDetails.vue
git commit -m "fix: change edit modal label from 'Name' to 'Display Name' for solo participants"
```

---

### Task 10: Update Tests for API Changes

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/ParticipantServiceTest.java`
- Modify: `BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java:160-169`

- [ ] **Step 1: Update ParticipantServiceTest to mock `IgnoreCase` method**

Replace `findFirstByParticipantName` with `findFirstByParticipantNameIgnoreCase` in all mock setups:

Lines 29, 43, 56 — change method name in `when()` calls:

```java
// Line 29 (addParticipant_createsAndSaves test) — no change needed (calls save only when not found)
// Line 43:
when(repo.findFirstByParticipantNameIgnoreCase("Existing")).thenReturn(Optional.of(existing));
// Line 56:
when(repo.findFirstByParticipantNameIgnoreCase("Newbie")).thenReturn(Optional.of(empty));
```

- [ ] **Step 2: Update EventControllerIntegrationTest for walk-in return type change**

Replace lines 161-169:

```java
        when(participantService.addWalkInService(any())).thenReturn(p);
        when(eventParticipantService.addNewWalkInInEventService(any(), any())).thenReturn(ep);
        Map<String, String> walkinResult = new java.util.HashMap<>();
        walkinResult.put("status", "created");
        walkinResult.put("genre", "Test Genre");
        when(eventGenreParticipantService.addWalkInToEventGenreParticipant(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(walkinResult);

        mockMvc.perform(post("/api/v1/event/walkins/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.genre").value("Test Genre"));
```

- [ ] **Step 3: Run tests to verify**

Run: `cd BES && mvn test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/java/com/example/BES/services/ParticipantServiceTest.java \
        BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java
git commit -m "test: update mocks for IgnoreCase lookup and walk-in result type"
```

---

### Task 11: End-to-End Verification

- [ ] **Step 1: Run backend tests**

Run: `cd BES && mvn test`
Expected: All tests pass

- [ ] **Step 2: Run frontend tests**

Run: `cd BES-frontend && npm test`
Expected: All tests pass

- [ ] **Step 3: Manual smoke test checklist**

Run the app and verify each item from the spec verification checklist:

- Import "Alice" → reimport → 1 existing, 0 imported
- Import "Alice" → edit name → reimport → no duplicate
- Import "alice" → reimport "Alice" → no duplicate
- Sheet with only "Stage Name" → imports correctly
- Same person, two rows, two teams, two genres → two EGPs, one check-in entry
- Walk-in Alice to same genre → "existing" result
- Walk-in Alice multi-genre [Popping, Hip Hop] → per-genre summary
- Edit solo name → displayName changes, participantName unchanged
- Edit team name → teamName changes, participantName unchanged

- [ ] **Step 4: Final commit (if any fixes from verification)**

```bash
git add -A
git commit -m "chore: verification fixes for import dedup"
```
