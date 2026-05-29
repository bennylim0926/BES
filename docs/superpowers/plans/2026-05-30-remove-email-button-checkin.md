# Remove Email Dependency & Button-Based Check-in — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove email as a participant identifier and delivery mechanism; replace QR-based event-day check-in with an organizer-driven button on the AuditionNumber screen.

**Architecture:** Strip email fields from DB, models, DTOs, and services bottom-up; simplify `RegistrationService` to only track payment status; add a `GET /{eventName}/checkin-list` endpoint + a split-pane check-in UI in `AuditionNumber.vue` that calls the existing audition number assignment endpoint.

**Tech Stack:** Spring Boot (Java 21), Flyway, Spring Data JPA, MockMvc, JUnit 5, Mockito, Vue 3 (Composition API), Tailwind CSS, STOMP WebSocket.

---

## File Map

**Create:**
- `BES/src/main/resources/db/migration/V17__remove_email_from_participant.sql`
- `BES/src/main/java/com/example/BES/dtos/GetCheckinListDto.java`

**Modify:**
- `BES/src/main/java/com/example/BES/models/Participant.java`
- `BES/src/main/java/com/example/BES/models/EventParticipant.java`
- `BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java`
- `BES/src/main/java/com/example/BES/dtos/GetEventGenreParticipantDto.java`
- `BES/src/main/java/com/example/BES/respositories/ParticipantRepo.java`
- `BES/src/main/java/com/example/BES/services/ParticipantService.java`
- `BES/src/main/java/com/example/BES/services/RegistrationService.java`
- `BES/src/main/java/com/example/BES/services/GoogleSheetService.java`
- `BES/src/main/java/com/example/BES/controllers/EventController.java`
- `BES/src/test/java/com/example/BES/services/RegistrationServiceTest.java`
- `BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java`
- `BES-frontend/src/utils/api.js`
- `BES-frontend/src/views/EventDetails.vue`
- `BES-frontend/src/views/AuditionList.vue`
- `BES-frontend/src/views/AuditionNumber.vue`

---

## Task 1: Flyway Migration

**Files:**
- Create: `BES/src/main/resources/db/migration/V17__remove_email_from_participant.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Drop unique index on participant_email before dropping the column
ALTER TABLE participant DROP CONSTRAINT IF EXISTS participant_participant_email_key;
ALTER TABLE participant DROP COLUMN IF EXISTS participant_email;

ALTER TABLE event_participant DROP COLUMN IF EXISTS email_sent;
```

- [ ] **Step 2: Verify the file exists and has no typos**

```bash
cat BES/src/main/resources/db/migration/V17__remove_email_from_participant.sql
```

Expected: file prints both ALTER statements cleanly.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V17__remove_email_from_participant.sql
git commit -m "feat: V17 migration — drop participant_email and email_sent columns"
```

---

## Task 2: Strip Email from Models

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/Participant.java`
- Modify: `BES/src/main/java/com/example/BES/models/EventParticipant.java`

- [ ] **Step 1: Remove `participantEmail` from `Participant.java`**

Replace the current file content with:

```java
package com.example.BES.models;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participantId;

    private String participantName;

    @OneToMany(mappedBy = "participant")
    private List<EventParticipant> eventParticipants;

    @OneToMany(mappedBy = "participant")
    private List<EventGenreParticipant> eventGenreParticipants;
}
```

- [ ] **Step 2: Remove `emailSent` from `EventParticipant.java`**

Remove the line `private boolean emailSent = false;` from `EventParticipant.java`. The rest of the file is unchanged.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/Participant.java \
        BES/src/main/java/com/example/BES/models/EventParticipant.java
git commit -m "feat: remove participantEmail from Participant and emailSent from EventParticipant"
```

---

## Task 3: Update DTOs

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/GetEventGenreParticipantDto.java`
- Create: `BES/src/main/java/com/example/BES/dtos/GetCheckinListDto.java`

- [ ] **Step 1: Remove `participantEmail` from `AddParticipantDto.java`**

Replace with:

```java
package com.example.BES.dtos;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddParticipantDto {
    public String eventName;
    public String participantName;
    public String residency;
    public List<String> genres;
    public Boolean paymentStatus;
    public String screenshotUrl;
    public String stageName;
    public String teamName;
    public List<String> memberNames;
    public Map<String, String> genreFormats;
}
```

- [ ] **Step 2: Remove `emailSent` from `GetEventGenreParticipantDto.java`**

Replace with:

```java
package com.example.BES.dtos;

import java.util.List;

public class GetEventGenreParticipantDto {
    public String eventName;
    public String participantName;
    public String genreName;
    public String judgeName;
    public Integer auditionNumber;
    public Boolean walkin;
    public Long participantId;
    public Long eventId;
    public Long genreId;
    public String referenceCode;
    public List<String> memberNames;
    public String format;
}
```

- [ ] **Step 3: Create `GetCheckinListDto.java`**

```java
package com.example.BES.dtos;

import java.util.List;

public class GetCheckinListDto {
    public Long participantId;
    public Long eventId;
    public String label;
    public List<GenreStatus> genres;

    public static class GenreStatus {
        public String genreName;
        public Integer auditionNumber;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java \
        BES/src/main/java/com/example/BES/dtos/GetEventGenreParticipantDto.java \
        BES/src/main/java/com/example/BES/dtos/GetCheckinListDto.java
git commit -m "feat: remove email fields from DTOs, add GetCheckinListDto"
```

---

## Task 4: Update ParticipantRepo and ParticipantService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/respositories/ParticipantRepo.java`
- Modify: `BES/src/main/java/com/example/BES/services/ParticipantService.java`

- [ ] **Step 1: Remove `findByParticipantEmail` from `ParticipantRepo.java`**

Replace with:

```java
package com.example.BES.respositories;

import org.springframework.stereotype.Repository;
import com.example.BES.models.Participant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface ParticipantRepo extends JpaRepository<Participant, Long>{
    Optional<Participant> findByParticipantName(String participantName);
}
```

- [ ] **Step 2: Simplify `ParticipantService.addParticipantService()` — always create a new `Participant`**

Replace with:

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

    public Participant addParticpantService(AddParticipantDto dto) {
        Participant participant = new Participant();
        participant.setParticipantName(dto.getParticipantName());
        return repo.save(participant);
    }

    public Participant addWalkInService(AddWalkInDto dto) {
        Participant participant = repo.findByParticipantName(dto.name).orElse(new Participant());
        if (participant.getParticipantName() == null) {
            participant.setParticipantName(dto.name);
            participant = repo.save(participant);
        }
        return participant;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/respositories/ParticipantRepo.java \
        BES/src/main/java/com/example/BES/services/ParticipantService.java
git commit -m "feat: remove email lookup from ParticipantRepo and ParticipantService"
```

---

## Task 5: Refactor RegistrationService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/RegistrationService.java`

Changes:
- Remove `MailSenderService` dependency
- `addParticipantToEvent` no longer sends email; throws only `IOException`
- `verifyAndEmail` → `verifyPayment` (just sets `paymentVerified = true`)
- `verifyAndEmailBatch` → `verifyPaymentBatch`
- Add `getCheckinList(String eventName)`
- Remove the private `resolveEmailDisplayName` helper name (rename to `resolveDisplayName`)

- [ ] **Step 1: Replace `RegistrationService.java`**

```java
package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.GetCheckinListDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.dtos.VerifyParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.EventParticipantTeamMember;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.utils.ReferenceCodeUtil;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;

@Service
public class RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    @Autowired(required = false)
    GoogleSheetService sheetService;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    ParticipantService participantService;

    @Autowired
    ParticipantRepo participantRepo;

    @Autowired
    EventParticipantRepo eventParticipantRepo;

    @Autowired
    EventGenreParticpantRepo eventGenreParticipantRepo;

    @Autowired
    EventParticipantTeamMemberRepo teamMemberRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

    public void addParticipantToEvent(AddParticipantToEventDto dto) throws IOException {
        Event event = eventRepo.findByEventName(dto.eventName).orElse(null);
        if (event == null) throw new NullPointerException("event is null");

        List<AddParticipantDto> importable = sheetService.getAllImportableParticipants(dto);

        for (AddParticipantDto participant : importable) {
            Participant toAddParticipant = participantService.addParticpantService(participant);
            EventParticipant ep = eventParticipantRepo
                .findByEventAndParticipant(event, toAddParticipant).orElse(null);

            if (ep != null) continue; // already imported

            ep = new EventParticipant();
            ep.setParticipant(toAddParticipant);
            ep.setEvent(event);
            ep.setStageName(participant.getStageName());
            ep.setTeamName(participant.getTeamName());
            ep.setDisplayName(resolveDisplayName(participant));
            ep.setResidency(participant.getResidency());
            ep.setGenre(participant.getGenres() != null ? String.join(", ", participant.getGenres()) : "");
            ep.setPaymentVerified(!event.isPaymentRequired());
            ep.setScreenshotUrl(participant.getScreenshotUrl());
            ep.setReferenceCode(ReferenceCodeUtil.generate());
            eventParticipantRepo.save(ep);

            if (participant.getMemberNames() != null && !participant.getMemberNames().isEmpty()) {
                for (String memberName : participant.getMemberNames()) {
                    teamMemberRepo.save(new EventParticipantTeamMember(ep, memberName));
                }
            }

            if (participant.getGenres() != null) {
                for (String genreName : participant.getGenres()) {
                    Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
                    if (genre == null) continue;
                    EventGenreParticipantId id = new EventGenreParticipantId(
                        event.getEventId(), genre.getGenreId(), toAddParticipant.getParticipantId());
                    EventGenreParticipant egp = new EventGenreParticipant();
                    egp.setId(id);
                    egp.setEvent(event);
                    egp.setGenre(genre);
                    egp.setParticipant(toAddParticipant);

                    EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
                    String effectiveFormat = eg != null ? eg.getFormat() : null;

                    boolean isTeamEntry = isTeamFormat(effectiveFormat)
                        && ((participant.getTeamName() != null && !participant.getTeamName().isBlank())
                            || (participant.getMemberNames() != null && !participant.getMemberNames().isEmpty()));
                    String format = isTeamEntry ? effectiveFormat : (isTeamFormat(effectiveFormat) ? null : effectiveFormat);
                    egp.setFormat(format);
                    egp.setDisplayName(isTeamFormat(format)
                        ? orElse(participant.getTeamName(), participant.getParticipantName())
                        : orElse(participant.getStageName(), participant.getParticipantName()));

                    eventGenreParticipantRepo.save(egp);
                }
            }
        }
    }

    public void verifyPayment(long participantId, long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        Participant participant = participantRepo.findById(participantId).orElse(null);
        if (event == null || participant == null) throw new RuntimeException("Event or participant not found");

        EventParticipant ep = eventParticipantRepo
            .findByEventAndParticipant(event, participant).orElse(null);
        if (ep == null) throw new RuntimeException("EventParticipant not found");

        ep.setPaymentVerified(true);
        eventParticipantRepo.save(ep);
    }

    public void verifyPaymentBatch(List<VerifyParticipantDto> list) {
        for (VerifyParticipantDto item : list) {
            verifyPayment(item.getParticipantId(), item.getEventId());
        }
    }

    public List<GetUnverifiedParticipantDto> getUnverifiedParticipantsFromDb(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();

        List<EventParticipant> eps = eventParticipantRepo.findByEventAndPaymentVerifiedFalse(event);
        List<GetUnverifiedParticipantDto> result = new ArrayList<>();
        for (EventParticipant ep : eps) {
            GetUnverifiedParticipantDto dto = new GetUnverifiedParticipantDto();
            dto.participantId = ep.getParticipant().getParticipantId();
            dto.eventId = ep.getEvent().getEventId();
            dto.name = ep.getDisplayName();
            List<EventGenreParticipant> egps = eventGenreParticipantRepo
                .findByEventIdAndParticipantId(ep.getEvent().getEventId(), ep.getParticipant().getParticipantId());
            dto.genres = egps.stream()
                .map(egp -> egp.getGenre().getGenreName())
                .collect(Collectors.toList());
            dto.screenshotUrl = ep.getScreenshotUrl();
            result.add(dto);
        }
        return result;
    }

    public List<GetCheckinListDto> getCheckinList(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return new ArrayList<>();

        List<EventParticipant> eps = eventParticipantRepo.findByEvent(event);
        List<GetCheckinListDto> result = new ArrayList<>();
        for (EventParticipant ep : eps) {
            GetCheckinListDto dto = new GetCheckinListDto();
            dto.participantId = ep.getParticipant().getParticipantId();
            dto.eventId = ep.getEvent().getEventId();
            String stage = ep.getStageName();
            String display = ep.getDisplayName();
            String name = ep.getParticipant().getParticipantName();
            dto.label = (stage != null && !stage.isBlank()) ? stage
                      : (display != null && !display.isBlank()) ? display
                      : name;
            List<EventGenreParticipant> egps = eventGenreParticipantRepo
                .findByEventIdAndParticipantId(ep.getEvent().getEventId(), ep.getParticipant().getParticipantId());
            dto.genres = egps.stream().map(egp -> {
                GetCheckinListDto.GenreStatus gs = new GetCheckinListDto.GenreStatus();
                gs.genreName = egp.getGenre().getGenreName();
                gs.auditionNumber = egp.getAuditionNumber();
                return gs;
            }).collect(Collectors.toList());
            result.add(dto);
        }
        return result;
    }

    private List<EventGenreParticipant> getEgpsForParticipant(long eventId, long participantId) {
        return eventGenreParticipantRepo.findByEventIdAndParticipantId(eventId, participantId);
    }

    private boolean isTeamFormat(String format) {
        return format != null && !format.equalsIgnoreCase("1v1");
    }

    private String orElse(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private String resolveDisplayName(AddParticipantDto dto) {
        if (dto.getStageName() != null && !dto.getStageName().isBlank()) {
            return dto.getStageName();
        }
        return dto.getParticipantName();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/RegistrationService.java
git commit -m "feat: strip RegistrationService to verifyPayment, add getCheckinList, remove email sending"
```

---

## Task 6: Update GoogleSheetService Import Filter

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/GoogleSheetService.java`

The filter on line 139 currently requires both name and email. Change it to require only name.

- [ ] **Step 1: Update the filter in `getAllImportableParticipants`**

Find this block (around line 135-142):

```java
for (List<String> res : resultString) {
    AddParticipantDto participant = mapper.mapRow(res, colIndexMap, categoriesColumn, genres, memberCols);
    String name = participant.getParticipantName();
    String email = participant.getParticipantEmail();
    if (name != null && !name.isBlank() && email != null && !email.isBlank()) {
        importable.add(participant);
    }
}
```

Replace with:

```java
for (List<String> res : resultString) {
    AddParticipantDto participant = mapper.mapRow(res, colIndexMap, categoriesColumn, genres, memberCols);
    String name = participant.getParticipantName();
    if (name != null && !name.isBlank()) {
        importable.add(participant);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/GoogleSheetService.java
git commit -m "feat: make email optional in spreadsheet import — require name only"
```

---

## Task 7: Update EventController

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

Changes:
- Rename `/participants/verify-email` → `/participants/verify-payment`
- Rename `/participants/verify-email-batch` → `/participants/verify-payment-batch`
- Add `GET /{eventName}/checkin-list` endpoint

- [ ] **Step 1: Replace the three endpoint methods**

Find and replace the existing `verifyAndEmailParticipant` method:

```java
// OLD — remove this:
@PostMapping("/participants/verify-email")
public ResponseEntity<String> verifyAndEmailParticipant(@Valid @RequestBody VerifyParticipantDto dto) {
    try {
        registerService.verifyAndEmail(dto.getParticipantId(), dto.getEventId());
        return new ResponseEntity<>(gson.toJson("Verified and email sent"), HttpStatus.OK);
    } catch (Exception e) {
        log.error("Error in verify-email", e);
        return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
    }
}
```

```java
// NEW — replace with:
@Operation(summary = "Verify Payment", description = "Marks a participant as payment-verified")
@PostMapping("/participants/verify-payment")
public ResponseEntity<String> verifyPayment(@Valid @RequestBody VerifyParticipantDto dto) {
    try {
        registerService.verifyPayment(dto.getParticipantId(), dto.getEventId());
        return new ResponseEntity<>(gson.toJson("Payment verified"), HttpStatus.OK);
    } catch (Exception e) {
        log.error("Error in verify-payment", e);
        return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
    }
}
```

Find and replace the existing `verifyAndEmailBatch` method:

```java
// OLD — remove this:
@PostMapping("/participants/verify-email-batch")
public ResponseEntity<String> verifyAndEmailBatch(@Valid @RequestBody List<VerifyParticipantDto> list) {
    try {
        registerService.verifyAndEmailBatch(list);
        return new ResponseEntity<>(gson.toJson("Batch verified and emails sent"), HttpStatus.OK);
    } catch (Exception e) {
        log.error("Error in verify-email-batch", e);
        return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
    }
}
```

```java
// NEW — replace with:
@Operation(summary = "Verify Payment Batch", description = "Marks a batch of participants as payment-verified")
@PostMapping("/participants/verify-payment-batch")
public ResponseEntity<String> verifyPaymentBatch(@Valid @RequestBody List<VerifyParticipantDto> list) {
    try {
        registerService.verifyPaymentBatch(list);
        return new ResponseEntity<>(gson.toJson("Batch payment verified"), HttpStatus.OK);
    } catch (Exception e) {
        log.error("Error in verify-payment-batch", e);
        return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
    }
}
```

Add the new `checkin-list` endpoint (place it after the `unverified-participants` endpoint):

```java
@Operation(summary = "Get Check-in List", description = "Returns all participants for the event with their genre audition status")
@GetMapping("/{eventName}/checkin-list")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<List<GetCheckinListDto>> getCheckinList(@PathVariable String eventName) {
    try {
        return new ResponseEntity<>(registerService.getCheckinList(eventName), HttpStatus.OK);
    } catch (Exception e) {
        log.error("Error fetching checkin list", e);
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
    }
}
```

Also add the import at the top of the file:
```java
import com.example.BES.dtos.GetCheckinListDto;
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: rename verify-email endpoints to verify-payment, add checkin-list endpoint"
```

---

## Task 8: Fix RegistrationServiceTest

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/RegistrationServiceTest.java`

The existing tests reference `verifyAndEmail`, `ep.setEmailSent()`, and `@Mock MailSenderService`. All need updating.

- [ ] **Step 1: Write the failing test first (new `verifyPayment` test)**

Add this test to the class to verify the new behavior compiles and the old ones break as expected:

```java
@Test
void verifyPayment_setsPaymentVerified() {
    Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
    Participant p = new Participant(); p.setParticipantId(10L);
    EventParticipant ep = new EventParticipant();
    ep.setEvent(e);
    ep.setParticipant(p);
    ep.setPaymentVerified(false);
    when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
    when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
    when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(ep));

    service.verifyPayment(10L, 1L);

    assertThat(ep.isPaymentVerified()).isTrue();
    verify(eventParticipantRepo).save(ep);
}
```

- [ ] **Step 2: Run tests to confirm compilation failures on old tests**

```bash
cd BES && mvn test -Dtest=RegistrationServiceTest -pl . 2>&1 | tail -30
```

Expected: compilation errors on `verifyAndEmail`, `setEmailSent`, `MailSenderService`.

- [ ] **Step 3: Replace the entire test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.GetCheckinListDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventParticipantTeamMemberRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock GoogleSheetService sheetService;
    @Mock EventRepo eventRepo;
    @Mock ParticipantService participantService;
    @Mock ParticipantRepo participantRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreParticpantRepo eventGenreParticipantRepo;
    @Mock EventParticipantTeamMemberRepo teamMemberRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @InjectMocks RegistrationService service;

    // ── getUnverifiedParticipantsFromDb ──────────────────────────────────────

    @Test
    void getUnverifiedParticipants_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetUnverifiedParticipantDto> result = service.getUnverifiedParticipantsFromDb("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void getUnverifiedParticipants_returnsListWhenFound() {
        Event e = new Event();
        e.setEventId(1L);
        e.setEventName("Fest");
        Participant p = new Participant();
        p.setParticipantId(10L);
        p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setDisplayName("Player1");
        ep.setScreenshotUrl("http://img.png");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEventAndPaymentVerifiedFalse(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L))
            .thenReturn(List.of());

        List<GetUnverifiedParticipantDto> result = service.getUnverifiedParticipantsFromDb("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Player1");
    }

    // ── verifyPayment ────────────────────────────────────────────────────────

    @Test
    void verifyPayment_throwsWhenEventOrParticipantNotFound() {
        when(eventRepo.findById(1L)).thenReturn(Optional.empty());
        when(participantRepo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyPayment(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyPayment_throwsWhenEventParticipantNotFound() {
        Event e = new Event(); e.setEventId(1L);
        Participant p = new Participant(); p.setParticipantId(10L);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyPayment(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyPayment_setsPaymentVerifiedAndSaves() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L);
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setPaymentVerified(false);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(ep));

        service.verifyPayment(10L, 1L);

        assertThat(ep.isPaymentVerified()).isTrue();
        verify(eventParticipantRepo).save(ep);
    }

    // ── getCheckinList ───────────────────────────────────────────────────────

    @Test
    void getCheckinList_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.getCheckinList("Missing")).isEmpty();
    }

    @Test
    void getCheckinList_usesStageNameAsLabel() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L); p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e); ep.setParticipant(p);
        ep.setStageName("StageName"); ep.setDisplayName("DisplayName");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of());

        List<GetCheckinListDto> result = service.getCheckinList("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label).isEqualTo("StageName");
        assertThat(result.get(0).participantId).isEqualTo(10L);
        assertThat(result.get(0).eventId).isEqualTo(1L);
    }

    @Test
    void getCheckinList_fallsBackToDisplayNameWhenNoStageName() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L); p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e); ep.setParticipant(p);
        ep.setStageName(null); ep.setDisplayName("DisplayName");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of());

        List<GetCheckinListDto> result = service.getCheckinList("Fest");

        assertThat(result.get(0).label).isEqualTo("DisplayName");
    }

    @Test
    void getCheckinList_includesGenreAuditionStatus() {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L); p.setParticipantName("Player1");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e); ep.setParticipant(p); ep.setDisplayName("Player1");
        Genre genre = new Genre(); genre.setGenreName("popping");
        EventGenreParticipant egp = new EventGenreParticipant();
        egp.setGenre(genre); egp.setAuditionNumber(5);
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep));
        when(eventGenreParticipantRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of(egp));

        List<GetCheckinListDto> result = service.getCheckinList("Fest");

        assertThat(result.get(0).genres).hasSize(1);
        assertThat(result.get(0).genres.get(0).genreName).isEqualTo("popping");
        assertThat(result.get(0).genres.get(0).auditionNumber).isEqualTo(5);
    }
}
```

- [ ] **Step 4: Run the tests — all should pass**

```bash
cd BES && mvn test -Dtest=RegistrationServiceTest -pl . 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add BES/src/test/java/com/example/BES/services/RegistrationServiceTest.java
git commit -m "test: update RegistrationServiceTest for verifyPayment and getCheckinList"
```

---

## Task 9: Add EventController Integration Test for Checkin List

**Files:**
- Modify: `BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java`

- [ ] **Step 1: Add test for the new checkin-list endpoint**

Add these two tests inside the `EventControllerIntegrationTest` class:

```java
@Test
@WithMockUser(roles = "ADMIN")
void getCheckinList_returnsOk() throws Exception {
    when(registerService.getCheckinList("TestEvent")).thenReturn(List.of());

    mockMvc.perform(get("/api/v1/event/TestEvent/checkin-list"))
        .andExpect(status().isOk());
}

@Test
@WithMockUser(roles = "ADMIN")
void verifyPayment_returnsOk() throws Exception {
    doNothing().when(registerService).verifyPayment(anyLong(), anyLong());

    mockMvc.perform(post("/api/v1/event/participants/verify-payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"participantId\":1,\"eventId\":1}"))
        .andExpect(status().isOk());
}
```

Also add the missing import at the top:
```java
import com.example.BES.dtos.GetCheckinListDto;
import static org.mockito.ArgumentMatchers.anyLong;
```

- [ ] **Step 2: Run the integration tests**

```bash
cd BES && mvn test -Dtest=EventControllerIntegrationTest -pl . 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run all backend tests**

```bash
cd BES && mvn test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java
git commit -m "test: add checkin-list and verify-payment endpoint tests"
```

---

## Task 10: Update `api.js`

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Replace the two email-verify functions with payment-verify equivalents**

Find and delete (lines ~682-704):
```javascript
export const verifyAndEmailParticipant = async (participantId, eventId) => { ... }
export const verifyAndEmailBatch = async (list) => { ... }
```

Replace with:
```javascript
export const verifyPayment = async (participantId, eventId) => {
  return await fetch(`${domain}/api/v1/event/participants/verify-payment`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify({ participantId, eventId })
  })
}

export const verifyPaymentBatch = async (list) => {
  return await fetch(`${domain}/api/v1/event/participants/verify-payment-batch`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    body: JSON.stringify(list)
  })
}
```

- [ ] **Step 2: Add two new functions at the end of the file**

```javascript
export const getCheckinList = async (eventName) => {
  return await fetch(`${domain}/api/v1/event/${eventName}/checkin-list`, {
    credentials: 'include'
  })
}

export const checkInParticipant = async (participantId, eventId) => {
  return await fetch(`${domain}/api/v1/event/register-participant/${participantId}/${eventId}`, {
    credentials: 'include'
  })
}
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: replace verifyAndEmail with verifyPayment, add getCheckinList and checkInParticipant"
```

---

## Task 11: Update `EventDetails.vue`

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

Four changes: update the import line, rename the handler functions, remove the "Emails Sent" stat card, and remove `emailSent` badges from the registered list.

- [ ] **Step 1: Update the import line**

Find line 5 which imports `verifyAndEmailParticipant` and `verifyAndEmailBatch`. Replace both names with `verifyPayment` and `verifyPaymentBatch`:

```javascript
// OLD (partial — just the names to swap):
import { ..., verifyAndEmailParticipant, verifyAndEmailBatch, ... } from '@/utils/api';

// NEW:
import { ..., verifyPayment, verifyPaymentBatch, ... } from '@/utils/api';
```

- [ ] **Step 2: Remove `emailedCount` and update the handler functions**

Delete the `emailedCount` computed (lines ~462-468):
```javascript
// DELETE this block:
const emailedCount = computed(() =>
  new Set(
    verifiedDbParticipants.value
      .filter(p => !p.walkin && p.emailSent)
      .map(p => p.participantId)
  ).size
)
```

Replace `handleVerifyAndEmail` with `handleVerifyPayment`:
```javascript
const handleVerifyPayment = async (participant) => {
  verifyingParticipantId.value = participant.participantId
  try {
    await verifyPayment(participant.participantId, participant.eventId)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    selectedUnverified.value.delete(participant.participantId)
    selectedUnverified.value = new Set(selectedUnverified.value)
  } catch (_e) {
    openModal('Error', 'Failed to verify participant.', 'error')
  }
  verifyingParticipantId.value = null
}
```

Replace `handleBatchVerify` with `handleBatchVerifyPayment`:
```javascript
const handleBatchVerifyPayment = async () => {
  if (selectedUnverified.value.size === 0) return
  batchVerifying.value = true
  const list = [...selectedUnverified.value].map(pid => {
    const p = unverifiedParticipants.value.find(x => x.participantId === pid)
    return { participantId: p.participantId, eventId: p.eventId }
  })
  try {
    await verifyPaymentBatch(list)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    selectedUnverified.value = new Set()
  } catch (_e) {
    openModal('Error', 'Batch verification failed.', 'error')
  }
  batchVerifying.value = false
}
```

- [ ] **Step 3: Update the template — remove "Emails Sent" stat card**

Find and delete the entire card block (lines ~682-686):
```html
<!-- DELETE this card: -->
<div class="card p-4">
  <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1">Emails Sent</p>
  <p class="text-2xl font-heading font-extrabold text-content-primary">{{ emailedCount }}<span class="text-base font-medium text-content-muted"> / {{ totalVerified }}</span></p>
  <span class="text-xs text-content-muted">QR emails</span>
</div>
```

- [ ] **Step 4: Update the template — fix the unverified panel button labels**

Find `@click="handleBatchVerify"` and change to `@click="handleBatchVerifyPayment"`.

Find `@click="handleVerifyAndEmail(p)"` and change to `@click="handleVerifyPayment(p)"`.

Change button label from `'Verify & Email'` to `'Verify Payment'` and `'Sending…'` stays.

Change batch button label from `Send Batch (${selectedUnverified.size})` to `Verify Batch (${selectedUnverified.size})`.

- [ ] **Step 5: Update the template — remove emailSent badges from registered list**

Find and delete this badge (line ~1065):
```html
<!-- DELETE: -->
<span
  v-if="verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-teal-300 border border-teal-900/30"
><i class="pi pi-check text-xs"></i> Email Sent</span>
```

Find and replace the email-status block in the registered list (lines ~1163-1168):
```html
<!-- OLD — delete both span blocks referencing emailSent: -->
<span v-else-if="p.entries.length > 0 && verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
  ...>Email Sent</span>
<span v-else-if="!p.walkin"
  ...>Email Pending</span>

<!-- NEW — remove both, leave nothing (the walk-in badge above remains) -->
```

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: replace verify-email flow with verify-payment in EventDetails"
```

---

## Task 12: Update `AuditionList.vue`

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

- [ ] **Step 1: Remove `emailSent: false` from the participant data structure**

Find line ~395:
```javascript
emailSent: false,
```

Delete that line. The surrounding participant object fields remain.

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: remove emailSent field from AuditionList participant data"
```

---

## Task 13: Update `AuditionNumber.vue` — Add Check-in List Pane

**Files:**
- Modify: `BES-frontend/src/views/AuditionNumber.vue`

This is the largest frontend change. The view gets a left-pane check-in list alongside the existing right-pane animation.

- [ ] **Step 1: Replace the entire `<script setup>` block**

```javascript
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue"
import ActionDoneModal from './ActionDoneModal.vue';
import { createClient, deactivateClient, subscribeToChannel } from "@/utils/websocket";
import { getActiveEvent } from "@/utils/auth";
import { getCheckinList, checkInParticipant } from "@/utils/api";

// ── animation state ──────────────────────────────────────────────────────────
const loading = ref(false)
const fakeNumber = ref(null)
let intervalId = null
let client = ref(null)

const assignments = ref([])
const currentAssignment = ref(null)
const queue = ref([])
const isAnimating = ref(false)

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const revealingRef = ref(null)

// ── check-in list state ──────────────────────────────────────────────────────
const activeEvent = getActiveEvent()
const checkinList = ref([])
const loadingCheckinList = ref(false)
const checkingInId = ref(null)

const isCheckedIn = (p) => p.genres.length > 0 && p.genres.every(g => g.auditionNumber !== null)

const fetchCheckinList = async () => {
  if (!activeEvent?.name) return
  loadingCheckinList.value = true
  try {
    const res = await getCheckinList(activeEvent.name)
    if (res.ok) checkinList.value = await res.json()
  } catch (e) {
    console.error(e)
  }
  loadingCheckinList.value = false
}

const checkIn = async (p) => {
  checkingInId.value = p.participantId
  try {
    await checkInParticipant(p.participantId, p.eventId)
  } catch (e) {
    console.error(e)
  }
  checkingInId.value = null
}

// ── animation logic ──────────────────────────────────────────────────────────
const groupedHistory = computed(() => {
  const order = []
  const map = new Map()
  for (const a of assignments.value) {
    if (map.has(a.name)) {
      const idx = order.indexOf(a.name)
      order.splice(idx, 1)
    } else {
      map.set(a.name, { refCode: a.refCode || '', genres: new Map() })
    }
    order.push(a.name)
    map.get(a.name).genres.set(a.genre, a)
  }
  return order.reverse().map(name => ({
    name,
    refCode: map.get(name).refCode,
    entries: [...map.get(name).genres.values()]
  }))
})

const openModal = (title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  showModal.value = true
}

const clearHistory = () => {
  assignments.value = []
}

function startSlotAnimation(finalNumber, onDone) {
  loading.value = true
  clearInterval(intervalId)
  intervalId = setInterval(() => {
    fakeNumber.value = Math.floor(Math.random() * 50) + 1
  }, 100)
  setTimeout(() => {
    clearInterval(intervalId)
    fakeNumber.value = null
    loading.value = false
    onDone()
  }, 2000)
}

const processQueue = () => {
  if (queue.value.length === 0) {
    isAnimating.value = false
    return
  }
  isAnimating.value = true
  const next = queue.value.shift()
  currentAssignment.value = next
  startSlotAnimation(next.auditionNumber, () => {
    assignments.value.push(next)
    currentAssignment.value = null
    processQueue()
  })
}

const onReceiveAuditionNumber = (msg) => {
  queue.value.push(msg)
  if (!isAnimating.value) processQueue()
  // Reactively update check-in list — no re-fetch needed
  const participant = checkinList.value.find(p => p.participantId === msg.participantId)
  if (participant) {
    const genre = participant.genres.find(g => g.genreName === msg.genre)
    if (genre) genre.auditionNumber = msg.auditionNumber
  }
}

const onRepeatAudition = (msg) => {
  const judgeLabel = msg.judge === "" ? "" : `\nJudge: ${msg.judge}`
  openModal(`Hey ${msg.name}!`, `Your audition number is ${msg.genre} #${msg.audition}${judgeLabel}`)
}

onMounted(async () => {
  await fetchCheckinList()
  subscribeToChannel(createClient(), "/topic/audition/", (msg) => onReceiveAuditionNumber(msg))
  subscribeToChannel(createClient(), "/topic/error/", (msg) => onRepeatAudition(msg))
})

onBeforeUnmount(() => {
  if (intervalId) clearInterval(intervalId)
  deactivateClient(client.value)
})
</script>
```

- [ ] **Step 2: Replace the entire `<template>` block**

```html
<template>
  <div class="flex flex-col lg:flex-row gap-4 w-full max-w-6xl mx-auto px-4 py-4 min-h-[calc(100vh-64px)]">

    <!-- Left pane: check-in list -->
    <div class="lg:w-2/5 w-full flex-shrink-0">
      <div class="card p-4 h-full flex flex-col">
        <div class="flex items-center justify-between mb-3 shrink-0">
          <p class="font-heading font-bold text-content-secondary text-sm uppercase tracking-wide">Check-In</p>
          <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-surface-700 border border-surface-600 text-content-muted">
            {{ checkinList.filter(p => !isCheckedIn(p)).length }} pending
          </span>
        </div>

        <div v-if="loadingCheckinList" class="flex-1 flex items-center justify-center text-content-muted text-sm">
          <i class="pi pi-spinner pi-spin mr-2"></i> Loading…
        </div>
        <div v-else-if="checkinList.length === 0" class="flex-1 flex items-center justify-center text-content-muted text-sm">
          No participants found for this event
        </div>
        <div v-else class="flex-1 overflow-y-auto space-y-2 pr-1">
          <div
            v-for="p in checkinList"
            :key="p.participantId"
            class="flex items-center gap-3 px-3 py-2.5 rounded-xl border transition-colors"
            :class="isCheckedIn(p)
              ? 'bg-surface-700/30 border-surface-600/20 opacity-50'
              : 'bg-surface-800 border-surface-600'"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-semibold text-content-secondary truncate">{{ p.label }}</p>
              <div class="flex flex-wrap gap-1 mt-0.5">
                <span
                  v-for="g in p.genres"
                  :key="g.genreName"
                  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-xs bg-surface-700 border border-surface-600 text-content-muted"
                >
                  <span class="capitalize">{{ g.genreName }}</span>
                  <span v-if="g.auditionNumber !== null" class="font-heading font-extrabold text-primary-400">#{{ g.auditionNumber }}</span>
                </span>
              </div>
            </div>
            <button
              v-if="!isCheckedIn(p)"
              @click="checkIn(p)"
              :disabled="checkingInId === p.participantId"
              class="shrink-0 flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-primary-600 text-white text-xs font-semibold
                     hover:bg-primary-500 active:scale-95 disabled:opacity-50 transition-all"
            >
              <i class="pi text-xs" :class="checkingInId === p.participantId ? 'pi-spinner pi-spin' : 'pi-check'"></i>
              {{ checkingInId === p.participantId ? '…' : 'Check In' }}
            </button>
            <i v-else class="pi pi-check-circle text-teal-400 text-sm shrink-0"></i>
          </div>
        </div>
      </div>
    </div>

    <!-- Right pane: animation + history -->
    <div class="lg:w-3/5 w-full flex flex-col items-center justify-start">
      <div class="w-full max-w-lg mx-auto px-2 flex flex-col items-center justify-center min-h-[180px] text-center">

        <!-- Animating state -->
        <div v-if="loading" class="space-y-3">
          <p class="label-caps font-semibold text-content-muted uppercase">Drawing audition number</p>
          <div class="text-6xl font-heading font-extrabold tabular-nums animate-slot-roll shimmer-text">
            {{ fakeNumber ?? '—' }}
          </div>
          <div class="w-24 h-1 mx-auto rounded-full overflow-hidden bg-surface-700">
            <div class="h-full shimmer-bar" style="background: linear-gradient(90deg, transparent 0%, rgba(34,211,238,0.6) 50%, transparent 100%); background-size: 200% 100%; animation: shimmerMove 1.2s ease-in-out infinite;"></div>
          </div>
        </div>

        <!-- Current revealed assignment -->
        <div v-else-if="currentAssignment" class="space-y-1">
          <p class="label-caps font-semibold text-content-muted uppercase">Good Luck</p>
          <p class="font-heading font-extrabold text-2xl text-content-primary">{{ currentAssignment.name }}</p>
          <div class="flex items-baseline justify-center gap-2 mt-1">
            <span class="text-sm font-semibold text-content-muted capitalize">{{ currentAssignment.genre }}</span>
            <span class="text-4xl font-heading font-extrabold text-primary-400">#{{ currentAssignment.auditionNumber }}</span>
          </div>
          <p v-if="currentAssignment.judge" class="text-sm font-medium text-content-muted mt-1">Judge: {{ currentAssignment.judge }}</p>
        </div>

        <!-- Idle state -->
        <div v-else-if="assignments.length === 0" class="scan-zone">
          <div class="scan-zone-inner flex flex-col items-center gap-3"
               style="background: radial-gradient(ellipse 60% 50% at 50% 50%, rgba(34,211,238,0.04) 0%, transparent 100%);">
            <p class="label-caps font-semibold text-content-muted uppercase animate-scan-pulse">Waiting for check-in…</p>
            <div class="text-6xl font-heading font-extrabold tabular-nums text-surface-600/50">—</div>
          </div>
        </div>

        <!-- History -->
        <div v-if="groupedHistory.length > 0" class="mt-5 w-full">
          <div class="flex items-center justify-between mb-2">
            <p class="label-caps font-semibold text-content-muted uppercase">History</p>
            <button
              @click="clearHistory"
              class="text-xs font-medium text-content-muted hover:text-red-400 transition-colors"
            >
              Clear
            </button>
          </div>

          <div class="divide-y divide-surface-600/30">
            <div
              v-for="(group, i) in groupedHistory"
              :key="group.name"
              :class="i === 0
                ? 'flex items-center gap-4 py-3 bg-primary-100/30 rounded-xl px-3 -mx-3 mb-1'
                : 'flex items-center gap-3 py-2 opacity-60'"
            >
              <span
                :class="i === 0
                  ? 'font-heading font-extrabold text-base text-content-primary w-32 shrink-0 truncate'
                  : 'text-sm font-semibold text-content-secondary w-28 shrink-0 truncate'"
              >{{ group.name }}</span>
              <div class="flex flex-wrap gap-1.5 min-w-0">
                <span
                  v-for="a in group.entries"
                  :key="a.genre"
                  class="badge-neutral"
                  :class="i === 0
                    ? 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-700 border border-primary-500/30 text-sm shadow-sm'
                    : 'inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-surface-700 border border-primary-500/20 text-xs'"
                >
                  <span class="text-content-muted capitalize">{{ a.genre }}</span>
                  <span
                    :class="i === 0 ? 'font-heading font-extrabold text-primary-400 text-base' : 'font-heading font-extrabold text-primary-400'"
                  >#{{ a.auditionNumber }}</span>
                </span>
              </div>
              <span
                v-if="group.refCode"
                class="relative ml-auto shrink-0 inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-surface-700 border border-surface-500 text-content-secondary cursor-pointer select-none touch-none hover:border-primary-500/50 hover:text-primary-400 transition-colors"
                @mousedown="revealingRef = group.name"
                @mouseup="revealingRef = null"
                @mouseleave="revealingRef = null"
                @touchstart.prevent="revealingRef = group.name"
                @touchend="revealingRef = null"
                @touchcancel="revealingRef = null"
              >
                <i class="pi pi-eye text-content-muted" style="font-size:0.65rem"></i>
                <span class="text-content-muted">Ref code</span>
                <span
                  v-if="revealingRef === group.name"
                  class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-4 py-2.5 rounded-xl bg-surface-700 border border-surface-500 shadow-xl whitespace-nowrap z-50 pointer-events-none"
                >
                  <span class="font-source tracking-widest text-primary-400 text-base font-bold">{{ group.refCode }}</span>
                  <span class="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-surface-500"></span>
                </span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    variant="info"
    @accept="() => { showModal = false }"
    @close="() => { showModal = false }"
  >
    <p class="text-content-secondary leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/AuditionNumber.vue
git commit -m "feat: add check-in list pane to AuditionNumber with WebSocket reactive updates"
```

---

## Task 14: Verify and Rebuild

- [ ] **Step 1: Build the backend JAR**

```bash
cd BES && mvn clean package -DskipTests 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Build the frontend**

```bash
cd BES-frontend && npm run build 2>&1 | tail -15
```

Expected: no errors, `dist/` created.

- [ ] **Step 3: Rebuild Docker**

```bash
docker-compose down && docker-compose up --build --no-cache -d 2>&1 | tail -20
```

Expected: all three containers healthy.

- [ ] **Step 4: Smoke-test the check-in flow**

1. Navigate to `/event/audition-number` while logged in as Admin/Organiser with an active event set
2. Verify the left pane shows the participant list with genre badges
3. Click "Check In" on a participant — verify the animation fires in the right pane and the participant gets a checkmark
4. Verify the participant's genre badges update with the audition number without a page reload

- [ ] **Step 5: Smoke-test EventDetails**

1. Navigate to an event's details page
2. Confirm "Awaiting Payment Verification" section shows "Verify Payment" (not "Verify & Email")
3. Click "Verify Payment" on a participant — verify it marks them as verified without sending any email
4. Confirm the "Emails Sent" stat card is gone from the stats row

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "chore: final cleanup after email removal and button-based check-in"
```
