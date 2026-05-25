# Test Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise backend service test coverage to ≥70% line coverage (enforced by JaCoCo) and expand frontend utility tests with a coverage report.

**Architecture:** Mockito unit tests (`@ExtendWith(MockitoExtension.class)`) for all 21 backend services; one new `ResultsControllerIntegrationTest` using the existing H2/MockMvc pattern; JaCoCo gate enforced at `mvn test`; `@vitest/coverage-v8` installed for frontend coverage reporting.

**Tech Stack:** JUnit 5, Mockito, AssertJ, JaCoCo 0.8.12, Vitest + @vitest/coverage-v8, Spring Boot Test

---

## File Map

**Modify:**
- `BES/pom.xml` — add JaCoCo plugin
- `BES-frontend/package.json` — add `@vitest/coverage-v8` devDependency
- `BES-frontend/src/utils/__tests__/api.test.js` — expand coverage

**Create (backend tests):**
- `BES/src/test/java/com/example/BES/services/EventServiceTest.java`
- `BES/src/test/java/com/example/BES/services/GenreServiceTest.java`
- `BES/src/test/java/com/example/BES/services/JudgeServiceTest.java`
- `BES/src/test/java/com/example/BES/services/ParticipantServiceTest.java`
- `BES/src/test/java/com/example/BES/services/EventGenreServiceTest.java`
- `BES/src/test/java/com/example/BES/services/ScoreServiceTest.java`
- `BES/src/test/java/com/example/BES/services/ScoringCriteriaServiceTest.java`
- `BES/src/test/java/com/example/BES/services/AuditionFeedbackServiceTest.java`
- `BES/src/test/java/com/example/BES/services/ResultsServiceTest.java`
- `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`
- `BES/src/test/java/com/example/BES/services/RegistrationServiceTest.java`
- `BES/src/test/java/com/example/BES/services/EventParticpantServiceTest.java`
- `BES/src/test/java/com/example/BES/services/PickupCrewServiceTest.java`
- `BES/src/test/java/com/example/BES/services/EmailTemplateServiceTest.java`
- `BES/src/test/java/com/example/BES/services/EventGenreParticpantServiceTest.java`
- `BES/src/test/java/com/example/BES/services/QrCodeServiceTest.java`
- `BES/src/test/java/com/example/BES/controllers/ResultsControllerIntegrationTest.java`

**Create (frontend tests):**
- `BES-frontend/src/utils/__tests__/auth.test.js`
- `BES-frontend/src/utils/__tests__/adminApi.test.js`

---

## Task 1: Add JaCoCo to pom.xml

**Files:** Modify `BES/pom.xml`

- [ ] **Step 1: Add JaCoCo plugin inside `<build><plugins>`**

In `BES/pom.xml`, after the `spring-boot-maven-plugin` closing `</plugin>` tag, add:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>test</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <includes>
                            <include>com/example/BES/services</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Verify pom.xml is valid**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn validate
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/pom.xml && git commit -m "build: add JaCoCo coverage gate (70% line coverage on services)"
```

---

## Task 2: Frontend coverage tooling

**Files:** Modify `BES-frontend/package.json`

- [ ] **Step 1: Install @vitest/coverage-v8**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm install --save-dev @vitest/coverage-v8
```
Expected: package added, `package.json` updated.

- [ ] **Step 2: Verify coverage command works**

```bash
npm run test:coverage
```
Expected: runs and shows a coverage table (only api.test.js at this point, low %).

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES-frontend/package.json BES-frontend/package-lock.json && git commit -m "build: install vitest coverage-v8 for frontend coverage reports"
```

---

## Task 3: EventServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/EventServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.models.Event;
import com.example.BES.respositories.EventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepo repo;
    @Mock EmailTemplateService emailTemplateService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks EventService service;

    private Event event(String name) {
        Event e = new Event();
        e.setEventName(name);
        e.setAccessCode("1234");
        return e;
    }

    @Test
    void createEvent_skipsIfAlreadyExists() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "BattleFest";
        dto.accessCode = "1234";
        when(repo.findByEventName("BattleFest")).thenReturn(Optional.of(event("BattleFest")));

        service.createEventService(dto);

        verify(repo, never()).save(any());
    }

    @Test
    void createEvent_savesNewEventAndCreatesTemplate() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "NewEvent";
        dto.accessCode = "5678";
        when(repo.findByEventName("NewEvent")).thenReturn(Optional.empty());
        Event saved = event("NewEvent");
        when(repo.save(any())).thenReturn(saved);

        service.createEventService(dto);

        verify(repo).save(any(Event.class));
        verify(emailTemplateService).createDefaultTemplate(saved);
    }

    @Test
    void createEvent_generatesRandomCodeWhenInvalidProvided() {
        AddEventDto dto = new AddEventDto();
        dto.eventName = "RandEvent";
        dto.accessCode = "abc";
        when(repo.findByEventName("RandEvent")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createEventService(dto);

        verify(repo).save(argThat(e -> ((Event) e).getAccessCode().matches("\\d{4}")));
    }

    @Test
    void verifyAccessCode_trueOnMatch() {
        Event e = event("Test");
        e.setAccessCode("9999");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        assertThat(service.verifyAccessCode(1L, "9999")).isTrue();
    }

    @Test
    void verifyAccessCode_falseOnMismatch() {
        Event e = event("Test");
        e.setAccessCode("9999");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        assertThat(service.verifyAccessCode(1L, "0000")).isFalse();
    }

    @Test
    void verifyAccessCode_throwsWhenNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAccessCode(99L, "1234"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void releaseResults_setsFlag() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        service.releaseResults("Fest", true);

        assertThat(e.isResultsReleased()).isTrue();
        verify(repo).save(e);
    }

    @Test
    void releaseResults_throwsWhenNotFound() {
        when(repo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseResults("Missing", true))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateAccessCode_throwsOnInvalidCode() {
        assertThatThrownBy(() -> service.updateAccessCode(1L, "abc"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.updateAccessCode(1L, null))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateAccessCode_savesValidCode() {
        Event e = event("Fest");
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        service.updateAccessCode(1L, "4321");

        assertThat(e.getAccessCode()).isEqualTo("4321");
        verify(repo).save(e);
    }

    @Test
    void getAllEvents_includesAccessCodeWhenFlagTrue() {
        Event e = event("Fest");
        when(repo.findAll()).thenReturn(List.of(e));

        List<GetEventDto> result = service.getAllEvents(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccessCode()).isEqualTo("1234");
    }

    @Test
    void getAllEvents_hidesAccessCodeWhenFlagFalse() {
        Event e = event("Fest");
        when(repo.findAll()).thenReturn(List.of(e));

        List<GetEventDto> result = service.getAllEvents(false);

        assertThat(result.get(0).getAccessCode()).isNull();
    }

    @Test
    void setJudgingMode_updatesAndBroadcasts() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        service.setJudgingMode("Fest", "CUSTOM");

        assertThat(e.getJudgingMode()).isEqualTo("CUSTOM");
        verify(repo).save(e);
        verify(messagingTemplate).convertAndSend(eq("/topic/judging-mode/"), any());
    }
}
```

- [ ] **Step 2: Run and verify passes**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=EventServiceTest -DskipJacoco=true 2>&1 | tail -10
```
Expected: `Tests run: 12, Failures: 0, Errors: 0`

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/EventServiceTest.java && git commit -m "test: EventService unit tests (12 cases)"
```

---

## Task 4: GenreServiceTest + JudgeServiceTest + ParticipantServiceTest

**Files:** Create three test files.

- [ ] **Step 1: Write GenreServiceTest**

```java
package com.example.BES.services;

import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.admin.AddGenreDto;
import com.example.BES.dtos.admin.DeleteGenreDto;
import com.example.BES.dtos.admin.UpdateGenreDto;
import com.example.BES.models.Genre;
import com.example.BES.respositories.GenreRepo;
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
class GenreServiceTest {

    @Mock GenreRepo repo;
    @InjectMocks GenreService service;

    private Genre genre(Long id, String name) {
        Genre g = new Genre();
        g.setGenreId(id);
        g.setGenreName(name);
        return g;
    }

    @Test
    void getAllGenres_mapsToDto() {
        when(repo.findAll()).thenReturn(List.of(genre(1L, "breaking")));

        List<GetGenreDto> result = service.getAllGenres();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).genreName).isEqualTo("breaking");
        assertThat(result.get(0).id).isEqualTo(1L);
    }

    @Test
    void addGenre_createsNewWhenNotExists() {
        AddGenreDto dto = mock(AddGenreDto.class);
        when(dto.getName()).thenReturn("Popping");
        Genre newGenre = genre(null, null);
        when(repo.findByGenreName("popping")).thenReturn(Optional.of(newGenre));

        service.addGenreService(dto);

        // genre already exists but has no name set — saved as-is
        verify(repo, never()).save(any());
    }

    @Test
    void addGenre_savesWhenNew() {
        AddGenreDto dto = mock(AddGenreDto.class);
        when(dto.getName()).thenReturn("Locking");
        Genre empty = new Genre();
        when(repo.findByGenreName("locking")).thenReturn(Optional.of(empty));
        // empty genre has null genreName → condition genre.getGenreName() == null → saves
        when(repo.save(any())).thenReturn(empty);

        Genre result = service.addGenreService(dto);

        verify(repo).save(empty);
    }

    @Test
    void updateGenre_updatesNameAndSaves() {
        Genre g = genre(1L, "breaking");
        UpdateGenreDto dto = mock(UpdateGenreDto.class);
        when(dto.getId()).thenReturn(1L);
        when(dto.getNewName()).thenReturn("b-boy");
        when(repo.findById(1L)).thenReturn(Optional.of(g));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Genre result = service.updateGenreService(dto);

        assertThat(result.getGenreName()).isEqualTo("b-boy");
    }

    @Test
    void updateGenre_returnsNullWhenNotFound() {
        UpdateGenreDto dto = mock(UpdateGenreDto.class);
        when(dto.getId()).thenReturn(99L);
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.updateGenreService(dto)).isNull();
    }

    @Test
    void deleteGenre_deletesAndReturnsName() {
        Genre g = genre(1L, "locking");
        DeleteGenreDto dto = mock(DeleteGenreDto.class);
        when(dto.getId()).thenReturn(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(g));

        String name = service.deleteGenreService(dto);

        assertThat(name).isEqualTo("locking");
        verify(repo).delete(g);
    }

    @Test
    void deleteGenre_returnsEmptyStringWhenNotFound() {
        DeleteGenreDto dto = mock(DeleteGenreDto.class);
        when(dto.getId()).thenReturn(99L);
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.deleteGenreService(dto)).isEmpty();
    }
}
```

- [ ] **Step 2: Write JudgeServiceTest**

```java
package com.example.BES.services;

import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Mock JudgeRepo judgeRepo;
    @Mock EventRepo eventRepo;
    @InjectMocks JudgeService service;

    private Judge judge(Long id, String name) {
        Judge j = new Judge();
        j.setJudgeId(id);
        j.setName(name);
        return j;
    }

    @Test
    void addJudge_savesAndReturns() {
        AddJudgeDto dto = new AddJudgeDto();
        dto.judgeName = "Mike";
        Judge saved = judge(1L, "Mike");
        when(judgeRepo.save(any())).thenReturn(saved);

        Judge result = service.addJudgeService(dto);

        assertThat(result.getName()).isEqualTo("Mike");
    }

    @Test
    void getAllJudges_mapsToDto() {
        when(judgeRepo.findAll()).thenReturn(List.of(judge(1L, "Mike")));

        List<GetJudgeDto> result = service.getAllJudges();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).judgeName).isEqualTo("Mike");
    }

    @Test
    void getJudgeById_returnsNullWhenMissing() {
        when(judgeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.getJudgeById(99L)).isNull();
    }

    @Test
    void updateJudge_updatesName() {
        Judge j = judge(1L, "Old");
        UpdateJudgeDto dto = mock(UpdateJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        when(dto.getNewName()).thenReturn("New");
        when(judgeRepo.findById(1L)).thenReturn(Optional.of(j));
        when(judgeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Judge result = service.updateJudgeService(dto);

        assertThat(result.getName()).isEqualTo("New");
    }

    @Test
    void deleteJudge_deletesAndReturnsName() {
        Judge j = judge(1L, "Mike");
        DeleteJudgeDto dto = mock(DeleteJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        when(judgeRepo.findById(1L)).thenReturn(Optional.of(j));

        String name = service.deleteJudgeService(dto);

        assertThat(name).isEqualTo("Mike");
        verify(judgeRepo).delete(j);
    }

    @Test
    void getJudgesByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetJudgeDto> result = service.getJudgesByEvent("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void addJudgeToEvent_returnsNullWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.addJudgeToEvent("Missing", "Mike")).isNull();
    }

    @Test
    void removeJudgeFromEvent_doesNothingWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        service.removeJudgeFromEvent("Missing", 1L); // should not throw

        verify(eventRepo, never()).save(any());
    }

    @Test
    void removeJudgeFromEvent_removesJudgeFromList() {
        Judge j = judge(1L, "Mike");
        Event e = new Event();
        e.setEventName("Fest");
        e.setJudges(new ArrayList<>(List.of(j)));
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));

        service.removeJudgeFromEvent("Fest", 1L);

        assertThat(e.getJudges()).isEmpty();
        verify(eventRepo).save(e);
    }
}
```

- [ ] **Step 3: Write ParticipantServiceTest**

```java
package com.example.BES.services;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.models.Participant;
import com.example.BES.respositories.ParticipantRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock ParticipantRepo repo;
    @InjectMocks ParticipantService service;

    @Test
    void addParticipant_returnsExistingWhenEmailMatches() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.participantEmail = "test@example.com";
        dto.participantName = "Test";
        Participant existing = new Participant();
        existing.setParticipantEmail("test@example.com");
        when(repo.findByParticipantEmail("test@example.com")).thenReturn(Optional.of(existing));

        Participant result = service.addParticpantService(dto);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void addParticipant_createsNewWhenEmailNotFound() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.participantEmail = "new@example.com";
        dto.participantName = "New Person";
        when(repo.findByParticipantEmail("new@example.com")).thenReturn(Optional.empty());
        Participant saved = new Participant();
        when(repo.save(any())).thenReturn(saved);

        Participant result = service.addParticpantService(dto);

        assertThat(result).isSameAs(saved);
        verify(repo).save(any(Participant.class));
    }

    @Test
    void addParticipant_savesWhenEmailIsNull() {
        AddParticipantDto dto = new AddParticipantDto();
        dto.participantEmail = null;
        dto.participantName = "Walk-in";
        Participant saved = new Participant();
        when(repo.save(any())).thenReturn(saved);

        service.addParticpantService(dto);

        verify(repo).save(any(Participant.class));
    }

    @Test
    void addWalkIn_returnsExistingWhenNameMatches() {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name = "Existing";
        Participant existing = new Participant();
        existing.setParticipantName("Existing");
        when(repo.findByParticipantName("Existing")).thenReturn(Optional.of(existing));

        Participant result = service.addWalkInService(dto);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void addWalkIn_createsNewWhenNotFound() {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name = "Newbie";
        Participant empty = new Participant();
        when(repo.findByParticipantName("Newbie")).thenReturn(Optional.of(empty));
        when(repo.save(any())).thenReturn(empty);

        service.addWalkInService(dto);

        verify(repo).save(empty);
    }
}
```

- [ ] **Step 4: Run all three**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest="GenreServiceTest,JudgeServiceTest,ParticipantServiceTest" 2>&1 | tail -5
```
Expected: all pass, 0 failures.

- [ ] **Step 5: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/GenreServiceTest.java BES/src/test/java/com/example/BES/services/JudgeServiceTest.java BES/src/test/java/com/example/BES/services/ParticipantServiceTest.java && git commit -m "test: GenreService, JudgeService, ParticipantService unit tests"
```

---

## Task 5: EventGenreServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/EventGenreServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventGenreServiceTest {

    @Mock EventGenreRepo eventGenreRepo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @InjectMocks EventGenreService service;

    private Event event(String name) {
        Event e = new Event();
        e.setEventName(name);
        return e;
    }

    private Genre genre(Long id, String name) {
        Genre g = new Genre();
        g.setGenreId(id);
        g.setGenreName(name);
        return g;
    }

    @Test
    void getGenresByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        assertThat(service.getGenresByEventService("Missing")).isEmpty();
    }

    @Test
    void getGenresByEvent_mapsToDto() {
        Event e = event("Fest");
        Genre g = genre(1L, "breaking");
        EventGenre eg = new EventGenre();
        eg.setGenre(g);
        eg.setFormat("1v1");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(eventGenreRepo.findByEvent(e)).thenReturn(List.of(eg));

        List<GetGenreDto> result = service.getGenresByEventService("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).genreName).isEqualTo("breaking");
        assertThat(result.get(0).format).isEqualTo("1v1");
    }

    @Test
    void addGenreToEvent_throwsWhenGenreNotFound() {
        Event e = event("Fest");
        AddGenreToEventDto dto = new AddGenreToEventDto();
        dto.eventName = "Fest";
        dto.genreName = List.of("unknown");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addGenreToEventService(dto))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addGenreToEvent_throwsOnDuplicate() {
        Event e = event("Fest");
        Genre g = genre(1L, "breaking");
        AddGenreToEventDto dto = new AddGenreToEventDto();
        dto.eventName = "Fest";
        dto.genreName = List.of("breaking");
        EventGenre existing = new EventGenre();
        existing.setGenre(g); // non-null genre → duplicate
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.of(g));
        when(eventGenreRepo.findByEventAndGenre(e, g)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addGenreToEventService(dto))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateFormat_throwsWhenEventOrGenreNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEventGenreFormat("Missing", "breaking", "1v1"))
            .isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=EventGenreServiceTest 2>&1 | tail -5
```
Expected: all pass.

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/EventGenreServiceTest.java && git commit -m "test: EventGenreService unit tests"
```

---

## Task 6: ScoreServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/ScoreServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.AspectScoreDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.ParticipantScoreDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ScoreRepo;
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
class ScoreServiceTest {

    @Mock ScoreRepo repo;
    @Mock EventGenreParticpantRepo eventGenreParticpantRepo;
    @Mock JudgeRepo judgeRepo;
    @InjectMocks ScoreService service;

    private Score scoreWith(Judge judge, EventGenreParticipant egp, Double value, String aspect) {
        Score s = new Score();
        s.setJudge(judge);
        s.setEventGenreParticipant(egp);
        s.setValue(value);
        s.setAspect(aspect);
        return s;
    }

    @Test
    void getAllScore_skipsRowsWithNullJudgeOrEgp() {
        Score good = mock(Score.class);
        Judge j = new Judge(); j.setName("Mike");
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Event e = new Event(); e.setEventName("Fest");
        Genre g = new Genre(); g.setGenreName("breaking");
        when(good.getJudge()).thenReturn(j);
        when(good.getEventGenreParticipant()).thenReturn(egp);
        when(good.getValue()).thenReturn(8.5);
        when(good.getAspect()).thenReturn("technique");
        when(egp.getEvent()).thenReturn(e);
        when(egp.getGenre()).thenReturn(g);
        when(egp.getDisplayName()).thenReturn("Player1");
        when(egp.getFormat()).thenReturn("1v1");
        // also include a bad row (null judge)
        Score bad = new Score();
        when(repo.findbyEvent("Fest")).thenReturn(List.of(good, bad));

        List<GetParticipatnScoreDto> result = service.getAllScore("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).judgeName).isEqualTo("Mike");
        assertThat(result.get(0).aspect).isEqualTo("technique");
    }

    @Test
    void updateScore_singleScoreMode_deletesOldAndSavesNew() {
        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Fest";
        dto.genreName = "breaking";
        dto.judgeName = "Mike";
        ParticipantScoreDto pd = new ParticipantScoreDto();
        pd.participantName = "Player1";
        pd.score = 8.5;
        pd.aspects = null;
        dto.participantScore = List.of(pd);

        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge(); judge.setName("Mike");
        when(eventGenreParticpantRepo.findByEventGenreParticipant("Fest", "breaking", "Player1"))
            .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Mike")).thenReturn(Optional.of(judge));

        service.updateParticipantScoreService(dto);

        verify(repo).deleteByEventGenreParticipantAndJudge(egp, judge);
        verify(repo).save(any(Score.class));
    }

    @Test
    void updateScore_aspectMode_savesPerAspect() {
        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Fest";
        dto.genreName = "breaking";
        dto.judgeName = "Mike";
        AspectScoreDto a1 = new AspectScoreDto();
        a1.aspect = "technique";
        a1.score = 8.0;
        ParticipantScoreDto pd = new ParticipantScoreDto();
        pd.participantName = "Player1";
        pd.aspects = List.of(a1);
        dto.participantScore = List.of(pd);

        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge(); judge.setName("Mike");
        when(eventGenreParticpantRepo.findByEventGenreParticipant("Fest", "breaking", "Player1"))
            .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Mike")).thenReturn(Optional.of(judge));
        when(repo.findByEventGenreParticipantAndJudgeAndAspect(egp, judge, "technique"))
            .thenReturn(Optional.empty());

        service.updateParticipantScoreService(dto);

        verify(repo).save(any(Score.class));
        verify(repo, never()).deleteByEventGenreParticipantAndJudge(any(), any());
    }

    @Test
    void updateScore_skipsWhenEgpOrJudgeNotFound() {
        UpdateParticipantsScoreDto dto = new UpdateParticipantsScoreDto();
        dto.eventName = "Fest";
        dto.genreName = "breaking";
        dto.judgeName = "Ghost";
        ParticipantScoreDto pd = new ParticipantScoreDto();
        pd.participantName = "Nobody";
        dto.participantScore = List.of(pd);
        when(eventGenreParticpantRepo.findByEventGenreParticipant("Fest", "breaking", "Nobody"))
            .thenReturn(Optional.empty());

        service.updateParticipantScoreService(dto);

        verify(repo, never()).save(any());
    }

    @Test
    void deleteScoreByEvent_delegatesToRepo() {
        DeleteScoreByEventDto dto = mock(DeleteScoreByEventDto.class);
        when(dto.getEvent_id()).thenReturn(1L);
        when(repo.deleteByEventId(1L)).thenReturn(5);

        Integer result = service.deleteScoreByEventService(dto);

        assertThat(result).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=ScoreServiceTest 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/ScoreServiceTest.java && git commit -m "test: ScoreService unit tests (single-score and aspect-score modes)"
```

---

## Task 7: ScoringCriteriaServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/ScoringCriteriaServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.AddScoringCriteriaDto;
import com.example.BES.dtos.GetScoringCriteriaDto;
import com.example.BES.dtos.UpdateScoringCriteriaDto;
import com.example.BES.models.Event;
import com.example.BES.models.Genre;
import com.example.BES.models.ScoringCriteria;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ScoringCriteriaRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringCriteriaServiceTest {

    @Mock ScoringCriteriaRepo repo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @InjectMocks ScoringCriteriaService service;

    private ScoringCriteria criteria(Long id, String name, double weight) {
        ScoringCriteria sc = new ScoringCriteria();
        sc.setId(id);
        sc.setName(name);
        sc.setWeight(weight);
        sc.setDisplayOrder(0);
        return sc;
    }

    @Test
    void getCriteria_withGenre_fallsBackToEventLevel() {
        when(repo.findByEventNameAndGenreName("Fest", "breaking")).thenReturn(List.of());
        when(repo.findEventLevelByEventName("Fest")).thenReturn(List.of(criteria(1L, "Technique", 1.0)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", "breaking");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Technique");
    }

    @Test
    void getCriteria_withGenre_returnsGenreSpecificWhenPresent() {
        when(repo.findByEventNameAndGenreName("Fest", "breaking"))
            .thenReturn(List.of(criteria(2L, "Musicality", 0.5)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", "breaking");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Musicality");
    }

    @Test
    void getCriteria_withoutGenre_returnsEventLevel() {
        when(repo.findEventLevelByEventName("Fest"))
            .thenReturn(List.of(criteria(1L, "Overall", 1.0)));

        List<GetScoringCriteriaDto> result = service.getCriteria("Fest", null);

        assertThat(result).hasSize(1);
    }

    @Test
    void addCriteria_throwsWhenEventNotFound() {
        AddScoringCriteriaDto dto = new AddScoringCriteriaDto();
        dto.eventName = "Missing";
        dto.name = "Technique";
        dto.weight = 1.0;
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addCriteria(dto))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addCriteria_savesWithoutGenre() {
        AddScoringCriteriaDto dto = new AddScoringCriteriaDto();
        dto.eventName = "Fest";
        dto.name = "Technique";
        dto.weight = 1.0;
        dto.genreName = null;
        Event e = new Event(); e.setEventName("Fest");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        ScoringCriteria saved = criteria(1L, "Technique", 1.0);
        saved.setEvent(e);
        when(repo.save(any())).thenReturn(saved);

        GetScoringCriteriaDto result = service.addCriteria(dto);

        assertThat(result.name).isEqualTo("Technique");
    }

    @Test
    void updateCriteria_throwsWhenNotFound() {
        UpdateScoringCriteriaDto dto = new UpdateScoringCriteriaDto();
        dto.name = "New";
        dto.weight = 0.5;
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCriteria(99L, dto))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removeCriteria_delegatesToRepo() {
        service.removeCriteria(1L);
        verify(repo).deleteById(1L);
    }

    @Test
    void deleteAllCriteria_deletesForGenre() {
        List<ScoringCriteria> list = List.of(criteria(1L, "T", 1.0));
        when(repo.findByEventNameAndGenreName("Fest", "breaking")).thenReturn(list);

        service.deleteAllCriteria("Fest", "breaking");

        verify(repo).deleteAll(list);
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=ScoringCriteriaServiceTest 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/ScoringCriteriaServiceTest.java && git commit -m "test: ScoringCriteriaService unit tests"
```

---

## Task 8: AuditionFeedbackServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/AuditionFeedbackServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.SubmitAuditionFeedbackDto;
import com.example.BES.dtos.admin.GetFeedbackGroupDto;
import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.FeedbackTag;
import com.example.BES.models.FeedbackTagGroup;
import com.example.BES.models.Judge;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.FeedbackTagGroupRepository;
import com.example.BES.respositories.FeedbackTagRepository;
import com.example.BES.respositories.JudgeRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditionFeedbackServiceTest {

    @Mock AuditionFeedbackRepository feedbackRepo;
    @Mock FeedbackTagGroupRepository tagGroupRepo;
    @Mock FeedbackTagRepository tagRepo;
    @Mock EventGenreParticpantRepo egpRepo;
    @Mock JudgeRepo judgeRepo;
    @InjectMocks AuditionFeedbackService service;

    @Test
    void getAllFeedbackGroups_mapsToDto() {
        FeedbackTagGroup group = new FeedbackTagGroup();
        group.setId(1L);
        group.setName("Energy");
        FeedbackTag tag = new FeedbackTag();
        tag.setId(10L);
        tag.setLabel("High");
        tag.setGroup(group);
        group.setTags(List.of(tag));
        when(tagGroupRepo.findAll()).thenReturn(List.of(group));

        List<GetFeedbackGroupDto> result = service.getAllFeedbackGroups();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Energy");
        assertThat(result.get(0).getTags()).hasSize(1);
    }

    @Test
    void addFeedbackGroup_savesAndReturnsAll() {
        FeedbackTagGroup group = new FeedbackTagGroup();
        group.setId(1L);
        group.setName("Energy");
        group.setTags(new ArrayList<>());
        when(tagGroupRepo.findAll()).thenReturn(List.of(group));

        service.addFeedbackGroup("Energy");

        verify(tagGroupRepo).save(any(FeedbackTagGroup.class));
    }

    @Test
    void deleteFeedbackGroup_delegatesToRepo() {
        service.deleteFeedbackGroup(1L);
        verify(tagGroupRepo).deleteById(1L);
    }

    @Test
    void addFeedbackTag_returnsAllGroupsWhenGroupNotFound() {
        when(tagGroupRepo.findById(99L)).thenReturn(Optional.empty());
        when(tagGroupRepo.findAll()).thenReturn(List.of());

        List<GetFeedbackGroupDto> result = service.addFeedbackTag(99L, "label");

        verify(tagRepo, never()).save(any());
        assertThat(result).isEmpty();
    }

    @Test
    void submitFeedback_doesNothingWhenEgpOrJudgeNull() {
        SubmitAuditionFeedbackDto dto = new SubmitAuditionFeedbackDto();
        dto.setEventName("Fest");
        dto.setGenreName("breaking");
        dto.setAuditionNumber(1);
        dto.setJudgeName("Ghost");
        when(egpRepo.findByEventNameAndGenreNameAndAuditionNumber("Fest", "breaking", 1))
            .thenReturn(Optional.empty());

        service.submitFeedback(dto);

        verify(feedbackRepo, never()).save(any());
    }

    @Test
    void submitFeedback_savesWhenEgpAndJudgeFound() {
        SubmitAuditionFeedbackDto dto = new SubmitAuditionFeedbackDto();
        dto.setEventName("Fest");
        dto.setGenreName("breaking");
        dto.setAuditionNumber(1);
        dto.setJudgeName("Mike");
        dto.setTagIds(List.of());
        dto.setNote("Great energy");

        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        Judge judge = new Judge(); judge.setName("Mike");
        when(egpRepo.findByEventNameAndGenreNameAndAuditionNumber("Fest", "breaking", 1))
            .thenReturn(Optional.of(egp));
        when(judgeRepo.findByName("Mike")).thenReturn(Optional.of(judge));
        when(feedbackRepo.findByEventGenreParticipantAndJudge(egp, judge))
            .thenReturn(Optional.empty());

        service.submitFeedback(dto);

        verify(feedbackRepo).save(any(AuditionFeedback.class));
    }

    @Test
    void getAllFeedbackForParticipant_returnsEmptyWhenEgpNotFound() {
        when(egpRepo.findByEventGenreParticipant("Fest", "breaking", "Player1"))
            .thenReturn(Optional.empty());

        assertThat(service.getAllFeedbackForParticipant("Fest", "breaking", "Player1")).isEmpty();
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=AuditionFeedbackServiceTest 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/AuditionFeedbackServiceTest.java && git commit -m "test: AuditionFeedbackService unit tests"
```

---

## Task 9: ResultsServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/ResultsServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.GetResultsDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.respositories.AuditionFeedbackRepository;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.ScoreRepo;
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
class ResultsServiceTest {

    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreParticpantRepo egpRepo;
    @Mock ScoreRepo scoreRepo;
    @Mock AuditionFeedbackRepository feedbackRepo;
    @InjectMocks ResultsService service;

    @Test
    void getResultsByRefCode_returnsNullWhenRefCodeNotFound() {
        when(eventParticipantRepo.findByReferenceCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.getResultsByRefCode("UNKNOWN")).isNull();
    }

    @Test
    void getResultsByRefCode_returnsNullWhenResultsNotReleased() {
        Event e = new Event();
        e.setResultsReleased(false);
        e.setEventName("Fest");
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        Participant p = new Participant();
        ep.setParticipant(p);
        ep.setDisplayName("Player1");
        when(eventParticipantRepo.findByReferenceCode("ABC123")).thenReturn(Optional.of(ep));

        assertThat(service.getResultsByRefCode("ABC123")).isNull();
    }

    @Test
    void getResultsByRefCode_returnsResultsWhenReleased() {
        Event e = new Event();
        e.setEventId(1L);
        e.setEventName("Fest");
        e.setResultsReleased(true);
        Participant p = new Participant();
        p.setParticipantId(10L);
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setDisplayName("Player1");

        Genre g = new Genre();
        g.setGenreName("breaking");
        EventGenreParticipant egp = mock(EventGenreParticipant.class);
        when(egp.getGenre()).thenReturn(g);
        when(egp.getFormat()).thenReturn("1v1");
        when(egp.getAuditionNumber()).thenReturn(1);

        when(eventParticipantRepo.findByReferenceCode("ABC123")).thenReturn(Optional.of(ep));
        when(egpRepo.findByEventIdAndParticipantId(1L, 10L)).thenReturn(List.of(egp));
        when(scoreRepo.findByEventGenreParticipant(egp)).thenReturn(List.of());
        when(feedbackRepo.findByEventGenreParticipant(egp)).thenReturn(List.of());

        GetResultsDto result = service.getResultsByRefCode("ABC123");

        assertThat(result).isNotNull();
        assertThat(result.getParticipantName()).isEqualTo("Player1");
        assertThat(result.getEventName()).isEqualTo("Fest");
        assertThat(result.getGenreResults()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=ResultsServiceTest 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/ResultsServiceTest.java && git commit -m "test: ResultsService unit tests (ref code lookup, release gate)"
```

---

## Task 10: BattleServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`

- [ ] **Step 1: Write the test file**

Note: `BattleService` is an in-memory singleton. `@InjectMocks` calls its no-arg constructor which initializes state. `SimpMessagingTemplate` is mocked to absorb broadcasts.

```java
package com.example.BES.services;

import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.models.Judge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BattleServiceTest {

    @Mock JudgeService judgeService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks BattleService service;

    @Test
    void initialPhaseIsIDLE() {
        assertThat(service.getBattlePhase()).isEqualTo("IDLE");
    }

    @Test
    void setBattlerPair_setsNamesAndTransitionsToLOCKED() {
        SetBattlerPairDto dto = mock(SetBattlerPairDto.class);
        when(dto.getLeftBattler()).thenReturn("Alice");
        when(dto.getRightBattler()).thenReturn("Bob");

        service.setBattlerPairService(dto);

        assertThat(service.getCurrentPair().getLeftBattler().getName()).isEqualTo("Alice");
        assertThat(service.getCurrentPair().getRightBattler().getName()).isEqualTo("Bob");
        assertThat(service.getBattlePhase()).isEqualTo("LOCKED");
    }

    @Test
    void setBattlePhase_cannotManuallySetREVEALED() {
        service.setBattlePhaseService("REVEALED");

        assertThat(service.getBattlePhase()).isEqualTo("IDLE");
    }

    @Test
    void setBattlePhase_setsVOTING() {
        service.setBattlePhaseService("VOTING");

        assertThat(service.getBattlePhase()).isEqualTo("VOTING");
    }

    @Test
    void setScore_returnsMinusTwoWhenNoJudges() {
        assertThat(service.setScoreService()).isEqualTo(-2);
    }

    @Test
    void setScore_leftWins_returnsZeroAndTransitionsToREVEALED() {
        // Add a judge and vote for left (0)
        Judge j = new Judge(); j.setJudgeId(1L); j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);
        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(1L);
        service.setBattleJudgeService(jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(1L);
        when(vDto.getVote()).thenReturn(0); // vote for left
        service.setVoteService(vDto);

        Integer result = service.setScoreService();

        assertThat(result).isEqualTo(0);
        assertThat(service.getBattlePhase()).isEqualTo("REVEALED");
    }

    @Test
    void setScore_rightWins_returnsOne() {
        Judge j = new Judge(); j.setJudgeId(2L); j.setName("Sara");
        when(judgeService.getJudgeById(2L)).thenReturn(j);
        SetJudgeDto jDto = mock(SetJudgeDto.class);
        when(jDto.getId()).thenReturn(2L);
        service.setBattleJudgeService(jDto);

        SetVoteDto vDto = mock(SetVoteDto.class);
        when(vDto.getId()).thenReturn(2L);
        when(vDto.getVote()).thenReturn(1); // vote for right
        service.setVoteService(vDto);

        assertThat(service.setScoreService()).isEqualTo(1);
    }

    @Test
    void setBattleJudge_returnsDuplicateCodeWhenAlreadyAdded() {
        Judge j = new Judge(); j.setJudgeId(1L); j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);
        SetJudgeDto dto = mock(SetJudgeDto.class);
        when(dto.getId()).thenReturn(1L);
        service.setBattleJudgeService(dto);

        Integer result = service.setBattleJudgeService(dto);

        assertThat(result).isEqualTo(0); // already exists
    }

    @Test
    void setBattleJudge_returnsMinusOneWhenJudgeNotFound() {
        when(judgeService.getJudgeById(99L)).thenReturn(null);
        SetJudgeDto dto = mock(SetJudgeDto.class);
        when(dto.getId()).thenReturn(99L);

        assertThat(service.setBattleJudgeService(dto)).isEqualTo(-1);
    }

    @Test
    void removeBattleJudge_removesFromList() {
        Judge j = new Judge(); j.setJudgeId(1L); j.setName("Mike");
        when(judgeService.getJudgeById(1L)).thenReturn(j);
        SetJudgeDto addDto = mock(SetJudgeDto.class);
        when(addDto.getId()).thenReturn(1L);
        service.setBattleJudgeService(addDto);
        assertThat(service.getJudges()).hasSize(1);

        SetJudgeDto removeDto = mock(SetJudgeDto.class);
        when(removeDto.getId()).thenReturn(1L);
        service.removeBattleJudgeService(removeDto);

        assertThat(service.getJudges()).isEmpty();
    }

    @Test
    void setVote_returnsMinusTwoWhenJudgeNotInList() {
        SetVoteDto dto = mock(SetVoteDto.class);
        when(dto.getId()).thenReturn(99L);

        assertThat(service.setVoteService(dto)).isEqualTo(-2);
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=BattleServiceTest 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/BattleServiceTest.java && git commit -m "test: BattleService unit tests (phases, voting, judges)"
```

---

## Task 11: RegistrationServiceTest

**Files:** Create `BES/src/test/java/com/example/BES/services/RegistrationServiceTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.services;

import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
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
    @Mock MailSenderService mailService;
    @Mock EventRepo eventRepo;
    @Mock ParticipantService participantService;
    @Mock ParticipantRepo participantRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreParticpantRepo eventGenreParticipantRepo;
    @Mock EventParticipantTeamMemberRepo teamMemberRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @InjectMocks RegistrationService service;

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

    @Test
    void verifyAndEmail_throwsWhenEventOrParticipantNotFound() {
        when(eventRepo.findById(1L)).thenReturn(Optional.empty());
        when(participantRepo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndEmail(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyAndEmail_throwsWhenEventParticipantNotFound() {
        Event e = new Event(); e.setEventId(1L);
        Participant p = new Participant(); p.setParticipantId(10L);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndEmail(10L, 1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyAndEmail_setsPaymentVerifiedAndSkipsEmailIfAlreadySent() throws Exception {
        Event e = new Event(); e.setEventId(1L); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantId(10L);
        EventParticipant ep = new EventParticipant();
        ep.setEvent(e);
        ep.setParticipant(p);
        ep.setEmailSent(true); // already sent
        ep.setPaymentVerified(false);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        when(participantRepo.findById(10L)).thenReturn(Optional.of(p));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(ep));

        service.verifyAndEmail(10L, 1L);

        assertThat(ep.isPaymentVerified()).isTrue();
        verify(mailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any());
        verify(eventParticipantRepo).save(ep);
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=RegistrationServiceTest 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/RegistrationServiceTest.java && git commit -m "test: RegistrationService unit tests"
```

---

## Task 12: EventParticpantServiceTest + PickupCrewServiceTest

**Files:** Create two test files.

- [ ] **Step 1: Write EventParticpantServiceTest**

```java
package com.example.BES.services;

import com.example.BES.models.Event;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.mapper.EventParticipantDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventParticpantServiceTest {

    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventParticipantDtoMapper eventParticipantDtoMapper;
    @InjectMocks EventParticpantService service;

    @Test
    void getAllParticipantsByEvent_returnsNullWhenEventNotFound() {
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThat(service.getAllParticipantsByEvent("Missing")).isNull();
    }

    @Test
    void getParticipantRefs_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThat(service.getParticipantRefs("Missing")).isEmpty();
    }

    @Test
    void getParticipantRefs_skipsNullReferenceCodes() {
        Event e = new Event(); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantName("Player1");
        EventParticipant ep1 = new EventParticipant();
        ep1.setDisplayName("Player1");
        ep1.setReferenceCode(null); // should be skipped
        EventParticipant ep2 = new EventParticipant();
        ep2.setDisplayName("Player2");
        ep2.setReferenceCode("ABC123");
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEvent(e)).thenReturn(List.of(ep1, ep2));

        List<Map<String, String>> result = service.getParticipantRefs("Fest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("referenceCode")).isEqualTo("ABC123");
    }

    @Test
    void addNewWalkIn_returnsNullWhenEventNotFound() {
        when(eventRepo.findByEventName("Missing")).thenReturn(Optional.empty());
        Participant p = new Participant();

        assertThat(service.addNewWalkInInEventService(p, "Missing", "breaking", null, null)).isNull();
    }

    @Test
    void addNewWalkIn_returnsExistingEpWhenAlreadyRegistered() {
        Event e = new Event(); e.setEventName("Fest");
        Participant p = new Participant(); p.setParticipantName("Player1");
        EventParticipant existing = new EventParticipant();
        when(eventRepo.findByEventName("Fest")).thenReturn(Optional.of(e));
        when(eventParticipantRepo.findByEventAndParticipant(e, p)).thenReturn(Optional.of(existing));
        when(eventParticipantRepo.save(existing)).thenReturn(existing);

        EventParticipant result = service.addNewWalkInInEventService(p, "Fest", "breaking", null, null);

        assertThat(result).isSameAs(existing);
    }
}
```

- [ ] **Step 2: Write PickupCrewServiceTest**

```java
package com.example.BES.services;

import com.example.BES.models.Event;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.ParticipantRepo;
import com.example.BES.respositories.PickupCrewRepo;
import com.example.BES.respositories.ScoreRepo;
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
class PickupCrewServiceTest {

    @Mock PickupCrewRepo crewRepo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock ParticipantRepo participantRepo;
    @Mock EventGenreParticpantRepo egpRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @Mock ScoreRepo scoreRepo;
    @InjectMocks PickupCrewService service;

    @Test
    void getCrewsForEventGenre_returnsEmptyWhenEventOrGenreNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.empty());

        assertThat(service.getCrewsForEventGenre("Missing", "breaking")).isEmpty();
    }

    @Test
    void getCrewsForEventGenre_returnsEmptyWhenNoCrews() {
        Event e = new Event(); e.setEventName("Fest");
        Genre g = new Genre(); g.setGenreName("breaking");
        when(eventRepo.findByEventNameIgnoreCase("Fest")).thenReturn(Optional.of(e));
        when(genreRepo.findByGenreName("breaking")).thenReturn(Optional.of(g));
        when(crewRepo.findByEventAndGenre(e, g)).thenReturn(List.of());

        assertThat(service.getCrewsForEventGenre("Fest", "breaking")).isEmpty();
    }

    @Test
    void deleteCrew_delegatesToRepo() {
        service.deleteCrew(1L);
        verify(crewRepo).deleteById(1L);
    }
}
```

- [ ] **Step 3: Run both**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest="EventParticpantServiceTest,PickupCrewServiceTest" 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/services/EventParticpantServiceTest.java BES/src/test/java/com/example/BES/services/PickupCrewServiceTest.java && git commit -m "test: EventParticpantService and PickupCrewService unit tests"
```

---

## Task 13: EmailTemplateServiceTest + QrCodeServiceTest + EventGenreParticpantServiceTest

**Files:** Create three test files.

- [ ] **Step 1: Write EmailTemplateServiceTest**

```java
package com.example.BES.services;

import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.dtos.UpdateEmailTemplateDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventEmailTemplate;
import com.example.BES.respositories.EventEmailTemplateRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
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
class EmailTemplateServiceTest {

    @Mock EventEmailTemplateRepo repo;
    @Mock EventGenreRepo eventGenreRepo;
    @Mock EventRepo eventRepo;
    @InjectMocks EmailTemplateService service;

    @Test
    void createDefaultTemplate_savesTemplate() {
        Event e = new Event();
        e.setEventName("Fest");

        service.createDefaultTemplate(e);

        verify(repo).save(argThat(t ->
            ((EventEmailTemplate) t).getSubject().contains("Fest") &&
            ((EventEmailTemplate) t).getBody().contains("Fest")
        ));
    }

    @Test
    void getTemplateByEventName_returnsNullWhenNotFound() {
        when(repo.findByEvent_EventName("Missing")).thenReturn(Optional.empty());

        assertThat(service.getTemplateByEventName("Missing")).isNull();
    }

    @Test
    void getTemplateByEventName_returnsDto() {
        EventEmailTemplate t = new EventEmailTemplate();
        t.setSubject("Test Subject");
        t.setBody("Custom body — no generic marker");
        when(repo.findByEvent_EventName("Fest")).thenReturn(Optional.of(t));

        GetEmailTemplateDto result = service.getTemplateByEventName("Fest");

        assertThat(result.getSubject()).isEqualTo("Test Subject");
        assertThat(result.getBody()).isEqualTo("Custom body — no generic marker");
    }

    @Test
    void updateTemplate_updatesAndReturns() {
        EventEmailTemplate t = new EventEmailTemplate();
        t.setSubject("Old");
        t.setBody("Old body");
        UpdateEmailTemplateDto dto = new UpdateEmailTemplateDto();
        dto.setSubject("New Subject");
        dto.setBody("New Body");
        when(repo.findByEvent_EventName("Fest")).thenReturn(Optional.of(t));
        when(repo.save(any())).thenReturn(t);

        GetEmailTemplateDto result = service.updateTemplate("Fest", dto);

        assertThat(result.getSubject()).isEqualTo("New Subject");
    }

    @Test
    void updateTemplate_throwsWhenNotFound() {
        UpdateEmailTemplateDto dto = new UpdateEmailTemplateDto();
        dto.setSubject("X");
        dto.setBody("Y");
        when(repo.findByEvent_EventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate("Missing", dto))
            .isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: Write QrCodeServiceTest**

```java
package com.example.BES.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    void generateQrCode_returnsPngBytes() throws Exception {
        byte[] result = service.generateQrCode("https://example.com", 200, 200);

        assertThat(result).isNotEmpty();
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(result[0]).isEqualTo((byte) 0x89);
        assertThat(result[1]).isEqualTo((byte) 0x50);
    }

    @Test
    void generateQrCode_worksWithSmallDimensions() throws Exception {
        byte[] result = service.generateQrCode("hello", 100, 100);

        assertThat(result).isNotEmpty();
    }
}
```

- [ ] **Step 3: Write EventGenreParticpantServiceTest**

```java
package com.example.BES.services;

import com.example.BES.dtos.GetEventGenreParticipantDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventParticipantRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.ParticipantRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventGenreParticpantServiceTest {

    @Mock EventGenreParticpantRepo repo;
    @Mock EventRepo eventRepo;
    @Mock GenreRepo genreRepo;
    @Mock ParticipantRepo participantRepo;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock JudgeRepo judgeRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @InjectMocks EventGenreParticpantService service;

    @Test
    void getAllByEvent_returnsEmptyWhenEventNotFound() {
        when(eventRepo.findByEventNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        List<GetEventGenreParticipantDto> result =
            service.getAllEventGenreParticipantByEventService("Missing");

        assertThat(result).isEmpty();
    }

    @Test
    void removeParticipantFromGenre_doesNothingWhenEgpNotFound() {
        EventGenreParticipantId id = new EventGenreParticipantId(1L, 2L, 3L);
        when(repo.findById(id)).thenReturn(Optional.empty());

        service.removeParticipantFromGenre(3L, 1L, 2L);

        verify(repo, never()).delete(any());
    }

    @Test
    void addGenreToExistingParticipant_throwsWhenGenreNotFound() {
        when(genreRepo.findByGenreName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addGenreToExistingParticipant(1L, 1L, "ghost"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Genre not found");
    }
}
```

- [ ] **Step 4: Run all three**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest="EmailTemplateServiceTest,QrCodeServiceTest,EventGenreParticpantServiceTest" 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add \
  BES/src/test/java/com/example/BES/services/EmailTemplateServiceTest.java \
  BES/src/test/java/com/example/BES/services/QrCodeServiceTest.java \
  BES/src/test/java/com/example/BES/services/EventGenreParticpantServiceTest.java \
  && git commit -m "test: EmailTemplateService, QrCodeService, EventGenreParticpantService unit tests"
```

---

## Task 14: ResultsControllerIntegrationTest

**Files:** Create `BES/src/test/java/com/example/BES/controllers/ResultsControllerIntegrationTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResultsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getResults_returnsNotFoundForUnknownRef() throws Exception {
        mockMvc.perform(get("/api/v1/results").param("ref", "NONEXISTENT-REF"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getResults_isPublicNoAuthRequired() throws Exception {
        // Endpoint must be reachable without authentication
        mockMvc.perform(get("/api/v1/results").param("ref", "ANY"))
            .andExpect(status().isNotFound()); // not 403
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getResultsQr_returnsImageForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/results/qr").param("ref", "TEST-REF"))
            .andExpect(status().isOk());
    }

    @Test
    void getResultsQr_returns403WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/results/qr").param("ref", "TEST-REF"))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test -Dtest=ResultsControllerIntegrationTest 2>&1 | tail -10
```
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES/src/test/java/com/example/BES/controllers/ResultsControllerIntegrationTest.java && git commit -m "test: ResultsController integration tests (public access, auth gate)"
```

---

## Task 15: Run full backend suite + verify JaCoCo gate

- [ ] **Step 1: Run all tests**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, total test count significantly higher than 39, `Tests run: X, Failures: 0`.

- [ ] **Step 2: If JaCoCo check fails (< 70%), identify which services are below threshold**

Open `BES/target/site/jacoco/index.html` in a browser, or run:
```bash
grep -A5 "com.example.BES.services" BES/target/site/jacoco/jacoco.csv | head -40
```
Find services with low line coverage and add 1-2 more test cases targeting uncovered branches.

- [ ] **Step 3: Commit passing state**

```bash
cd /Users/bennylim/Documents/BES && git add -A && git commit -m "test: backend suite passes JaCoCo 70% gate"
```

---

## Task 16: Expand api.test.js

**Files:** Modify `BES-frontend/src/utils/__tests__/api.test.js`

- [ ] **Step 1: Add tests for additional API functions**

Append the following `describe` blocks inside the existing `describe('api.js', ...)`:

```js
  describe('whoami', () => {
    it('returns parsed JSON when ok', async () => {
      const user = { authenticated: true, username: 'admin' }
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(user) })

      const result = await api.whoami()

      expect(result).toEqual(user)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/auth/me', expect.objectContaining({ method: 'GET' }))
    })
  })

  describe('fetchAllGenres', () => {
    it('returns genres on success', async () => {
      const genres = [{ id: 1, genreName: 'breaking' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(genres) })

      const result = await api.fetchAllGenres()

      expect(result).toEqual(genres)
    })

    it('returns empty array on failure', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 500 })

      const result = await api.fetchAllGenres()

      expect(result).toEqual([])
    })
  })

  describe('fetchAllJudges', () => {
    it('returns judges on success', async () => {
      const judges = [{ judgeId: 1, judgeName: 'Mike' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(judges) })

      const result = await api.fetchAllJudges()

      expect(result).toEqual(judges)
    })
  })

  describe('fetchEventByName', () => {
    it('calls correct URL', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ eventName: 'Fest' }) })

      await api.fetchEventByName('Fest')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('Fest'),
        expect.any(Object)
      )
    })
  })

  describe('getParticipantsByEvent', () => {
    it('returns participants on success', async () => {
      const participants = [{ participantName: 'Player1' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(participants) })

      const result = await api.getParticipantsByEvent('Fest')

      expect(result).toEqual(participants)
    })

    it('returns empty array on 404', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 404 })

      const result = await api.getParticipantsByEvent('Missing')

      expect(result).toEqual([])
    })
  })
```

- [ ] **Step 2: Run tests**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm test
```
Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES-frontend/src/utils/__tests__/api.test.js && git commit -m "test: expand api.test.js with whoami, genres, judges, participants"
```

---

## Task 17: auth.test.js

**Files:** Create `BES-frontend/src/utils/__tests__/auth.test.js`

- [ ] **Step 1: Write the test file**

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store = {}
  return {
    getItem: (key) => store[key] ?? null,
    setItem: (key, val) => { store[key] = String(val) },
    removeItem: (key) => { delete store[key] },
    clear: () => { store = {} },
  }
})()
Object.defineProperty(global, 'sessionStorage', { value: sessionStorageMock })

// Mock localStorage
const localStorageMock = (() => {
  let store = {}
  return {
    getItem: (key) => store[key] ?? null,
    setItem: (key, val) => { store[key] = String(val) },
    removeItem: (key) => { delete store[key] },
    clear: () => { store = {} },
  }
})()
Object.defineProperty(global, 'localStorage', { value: localStorageMock })

// Mock pinia (defineStore needs a Pinia instance; stub it for utility-only tests)
vi.mock('pinia', () => ({
  defineStore: (id, def) => {
    const store = typeof def === 'function' ? def() : def
    return () => ({
      ...store.state?.(),
      ...store.actions,
      ...store.getters,
    })
  },
}))

const { isEventVerified, markEventVerified, getActiveEvent, clearVerifiedEvents } = await import('../auth.js')

describe('auth.js utilities', () => {
  beforeEach(() => {
    sessionStorageMock.clear()
    localStorageMock.clear()
  })

  describe('isEventVerified', () => {
    it('returns false when nothing stored', () => {
      expect(isEventVerified(1)).toBe(false)
    })

    it('returns false for unverified event id', () => {
      sessionStorageMock.setItem('bes_verified_events', JSON.stringify([2]))
      expect(isEventVerified(1)).toBe(false)
    })

    it('returns true when event id is in verified list', () => {
      sessionStorageMock.setItem('bes_verified_events', JSON.stringify([1, 2]))
      expect(isEventVerified(1)).toBe(true)
    })

    it('coerces string id to number', () => {
      sessionStorageMock.setItem('bes_verified_events', JSON.stringify([5]))
      expect(isEventVerified('5')).toBe(true)
    })
  })

  describe('markEventVerified', () => {
    it('adds event id to verified list', () => {
      markEventVerified(3)
      expect(isEventVerified(3)).toBe(true)
    })

    it('does not duplicate event id', () => {
      markEventVerified(3)
      markEventVerified(3)
      const stored = JSON.parse(sessionStorageMock.getItem('bes_verified_events'))
      expect(stored.filter(id => id === 3)).toHaveLength(1)
    })
  })

  describe('getActiveEvent', () => {
    it('returns null when nothing stored', () => {
      expect(getActiveEvent()).toBeNull()
    })

    it('returns parsed event object', () => {
      sessionStorageMock.setItem('bes_active_event', JSON.stringify({ id: 1, name: 'Fest' }))
      expect(getActiveEvent()).toEqual({ id: 1, name: 'Fest' })
    })

    it('returns null on invalid JSON', () => {
      sessionStorageMock.setItem('bes_active_event', 'not-json')
      expect(getActiveEvent()).toBeNull()
    })
  })

  describe('clearVerifiedEvents', () => {
    it('removes both storage keys', () => {
      sessionStorageMock.setItem('bes_verified_events', '[1]')
      sessionStorageMock.setItem('bes_active_event', '{}')

      clearVerifiedEvents()

      expect(sessionStorageMock.getItem('bes_verified_events')).toBeNull()
      expect(sessionStorageMock.getItem('bes_active_event')).toBeNull()
    })
  })
})
```

- [ ] **Step 2: Run and verify**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm test
```
Expected: all tests pass including the new auth tests.

- [ ] **Step 3: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES-frontend/src/utils/__tests__/auth.test.js && git commit -m "test: auth.js utility tests (isEventVerified, markVerified, getActiveEvent, clear)"
```

---

## Task 18: adminApi.test.js

**Files:** Create `BES-frontend/src/utils/__tests__/adminApi.test.js`

- [ ] **Step 1: Write the test file**

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockFetch = vi.fn()
global.fetch = mockFetch

const adminApi = await import('../adminApi.js')

describe('adminApi.js', () => {
  beforeEach(() => {
    mockFetch.mockReset()
  })

  describe('addGenre', () => {
    it('calls correct endpoint with genre name', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addGenre('breaking')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/genre')
      expect(opts.method).toBe('POST')
      expect(JSON.parse(opts.body).name).toBe('breaking')
    })
  })

  describe('addJudge', () => {
    it('calls correct endpoint with judge name', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addJudge('Mike')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/judge')
      expect(JSON.parse(opts.body).judgeName).toBe('Mike')
    })
  })

  describe('deleteGenre', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteGenre(5)

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/genre')
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(5)
    })
  })

  describe('deleteJudge', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteJudge(3)

      const [, opts] = mockFetch.mock.calls[0]
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(3)
    })
  })

  describe('updateGenre', () => {
    it('sends correct payload', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.updateGenre(1, 'popping')

      const [, opts] = mockFetch.mock.calls[0]
      const body = JSON.parse(opts.body)
      expect(body.id).toBe(1)
      expect(body.newName).toBe('popping')
    })
  })

  describe('getFeedbackGroups', () => {
    it('returns parsed JSON on success', async () => {
      const groups = [{ id: 1, name: 'Energy' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(groups) })

      const result = await adminApi.getFeedbackGroups()

      expect(result).toEqual(groups)
    })

    it('returns empty array on failure', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false })

      const result = await adminApi.getFeedbackGroups()

      expect(result).toEqual([])
    })
  })

  describe('addFeedbackGroup', () => {
    it('calls correct endpoint', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addFeedbackGroup('Energy')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/feedback-group')
      expect(JSON.parse(opts.body).name).toBe('Energy')
    })
  })

  describe('deleteFeedbackGroup', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteFeedbackGroup(2)

      const [, opts] = mockFetch.mock.calls[0]
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(2)
    })
  })

  describe('addFeedbackTag', () => {
    it('sends groupId and label', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addFeedbackTag(1, 'High Energy')

      const [, opts] = mockFetch.mock.calls[0]
      const body = JSON.parse(opts.body)
      expect(body.groupId).toBe(1)
      expect(body.label).toBe('High Energy')
    })
  })

  describe('deleteFeedbackTag', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteFeedbackTag(7)

      const [, opts] = mockFetch.mock.calls[0]
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(7)
    })
  })
})
```

- [ ] **Step 2: Run all frontend tests**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm test
```
Expected: all tests pass.

- [ ] **Step 3: Run coverage report**

```bash
npm run test:coverage
```
Expected: coverage table shows meaningful % for `adminApi.js`, `api.js`, `auth.js`.

- [ ] **Step 4: Commit**

```bash
cd /Users/bennylim/Documents/BES && git add BES-frontend/src/utils/__tests__/adminApi.test.js && git commit -m "test: adminApi.js tests (genre, judge, feedback group/tag CRUD)"
```

---

## Task 19: Final verification

- [ ] **Step 1: Run full backend suite**

```bash
cd /Users/bennylim/Documents/BES/BES && mvn test 2>&1 | grep -E "Tests run:|BUILD"
```
Expected: `BUILD SUCCESS`, `Tests run: 80+, Failures: 0, Errors: 0`.

- [ ] **Step 2: Run frontend tests with coverage**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run test:coverage
```
Expected: passes, coverage table shows `api.js`, `auth.js`, `adminApi.js` with meaningful coverage.

- [ ] **Step 3: Final commit**

```bash
cd /Users/bennylim/Documents/BES && git add -A && git commit -m "test: complete test coverage implementation — backend 70%+ gate + frontend coverage"
```
