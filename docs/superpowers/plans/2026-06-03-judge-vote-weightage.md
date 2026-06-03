# Judge Vote Weightage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow organisers to assign a per-judge vote weightage in BattleControl so match outcomes are decided by the sum of weightages rather than headcount.

**Architecture:** `weightage` lives on the in-memory `BattleJudge` inner class and is serialised into the existing `battle_genre_state.judges_json` TEXT column — no DB migration needed. The frontend stores `{ id, vote, weightage }` in per-genre localStorage and passes weightage on `addBattleJudge` calls so genre-switch restores are a single round-trip.

**Tech Stack:** Spring Boot (Java 21), JUnit 5 + Mockito, Vue 3 Composition API, Vitest

---

## Files

| Action | Path |
|--------|------|
| Modify | `BES/src/main/java/com/example/BES/services/BattleService.java` |
| Modify | `BES/src/main/java/com/example/BES/dtos/battle/SetJudgeDto.java` |
| Create | `BES/src/main/java/com/example/BES/dtos/battle/UpdateJudgeWeightageDto.java` |
| Modify | `BES/src/main/java/com/example/BES/controllers/BattleController.java` |
| Modify | `BES/src/test/java/com/example/BES/services/BattleServiceTest.java` |
| Modify | `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java` |
| Modify | `BES-frontend/src/utils/api.js` |
| Modify | `BES-frontend/src/views/BattleControl.vue` |
| Modify | `BES-frontend/src/utils/__tests__/api.test.js` |

---

## Task 1: Add `weightage` to `BattleJudge` + DTOs

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java` (inner class `BattleJudge`)
- Modify: `BES/src/main/java/com/example/BES/dtos/battle/SetJudgeDto.java`
- Create: `BES/src/main/java/com/example/BES/dtos/battle/UpdateJudgeWeightageDto.java`

- [ ] **Step 1: Add `weightage` field + normalising getter to `BattleJudge`**

In `BattleService.java`, find the `BattleJudge` static inner class (around line 460) and update it:

```java
public static class BattleJudge {
    private Long id;
    private String name;
    private Integer vote;
    private Integer weightage;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getVote() { return vote; }
    public void setVote(Integer vote) { this.vote = vote; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getWeightage() { return weightage != null && weightage > 0 ? weightage : 1; }
    public void setWeightage(int weightage) { this.weightage = weightage; }
}
```

- [ ] **Step 2: Add `weightage` to `SetJudgeDto`**

Full file content for `BES/src/main/java/com/example/BES/dtos/battle/SetJudgeDto.java`:

```java
package com.example.BES.dtos.battle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SetJudgeDto {
    @NotNull
    private Long id;

    @Min(1)
    private int weightage = 1;

    public Long getId() { return id; }
    public int getWeightage() { return weightage; }
}
```

- [ ] **Step 3: Create `UpdateJudgeWeightageDto`**

```java
package com.example.BES.dtos.battle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateJudgeWeightageDto {
    @NotNull
    private Long id;

    @Min(1)
    private int weightage;

    public Long getId() { return id; }
    public int getWeightage() { return weightage; }
}
```

- [ ] **Step 4: Verify project still compiles**

```bash
cd BES && mvn clean package -DskipTests -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java \
        BES/src/main/java/com/example/BES/dtos/battle/SetJudgeDto.java \
        BES/src/main/java/com/example/BES/dtos/battle/UpdateJudgeWeightageDto.java
git commit -m "feat: add weightage field to BattleJudge and judge DTOs"
```

---

## Task 2: Update service — `setBattleJudgeService` and `updateJudgeWeightageService`

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

- [ ] **Step 1: Update `setBattleJudgeService` to set weightage from DTO**

Find `setBattleJudgeService` (around line 195). Replace the block that creates `BattleJudge`:

```java
// OLD:
BattleJudge battleJudge = new BattleJudge();
battleJudge.setName(judge.getName());
battleJudge.setVote(-3);
battleJudge.setId(dto.getId());
judges.add(battleJudge);

// NEW:
BattleJudge battleJudge = new BattleJudge();
battleJudge.setName(judge.getName());
battleJudge.setVote(-3);
battleJudge.setId(dto.getId());
battleJudge.setWeightage(Math.max(1, dto.getWeightage()));
judges.add(battleJudge);
```

- [ ] **Step 2: Add `updateJudgeWeightageService` method**

Add after `removeBattleJudgeService`:

```java
public void updateJudgeWeightageService(UpdateJudgeWeightageDto dto) {
    synchronized (judges) {
        judges.stream()
            .filter(j -> j.getId().equals(dto.getId()))
            .findFirst()
            .ifPresent(j -> j.setWeightage(Math.max(1, dto.getWeightage())));
    }
    messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
    persistActiveState();
}
```

Also add the import at the top of `BattleService.java`:

```java
import com.example.BES.dtos.battle.UpdateJudgeWeightageDto;
```

- [ ] **Step 3: Verify project still compiles**

```bash
cd BES && mvn clean package -DskipTests -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "feat: update setBattleJudgeService for weightage, add updateJudgeWeightageService"
```

---

## Task 3: Update `setScoreService` to use weightage sums

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

- [ ] **Step 1: Replace headcount scoring with weightage sum**

Find `setScoreService` (around line 155). Replace the entire method:

```java
public Integer setScoreService(boolean isFinal) {
    int leftWeight, rightWeight;
    synchronized (judges) {
        leftWeight  = judges.stream().filter(j -> j.getVote() == 0).mapToInt(BattleJudge::getWeightage).sum();
        rightWeight = judges.stream().filter(j -> j.getVote() == 1).mapToInt(BattleJudge::getWeightage).sum();
    }
    Integer res;
    if (leftWeight == rightWeight) {
        if (isFinal) return -3;
        res = -1;
    } else if (leftWeight > rightWeight) {
        currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
        res = 0;
    } else {
        currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
        res = 1;
    }
    messagingTemplate.convertAndSend("/topic/battle/score", Map.of(
        "message", res,
        "left",    currentPair.getLeftBattler().getScore(),
        "right",   currentPair.getRightBattler().getScore()
    ));
    if (res == 0 || res == 1) {
        battlePhase = "REVEALED";
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
            "phase", battlePhase,
            "genre", activeGenreName != null ? activeGenreName : ""
        ));
        persistActiveState();
    }
    return res;
}
```

- [ ] **Step 2: Verify project still compiles**

```bash
cd BES && mvn clean package -DskipTests -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "feat: replace headcount vote tallying with weightage sum in setScoreService"
```

---

## Task 4: Add controller endpoint

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

- [ ] **Step 1: Add import and new endpoint**

Add import near the top of `BattleController.java`:

```java
import com.example.BES.dtos.battle.UpdateJudgeWeightageDto;
```

Add this method after the `POST /judge` endpoint (around line 212):

```java
@PostMapping("/judge/weightage")
public ResponseEntity<?> updateJudgeWeightage(@Valid @RequestBody UpdateJudgeWeightageDto dto) {
    battleService.updateJudgeWeightageService(dto);
    return ResponseEntity.ok(Map.of("message", "Weightage updated"));
}
```

- [ ] **Step 2: Verify project compiles**

```bash
cd BES && mvn clean package -DskipTests -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add POST /api/v1/battle/judge/weightage endpoint"
```

---

## Task 5: Backend tests

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`
- Modify: `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java`

- [ ] **Step 1: Write failing unit tests for weighted scoring**

Add these tests to `BattleServiceTest.java`. Add the import at the top:

```java
import com.example.BES.dtos.battle.UpdateJudgeWeightageDto;
```

Then add these test methods:

```java
@Test
void setScore_heavierJudgeWins_overrulesHeadcount() {
    // Judge A: weightage 2, votes Left  → leftWeight = 2
    // Judge B: weightage 1, votes Right → rightWeight = 1
    // Left wins despite 1v1 headcount tie
    Judge judgeA = new Judge(); judgeA.setJudgeId(10L); judgeA.setName("A");
    Judge judgeB = new Judge(); judgeB.setJudgeId(11L); judgeB.setName("B");
    when(judgeService.getJudgeById(10L)).thenReturn(judgeA);
    when(judgeService.getJudgeById(11L)).thenReturn(judgeB);

    SetJudgeDto dtoA = mock(SetJudgeDto.class);
    when(dtoA.getId()).thenReturn(10L);
    when(dtoA.getWeightage()).thenReturn(2);
    service.setBattleJudgeService(dtoA);

    SetJudgeDto dtoB = mock(SetJudgeDto.class);
    when(dtoB.getId()).thenReturn(11L);
    when(dtoB.getWeightage()).thenReturn(1);
    service.setBattleJudgeService(dtoB);

    SetVoteDto vA = mock(SetVoteDto.class); when(vA.getId()).thenReturn(10L); when(vA.getVote()).thenReturn(0);
    SetVoteDto vB = mock(SetVoteDto.class); when(vB.getId()).thenReturn(11L); when(vB.getVote()).thenReturn(1);
    service.setVoteService(vA);
    service.setVoteService(vB);

    assertThat(service.setScoreService(false)).isEqualTo(0); // Left wins
}

@Test
void setScore_equalWeightedVotes_returnsTie() {
    // Judge A: weightage 2, votes Left
    // Judge B: weightage 2, votes Right → leftWeight == rightWeight → tie
    Judge judgeA = new Judge(); judgeA.setJudgeId(12L); judgeA.setName("C");
    Judge judgeB = new Judge(); judgeB.setJudgeId(13L); judgeB.setName("D");
    when(judgeService.getJudgeById(12L)).thenReturn(judgeA);
    when(judgeService.getJudgeById(13L)).thenReturn(judgeB);

    SetJudgeDto dtoA = mock(SetJudgeDto.class);
    when(dtoA.getId()).thenReturn(12L);
    when(dtoA.getWeightage()).thenReturn(2);
    service.setBattleJudgeService(dtoA);

    SetJudgeDto dtoB = mock(SetJudgeDto.class);
    when(dtoB.getId()).thenReturn(13L);
    when(dtoB.getWeightage()).thenReturn(2);
    service.setBattleJudgeService(dtoB);

    SetVoteDto vA = mock(SetVoteDto.class); when(vA.getId()).thenReturn(12L); when(vA.getVote()).thenReturn(0);
    SetVoteDto vB = mock(SetVoteDto.class); when(vB.getId()).thenReturn(13L); when(vB.getVote()).thenReturn(1);
    service.setVoteService(vA);
    service.setVoteService(vB);

    assertThat(service.setScoreService(false)).isEqualTo(-1); // tie
}

@Test
void updateJudgeWeightage_setsNewWeightageAndBroadcasts() {
    Judge j = new Judge(); j.setJudgeId(14L); j.setName("WeightJudge");
    when(judgeService.getJudgeById(14L)).thenReturn(j);

    SetJudgeDto addDto = mock(SetJudgeDto.class);
    when(addDto.getId()).thenReturn(14L);
    service.setBattleJudgeService(addDto);

    UpdateJudgeWeightageDto updateDto = mock(UpdateJudgeWeightageDto.class);
    when(updateDto.getId()).thenReturn(14L);
    when(updateDto.getWeightage()).thenReturn(3);
    service.updateJudgeWeightageService(updateDto);

    assertThat(service.getJudges().get(0).getWeightage()).isEqualTo(3);
    verify(messagingTemplate, atLeastOnce()).convertAndSend(
        eq("/topic/battle/judges"), any(Map.class));
}
```

- [ ] **Step 2: Run unit tests to verify they fail**

```bash
cd BES && mvn test -Dtest=BattleServiceTest -q 2>&1 | tail -20
```
Expected: Tests that reference `UpdateJudgeWeightageDto` or `getWeightage()` fail with compile error or assertion error.

- [ ] **Step 3: Run unit tests again after Task 1–3 implementation**

```bash
cd BES && mvn test -Dtest=BattleServiceTest -q 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, all tests pass including the 3 new ones.

- [ ] **Step 4: Write failing integration test**

In `BattleControllerIntegrationTest.java`, add this test method:

```java
@Test
@WithMockUser(roles = {"ADMIN"})
public void testUpdateJudgeWeightage_setsWeightageAndReturnsOk() throws Exception {
    Judge j = new Judge();
    j.setJudgeId(42L);
    j.setName("WeightTest");
    when(judgeService.getJudgeById(42L)).thenReturn(j);

    // Add judge
    mockMvc.perform(post("/api/v1/battle/judge")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("id", 42))))
            .andExpect(status().isOk());

    // Update weightage
    mockMvc.perform(post("/api/v1/battle/judge/weightage")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("id", 42, "weightage", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Weightage updated"));

    // Verify weightage reflected in judge list
    mockMvc.perform(get("/api/v1/battle/judges"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.judges[?(@.id == 42)].weightage").value(3));

    // Cleanup — remove judge so it doesn't pollute other tests
    mockMvc.perform(delete("/api/v1/battle/judge")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("id", 42))))
            .andExpect(status().isOk());
}
```

- [ ] **Step 5: Run integration test**

```bash
cd BES && mvn test -Dtest=BattleControllerIntegrationTest -q 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Run full backend test suite**

```bash
cd BES && mvn test -q 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add BES/src/test/java/com/example/BES/services/BattleServiceTest.java \
        BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java
git commit -m "test: add weighted scoring and weightage endpoint tests"
```

---

## Task 6: Frontend `api.js` — extend `addBattleJudge`, add `updateJudgeWeightage`

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Extend `addBattleJudge` to accept `weightage`**

Find `addBattleJudge` in `api.js` (around line 440). Replace:

```js
export const addBattleJudge = async(id) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}
```

With:

```js
export const addBattleJudge = async(id, weightage = 1) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        weightage: Math.max(1, Number(weightage) || 1),
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}
```

- [ ] **Step 2: Add `updateJudgeWeightage` function**

Add after `addBattleJudge`:

```js
export const updateJudgeWeightage = async(id, weightage) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge/weightage`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        weightage: Math.max(1, Number(weightage) || 1),
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: extend addBattleJudge with weightage param, add updateJudgeWeightage"
```

---

## Task 7: Frontend `BattleControl.vue` — localStorage shape + `syncJudgesForGenre`

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Add `updateJudgeWeightage` to the api import**

Find line 3 in `BattleControl.vue` (the long `import { ... } from '@/utils/api'` line). Add `updateJudgeWeightage` to the destructured list. The line currently starts with:

```js
import { addBattleJudge, addBattleGuest, battleJudgeVote, clearBattlePair,
```

Add `updateJudgeWeightage` anywhere in that list (alphabetical position: after `updateSmokeList`):

```js
import { addBattleJudge, addBattleGuest, battleJudgeVote, clearBattlePair, getBattleChampions, getBattleGuests, getBattleJudges, getBattlePhase, getBattleState, getOverlayConfig, getParticipantScore, getPickupCrews, getRegisteredParticipantsByEvent, removeBattleGuest, removeBattleJudge, resetBattleVotes, revealChampion, dismissChampionReveal, setActiveGenre, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateJudgeWeightage, updateSmokeList, uploadImage } from '@/utils/api'
```

- [ ] **Step 2: Update `saveGenreJudges` to include `weightage`**

Find `saveGenreJudges` (around line 1076). Replace:

```js
const saveGenreJudges = (genre) => {
  const judges = (battleJudges.value?.judges ?? []).map(j => ({ id: j.id, vote: j.vote }))
  localStorage.setItem(genreJudgeKey(genre), JSON.stringify(judges))
}
```

With:

```js
const saveGenreJudges = (genre) => {
  const judges = (battleJudges.value?.judges ?? []).map(j => ({ id: j.id, vote: j.vote, weightage: j.weightage ?? 1 }))
  localStorage.setItem(genreJudgeKey(genre), JSON.stringify(judges))
}
```

- [ ] **Step 3: Update `syncJudgesForGenre` to restore weightage**

Find the restore loop inside `syncJudgesForGenre` (around line 1100). Replace:

```js
const entries = raw.map(s => (typeof s === 'object' ? s : { id: s, vote: -3 }))
// Add sequentially for the same reason as removes above
for (const { id } of entries) {
  await addBattleJudge(id)
}
```

With:

```js
const entries = raw.map(s => (typeof s === 'object' ? s : { id: s, vote: -3, weightage: 1 }))
// Add sequentially for the same reason as removes above
for (const { id, weightage } of entries) {
  await addBattleJudge(id, weightage ?? 1)
}
```

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: persist weightage in localStorage and restore on genre switch"
```

---

## Task 8: Frontend `BattleControl.vue` — judge row UI with weightage input

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Add `submitUpdateJudgeWeightage` handler**

Find `submitAddBattleJudge` (around line 1123). Add this new function just before it:

```js
const submitUpdateJudgeWeightage = async (id, value) => {
  const weightage = Math.max(1, parseInt(value) || 1)
  await updateJudgeWeightage(id, weightage)
  battleJudges.value = await getBattleJudges()
  saveGenreJudges(selectedGenre.value)
}
```

- [ ] **Step 2: Update judge row template to include Weight input**

Find the judge row `v-for` in the template (around line 1881). Replace the entire `<div v-for="(j, index) in battleJudges?.judges || []"...>` block:

```html
<div
  v-for="(j, index) in battleJudges?.judges || []"
  :key="index"
  class="card-hover p-2 relative inline-flex items-center gap-2 px-3"
>
  <div class="corner-bar-tl"></div>
  <span class="type-body text-content-primary">{{ j.name }}</span>
  <div class="flex items-center gap-1">
    <span class="type-label text-content-muted" style="font-size:9px;letter-spacing:0.12em">WT</span>
    <input
      type="number"
      :value="j.weightage ?? 1"
      min="1"
      class="w-10 bg-surface-900 border border-surface-600 text-content-primary text-center type-body"
      style="padding:2px 4px;font-size:12px;clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
      @change="e => submitUpdateJudgeWeightage(j.id, e.target.value)"
    />
  </div>
  <button
    @click="submitRemoveBattleJudge(j.name)"
    class="flex items-center justify-center hover:text-red-400 transition-colors"
  >
    <i class="pi pi-times text-xs"></i>
  </button>
</div>
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: add inline weightage input to judge rows in BattleControl"
```

---

## Task 9: Frontend `BattleControl.vue` — vote panel `×N` badge + weighted winner logic

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Update `tentativeWinner` computed to use weightage sums**

Find `tentativeWinner` (around line 895). Replace:

```js
const tentativeWinner = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  if (judges.some(j => j.vote === -3)) return -2   // not all voted yet
  const leftVotes  = judges.filter(j => j.vote === 0).length
  const rightVotes = judges.filter(j => j.vote === 1).length
  if (leftVotes === rightVotes) return -1            // tie
  return leftVotes > rightVotes ? 0 : 1
})
```

With:

```js
const tentativeWinner = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  if (judges.some(j => j.vote === -3)) return -2
  const leftWeight  = judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0)
  const rightWeight = judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0)
  if (leftWeight === rightWeight) return -1
  return leftWeight > rightWeight ? 0 : 1
})
```

- [ ] **Step 2: Replace `voteCountDisplay` with `voteWeightDisplay`**

Find `voteCountDisplay` (around line 912). Replace:

```js
const voteCountDisplay = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return {
    left:  judges.filter(j => j.vote === 0).length,
    right: judges.filter(j => j.vote === 1).length,
  }
})
```

With:

```js
const voteWeightDisplay = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return {
    left:  judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0),
    right: judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0),
  }
})
```

- [ ] **Step 3: Update template references from `voteCountDisplay` to `voteWeightDisplay`**

Find both occurrences of `voteCountDisplay` in the template (around lines 2584 and 2592) and replace each with `voteWeightDisplay`.

Line ~2584:
```html
<div class="type-label text-content-muted mt-1" style="font-size:13px;letter-spacing:0.06em">{{ voteWeightDisplay.left }} – {{ voteWeightDisplay.right }}</div>
```

Line ~2592:
```html
<div class="type-body" style="font-size:20px;letter-spacing:0.06em;font-weight:bold;color:#9ca3af">TIE — {{ voteWeightDisplay.left }} – {{ voteWeightDisplay.right }}</div>
```

- [ ] **Step 4: Add `×N` weightage badge to each judge card in the vote panel**

Find the judge name div inside the vote panel `v-for` (around line 2553):

```html
<div style="font-size:10px;letter-spacing:0.18em;color:rgba(255,255,255,0.55);margin-bottom:6px">{{ judge.name }}</div>
```

Replace with:

```html
<div style="font-size:10px;letter-spacing:0.18em;color:rgba(255,255,255,0.55);margin-bottom:2px">{{ judge.name }}</div>
<div style="font-size:10px;color:#93c5fd;letter-spacing:0.1em;margin-bottom:4px">×{{ judge.weightage ?? 1 }}</div>
```

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: weighted vote panel — ×N badge, weighted tentativeWinner and voteWeightDisplay"
```

---

## Task 10: Frontend tests

**Files:**
- Modify: `BES-frontend/src/utils/__tests__/api.test.js`

- [ ] **Step 1: Write failing tests for `addBattleJudge` (with weightage) and `updateJudgeWeightage`**

Add to `api.test.js` inside the top-level `describe('api.js', ...)` block:

```js
describe('addBattleJudge', () => {
  it('sends id and default weightage 1 when no weightage given', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true })
    await api.addBattleJudge(5)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/battle/judge', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ id: 5, weightage: 1 }),
    }))
  })

  it('sends the given weightage', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true })
    await api.addBattleJudge(5, 3)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/battle/judge', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ id: 5, weightage: 3 }),
    }))
  })
})

describe('updateJudgeWeightage', () => {
  it('posts to /api/v1/battle/judge/weightage with id and weightage', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true })
    await api.updateJudgeWeightage(7, 2)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/battle/judge/weightage', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ id: 7, weightage: 2 }),
    }))
  })

  it('clamps weightage to minimum 1', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true })
    await api.updateJudgeWeightage(7, 0)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/battle/judge/weightage', expect.objectContaining({
      body: JSON.stringify({ id: 7, weightage: 1 }),
    }))
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd BES-frontend && npm test -- --reporter=verbose 2>&1 | grep -E "PASS|FAIL|✓|✗|×" | tail -20
```
Expected: `updateJudgeWeightage` tests fail (function doesn't exist yet from this file's perspective). Note: tests run after Tasks 6–9 are complete, so this step confirms the new test code is syntactically valid.

- [ ] **Step 3: Run full frontend test suite**

```bash
cd BES-frontend && npm test 2>&1 | tail -20
```
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/__tests__/api.test.js
git commit -m "test: add tests for addBattleJudge weightage param and updateJudgeWeightage"
```

---

## Final Verification

- [ ] **Run all backend tests**

```bash
cd BES && mvn test -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Run all frontend tests**

```bash
cd BES-frontend && npm test 2>&1 | tail -10
```
Expected: All tests pass.

- [ ] **Check branch is clean**

```bash
git status
```
Expected: `nothing to commit, working tree clean`
