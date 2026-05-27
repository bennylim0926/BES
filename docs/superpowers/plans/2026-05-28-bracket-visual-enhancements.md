# Bracket Visual Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add color theming, a bolder advance animation, champion reveal, and final-match tie prevention to the live bracket system.

**Architecture:** Backend adds three new endpoints (score isFinal check, revote, champion-reveal) and two new DTOs. Frontend adds overlay-config subscription to BracketVisualization, rewrites the advance animation, adds champion-reveal overlays to both BracketVisualization and BattleOverlay, and adds tie/reveal controls to BattleControl.

**Tech Stack:** Vue 3 `<script setup>`, Spring Boot, STOMP WebSocket, CSS `color-mix()`, Web Animations API.

---

## File Map

| File | What changes |
|---|---|
| `BES/src/main/java/com/example/BES/dtos/battle/SetBattleScoreDto.java` | **New** — `{ isFinal: boolean }` |
| `BES/src/main/java/com/example/BES/dtos/battle/ChampionRevealDto.java` | **New** — `{ genreName, championName, dismiss }` |
| `BES/src/main/java/com/example/BES/services/BattleService.java` | `setScoreService(boolean isFinal)`, `resetJudgeVotesService()`, `broadcastChampionReveal(dto)` |
| `BES/src/main/java/com/example/BES/controllers/BattleController.java` | Score endpoint accepts optional body; new `/revote` and `/champion-reveal` endpoints |
| `BES/src/test/java/com/example/BES/services/BattleServiceTest.java` | New tests for isFinal tie block, resetJudgeVotes, championReveal |
| `BES-frontend/src/utils/api.js` | Update `setBattleScore(isFinal)`, add `resetBattleVotes()`, `revealChampion(genreName, championName)`, `dismissChampionReveal()` |
| `BES-frontend/src/views/BattleControl.vue` | Final tie warning + START REVOTE; REVEAL CHAMPION / DISMISS buttons |
| `BES-frontend/src/views/BracketVisualization.vue` | Cyan → white, overlay config subscription, slot color theming, WIN badge, advance animation rewrite, champion reveal overlay |
| `BES-frontend/src/views/BattleOverlay.vue` | Champion reveal overlay subscription + animation |

---

## Task 1: New backend DTOs

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/battle/SetBattleScoreDto.java`
- Create: `BES/src/main/java/com/example/BES/dtos/battle/ChampionRevealDto.java`

- [ ] **Step 1: Create SetBattleScoreDto**

```java
// BES/src/main/java/com/example/BES/dtos/battle/SetBattleScoreDto.java
package com.example.BES.dtos.battle;

public class SetBattleScoreDto {
    private boolean isFinal;
    public boolean isFinal() { return isFinal; }
}
```

- [ ] **Step 2: Create ChampionRevealDto**

```java
// BES/src/main/java/com/example/BES/dtos/battle/ChampionRevealDto.java
package com.example.BES.dtos.battle;

public class ChampionRevealDto {
    private String genreName;
    private String championName;
    private boolean dismiss;

    public String getGenreName()    { return genreName; }
    public String getChampionName() { return championName; }
    public boolean isDismiss()      { return dismiss; }
}
```

- [ ] **Step 3: Compile check**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw clean compile -q
```
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/battle/SetBattleScoreDto.java \
        BES/src/main/java/com/example/BES/dtos/battle/ChampionRevealDto.java
git commit -m "feat(battle): add SetBattleScoreDto and ChampionRevealDto"
```

---

## Task 2: BattleService — isFinal tie blocking

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`
- Modify: `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `BattleServiceTest.java` after the existing `setScore_returnsMinusOneWhenNoJudges` test:

```java
@Test
void setScore_finalTie_returnsMinusTwoToSignalBlock() {
    // empty judges → tie, isFinal=true → returns -2 (blocked)
    assertThat(service.setScoreService(true)).isEqualTo(-2);
}

@Test
void setScore_nonFinalTie_returnsMinusOne() {
    // empty judges → tie, isFinal=false → normal tie return -1
    assertThat(service.setScoreService(false)).isEqualTo(-1);
}

@Test
void setScore_finalWinner_returnsZeroAndTransitionsToREVEALED() {
    Judge j = new Judge();
    j.setJudgeId(10L);
    j.setName("Judge_final");
    when(judgeService.getJudgeById(10L)).thenReturn(j);

    SetJudgeDto jDto = mock(SetJudgeDto.class);
    when(jDto.getId()).thenReturn(10L);
    service.setBattleJudgeService(jDto);

    SetVoteDto vDto = mock(SetVoteDto.class);
    when(vDto.getId()).thenReturn(10L);
    when(vDto.getVote()).thenReturn(0);
    service.setVoteService(vDto);

    assertThat(service.setScoreService(true)).isEqualTo(0);
    assertThat(service.getBattlePhase()).isEqualTo("REVEALED");
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw test -Dtest=BattleServiceTest -pl . -q 2>&1 | tail -20
```
Expected: compilation error (method `setScoreService(boolean)` doesn't exist yet).

- [ ] **Step 3: Update setScoreService to accept isFinal**

In `BattleService.java`, replace the `setScoreService()` signature and add the tie-block logic. Find the existing method:

```java
public Integer setScoreService(){
    // Broadcast the score here
    // This is where we reveal the judge decision on the screen
    // After that we add the point
    List<Integer> score = new ArrayList<>();
    Integer res = -100;
    if(judges.size() == 0){
        res = -2;
    }
    for (BattleJudge judge : judges) {
        score.add(judge.getVote());
    }
    if(Collections.frequency(score, 0) == Collections.frequency(score, 1)){
        res = -1;
    }else if(Collections.frequency(score, 0) > Collections.frequency(score, 1)){
        currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
        res = 0;
    }else{
        currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
        res = 1;
    }
    messagingTemplate.convertAndSend("/topic/battle/score",
        Map.of(
            "message", res,
            "left", currentPair.getLeftBattler().getScore(),
            "right", currentPair.getRightBattler().getScore()
        ));
    // Auto-transition: winner → REVEALED, tie → stay VOTING
    if (res == 0 || res == 1) {
        battlePhase = "REVEALED";
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of("phase", battlePhase));
    }
    return res;
}
```

Replace with:

```java
public Integer setScoreService(boolean isFinal){
    List<Integer> score = new ArrayList<>();
    Integer res = -100;
    if(judges.size() == 0){
        res = -2;
    }
    for (BattleJudge judge : judges) {
        score.add(judge.getVote());
    }
    if(Collections.frequency(score, 0) == Collections.frequency(score, 1)){
        // Final match tie is blocked — return -2 (same as "no judges") to signal the controller
        if (isFinal) return -2;
        res = -1;
    }else if(Collections.frequency(score, 0) > Collections.frequency(score, 1)){
        currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
        res = 0;
    }else{
        currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
        res = 1;
    }
    messagingTemplate.convertAndSend("/topic/battle/score",
        Map.of(
            "message", res,
            "left", currentPair.getLeftBattler().getScore(),
            "right", currentPair.getRightBattler().getScore()
        ));
    if (res == 0 || res == 1) {
        battlePhase = "REVEALED";
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of("phase", battlePhase));
    }
    return res;
}
```

> **Note on -2 reuse:** The existing `setScoreService()` already returns `-2` for "no judges found" and the controller maps that to 404. The new final-tie case also returns `-2` from the service, but the controller will distinguish it by checking the HTTP status it sends back. The controller in the next task will handle this with a 409 response for the `isFinal=true` + tie case specifically. We avoid adding a new return code value to keep the change minimal.

Actually, to avoid ambiguity between "no judges" and "final tie blocked", use a distinct return value. Replace `if (isFinal) return -2;` with `if (isFinal) return -3;` — we use -3 here specifically for "final tie blocked" in the service, and the controller maps it to 409.

Full corrected step (replace the service method as above but with `-3` for the final tie case):

```java
if(Collections.frequency(score, 0) == Collections.frequency(score, 1)){
    if (isFinal) return -3;   // final tie — blocked, do not broadcast
    res = -1;
}
```

- [ ] **Step 4: Fix the test to match the -3 return value**

Update the test added in Step 1:
```java
@Test
void setScore_finalTie_returnsMinusThreeToSignalBlock() {
    assertThat(service.setScoreService(true)).isEqualTo(-3);
}
```

- [ ] **Step 5: Update existing tests that call setScoreService() with no args**

The three existing tests call `service.setScoreService()` (no args). Update them to pass `false` (non-final):
- `setScore_returnsMinusOneWhenNoJudges` → `service.setScoreService(false)` — but wait, no judges means score list is empty → `frequency(0,0) == frequency(1,0)` → tie → but `isFinal=false` → returns `-1`. Wait, but the original test checks `== -1`. That's wrong: with no judges the original code returns `-2` (the `if(judges.size() == 0)` branch sets `res = -2`). Let me re-read...

Looking again: with `judges.size() == 0`, `res = -2`, then the for loop adds nothing to score (empty list), then `frequency(0)==frequency(1)` is both 0 so they're equal → `res = -1`. So the `-2` set earlier gets overwritten to `-1`. That's existing behavior.

So with the new code, when `judges.size() == 0` and `isFinal=false`: returns `-1`. When `judges.size() == 0` and `isFinal=true`: returns `-3`.

Update the three existing tests:
```java
// setScore_returnsMinusOneWhenNoJudges
assertThat(service.setScoreService(false)).isEqualTo(-1);

// setScore_leftWins_...
Integer result = service.setScoreService(false);

// setScore_rightWins_...
assertThat(service.setScoreService(false)).isEqualTo(1);
```

- [ ] **Step 6: Run tests — verify they pass**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw test -Dtest=BattleServiceTest -pl . -q 2>&1 | tail -20
```
Expected: `Tests run: N, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java \
        BES/src/test/java/com/example/BES/services/BattleServiceTest.java
git commit -m "feat(battle): block final match tie in setScoreService"
```

---

## Task 3: BattleService — resetJudgeVotesService + broadcastChampionReveal

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`
- Modify: `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add to `BattleServiceTest.java`:

```java
@Test
void resetJudgeVotes_setsAllVotesToMinusThreeAndBroadcasts() {
    Judge j = new Judge();
    j.setJudgeId(5L);
    j.setName("Alex");
    when(judgeService.getJudgeById(5L)).thenReturn(j);

    SetJudgeDto addDto = mock(SetJudgeDto.class);
    when(addDto.getId()).thenReturn(5L);
    service.setBattleJudgeService(addDto);

    SetVoteDto voteDto = mock(SetVoteDto.class);
    when(voteDto.getId()).thenReturn(5L);
    when(voteDto.getVote()).thenReturn(0);
    service.setVoteService(voteDto);

    service.resetJudgeVotesService();

    assertThat(service.getJudges().get(0).getVote()).isEqualTo(-3);
    verify(messagingTemplate, atLeastOnce()).convertAndSend(
        eq("/topic/battle/judges"), any(Map.class));
}

@Test
void broadcastChampionReveal_sendsToCorrectTopic() {
    ChampionRevealDto dto = mock(ChampionRevealDto.class);
    when(dto.getGenreName()).thenReturn("B-Boy/B-Girl");
    when(dto.getChampionName()).thenReturn("PULSE");
    when(dto.isDismiss()).thenReturn(false);

    service.broadcastChampionReveal(dto);

    verify(messagingTemplate).convertAndSend(
        eq("/topic/battle/champion-reveal"),
        any(Map.class)
    );
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw test -Dtest=BattleServiceTest -pl . -q 2>&1 | tail -10
```
Expected: compilation errors for missing methods.

- [ ] **Step 3: Add resetJudgeVotesService and broadcastChampionReveal to BattleService**

Add these two methods to `BattleService.java` (after the existing `setOverlayConfigService` method, before the inner classes):

```java
public void resetJudgeVotesService() {
    for (BattleJudge judge : judges) {
        judge.setVote(-3);
        messagingTemplate.convertAndSend(
            "/topic/battle/vote/" + judge.getId(),
            Map.of("vote", -3, "judge", judge.getId())
        );
    }
    messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
}

public void broadcastChampionReveal(ChampionRevealDto dto) {
    if (dto.isDismiss()) {
        messagingTemplate.convertAndSend("/topic/battle/champion-reveal",
            Map.of("dismiss", true));
    } else {
        messagingTemplate.convertAndSend("/topic/battle/champion-reveal",
            Map.of("dismiss", false,
                   "genreName", dto.getGenreName() != null ? dto.getGenreName() : "",
                   "championName", dto.getChampionName() != null ? dto.getChampionName() : ""));
    }
}
```

Also add the import at the top of the file:
```java
import com.example.BES.dtos.battle.ChampionRevealDto;
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw test -Dtest=BattleServiceTest -pl . -q 2>&1 | tail -10
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java \
        BES/src/test/java/com/example/BES/services/BattleServiceTest.java
git commit -m "feat(battle): add resetJudgeVotes and broadcastChampionReveal to BattleService"
```

---

## Task 4: BattleController — update score endpoint + add revote + champion-reveal

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

- [ ] **Step 1: Add import for new DTOs**

At the top of `BattleController.java`, add to the existing import block:
```java
import com.example.BES.dtos.battle.SetBattleScoreDto;
import com.example.BES.dtos.battle.ChampionRevealDto;
```

- [ ] **Step 2: Update the score endpoint**

Find the existing `setBattleScore()` method:
```java
@PostMapping("/score")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setBattleScore(){
    Integer code = battleService.setScoreService();
```

Replace it with:
```java
@PostMapping("/score")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setBattleScore(
        @RequestBody(required = false) SetBattleScoreDto dto){
    boolean isFinal = dto != null && dto.isFinal();
    Integer code = battleService.setScoreService(isFinal);
    if(code == -2){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            Map.of("message", "No judge found")
        );
    }else if(code == -3){
        // Final match tie — blocked
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of("message", "Tie in final match — revote required", "tie", true)
        );
    }else if(code == -1){
        return ResponseEntity.ok(
            Map.of("message", "Its a tie",
            "winner", -1)
        );
    }
    else if(code == 0){
        return ResponseEntity.ok(
            Map.of(
                "message", "Left side get one point",
                "winner", 0,
                "current score", battleService.getCurrentPair().getLeftBattler().getScore()
            )
        );
    }
    else {
        return ResponseEntity.ok(
            Map.of(
                "message", "Right side get one point",
                "winner", 1,
                "current score", battleService.getCurrentPair().getRightBattler().getScore())
        );
    }
}
```

- [ ] **Step 3: Add revote endpoint**

Add after the score endpoint:
```java
@PostMapping("/revote")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> revote(){
    battleService.resetJudgeVotesService();
    return ResponseEntity.ok(Map.of("message", "Judge votes reset"));
}
```

- [ ] **Step 4: Add champion-reveal endpoint**

Add after the revote endpoint (before the closing brace of the class):
```java
@PostMapping("/champion-reveal")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> championReveal(@RequestBody ChampionRevealDto dto){
    battleService.broadcastChampionReveal(dto);
    return ResponseEntity.ok(Map.of("message", "Champion reveal broadcast"));
}
```

- [ ] **Step 5: Compile + run full test suite**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw clean test -q 2>&1 | tail -15
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat(battle): update score endpoint for isFinal; add revote and champion-reveal endpoints"
```

---

## Task 5: Frontend API — new functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Update setBattleScore to accept isFinal**

Find the existing `setBattleScore` function:
```js
export const setBattleScore = async() =>{
  try{
    return await fetch(`${domain}/api/v1/battle/score`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    })
  }catch(e){
    console.log(e)
    return null
  }
}
```

Replace with:
```js
export const setBattleScore = async (isFinal = false) => {
  try {
    return await fetch(`${domain}/api/v1/battle/score`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ isFinal })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}
```

- [ ] **Step 2: Add resetBattleVotes, revealChampion, dismissChampionReveal**

Add after the `setBattleScore` function:

```js
export const resetBattleVotes = async () => {
  try {
    return await fetch(`${domain}/api/v1/battle/revote`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' }
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const revealChampion = async (genreName, championName) => {
  try {
    return await fetch(`${domain}/api/v1/battle/champion-reveal`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ genreName, championName, dismiss: false })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const dismissChampionReveal = async () => {
  try {
    return await fetch(`${domain}/api/v1/battle/champion-reveal`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ dismiss: true })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat(api): add setBattleScore isFinal, resetBattleVotes, revealChampion, dismissChampionReveal"
```

---

## Task 6: BattleControl — final tie warning + revote

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Add imports and new refs**

In `BattleControl.vue`, add `resetBattleVotes` to the existing import from `@/utils/api`:
```js
import { addBattleJudge, battleJudgeVote, getBattleJudges, getBattlePhase, getOverlayConfig,
         getParticipantScore, getPickupCrews, removeBattleJudge, resetBattleVotes,
         setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig,
         updateSmokeList, uploadImage } from '@/utils/api'
```

Add two new refs after `const showResetConfirm = ref(false)`:
```js
const finalTieBlocked = ref(false)  // true when final match returned a tie
```

- [ ] **Step 2: Update submitGetScore to pass isFinal and handle 409**

Find the `submitGetScore` function. It currently calls `const res = await setBattleScore()`. Update the standard (non-smoke) branch:

Find this section inside `submitGetScore`:
```js
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  if (left === "" || right === "") return
  const res = await setBattleScore()
  const data = await res.json()
  currentWinner.value = Number(data.winner)
  if (data.winner === 1 || data.winner === 0) { setWinner(currentTop.value, currentRound.value, data.winner) }
```

Replace with:
```js
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  if (left === "" || right === "") return
  const isFinal = !isSmoke.value && currentTop.value === 'Top2'
  const res = await setBattleScore(isFinal)
  if (res?.status === 409) {
    // Final match tie — block progression, require revote
    finalTieBlocked.value = true
    return
  }
  const data = await res.json()
  finalTieBlocked.value = false
  currentWinner.value = Number(data.winner)
  if (data.winner === 1 || data.winner === 0) { setWinner(currentTop.value, currentRound.value, data.winner) }
```

- [ ] **Step 3: Add startRevote function**

Add after `submitGetScore`:
```js
const startRevote = async () => {
  await resetBattleVotes()
  finalTieBlocked.value = false
  currentWinner.value = -2
}
```

- [ ] **Step 4: Add tie warning + revote button to template**

Find the winner announcement div in the template (around line 976):
```html
      <!-- Winner announcement -->
      <div
        class="px-4 py-3 rounded-xl text-center text-sm font-semibold mb-4"
```

Add the tie warning block immediately **before** this div:
```html
      <!-- Final tie warning -->
      <div
        v-if="finalTieBlocked"
        class="px-4 py-3 rounded-xl text-sm font-semibold mb-4 flex items-center justify-between gap-3
               bg-amber-500/10 border border-amber-500/40 text-amber-400"
      >
        <span><i class="pi pi-exclamation-triangle mr-2"></i>TIE in Final — Revote required</span>
        <button
          @click="startRevote"
          class="flex-shrink-0 px-3 py-1.5 rounded-lg bg-amber-500/20 border border-amber-500/40
                 text-amber-400 text-xs font-bold hover:bg-amber-500/30 transition-all"
        >START REVOTE</button>
      </div>
```

- [ ] **Step 5: Frontend build check**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -20
```
Expected: build completes with no errors.

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat(battle-control): final tie detection and revote UI"
```

---

## Task 7: BattleControl — champion reveal button

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Add imports and state**

Add `revealChampion` and `dismissChampionReveal` to the api import line (extending what was added in Task 6):
```js
import { ..., revealChampion, dismissChampionReveal } from '@/utils/api'
```

Add a new ref after `finalTieBlocked`:
```js
const revealActive = ref(false)
```

- [ ] **Step 2: Add computed for current genre's champion**

Add after `uniqueGenres` computed:
```js
const currentGenreChampion = computed(() => {
  if (isSmoke.value) return null
  return rounds.value['Top2']?.[0]?.[2] ?? null
})
```

- [ ] **Step 3: Add revealChampionForGenre and dismissReveal functions**

Add after `startRevote`:
```js
const revealChampionForGenre = async () => {
  if (!currentGenreChampion.value) return
  await revealChampion(selectedGenre.value, currentGenreChampion.value)
  revealActive.value = true
}

const dismissReveal = async () => {
  await dismissChampionReveal()
  revealActive.value = false
}
```

- [ ] **Step 4: Add REVEAL CHAMPION / DISMISS button to template**

Find the Reset Bracket button in the action buttons section (around line 1081):
```html
        <button
          @click="showResetConfirm = true"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-red-200 bg-surface-800
                 text-sm font-semibold text-red-400 hover:bg-red-950 transition-all"
        >
```

Add the champion reveal button **immediately before** the Reset Bracket button:
```html
        <!-- Champion Reveal -->
        <button
          v-if="currentGenreChampion && !revealActive"
          @click="revealChampionForGenre"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-amber-500/40 bg-amber-500/10
                 text-sm font-semibold text-amber-400 hover:bg-amber-500/20 transition-all"
        >
          <i class="pi pi-star text-xs"></i>
          Reveal Champion
        </button>
        <button
          v-if="revealActive"
          @click="dismissReveal"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
                 text-sm font-semibold text-content-secondary hover:bg-surface-700 transition-all"
        >
          <i class="pi pi-times text-xs"></i>
          Dismiss Reveal
        </button>
```

- [ ] **Step 5: Reset revealActive when genre changes**

In the `watch(selectedGenre, ...)` handler, add `revealActive.value = false` at the start of the handler. Find:
```js
watch(selectedGenre, async (newVal) => {
```

Add inside the handler (at the very top of the callback body):
```js
  revealActive.value = false
```

- [ ] **Step 6: Build check**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat(battle-control): add Reveal Champion / Dismiss button per genre"
```

---

## Task 8: BracketVisualization — CSS cleanup (cyan → white/silver)

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`

- [ ] **Step 1: Update CSS custom properties block**

Find the `.bracket-root` vars block (lines 501–518):
```css
.bracket-root {
  --c-bg:         #060818;
  --c-surface:    rgba(255,255,255,0.045);
  --c-border:     rgba(255,255,255,0.07);
  --c-border-act: #06b6d4;
  --c-text:       #f0f0f0;
  --c-muted:      rgba(255,255,255,0.28);
  --c-accent:     #06b6d4;
  --c-win-bg:     rgba(6,182,212,0.13);
  --c-lose-text:  rgba(255,255,255,0.16);
  --c-connector:  rgba(6,182,212,0.2);
  --c-champ-bg:   rgba(245,158,11,0.1);
  --c-champ:      #f59e0b;
  --col-gap:      24px;
  --header-h:     52px;
  --slot-h:       38px;
}
```

Replace with:
```css
.bracket-root {
  --c-bg:         #060818;
  --c-surface:    rgba(255,255,255,0.04);
  --c-border:     rgba(255,255,255,0.08);
  --c-border-act: rgba(255,255,255,0.35);
  --c-text:       #f0f0f0;
  --c-muted:      rgba(255,255,255,0.28);
  --c-accent:     rgba(255,255,255,0.55);
  --c-win-bg:     rgba(245,158,11,0.15);
  --c-lose-text:  rgba(255,255,255,0.16);
  --c-connector:  rgba(255,255,255,0.12);
  --c-champ-bg:   rgba(245,158,11,0.1);
  --c-champ:      #f59e0b;
  --left-color:   #dc2626;
  --right-color:  #2563eb;
  --col-gap:      24px;
  --header-h:     52px;
  --slot-h:       38px;
}
```

- [ ] **Step 2: Update bracket-root background gradient (remove cyan radial)**

Find:
```css
  background:
    radial-gradient(ellipse 50vw 55vh at 5% 8%,  rgba(6,182,212,0.07)  0%, transparent 70%),
    radial-gradient(ellipse 45vw 50vh at 95% 92%, rgba(245,158,11,0.05) 0%, transparent 70%),
    #060818;
```

Replace with:
```css
  background:
    radial-gradient(ellipse 50vw 55vh at 5% 8%,  rgba(255,255,255,0.04) 0%, transparent 70%),
    radial-gradient(ellipse 45vw 50vh at 95% 92%, rgba(245,158,11,0.06) 0%, transparent 70%),
    #060818;
```

- [ ] **Step 3: Update header border and active-pill (remove cyan)**

Find:
```css
  border-bottom: 1px solid rgba(6,182,212,0.12);
```
Replace with:
```css
  border-bottom: 1px solid rgba(255,255,255,0.07);
```

Find the `.brand-dot` rule:
```css
.brand-dot {
  ...
  background: var(--c-accent);
  box-shadow: 0 0 10px var(--c-accent), 0 0 22px rgba(6,182,212,0.45);
```
Replace `box-shadow` line with:
```css
  box-shadow: 0 0 10px rgba(255,255,255,0.5), 0 0 22px rgba(255,255,255,0.2);
```

Find `.active-pill`:
```css
  color: var(--c-accent);
  background: rgba(6,182,212,0.08);
  border: 1px solid rgba(6,182,212,0.22);
```
Replace with:
```css
  color: rgba(255,255,255,0.75);
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.18);
```

Find `.pill-dot`:
```css
  background: var(--c-accent); box-shadow: 0 0 6px var(--c-accent);
```
Replace with:
```css
  background: rgba(255,255,255,0.7); box-shadow: 0 0 6px rgba(255,255,255,0.5);
```

- [ ] **Step 4: Update smoke slot active color**

Find:
```css
.smoke-active { background: var(--c-win-bg); border-color: rgba(6,182,212,0.3); }
```
Replace with:
```css
.smoke-active { background: rgba(255,255,255,0.07); border-color: rgba(255,255,255,0.25); }
```

Find `.smoke-score` and `.smoke-badge`:
```css
.smoke-score  { ... color: var(--c-accent); ... }
.smoke-badge  { ... background: var(--c-win-bg); color: var(--c-accent); ... }
```
Replace `var(--c-accent)` usages with `rgba(255,255,255,0.65)` in both rules.

- [ ] **Step 5: Update .round-label, .match-active connector, .final-match active, and .final-vs**

`.round-label`: find `color: var(--c-accent);` → replace with `color: rgba(255,255,255,0.38);`

`.match-active.pair-group::after`: find `border-color: rgba(6,182,212,0.45);` → replace with `border-color: rgba(255,255,255,0.3);`

`.final-match.match-active .battler-slot`: find `border-color: rgba(6,182,212,0.42); box-shadow: 0 0 0 1px rgba(6,182,212,0.12), 0 0 16px rgba(6,182,212,0.18);` → replace with `border-color: rgba(255,255,255,0.3); box-shadow: 0 0 0 1px rgba(255,255,255,0.08), 0 0 16px rgba(255,255,255,0.1);`

`.final-vs`: find `color: var(--c-accent);` → replace with `color: rgba(255,255,255,0.3);`

- [ ] **Step 6: Update slot winner/active states and anim-ball**

Find `.match-active .battler-slot`:
```css
.match-active .battler-slot {
  background: var(--c-win-bg);
  border-color: rgba(6,182,212,0.38);
  box-shadow: 0 0 0 1px rgba(6,182,212,0.1), 0 0 14px rgba(6,182,212,0.14);
}
```
Replace with (active match slots now use color theming — see Task 9):
```css
.match-active .battler-slot {
  background: rgba(255,255,255,0.05);
  border-color: rgba(255,255,255,0.18);
  box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 0 14px rgba(255,255,255,0.08);
}
```

Find `.slot-winner` (still cyan):
```css
.slot-winner {
  background: rgba(6,182,212,0.17) !important;
  border-color: rgba(6,182,212,0.48) !important;
  box-shadow: 0 0 16px rgba(6,182,212,0.28) !important;
}
.slot-winner .battler-name { color: var(--c-accent); }
```
Replace with:
```css
.slot-winner {
  background: rgba(245,158,11,0.15) !important;
  border-color: rgba(245,158,11,0.45) !important;
  box-shadow: 0 0 18px rgba(245,158,11,0.25) !important;
}
.slot-winner .battler-name { color: #fde68a; }
```

Find `.slot-loser .battler-name`:
```css
.slot-loser  .battler-name { color: var(--c-lose-text); text-decoration: line-through; }
```
Replace with (remove strikethrough, just dim):
```css
.slot-loser  .battler-name { color: var(--c-lose-text); }
```

Also update `.slot-loser` opacity:
Find `.slot-loser`:
```css
.slot-loser  { background: rgba(255,255,255,0.02) !important; border-color: rgba(255,255,255,0.03) !important; }
```
Replace with:
```css
.slot-loser { background: rgba(255,255,255,0.02) !important; border-color: rgba(255,255,255,0.03) !important; opacity: 0.42; }
```

Find `.slot-glow` keyframes — replace cyan glow with gold glow:
```css
@keyframes slotGlow {
  0%   { background: var(--c-surface);       border-color: var(--c-border);  box-shadow: none; }
  20%  { background: rgba(6,182,212,0.30);   border-color: var(--c-accent);  box-shadow: 0 0 0 2px rgba(6,182,212,0.38), 0 0 22px rgba(6,182,212,0.75), 0 0 44px rgba(6,182,212,0.35); }
  70%  { background: rgba(6,182,212,0.20);   border-color: var(--c-accent);  box-shadow: 0 0 0 2px rgba(6,182,212,0.28), 0 0 16px rgba(6,182,212,0.58), 0 0 32px rgba(6,182,212,0.22); }
  100% { background: var(--c-surface);       border-color: var(--c-border);  box-shadow: none; }
}
.slot-glow .battler-name { color: var(--c-accent); opacity: 1; }
```
Replace with:
```css
@keyframes slotGlow {
  0%   { background: var(--c-surface);        border-color: var(--c-border);             box-shadow: none; }
  20%  { background: rgba(245,158,11,0.28);   border-color: rgba(245,158,11,0.8);        box-shadow: 0 0 0 2px rgba(245,158,11,0.35), 0 0 22px rgba(245,158,11,0.7), 0 0 44px rgba(245,158,11,0.3); }
  70%  { background: rgba(245,158,11,0.18);   border-color: rgba(245,158,11,0.55);       box-shadow: 0 0 0 2px rgba(245,158,11,0.25), 0 0 16px rgba(245,158,11,0.5), 0 0 32px rgba(245,158,11,0.18); }
  100% { background: var(--c-surface);        border-color: var(--c-border);             box-shadow: none; }
}
.slot-glow .battler-name { color: #fde68a; opacity: 1; }
```

Find `:global(.anim-ball)`:
```css
:global(.anim-ball) {
  ...
  background: radial-gradient(circle, #ffffff 0%, #06b6d4 55%, transparent 100%);
  box-shadow: 0 0 6px #06b6d4, 0 0 14px #06b6d4, 0 0 28px rgba(6,182,212,0.6);
```
Replace with:
```css
:global(.anim-ball) {
  position: fixed;
  width: 12px; height: 12px; border-radius: 50%;
  background: radial-gradient(circle, #ffffff 0%, rgba(245,158,11,0.9) 55%, transparent 100%);
  box-shadow: 0 0 6px rgba(245,158,11,0.9), 0 0 14px rgba(245,158,11,0.7), 0 0 28px rgba(245,158,11,0.4);
  pointer-events: none; z-index: 9999;
}
```

- [ ] **Step 7: Update ticker bar (remove cyan)**

Find `.ticker-bar`:
```css
  border-top: 1px solid rgba(6,182,212,0.1);
```
Replace with:
```css
  border-top: 1px solid rgba(255,255,255,0.07);
```

Find `.ticker-label`:
```css
  background: var(--c-accent);
  ...
  color: #030610;
```
Replace with:
```css
  background: rgba(255,255,255,0.9);
  ...
  color: #060818;
```

Find `.ticker-now .ticker-tag` and `.ticker-now .ticker-text`:
```css
.ticker-now   .ticker-tag  { color: var(--c-accent); opacity: 1; }
...
.ticker-now   .ticker-text { color: var(--c-accent); }
```
Replace both `var(--c-accent)` with `rgba(255,255,255,0.85)`.

- [ ] **Step 8: Build check**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue
git commit -m "style(bracket): remove all cyan, replace with white/silver neutral"
```

---

## Task 9: BracketVisualization — overlay config + slot color theming + WIN badge

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`

- [ ] **Step 1: Add overlayConfig ref and getOverlayConfig import**

In `<script setup>`, update the import line:
```js
import { getBracketState, getOverlayConfig } from '@/utils/api'
```

Add a new ref after `const bracketState = ref(null)`:
```js
const overlayConfig = ref({ leftColor: '#dc2626', rightColor: '#2563eb' })
```

- [ ] **Step 2: Subscribe to overlay-config in onMounted**

Inside the `wsClient.onConnect` callback, add a new subscription after the existing ones:
```js
    wsClient.subscribe('/topic/battle/overlay-config', (msg) => {
      const cfg = JSON.parse(msg.body)
      overlayConfig.value = cfg
    })
```

Also in `onMounted`, after `const state = await getBracketState()`, fetch the initial config:
```js
  const cfg = await getOverlayConfig()
  if (cfg) overlayConfig.value = cfg
```

- [ ] **Step 3: Bind CSS vars to bracket-root in template**

Find the root div in the template:
```html
  <div class="bracket-root">
```

Replace with:
```html
  <div class="bracket-root" :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor }">
```

- [ ] **Step 4: Update slotClass computed to add active-left / active-right**

Find the existing `slotClass` function:
```js
const slotClass = (match, slot) => ({
  'slot-winner': match[2] && match[2] === match[slot],
  'slot-loser':  match[2] && match[2] !== match[slot] && match[slot],
})
```

Replace with:
```js
const slotClass = (match, slot) => {
  if (match[2]) {
    if (match[2] === match[slot]) return 'slot-winner'
    if (match[slot]) return 'slot-loser'
    return ''
  }
  if (isActiveMatch(match) && match[slot]) {
    return slot === 0 ? 'slot-active-left' : 'slot-active-right'
  }
  return ''
}
```

- [ ] **Step 5: Add slot-active-left / slot-active-right CSS classes**

Find the existing `.match-active .battler-slot` rule (updated in Task 8) and add these two new rules immediately after it:

```css
.slot-active-left {
  background: color-mix(in srgb, var(--left-color) 18%, transparent) !important;
  border-color: color-mix(in srgb, var(--left-color) 50%, transparent) !important;
  box-shadow: 0 0 16px color-mix(in srgb, var(--left-color) 28%, transparent) !important;
}
.slot-active-left .battler-name {
  color: color-mix(in srgb, var(--left-color) 80%, #fff) !important;
}
.slot-active-right {
  background: color-mix(in srgb, var(--right-color) 18%, transparent) !important;
  border-color: color-mix(in srgb, var(--right-color) 50%, transparent) !important;
  box-shadow: 0 0 16px color-mix(in srgb, var(--right-color) 28%, transparent) !important;
}
.slot-active-right .battler-name {
  color: color-mix(in srgb, var(--right-color) 80%, #fff) !important;
}
```

- [ ] **Step 6: Replace crown icon with WIN badge in template**

The crown icon `<i v-if="match[2] === match[slot] && match[slot]" class="pi pi-crown crown-icon"></i>` appears in multiple places. Replace every occurrence with the WIN badge span.

There are 6 locations (left half slot 0, slot 1; center final slot 0, slot 1; right half slot 0, slot 1). For every instance of:
```html
                  <i v-if="match[2] === match[0] && match[0]" class="pi pi-crown crown-icon"></i>
```
Replace with:
```html
                  <span v-if="match[2] === match[0] && match[0]" class="win-badge">WIN</span>
```

And every instance of:
```html
                  <i v-if="match[2] === match[1] && match[1]" class="pi pi-crown crown-icon"></i>
```
Replace with:
```html
                  <span v-if="match[2] === match[1] && match[1]" class="win-badge">WIN</span>
```

Do the same for the center column final match slot 0 and slot 1 (same pattern).

- [ ] **Step 7: Add WIN badge CSS + remove old crown CSS**

Find and remove:
```css
.crown-icon {
  font-size: 11px; color: var(--c-champ); flex-shrink: 0;
  display: inline-block; transform: skewX(4deg);
}
```

Add in its place:
```css
.win-badge {
  display: inline-flex; align-items: center; justify-content: center;
  transform: skewX(5deg); flex-shrink: 0;
  background: rgba(245,158,11,0.22); border: 1px solid rgba(245,158,11,0.5);
  color: #fbbf24; font-size: 8px; font-weight: 900; font-family: 'Inter', sans-serif;
  letter-spacing: 0.12em; padding: 1px 5px; border-radius: 2px;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
}
```

Also remove `.champ-icon` and `.champion-card` and `.champ-name` styles since the champion card below the final is no longer needed (the champion reveal overlay replaces it). The `v-if="champion"` champion card in the template at line 411 can also be removed:
```html
          <div v-if="champion" class="champion-card">
            <i class="pi pi-crown champ-icon"></i>
            <span class="champ-name">{{ champion }}</span>
          </div>
```
Remove this block entirely.

- [ ] **Step 8: Build check**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue
git commit -m "feat(bracket): overlay config subscription, slot color theming, WIN badge"
```

---

## Task 10: BracketVisualization — rewrite advance animation

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`

The existing animation uses a travelling ball. We replace it with: ring burst on source slot → lightning streak across gap → destination slot ignites.

- [ ] **Step 1: Replace the travelBall function**

Find the `travelBall` function (lines 127–168) and the `ballVisible`/`ballOrigin` refs and the `anim-ball` Teleport at the bottom of the template. Replace all of this.

First, **remove** these refs:
```js
const ballVisible     = ref(false)
const ballOrigin      = ref({ x: 0, y: 0 })
```

Replace with:
```js
const animLayers = ref([])   // absolutely-positioned animation elements rendered over bracket
```

**Remove** the existing `travelBall` function entirely (lines 127–168).

**Add** the new `travelBall` function in its place:

```js
async function travelBall(winnerKey, destKey) {
  const winEl  = slotEls[winnerKey]
  const destEl = destKey ? slotEls[destKey] : null
  if (!winEl) return

  const bracketEl = document.querySelector('.bracket-area')
  if (!bracketEl) return
  const bRect  = bracketEl.getBoundingClientRect()
  const wRect  = winEl.getBoundingClientRect()

  // ── Phase 1: Ring burst on source slot ──────────────────────────
  const ring = document.createElement('div')
  ring.style.cssText = `
    position: absolute;
    left: ${wRect.left - bRect.left}px;
    top:  ${wRect.top  - bRect.top}px;
    width:  ${wRect.width}px;
    height: ${wRect.height}px;
    border: 2px solid rgba(245,158,11,0.9);
    border-radius: 1px;
    box-shadow: 0 0 20px rgba(245,158,11,0.6);
    pointer-events: none;
    z-index: 20;
    transform: skewX(-4deg);
  `
  bracketEl.style.position = 'relative'
  bracketEl.appendChild(ring)

  await ring.animate(
    [{ transform: 'skewX(-4deg) scale(1)', opacity: 1 },
     { transform: 'skewX(-4deg) scale(1.65)', opacity: 0 }],
    { duration: 380, easing: 'ease-out', fill: 'forwards' }
  ).finished
  ring.remove()

  if (!destEl) return

  const dRect = destEl.getBoundingClientRect()

  // ── Phase 2: Lightning streak ────────────────────────────────────
  const srcCenterX = wRect.left  + wRect.width / 2  - bRect.left
  const srcCenterY = wRect.top   + wRect.height / 2 - bRect.top
  const dstCenterX = dRect.left  + dRect.width / 2  - bRect.left
  const dstCenterY = dRect.top   + dRect.height / 2 - bRect.top

  const dx = dstCenterX - srcCenterX
  const dy = dstCenterY - srcCenterY
  const length = Math.sqrt(dx * dx + dy * dy)
  const angle  = Math.atan2(dy, dx) * (180 / Math.PI)

  const streak = document.createElement('div')
  streak.style.cssText = `
    position: absolute;
    left:   ${srcCenterX}px;
    top:    ${srcCenterY - 2}px;
    width:  0;
    height: 3px;
    transform-origin: left center;
    transform: rotate(${angle}deg);
    background: linear-gradient(90deg, rgba(245,158,11,0.2) 0%, rgba(245,158,11,0.95) 55%, #fff 100%);
    border-radius: 2px;
    box-shadow: 0 0 8px rgba(245,158,11,0.7);
    pointer-events: none;
    z-index: 20;
  `
  bracketEl.appendChild(streak)

  await streak.animate(
    [{ width: '0px' }, { width: `${length}px` }],
    { duration: 200, easing: 'ease-in', fill: 'forwards' }
  ).finished

  await streak.animate(
    [{ opacity: 1 }, { opacity: 0 }],
    { duration: 140, easing: 'linear', fill: 'forwards' }
  ).finished
  streak.remove()

  // ── Phase 3: Destination slot ignites ───────────────────────────
  destEl.style.transition = 'none'
  destEl.style.boxShadow  = '0 0 40px rgba(245,158,11,0.9), 0 0 80px rgba(245,158,11,0.4)'
  await sleep(60)
  destEl.style.transition = 'box-shadow 0.45s ease-out'
  destEl.style.boxShadow  = ''
  await sleep(450)
  destEl.style.transition = ''
}
```

- [ ] **Step 2: Remove the Teleport anim-ball from template**

Find and remove from the template (below the closing `</div>` of `bracket-root`):
```html
  <!-- ── Travelling ball (outside bracket-root to avoid overflow clipping) ── -->
  <Teleport to="body">
    <div
      v-if="ballVisible"
      class="anim-ball"
      :style="{ left: ballOrigin.x + 'px', top: ballOrigin.y + 'px' }"
    ></div>
  </Teleport>
```

- [ ] **Step 3: Remove the :global(.anim-ball) CSS**

Find and remove:
```css
/* ── Travelling ball ─────────────────────────────── */
:global(.anim-ball) {
  position: fixed;
  width: 12px; height: 12px; border-radius: 50%;
  background: radial-gradient(circle, #ffffff 0%, rgba(245,158,11,0.9) 55%, transparent 100%);
  box-shadow: 0 0 6px rgba(245,158,11,0.9), 0 0 14px rgba(245,158,11,0.7), 0 0 28px rgba(245,158,11,0.4);
  pointer-events: none; z-index: 9999;
}
```

- [ ] **Step 4: Build check**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue
git commit -m "feat(bracket): rewrite advance animation (ring burst + lightning streak)"
```

---

## Task 11: Champion reveal overlay — BracketVisualization + BattleOverlay

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

### BracketVisualization

- [ ] **Step 1: Add championReveal ref and WebSocket subscription**

In `<script setup>`, add after `overlayConfig`:
```js
const championReveal = ref(null)   // null = no reveal; { genreName, championName } = showing
```

In `wsClient.onConnect`, add subscription:
```js
    wsClient.subscribe('/topic/battle/champion-reveal', (msg) => {
      const data = JSON.parse(msg.body)
      if (data.dismiss) {
        championReveal.value = null
      } else {
        championReveal.value = { genreName: data.genreName, championName: data.championName }
      }
    })
```

- [ ] **Step 2: Add champion reveal overlay to template**

Inside `.bracket-root`, add immediately after the `<div class="scanlines">` line:
```html
    <!-- ── Champion Reveal Overlay ──────────────────── -->
    <Transition name="champ-reveal">
      <div v-if="championReveal" class="champ-overlay">
        <div class="champ-overlay-bg"></div>
        <div class="champ-overlay-content">
          <div class="champ-genre-tag">{{ championReveal.genreName }} · Final</div>
          <div class="champ-label">Champion</div>
          <div class="champ-name-slam">{{ championReveal.championName }}</div>
          <div class="champ-gold-bar"></div>
        </div>
      </div>
    </Transition>
```

- [ ] **Step 3: Add champion reveal CSS**

Add to the `<style scoped>` section (before the `@keyframes` block):
```css
/* ── Champion Reveal Overlay ────────────────────────── */
.champ-overlay {
  position: absolute; inset: 0; z-index: 50;
  display: flex; align-items: center; justify-content: center;
  background: #060818;
}
.champ-overlay-bg {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(ellipse 60% 50% at 50% 55%, rgba(245,158,11,0.14) 0%, transparent 68%);
}
.champ-overlay-content {
  position: relative; z-index: 1;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  text-align: center;
}
.champ-genre-tag {
  font-family: 'Anton SC', sans-serif; font-size: 9px;
  letter-spacing: 0.45em; text-transform: uppercase;
  color: rgba(255,255,255,0.3);
}
.champ-label {
  font-family: 'Anton SC', sans-serif; font-size: 11px;
  letter-spacing: 0.5em; text-transform: uppercase;
  color: rgba(245,158,11,0.85);
}
.champ-name-slam {
  font-family: 'Anton SC', sans-serif; font-size: 58px;
  letter-spacing: 0.07em; text-transform: uppercase; line-height: 1;
  color: #fff;
  text-shadow: 0 0 40px rgba(245,158,11,0.65), 0 0 80px rgba(245,158,11,0.3);
}
.champ-gold-bar {
  width: 180px; height: 2px; margin-top: 6px;
  background: linear-gradient(90deg, transparent, rgba(245,158,11,0.8), transparent);
}

/* Transition */
.champ-reveal-enter-active { transition: opacity 0.3s ease; }
.champ-reveal-leave-active { transition: opacity 0.25s ease; }
.champ-reveal-enter-from,
.champ-reveal-leave-to    { opacity: 0; }

.champ-reveal-enter-active .champ-name-slam {
  animation: champNameSlam 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) 0.35s both;
}
.champ-reveal-enter-active .champ-label {
  animation: champFadeUp 0.4s ease 0.2s both;
}
.champ-reveal-enter-active .champ-genre-tag {
  animation: champFadeUp 0.4s ease 0.08s both;
}
.champ-reveal-enter-active .champ-gold-bar {
  animation: champBarExpand 0.6s ease 0.65s both;
}

@keyframes champNameSlam {
  from { opacity: 0; transform: scale(0.72) translateY(18px); }
  to   { opacity: 1; transform: scale(1)    translateY(0); }
}
@keyframes champFadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes champBarExpand {
  from { width: 0; opacity: 0; }
  to   { width: 180px; opacity: 1; }
}
```

### BattleOverlay

- [ ] **Step 4: Add championReveal ref and subscription to BattleOverlay**

In `BattleOverlay.vue`, in `<script setup>`, add after `const glitching = ref(false)`:
```js
const championReveal = ref(null)   // null | { genreName, championName }
```

In the WebSocket subscription setup area (wherever other subscriptions are added in `onMounted`), add:
```js
    subscribeToChannel(wsClient, '/topic/battle/champion-reveal', (msg) => {
      if (msg.dismiss) {
        championReveal.value = null
      } else {
        championReveal.value = { genreName: msg.genreName, championName: msg.championName }
      }
    })
```

Note: BattleOverlay uses `subscribeToChannel` (not `wsClient.subscribe` directly). The message object `msg` in `subscribeToChannel` callbacks may already be parsed — check the websocket utility to confirm. If it's a raw STOMP frame, use `JSON.parse(msg.body)` instead of `msg` directly. Check: `grep -n 'subscribeToChannel' /Users/bennylim/Documents/BES/BES-frontend/src/utils/websocket.js` before implementing to confirm the callback signature.

- [ ] **Step 5: Add champion reveal overlay to BattleOverlay template**

Find the root container div in BattleOverlay (the outermost `<div>` in `<template>`). Add the overlay as the first child:
```html
    <!-- ── Champion Reveal Overlay ─────────────────── -->
    <Transition name="champ-reveal">
      <div v-if="championReveal" class="champ-overlay">
        <div class="champ-overlay-bg"></div>
        <div class="champ-overlay-content">
          <div class="champ-genre-tag">{{ championReveal.genreName }} · Final</div>
          <div class="champ-label">Champion</div>
          <div class="champ-name-slam">{{ championReveal.championName }}</div>
          <div class="champ-gold-bar"></div>
        </div>
      </div>
    </Transition>
```

- [ ] **Step 6: Add champion reveal CSS to BattleOverlay**

Add the same CSS block from Step 3 to `BattleOverlay.vue`'s `<style scoped>`, but with a larger name font size to suit the full-screen overlay:

Use `font-size: 15vw` for `.champ-name-slam` instead of `58px`, so it scales with screen width on the stream.

The rest of the CSS is identical to Step 3.

- [ ] **Step 7: Build check**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue \
        BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat(bracket): champion reveal overlay on BracketVisualization and BattleOverlay"
```

---

## Task 12: Docker rebuild + smoke test

**Files:** None (build verification)

- [ ] **Step 1: Backend compile + test**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw clean test -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 2: Frontend production build**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run build 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Rebuild affected Docker containers**

```bash
cd /Users/bennylim/Documents/BES
docker compose stop frontend backend
docker compose rm -f frontend backend
docker compose build --no-cache frontend backend
docker compose up -d
```

- [ ] **Step 4: Verify containers are running**

```bash
docker compose ps
```
Expected: all services `Up`.

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:80
```
Expected: 200.

```bash
curl -s http://localhost:5050/actuator/health
```
Expected: `{"status":"UP"}`.

- [ ] **Step 5: Manual smoke tests**

Test each feature:

1. **Color theming** — Open BattleControl, set overlay config (e.g. orange left, blue right), start a battle. Open BracketVisualization — confirm active slots show the configured colors.

2. **Winner badge** — Reveal a result. Confirm the winning slot shows a gold WIN badge (not a crown icon). Confirm the losing slot is dimmed with no strikethrough.

3. **Advance animation** — After a result, confirm: ring expands from winner slot, lightning streak travels to destination, destination slot ignites.

4. **Final tie block** — Set up a final match, set judges to equal votes (tie). Click Get Score. Confirm BattleControl shows the amber "TIE in Final — Revote required" warning and a START REVOTE button. Confirm the overlay/bracket didn't change. Click START REVOTE — confirm warning clears and voting is open again.

5. **Champion reveal** — Complete a final match with a winner. In BattleControl, confirm "Reveal Champion" button appears. Click it — confirm BracketVisualization and BattleOverlay both show the full-screen name slam. Click Dismiss — confirm overlay clears and bracket is visible with WIN badge. Re-click Reveal Champion to confirm it can be retriggered.

---

## Task 13: Final commit

- [ ] **Step 1: Run full backend test suite one last time**

```bash
cd /Users/bennylim/Documents/BES/BES && ./mvnw test -q 2>&1 | tail -10
```
Expected: all tests pass.

- [ ] **Step 2: Final commit if any loose files remain**

```bash
git status
```
If any modified files remain unstaged, stage and commit them with an appropriate message.
