# BattleOverlay Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live overlay-config WebSocket channel (colors + image toggle), overhaul the 1v1 overlay UI to use custom team colors and angular design, and replace all animations with hard-impact underground-aesthetic keyframes.

**Architecture:** A new `overlay-config` topic carries `{ showImages, leftColor, rightColor }` from BattleControl → BattleOverlay via WebSocket, persisted in-memory in BattleService (same pattern as `battlePhase`). BattleOverlay.vue is fully rewritten: both panels use left-only positioning so winner centering works from either side, votes are hidden until score is revealed, and every animation uses hard-attack bezier curves. Smoke mode (`isSmoke=true`) is untouched throughout.

**Tech Stack:** Spring Boot (BattleService/BattleController), Flyway migration, Vue 3 `<script setup>`, STOMP WebSocket, CSS keyframe animations

---

### Task 1: DB Migration V17

**Files:**
- Create: `BES/src/main/resources/db/migration/V17__add_overlay_config.sql`

- [ ] **Step 1: Create migration file**

```sql
ALTER TABLE event
  ADD COLUMN overlay_config JSONB DEFAULT '{"showImages": true, "leftColor": "#dc2626", "rightColor": "#2563eb"}';
```

- [ ] **Step 2: Verify no existing V17 file conflicts**

Run: `ls BES/src/main/resources/db/migration/`
Expected: `V16__add_pickup_crew.sql` is the latest; no V17 exists.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V17__add_overlay_config.sql
git commit -m "feat: add overlay_config JSONB column to event table (V17)"
```

---

### Task 2: DTO — SetOverlayConfigDto

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/battle/SetOverlayConfigDto.java`

- [ ] **Step 1: Create the DTO**

```java
package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SetOverlayConfigDto {

    @NotNull
    private Boolean showImages;

    @NotNull
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "leftColor must be a valid 6-digit hex color")
    private String leftColor;

    @NotNull
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "rightColor must be a valid 6-digit hex color")
    private String rightColor;

    public Boolean isShowImages() { return showImages; }
    public String getLeftColor()  { return leftColor; }
    public String getRightColor() { return rightColor; }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/battle/SetOverlayConfigDto.java
git commit -m "feat: add SetOverlayConfigDto with hex color validation"
```

---

### Task 3: BattleService — overlay config in-memory

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`

The service already holds `battlePhase`, `currentPair`, etc. in memory. `overlayConfig` follows the same pattern.

- [ ] **Step 1: Add field and import to BattleService**

At the top of the class, after the existing `private String battlePhase = "IDLE";` field (line 36), add:

```java
import java.util.HashMap;
```

Add to imports section at top of file (after existing imports).

Then add field after `private String battlePhase = "IDLE";`:

```java
private Map<String, Object> overlayConfig = new HashMap<>(Map.of(
    "showImages", true,
    "leftColor",  "#dc2626",
    "rightColor", "#2563eb"
));
```

- [ ] **Step 2: Add getOverlayConfig and setOverlayConfigService methods**

Add these two methods after `getBattlePhase()` / `setBattlePhaseService()`:

```java
public Map<String, Object> getOverlayConfig() {
    return overlayConfig;
}

public void setOverlayConfigService(SetOverlayConfigDto dto) {
    overlayConfig = new HashMap<>();
    overlayConfig.put("showImages", dto.isShowImages());
    overlayConfig.put("leftColor",  dto.getLeftColor());
    overlayConfig.put("rightColor", dto.getRightColor());
    messagingTemplate.convertAndSend("/topic/battle/overlay-config", overlayConfig);
}
```

Add the import for `SetOverlayConfigDto` in the imports section:

```java
import com.example.BES.dtos.battle.SetOverlayConfigDto;
```

- [ ] **Step 3: Verify the file compiles**

Run: `cd BES && mvn compile -q`
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/BattleService.java
git commit -m "feat: add overlay config in-memory state and WS broadcast to BattleService"
```

---

### Task 4: BattleController — overlay config endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

- [ ] **Step 1: Add import for SetOverlayConfigDto**

In the imports section of `BattleController.java` (alongside existing `battle` dto imports):

```java
import com.example.BES.dtos.battle.SetOverlayConfigDto;
```

- [ ] **Step 2: Add two new endpoints**

Add after the existing `@PostMapping("/bracket")` endpoint (before the closing `}`):

```java
@GetMapping("/overlay-config")
public ResponseEntity<?> getOverlayConfig() {
    return ResponseEntity.ok(battleService.getOverlayConfig());
}

@PostMapping("/overlay-config")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setOverlayConfig(@Valid @RequestBody SetOverlayConfigDto dto) {
    battleService.setOverlayConfigService(dto);
    return ResponseEntity.ok(battleService.getOverlayConfig());
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd BES && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add GET/POST /api/v1/battle/overlay-config endpoints"
```

---

### Task 5: BattleService unit tests — overlay config

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/BattleServiceTest.java`

The existing test class uses `@ExtendWith(MockitoExtension.class)` with `@InjectMocks BattleService service`.

- [ ] **Step 1: Write the failing tests**

Add these test methods to `BattleServiceTest.java` (after the existing `setBattlePhase_setsVOTING` test):

```java
@Test
void getOverlayConfig_returnsDefaults() {
    Map<String, Object> config = service.getOverlayConfig();
    assertThat(config.get("showImages")).isEqualTo(true);
    assertThat(config.get("leftColor")).isEqualTo("#dc2626");
    assertThat(config.get("rightColor")).isEqualTo("#2563eb");
}

@Test
void setOverlayConfigService_updatesInMemoryState() {
    SetOverlayConfigDto dto = mock(SetOverlayConfigDto.class);
    when(dto.isShowImages()).thenReturn(false);
    when(dto.getLeftColor()).thenReturn("#ff0000");
    when(dto.getRightColor()).thenReturn("#0000ff");

    service.setOverlayConfigService(dto);

    Map<String, Object> config = service.getOverlayConfig();
    assertThat(config.get("showImages")).isEqualTo(false);
    assertThat(config.get("leftColor")).isEqualTo("#ff0000");
    assertThat(config.get("rightColor")).isEqualTo("#0000ff");
}

@Test
void setOverlayConfigService_broadcastsToWebSocket() {
    SetOverlayConfigDto dto = mock(SetOverlayConfigDto.class);
    when(dto.isShowImages()).thenReturn(true);
    when(dto.getLeftColor()).thenReturn("#aabbcc");
    when(dto.getRightColor()).thenReturn("#112233");

    service.setOverlayConfigService(dto);

    verify(messagingTemplate).convertAndSend(
        eq("/topic/battle/overlay-config"),
        any(Map.class)
    );
}
```

Add the missing import to the test file:

```java
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd BES && mvn test -Dtest=BattleServiceTest -q`
Expected: 3 new test failures (methods not implemented yet — but actually Task 3 already implemented them, so these should pass).

- [ ] **Step 3: Run tests to verify they pass**

Run: `cd BES && mvn test -Dtest=BattleServiceTest`
Expected: All tests PASS including the 3 new ones.

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/java/com/example/BES/services/BattleServiceTest.java
git commit -m "test: add overlay config unit tests for BattleService"
```

---

### Task 6: BattleControllerIntegrationTest — overlay config endpoints

**Files:**
- Modify: `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java`

The existing test class uses `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")` with a real `BattleService` bean and a mocked `SimpMessagingTemplate`.

- [ ] **Step 1: Write the failing tests**

Add these test methods to `BattleControllerIntegrationTest.java`:

```java
@Test
@WithMockUser
public void testGetOverlayConfig_returnsDefaults() throws Exception {
    mockMvc.perform(get("/api/v1/battle/overlay-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.showImages").value(true))
            .andExpect(jsonPath("$.leftColor").value("#dc2626"))
            .andExpect(jsonPath("$.rightColor").value("#2563eb"));
}

@Test
@WithMockUser(roles = "ORGANISER")
public void testSetOverlayConfig_updatesAndReturns() throws Exception {
    String body = "{\"showImages\":false,\"leftColor\":\"#ff0000\",\"rightColor\":\"#0000ff\"}";

    mockMvc.perform(post("/api/v1/battle/overlay-config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.showImages").value(false))
            .andExpect(jsonPath("$.leftColor").value("#ff0000"))
            .andExpect(jsonPath("$.rightColor").value("#0000ff"));
}

@Test
@WithMockUser(roles = "ORGANISER")
public void testSetOverlayConfig_rejectsInvalidHexColor() throws Exception {
    String body = "{\"showImages\":true,\"leftColor\":\"notacolor\",\"rightColor\":\"#2563eb\"}";

    mockMvc.perform(post("/api/v1/battle/overlay-config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isBadRequest());
}

@Test
public void testSetOverlayConfig_requiresAuth() throws Exception {
    String body = "{\"showImages\":true,\"leftColor\":\"#dc2626\",\"rightColor\":\"#2563eb\"}";

    mockMvc.perform(post("/api/v1/battle/overlay-config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is4xxClientError());
}
```

- [ ] **Step 2: Run the tests**

Run: `cd BES && mvn test -Dtest=BattleControllerIntegrationTest -q`
Expected: All tests PASS (endpoints were implemented in Task 4).

- [ ] **Step 3: Run full backend test suite to catch regressions**

Run: `cd BES && mvn test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java
git commit -m "test: add integration tests for overlay-config endpoints"
```

---

### Task 7: Frontend API — overlay config functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`
- Modify: `BES-frontend/src/utils/__tests__/api.test.js`

- [ ] **Step 1: Write the failing tests first**

Add to `BES-frontend/src/utils/__tests__/api.test.js`, inside the main `describe('api.js', ...)` block:

```js
describe('getOverlayConfig', () => {
  it('calls /api/v1/battle/overlay-config with GET', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }),
    })

    const result = await api.getOverlayConfig()

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/battle/overlay-config', {
      credentials: 'include',
    })
    expect(result.showImages).toBe(true)
  })

  it('returns fallback defaults on fetch error', async () => {
    mockFetch.mockRejectedValueOnce(new Error('network error'))

    const result = await api.getOverlayConfig()

    expect(result).toEqual({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })
  })
})

describe('setOverlayConfig', () => {
  it('calls /api/v1/battle/overlay-config with POST and JSON body', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true })

    await api.setOverlayConfig({ showImages: false, leftColor: '#ff0000', rightColor: '#0000ff' })

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/battle/overlay-config', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ showImages: false, leftColor: '#ff0000', rightColor: '#0000ff' }),
    })
  })
})
```

- [ ] **Step 2: Run tests to see them fail**

Run: `cd BES-frontend && npm test -- --reporter=verbose 2>&1 | grep -A2 "getOverlayConfig\|setOverlayConfig"`
Expected: FAIL — `api.getOverlayConfig is not a function`

- [ ] **Step 3: Add the two functions to api.js**

Add at the end of `BES-frontend/src/utils/api.js`:

```js
export const getOverlayConfig = async () => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/overlay-config`, {
      credentials: 'include',
    })
    return res.ok ? await res.json() : { showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }
  } catch (err) {
    console.log(err)
    return { showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }
  }
}

export const setOverlayConfig = async (config) => {
  try {
    return await fetch(`${domain}/api/v1/battle/overlay-config`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(config),
    })
  } catch (err) {
    console.log(err)
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd BES-frontend && npm test`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/utils/api.js BES-frontend/src/utils/__tests__/api.test.js
git commit -m "feat: add getOverlayConfig and setOverlayConfig API functions"
```

---

### Task 8: BattleControl.vue — Overlay settings panel

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

Add a compact, collapsible "Overlay" settings panel with left/right color pickers and an image toggle. Color changes broadcast on the `change` event (not `input`) to avoid flooding.

- [ ] **Step 1: Add imports and refs**

In the `<script setup>` section, add `getOverlayConfig` and `setOverlayConfig` to the existing api import line:

```js
import { addBattleJudge, battleJudgeVote, getAllJudges, getBattleJudges, getBattlePhase, getOverlayConfig, getParticipantScore, getPickupCrews, removeBattleJudge, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateSmokeList, uploadImage } from '@/utils/api'
```

Add the new ref after the existing `const showResetConfirm = ref(false)` line:

```js
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })
```

- [ ] **Step 2: Load initial config in onMounted**

In the existing `onMounted` block, add near the top (alongside other initial fetches):

```js
const savedConfig = await getOverlayConfig()
if (savedConfig?.showImages !== undefined) overlayConfig.value = savedConfig
```

- [ ] **Step 3: Add pushOverlayConfig helper**

Add after the `overlayConfig` ref declaration:

```js
const pushOverlayConfig = async () => {
  await setOverlayConfig(overlayConfig.value)
}
```

- [ ] **Step 4: Add Overlay panel to template**

Find the section in the template that contains the judge assignment controls (the area with `addBattleJudge`/`removeBattleJudge` buttons). Add the following panel directly after that section:

```html
<!-- Overlay Settings -->
<details class="overlay-settings-panel">
  <summary class="overlay-settings-summary">Overlay Settings</summary>
  <div class="overlay-settings-body">
    <div class="overlay-setting-row">
      <span class="overlay-setting-label">Left Color</span>
      <div class="overlay-color-group">
        <input
          type="color"
          v-model="overlayConfig.leftColor"
          @change="pushOverlayConfig"
          class="overlay-color-swatch"
          title="Left team color"
        />
        <input
          type="text"
          v-model="overlayConfig.leftColor"
          @change="pushOverlayConfig"
          maxlength="7"
          placeholder="#dc2626"
          class="overlay-hex-input"
        />
      </div>
    </div>
    <div class="overlay-setting-row">
      <span class="overlay-setting-label">Right Color</span>
      <div class="overlay-color-group">
        <input
          type="color"
          v-model="overlayConfig.rightColor"
          @change="pushOverlayConfig"
          class="overlay-color-swatch"
          title="Right team color"
        />
        <input
          type="text"
          v-model="overlayConfig.rightColor"
          @change="pushOverlayConfig"
          maxlength="7"
          placeholder="#2563eb"
          class="overlay-hex-input"
        />
      </div>
    </div>
    <div class="overlay-setting-row">
      <span class="overlay-setting-label">Show Images</span>
      <label class="overlay-toggle">
        <input
          type="checkbox"
          v-model="overlayConfig.showImages"
          @change="pushOverlayConfig"
        />
        <span class="overlay-toggle-track"></span>
      </label>
    </div>
  </div>
</details>
```

- [ ] **Step 5: Add scoped styles for the overlay panel**

Add to the `<style scoped>` section of `BattleControl.vue`:

```css
.overlay-settings-panel {
  background: #1a1a1a;
  border: 1px solid #2c2c2c;
  border-radius: 12px;
  margin-top: 12px;
  overflow: hidden;
}
.overlay-settings-summary {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #f0f0f0;
  cursor: pointer;
  user-select: none;
  list-style: none;
}
.overlay-settings-summary::-webkit-details-marker { display: none; }
.overlay-settings-body {
  padding: 4px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.overlay-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.overlay-setting-label {
  font-size: 13px;
  color: rgba(240,240,240,0.65);
}
.overlay-color-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.overlay-color-swatch {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  padding: 0;
  background: none;
}
.overlay-hex-input {
  width: 80px;
  background: #111;
  border: 1px solid #2c2c2c;
  border-radius: 6px;
  color: #f0f0f0;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  padding: 4px 8px;
}
.overlay-toggle { display: flex; align-items: center; cursor: pointer; }
.overlay-toggle input { display: none; }
.overlay-toggle-track {
  width: 36px;
  height: 20px;
  background: #2c2c2c;
  border-radius: 10px;
  position: relative;
  transition: background 0.2s;
}
.overlay-toggle input:checked + .overlay-toggle-track { background: #e53935; }
.overlay-toggle-track::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}
.overlay-toggle input:checked + .overlay-toggle-track::after { transform: translateX(16px); }
```

- [ ] **Step 6: Verify dev server starts without errors**

Run: `cd BES-frontend && npm run dev`
Expected: Server starts; navigate to `/battle/control` and confirm the Overlay Settings panel appears and is functional.

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: add Overlay Settings panel to BattleControl (colors + image toggle)"
```

---

### Task 9: BattleOverlay.vue — complete rewrite

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

This is a full replacement of the script, template, and style sections. The smoke mode (`isSmoke=true`) section is preserved exactly. The standard mode (`isSmoke=false`) gets the new UI, animations, and overlay config.

**Key changes from current:**
- Right panel: `right: 0` → `left: 54%; width: 46%` (fixes winner centering for right-side winners)
- Judge votes hidden during voting (`votesVisible` ref); revealed all at once on score
- `/topic/battle/phase` VOTING → `showVotingIndicator` wired up (currently empty callback)
- New `/topic/battle/overlay-config` subscription + initial GET fetch
- All animation keyframes replaced with hard-attack bezier curves
- CSS custom properties `--left-color` / `--right-color` drive all color treatments
- Judge cards: parallelogram `clip-path`, frosted glass when unvoted
- Picture mode: `aspect-ratio: 3/4` portrait, name overlaid at bottom
- Name-only mode: giant vertically-centered Anton SC names

- [ ] **Step 1: Write a smoke test for BattleOverlay**

Create `BES-frontend/src/views/__tests__/BattleOverlay.test.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'

// Mock heavy dependencies
vi.mock('@/utils/api', () => ({
  getBattleJudges: vi.fn().mockResolvedValue({ judges: [] }),
  getCurrentBattlePair: vi.fn().mockResolvedValue(null),
  getImage: vi.fn().mockResolvedValue(null),
  getOverlayConfig: vi.fn().mockResolvedValue({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }),
}))
vi.mock('@/utils/websocket', () => ({
  createClient: vi.fn().mockReturnValue({ value: null }),
  deactivateClient: vi.fn(),
  subscribeToChannel: vi.fn(),
}))
vi.mock('./Chart.vue', () => ({ default: { template: '<div />' } }))

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/battle/overlay', component: { template: '<div />' } }],
})

// Dynamically import after mocks are set up
const BattleOverlay = (await import('../BattleOverlay.vue')).default

describe('BattleOverlay.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.documentElement.classList.remove('transparent-page')
  })

  it('mounts without errors', async () => {
    const wrapper = mount(BattleOverlay, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('adds transparent-page class to html on mount', async () => {
    mount(BattleOverlay, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    expect(document.documentElement.classList.contains('transparent-page')).toBe(true)
  })

  it('initializes overlayConfig with defaults', async () => {
    const wrapper = mount(BattleOverlay, { global: { plugins: [router] } })
    // The root element should have CSS custom property bindings
    expect(wrapper.find('.overlay-root').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: Run the smoke test to see it fail (BattleOverlay not yet rewritten)**

Run: `cd BES-frontend && npm test -- src/views/__tests__/BattleOverlay.test.js`
Expected: At least one test fails (the current BattleOverlay won't have getOverlayConfig imported).

- [ ] **Step 3: Rewrite BattleOverlay.vue — script section**

Replace the entire `<script setup>` section with:

```vue
<script setup>
import { getBattleJudges, getCurrentBattlePair, getImage, getOverlayConfig } from '@/utils/api';
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket';
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';
import { useDelay } from '@/utils/utils';
import { useRoute } from 'vue-router'
import Chart from './Chart.vue';

const route = useRoute()

// ── Overlay config (live from BattleControl + API) ─────────────────────────
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })

// ── Battle state ───────────────────────────────────────────────────────────
const imageLeft  = ref(null)
const imageRight = ref(null)

let client = createClient()
const subscribedTopics = new Set()
const rightName  = ref('')
const leftName   = ref('')
const leftScore  = ref(0)
const rightScore = ref(0)
const currentWinner = ref(-2)
const battleJudges  = ref([])

// Judge panel visibility: true = off-screen (idle/battle), false = visible (score revealed)
const hideJudgeDecision = ref(true)

// ── Voting state ───────────────────────────────────────────────────────────
// showVotingIndicator: true when /topic/battle/phase emits VOTING
const showVotingIndicator = ref(false)
// votesVisible: false until score is revealed — hides vote state on judge cards
const votesVisible = ref(false)

// ── Animation state (standard mode only) ──────────────────────────────────
// judgeAnim: '' | 'slide-down' | 'slide-up' (also used for smoke mode panel)
const judgeAnim = ref('')
// leftWin / rightWin: winner-expand CSS class trigger
const leftWin  = ref(false)
const rightWin = ref(false)
// leftReset / rightReset: loser/winner exit animation trigger
const leftReset  = ref(false)
const rightReset = ref(false)
// vsAnim: '' | 'rush-in' | 'knock-left' | 'knock-right'
const vsAnim = ref('')
// stageShaking: brief shake burst on VS landing (~340ms into entrance)
const stageShaking = ref(false)
// winnerTagVisible: WINNER stamp appears after winner is determined
const winnerTagVisible = ref(false)
// glitching: glitch overlay during next-pair transition
const glitching = ref(false)

const isSmoke = computed(() => route.query.isSmoke === 'true')

const judgePanelClass = computed(() => {
  if (isSmoke.value) return 'smoke-judge-always-on'
  return judgeAnim.value
})

// ── Entrance animation ─────────────────────────────────────────────────────
const runEntrance = async () => {
  await useDelay().wait(50) // allow DOM to clear previous animation classes
  hideJudgeDecision.value = true
  vsAnim.value = 'rush-in'
  await useDelay().wait(340) // VS hits undershoot trough at ~340ms
  stageShaking.value = true
  await useDelay().wait(120)
  stageShaking.value = false
}

// ── Battle pair update ─────────────────────────────────────────────────────
const updateBattlePair = async (msg) => {
  if (!msg) return

  // SMOKE MODE: only wipe judge votes, keep panel on screen
  if (isSmoke.value) {
    if (battleJudges.value?.judges) {
      battleJudges.value = {
        ...battleJudges.value,
        judges: battleJudges.value.judges.map(j => ({ ...j, vote: -3 }))
      }
    }
    return
  }

  // STANDARD MODE: if a winner is currently showing, run exit sequence first
  if (!hideJudgeDecision.value) {
    glitching.value = true
    judgeAnim.value = 'slide-up'
    await useDelay().wait(100)

    if (currentWinner.value === 0) {
      leftReset.value = true
    } else if (currentWinner.value === 1) {
      rightReset.value = true
    } else {
      leftReset.value  = true
      rightReset.value = true
    }
    vsAnim.value = ''

    await useDelay().wait(280)
    glitching.value          = false
    hideJudgeDecision.value  = true
    judgeAnim.value          = ''
    votesVisible.value       = false
    winnerTagVisible.value   = false
    await useDelay().wait(50)
    leftReset.value  = false
    rightReset.value = false
  }

  // Reset all state before new pair
  leftWin.value          = false
  rightWin.value         = false
  currentWinner.value    = -2
  vsAnim.value           = ''
  showVotingIndicator.value = false
  votesVisible.value     = false
  winnerTagVisible.value = false

  leftName.value   = msg.left
  rightName.value  = msg.right
  leftScore.value  = msg.leftScore  ?? 0
  rightScore.value = msg.rightScore ?? 0
  imageLeft.value  = await getImage(`${msg.left}.png`)
  imageRight.value = await getImage(`${msg.right}.png`)

  await runEntrance()
}

// ── Judge list update ──────────────────────────────────────────────────────
const updateBattleJudge = (msg) => {
  battleJudges.value = msg
  battleJudges.value.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic)
      subscribeToChannel(createClient(), topic, (m) => updateJudgeVote(m))
    }
  })
}

// ── Judge vote update (internal only — hidden until score reveal) ──────────
const updateJudgeVote = (msg) => {
  const updatedJudges = battleJudges.value.judges.map(j =>
    j.id === msg.judge ? { ...j, vote: msg.vote } : j
  )
  battleJudges.value = { ...battleJudges.value, judges: updatedJudges }
}

watch(battleJudges, (newVal) => {
  if (!newVal?.judges) return
  newVal.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic)
      subscribeToChannel(createClient(), topic, (m) => updateJudgeVote(m))
    }
  })
}, { deep: true })

// ── Score reveal ───────────────────────────────────────────────────────────
const updateScore = async (msg) => {
  judgeAnim.value          = 'slide-down'
  hideJudgeDecision.value  = false
  showVotingIndicator.value = false
  rightScore.value = msg.right
  leftScore.value  = msg.left

  // Reveal votes shortly after panel starts dropping
  await useDelay().wait(200)
  votesVisible.value = true

  // Winner determination after cards have burst in
  await useDelay().wait(800)
  currentWinner.value = msg.message

  if (msg.message === 0) {
    // Left wins: VS rockets right (toward loser), left panel expands
    leftWin.value          = true
    winnerTagVisible.value = true
    vsAnim.value           = 'knock-right'
    await useDelay().wait(100)
    rightReset.value = true   // right (loser) hard-cuts off screen
  } else if (msg.message === 1) {
    // Right wins: VS rockets left (toward loser), right panel expands
    rightWin.value         = true
    winnerTagVisible.value = true
    vsAnim.value           = 'knock-left'
    await useDelay().wait(100)
    leftReset.value = true    // left (loser) hard-cuts off screen
  }
  // msg.message === -1: tie — no winner state, panels stay
}

// ── Mount ──────────────────────────────────────────────────────────────────
onMounted(async () => {
  document.documentElement.classList.add('transparent-page')
  document.body.classList.add('transparent-page')

  // Fetch initial overlay config (survives OBS refresh)
  const config = await getOverlayConfig()
  if (config?.showImages !== undefined) overlayConfig.value = config

  // Live overlay config updates from BattleControl
  subscribeToChannel(createClient(), '/topic/battle/overlay-config', (msg) => {
    if (msg?.showImages !== undefined) overlayConfig.value = msg
  })

  // Phase subscription: VOTING phase shows the voting indicator
  subscribeToChannel(createClient(), '/topic/battle/phase', (msg) => {
    showVotingIndicator.value = msg?.phase === 'VOTING'
  })

  if (isSmoke.value) {
    battleJudges.value = await getBattleJudges()
    subscribeToChannel(createClient(), '/topic/battle/judges',     (msg) => updateBattleJudge(msg))
    subscribeToChannel(createClient(), '/topic/battle/battle-pair',(msg) => updateBattlePair(msg))
    subscribeToChannel(createClient(), '/topic/battle/score',      (msg) => updateScore(msg))
  } else {
    battleJudges.value = await getBattleJudges()
    const res = await getCurrentBattlePair()
    if (res) await updateBattlePair(res)
    subscribeToChannel(createClient(), '/topic/battle/battle-pair',(msg) => updateBattlePair(msg))
    subscribeToChannel(createClient(), '/topic/battle/score',      (msg) => updateScore(msg))
    subscribeToChannel(createClient(), '/topic/battle/judges',     (msg) => updateBattleJudge(msg))
  }
})

onBeforeUnmount(() => { deactivateClient(client.value) })

onUnmounted(() => {
  document.documentElement.classList.remove('transparent-page')
  document.body.classList.remove('transparent-page')
  const appRoot = document.getElementById('app')
  if (appRoot) appRoot.style.background = ''
})
</script>
```

- [ ] **Step 4: Rewrite BattleOverlay.vue — template section**

Replace the entire `<template>` section with:

```vue
<template>
  <div
    class="overlay-root"
    :class="{ 'stage-shake': stageShaking }"
    :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor }"
  >
    <!-- Screen-reader live region -->
    <div class="sr-only" role="status" aria-live="assertive" aria-atomic="true">
      <template v-if="currentWinner === 0">{{ leftName }} wins!</template>
      <template v-else-if="currentWinner === 1">{{ rightName }} wins!</template>
      <template v-else-if="currentWinner === -1">Tie between {{ leftName }} and {{ rightName }}!</template>
    </div>

    <!-- Glitch transition overlay -->
    <div v-if="glitching" class="glitch-overlay" aria-hidden="true"></div>

    <!-- Structural decorators — standard mode only -->
    <template v-if="!isSmoke">
      <div class="center-divider" aria-hidden="true"></div>
      <div class="scanlines"      aria-hidden="true"></div>
      <div class="color-bleed color-bleed-left"  aria-hidden="true"></div>
      <div class="color-bleed color-bleed-right" aria-hidden="true"></div>
    </template>

    <!-- ══════════════════════════════════════════════════
         JUDGE PANEL — shared across both modes
    ═══════════════════════════════════════════════════ -->
    <div
      v-if="battleJudges?.judges?.length"
      class="judge-panel"
      :class="judgePanelClass"
      role="region"
      aria-label="Judges"
      aria-live="polite"
      aria-atomic="false"
    >
      <div class="judge-border-glow" aria-hidden="true"></div>
      <div class="judge-inner">
        <div class="judges-header" aria-hidden="true">
          <span class="judges-line"></span>
          <span class="judges-label">JUDGES</span>
          <span class="judges-line"></span>
        </div>
        <div class="judge-cards-row" role="list">
          <div
            v-for="(j, index) in battleJudges.judges"
            :key="index"
            class="judge-card"
            role="listitem"
            :aria-label="`${j.name}: ${j.vote === 0 ? 'voted left' : j.vote === 1 ? 'voted right' : j.vote === -1 ? 'voted tie' : 'awaiting vote'}`"
            :class="{
              'voted-left':   votesVisible && j.vote === 0,
              'voted-right':  votesVisible && j.vote === 1,
              'voted-tie':    votesVisible && j.vote === -1,
              'card-unvoted': !votesVisible,
              'card-burst':    votesVisible,
            }"
            :style="votesVisible ? { animationDelay: `${index * 55}ms` } : {}"
          >
            <div class="judge-row">
              <span
                class="vote-arrow vote-arrow-left"
                :class="{ 'arrow-lit-left': votesVisible && j.vote === 0 }"
                aria-hidden="true"
              ></span>
              <span class="judge-name">{{ j.name }}</span>
              <span
                class="vote-arrow vote-arrow-right"
                :class="{ 'arrow-lit-right': votesVisible && j.vote === 1 }"
                aria-hidden="true"
              ></span>
            </div>
            <div class="vote-track" aria-hidden="true">
              <div
                class="vote-fill"
                :class="{
                  'fill-left':  votesVisible && j.vote === 0,
                  'fill-right': votesVisible && j.vote === 1,
                  'fill-tie':   votesVisible && j.vote === -1,
                  'fill-blank': !votesVisible || j.vote === -3 || j.vote === null,
                }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ══════════════════════════════════════════════════
         STANDARD BATTLE  (isSmoke = false)
    ═══════════════════════════════════════════════════ -->
    <template v-if="!isSmoke">

      <!-- Left battler panel -->
      <div
        class="battler-panel left-panel"
        :class="{
          'slam-in-left':   hideJudgeDecision,
          'slide-left-out': leftReset,
          'panel-winner':   leftWin,
        }"
        role="region"
        :aria-label="`Left: ${leftName || 'TBD'}${leftScore > 0 ? ', score ' + leftScore : ''}${leftWin ? ' — winner' : ''}`"
      >
        <div class="panel-color-wash panel-color-wash-left" aria-hidden="true"></div>

        <!-- Picture mode -->
        <template v-if="overlayConfig.showImages">
          <img v-if="imageLeft" :src="imageLeft" :alt="leftName" class="battler-img" />
          <div v-else class="battler-placeholder" aria-hidden="true"></div>
          <div class="name-overlay">
            <span class="name-text name-text-left">{{ leftName || '???' }}</span>
            <span v-if="leftScore > 0" class="score-badge">{{ leftScore }}</span>
          </div>
        </template>

        <!-- Name-only mode -->
        <template v-else>
          <div class="name-center-wrap">
            <span class="name-giant name-giant-left">{{ leftName || '???' }}</span>
            <span v-if="leftScore > 0" class="score-badge-large">{{ leftScore }}</span>
          </div>
        </template>

        <div class="corner-accent corner-accent-tl" aria-hidden="true"></div>
        <div class="bottom-edge bottom-edge-left"   aria-hidden="true"></div>
        <div v-if="winnerTagVisible && leftWin" class="winner-tag" aria-hidden="true">WINNER</div>
      </div>

      <!-- VS badge -->
      <div
        class="vs-badge"
        :class="[vsAnim, { 'vs-gone': currentWinner !== -2 && vsAnim !== 'knock-left' && vsAnim !== 'knock-right' }]"
        aria-hidden="true"
      >
        <span class="vs-text">VS</span>
      </div>

      <!-- Right battler panel -->
      <div
        class="battler-panel right-panel"
        :class="{
          'slam-in-right':   hideJudgeDecision,
          'slide-right-out': rightReset,
          'panel-winner':    rightWin,
        }"
        role="region"
        :aria-label="`Right: ${rightName || 'TBD'}${rightScore > 0 ? ', score ' + rightScore : ''}${rightWin ? ' — winner' : ''}`"
      >
        <div class="panel-color-wash panel-color-wash-right" aria-hidden="true"></div>

        <!-- Picture mode -->
        <template v-if="overlayConfig.showImages">
          <img v-if="imageRight" :src="imageRight" :alt="rightName" class="battler-img" />
          <div v-else class="battler-placeholder" aria-hidden="true"></div>
          <div class="name-overlay name-overlay-right">
            <span class="name-text name-text-right">{{ rightName || '???' }}</span>
            <span v-if="rightScore > 0" class="score-badge">{{ rightScore }}</span>
          </div>
        </template>

        <!-- Name-only mode -->
        <template v-else>
          <div class="name-center-wrap">
            <span class="name-giant name-giant-right">{{ rightName || '???' }}</span>
            <span v-if="rightScore > 0" class="score-badge-large">{{ rightScore }}</span>
          </div>
        </template>

        <div class="corner-accent corner-accent-tr" aria-hidden="true"></div>
        <div class="bottom-edge bottom-edge-right"  aria-hidden="true"></div>
        <div v-if="winnerTagVisible && rightWin" class="winner-tag" aria-hidden="true">WINNER</div>
      </div>

      <!-- Voting indicator — visible during VOTING phase -->
      <transition name="fade-indicator">
        <div v-if="showVotingIndicator" class="voting-indicator" aria-label="Judges are voting">
          <span class="voting-dot" aria-hidden="true"></span>
          <span class="voting-label">JUDGES VOTING</span>
        </div>
      </transition>

    </template>

    <!-- ══════════════════════════════════════════════════
         SMOKE MODE  (isSmoke = true)
    ═══════════════════════════════════════════════════ -->
    <template v-else>
      <Chart />
    </template>

  </div>
</template>
```

- [ ] **Step 5: Rewrite BattleOverlay.vue — style section**

Replace the entire `<style>` section with:

```vue
<style>
/* ── Screen-reader only ─────────────────────────────────────── */
.sr-only {
  position: absolute; width: 1px; height: 1px; padding: 0;
  margin: -1px; overflow: hidden; clip: rect(0,0,0,0);
  white-space: nowrap; border: 0;
}

/* ── Transparent background (OBS) ──────────────────────────── */
html.transparent-page,
body.transparent-page,
html.transparent-page #app,
body.transparent-page #app {
  background: transparent !important;
  background-color: transparent !important;
}

/* ── Root ───────────────────────────────────────────────────── */
.overlay-root {
  position: fixed;
  top: 0; left: 0;
  width: 100vw; height: 100vh;
  overflow: hidden;
  font-family: 'Anton SC', sans-serif;
  /* Default CSS custom properties — overridden by :style binding */
  --left-color: #dc2626;
  --right-color: #2563eb;
}

/* ── Stage shake ────────────────────────────────────────────── */
.stage-shake {
  animation: stageShake 120ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

/* ── Glitch overlay ─────────────────────────────────────────── */
.glitch-overlay {
  position: absolute; inset: 0;
  z-index: 100;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    transparent, transparent 4px,
    rgba(255,255,255,0.04) 4px, rgba(255,255,255,0.04) 8px
  );
  animation: glitchFlicker 380ms steps(1) forwards;
}

/* ── Center divider ─────────────────────────────────────────── */
.center-divider {
  position: absolute;
  top: 0; bottom: 0;
  left: calc(50% - 1px);
  width: 2px;
  background: linear-gradient(
    to bottom,
    transparent 0%,
    rgba(255,255,255,0.12) 30%,
    rgba(255,255,255,0.22) 50%,
    rgba(255,255,255,0.12) 70%,
    transparent 100%
  );
  transform: skewX(-3deg);
  z-index: 5;
  pointer-events: none;
}

/* ── Scanlines ──────────────────────────────────────────────── */
.scanlines {
  position: absolute; inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0px, transparent 3px,
    rgba(0,0,0,0.035) 3px, rgba(0,0,0,0.035) 4px
  );
}

/* ── Global color bleeds ────────────────────────────────────── */
.color-bleed {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 1;
}
.color-bleed-left {
  background: radial-gradient(
    ellipse 45% 55% at 0% 100%,
    color-mix(in srgb, var(--left-color) 16%, transparent),
    transparent 70%
  );
}
.color-bleed-right {
  background: radial-gradient(
    ellipse 45% 55% at 100% 100%,
    color-mix(in srgb, var(--right-color) 16%, transparent),
    transparent 70%
  );
}

/* ══════════════════════════════════════════════════
   JUDGE PANEL
══════════════════════════════════════════════════ */
.judge-panel {
  position: absolute;
  top: 18px; left: 0; right: 0;
  z-index: 50;
  display: flex;
  justify-content: center;
  pointer-events: none;
  transform: translateY(-220px);
}
.smoke-judge-always-on {
  transform: translateY(0) !important;
  animation: none !important;
}
.judge-border-glow {
  position: absolute; inset: -1px;
  border-radius: 22px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--left-color) 55%, transparent) 0%,
    rgba(255,255,255,0.1) 35%,
    color-mix(in srgb, var(--right-color) 55%, transparent) 65%,
    rgba(255,255,255,0.1) 100%
  );
  filter: blur(1px);
  animation: borderRotate 6s linear infinite;
  z-index: -1;
}
.judge-inner {
  position: relative;
  display: flex; flex-direction: column; gap: 10px;
  background: rgba(6,8,18,0.85);
  backdrop-filter: blur(28px);
  -webkit-backdrop-filter: blur(28px);
  border-radius: 20px;
  padding: 14px 24px 16px;
}
.judges-header {
  display: flex; align-items: center; gap: 10px;
}
.judges-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px; font-weight: 700;
  letter-spacing: 0.28em;
  color: rgba(255,255,255,0.35);
  text-transform: uppercase;
  white-space: nowrap; flex-shrink: 0;
}
.judges-line {
  flex: 1; height: 1px;
  background: rgba(255,255,255,0.1);
}
.judge-cards-row {
  display: flex; gap: 12px;
}

/* Judge card — parallelogram shape */
.judge-card {
  display: flex; flex-direction: column;
  align-items: center; gap: 10px;
  min-width: 148px;
  padding: 10px 14px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  border: 1.5px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.025);
  transition: border-color 0.25s ease, box-shadow 0.25s ease, background 0.25s ease;
}

/* Unvoted: frosted glass with gentle pulse */
.card-unvoted {
  background: rgba(255,255,255,0.08) !important;
  border-color: rgba(255,255,255,0.18) !important;
  animation: cardPulse 2.2s ease-in-out infinite;
}

/* Burst in when votes are revealed */
.card-burst {
  animation: cardBurst 280ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

.voted-left {
  border-color: color-mix(in srgb, var(--left-color) 65%, transparent);
  background: color-mix(in srgb, var(--left-color) 9%, transparent);
  box-shadow: 0 0 28px color-mix(in srgb, var(--left-color) 35%, transparent),
              inset 0 0 14px color-mix(in srgb, var(--left-color) 8%, transparent);
}
.voted-right {
  border-color: color-mix(in srgb, var(--right-color) 65%, transparent);
  background: color-mix(in srgb, var(--right-color) 9%, transparent);
  box-shadow: 0 0 28px color-mix(in srgb, var(--right-color) 35%, transparent),
              inset 0 0 14px color-mix(in srgb, var(--right-color) 8%, transparent);
}
.voted-tie {
  border-color: rgba(251,191,36,0.65);
  background: rgba(251,191,36,0.07);
  box-shadow: 0 0 28px rgba(251,191,36,0.28), inset 0 0 14px rgba(251,191,36,0.06);
}

/* Judge name + arrow row */
.judge-row { display: flex; align-items: center; gap: 11px; }
.judge-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 28px;
  color: rgba(255,255,255,0.92);
  letter-spacing: 0.07em;
  text-transform: uppercase;
  line-height: 1;
}

/* Direction arrows */
.vote-arrow {
  display: inline-block;
  width: 18px; height: 18px; flex-shrink: 0;
  background: rgba(255,255,255,0.15);
  opacity: 0.2;
  transition: opacity 0.2s ease, background 0.2s ease, filter 0.2s ease;
}
.vote-arrow-left  { clip-path: polygon(100% 0%, 0% 50%, 100% 100%); }
.vote-arrow-right { clip-path: polygon(0% 0%, 100% 50%, 0% 100%); }
.arrow-lit-left {
  opacity: 1;
  background: var(--left-color);
  filter: drop-shadow(0 0 8px var(--left-color));
}
.arrow-lit-right {
  opacity: 1;
  background: var(--right-color);
  filter: drop-shadow(0 0 8px var(--right-color));
}

/* Vote track bar */
.vote-track {
  width: 100%; height: 10px;
  background: rgba(255,255,255,0.1);
  border-radius: 9999px;
  overflow: hidden;
}
.vote-fill {
  height: 100%;
  border-radius: 9999px;
  transition: background 0.25s ease, width 0.25s ease;
  width: 0%;
}
.fill-left  {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--left-color) 70%, black), var(--left-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--left-color) 90%, transparent);
}
.fill-right {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--right-color) 70%, black), var(--right-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--right-color) 90%, transparent);
}
.fill-tie   { width: 100%; background: linear-gradient(90deg, #b45309, #fbbf24); box-shadow: 0 0 14px rgba(251,191,36,0.9); }
.fill-blank { width: 0%; background: transparent; }

/* ══════════════════════════════════════════════════
   BATTLER PANELS
   Both panels use left-only positioning so winner
   centering (left:0, width:100%) works from either side.
══════════════════════════════════════════════════ */
.battler-panel {
  position: absolute;
  bottom: 0;
  height: 100vh;
  display: flex; flex-direction: column;
  align-items: center; justify-content: flex-end;
  /* CSS transition for winner expand */
  transition: left 420ms cubic-bezier(0.2, 0, 0.3, 1),
              width 420ms cubic-bezier(0.2, 0, 0.3, 1);
}
.left-panel  { left: 0;    width: 46%; }
.right-panel { left: 54%;  width: 46%; }

/* Winner: both panels expand to full width from left:0 */
.panel-winner { left: 0 !important; width: 100% !important; }

/* Panel color wash (gradient overlay) */
.panel-color-wash {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 0;
}
.panel-color-wash-left {
  background: linear-gradient(
    to top,
    color-mix(in srgb, var(--left-color) 55%, black) 0%,
    color-mix(in srgb, var(--left-color) 15%, transparent) 40%,
    transparent 70%
  );
}
.panel-color-wash-right {
  background: linear-gradient(
    to top,
    color-mix(in srgb, var(--right-color) 55%, black) 0%,
    color-mix(in srgb, var(--right-color) 15%, transparent) 40%,
    transparent 70%
  );
}

/* Corner accent bars (3px vertical, fade down in team color) */
.corner-accent {
  position: absolute; top: 0;
  width: 3px; height: 28%;
  pointer-events: none; z-index: 3;
}
.corner-accent-tl { left: 0;  background: linear-gradient(to bottom, var(--left-color), transparent); }
.corner-accent-tr { right: 0; background: linear-gradient(to bottom, var(--right-color), transparent); }

/* Bottom edge lines (3px horizontal, fade inward) */
.bottom-edge {
  position: absolute; bottom: 0;
  height: 3px; width: 40%;
  pointer-events: none; z-index: 3;
}
.bottom-edge-left  { left: 0;  background: linear-gradient(to right, var(--left-color), transparent); }
.bottom-edge-right { right: 0; background: linear-gradient(to left, var(--right-color), transparent); }

/* ── Picture mode ────────────────────────────────── */
.battler-img {
  position: relative; z-index: 10;
  width: 100%;
  aspect-ratio: 3/4;
  object-fit: cover;
  object-position: top;
  max-height: 85vh;
  display: block;
}
.battler-placeholder {
  position: relative; z-index: 10;
  width: 12vw; height: 30vh;
  margin-bottom: 8vh;
  border-radius: 8px;
  border: 1.5px dashed rgba(255,255,255,0.18);
  background: rgba(255,255,255,0.03);
  animation: placeholderBreath 3s ease-in-out infinite;
}
.name-overlay {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  z-index: 20;
  padding: 5vh 1.5vw 2vh;
  background: linear-gradient(to top, rgba(0,0,0,0.80) 0%, transparent 100%);
  display: flex; align-items: flex-end; gap: 10px;
}
.name-overlay-right { flex-direction: row-reverse; }
.name-text {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(18px, 3vw, 52px);
  text-transform: uppercase;
  color: #fff;
  line-height: 1; letter-spacing: 0.07em;
}
.name-text-left {
  text-shadow: 3px 3px 0 var(--left-color),
               0 0 30px color-mix(in srgb, var(--left-color) 60%, transparent);
}
.name-text-right {
  text-shadow: -3px 3px 0 var(--right-color),
               0 0 30px color-mix(in srgb, var(--right-color) 60%, transparent);
}

/* ── Name-only mode ──────────────────────────────── */
.name-center-wrap {
  position: absolute;
  top: 50%; transform: translateY(-50%);
  z-index: 20;
  width: 90%;
  display: flex; flex-direction: column;
  align-items: center; gap: 14px;
  padding: 0 8px;
}
.name-giant {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(28px, 5.5vw, 90px);
  text-transform: uppercase;
  color: #fff;
  line-height: 1; letter-spacing: 0.06em;
  word-break: break-word; text-align: center;
}
.name-giant-left {
  text-shadow: 4px 4px 0 var(--left-color),
               0 0 50px color-mix(in srgb, var(--left-color) 50%, transparent);
}
.name-giant-right {
  text-shadow: -4px 4px 0 var(--right-color),
               0 0 50px color-mix(in srgb, var(--right-color) 50%, transparent);
}

/* Score badges */
.score-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(12px, 1.6vw, 28px);
  line-height: 1;
  color: rgba(255,255,255,0.92);
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 6px;
  padding: 2px 8px;
  letter-spacing: 0.05em;
}
.score-badge-large {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(16px, 2.5vw, 42px);
  color: rgba(255,255,255,0.6);
  letter-spacing: 0.05em;
}

/* ── VS badge ────────────────────────────────────── */
.vs-badge {
  position: absolute;
  bottom: 2.5vh;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
}
.vs-text {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(24px, 4.2vw, 72px);
  color: rgba(255,255,255,0.88);
  letter-spacing: 0.14em;
  text-shadow: 0 0 40px rgba(255,255,255,0.25);
  clip-path: polygon(10% 0%, 90% 0%, 100% 50%, 90% 100%, 10% 100%, 0% 50%);
  background: rgba(0,0,0,0.25);
  padding: 4px 22px 4px 18px;
  display: block;
}
.vs-gone { display: none; }

/* ── Voting indicator ────────────────────────────── */
.voting-indicator {
  position: absolute;
  bottom: 2vh; left: 50%;
  transform: translateX(-50%);
  z-index: 40;
  display: flex; align-items: center; gap: 8px;
}
.voting-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: votingPulse 1.2s ease-in-out infinite;
}
.voting-label {
  font-family: 'Inter', sans-serif;
  font-size: 11px; font-weight: 700;
  letter-spacing: 0.2em;
  color: rgba(255,255,255,0.45);
  text-transform: uppercase;
}
/* Fade transition for voting indicator */
.fade-indicator-enter-active,
.fade-indicator-leave-active { transition: opacity 0.3s ease; }
.fade-indicator-enter-from,
.fade-indicator-leave-to     { opacity: 0; }

/* ── Winner tag ──────────────────────────────────── */
.winner-tag {
  position: absolute;
  top: 40%; left: 50%;
  transform: translate(-50%, -50%);
  z-index: 60;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(18px, 3.2vw, 58px);
  color: #fff;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  border: 3px solid rgba(255,255,255,0.8);
  padding: 8px 28px;
  background: rgba(0,0,0,0.45);
  animation: winnerStamp 300ms cubic-bezier(0.2, 0, 0.3, 1) both;
  animation-delay: 300ms;
  opacity: 0;
}

/* ══════════════════════════════════════════════════
   KEYFRAMES
══════════════════════════════════════════════════ */

/* Stage shake on VS landing */
@keyframes stageShake {
  0%   { transform: translate(0,    0);  }
  15%  { transform: translate(-4px, 3px); }
  30%  { transform: translate(5px, -2px); }
  45%  { transform: translate(-3px, 4px); }
  60%  { transform: translate(4px, -3px); }
  75%  { transform: translate(-2px, 2px); }
  100% { transform: translate(0,    0);  }
}

/* Glitch flicker during next-pair transition */
@keyframes glitchFlicker {
  0%   { opacity: 1;   clip-path: inset(0% 0 0% 0); transform: skewX(0); }
  12%  { opacity: 0;   }
  25%  { opacity: 1;   clip-path: inset(20% 0 30% 0); transform: skewX(2deg); }
  37%  { opacity: 0.7; }
  50%  { opacity: 1;   clip-path: inset(55% 0 5% 0);  transform: skewX(-1deg) translateX(3px); }
  62%  { opacity: 0.4; }
  75%  { opacity: 1;   clip-path: inset(10% 0 78% 0); transform: skewX(3deg); }
  87%  { opacity: 0.8; }
  100% { opacity: 0;   }
}

/* Left panel slams in from left edge */
@keyframes leftSlamIn {
  0%   { transform: translateX(-72vw); }
  82%  { transform: translateX(3px); }
  100% { transform: translateX(0); }
}

/* Right panel slams in from right edge */
@keyframes rightSlamIn {
  0%   { transform: translateX(72vw); }
  82%  { transform: translateX(-3px); }
  100% { transform: translateX(0); }
}

/* VS rushes in from in-front-of-camera (scale 6→0.72→1.10→1) */
@keyframes vsRushIn {
  0%   { transform: translateX(-50%) scale(6);    opacity: 0.5; }
  55%  { transform: translateX(-50%) scale(0.72); opacity: 1;   }
  75%  { transform: translateX(-50%) scale(1.10); }
  100% { transform: translateX(-50%) scale(1);    }
}

/* VS rockets toward right (left wins — loser is right) */
@keyframes vsKnockRight {
  0%   { transform: translateX(-50%) scale(1);    opacity: 1; }
  18%  { transform: translateX(-50%) scale(1.25); }
  100% { transform: translateX(80vw) scale(0.3) rotate(55deg); opacity: 0; }
}

/* VS rockets toward left (right wins — loser is left) */
@keyframes vsKnockLeft {
  0%   { transform: translateX(-50%) scale(1);    opacity: 1; }
  18%  { transform: translateX(-50%) scale(1.25); }
  100% { transform: translateX(-130vw) scale(0.3) rotate(-55deg); opacity: 0; }
}

/* Loser hard-cuts off left edge */
@keyframes hardCutLeft {
  0%   { transform: translateX(0);      opacity: 1; }
  100% { transform: translateX(-110vw); opacity: 0; }
}

/* Loser hard-cuts off right edge */
@keyframes hardCutRight {
  0%   { transform: translateX(0);     opacity: 1; }
  100% { transform: translateX(110vw); opacity: 0; }
}

/* Judge panel slams down (bounce easing applied via class) */
@keyframes slideDown {
  from { transform: translateY(-220px); }
  to   { transform: translateY(0); }
}
@keyframes slideUp {
  from { transform: translateY(0); }
  to   { transform: translateY(-220px); }
}

/* Card burst entrance on score reveal */
@keyframes cardBurst {
  0%   { transform: scale(1.4) skewX(-5deg); opacity: 0; }
  65%  { transform: scale(0.97) skewX(0);   opacity: 1; }
  100% { transform: scale(1) skewX(0);      opacity: 1; }
}

/* Unvoted card pulse */
@keyframes cardPulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.58; }
}

/* Voting dot pulse */
@keyframes votingPulse {
  0%, 100% { transform: scale(1);   opacity: 1; }
  50%       { transform: scale(1.6); opacity: 0.4; }
}

/* WINNER stamp */
@keyframes winnerStamp {
  0%   { transform: translate(-50%, -50%) scale(2.8); opacity: 0; }
  60%  { transform: translate(-50%, -50%) scale(0.94); opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(1);    opacity: 1; }
}

/* Placeholder breathing */
@keyframes placeholderBreath {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.5; }
}

/* Judge border rotation */
@keyframes borderRotate {
  0%   { filter: blur(1px) hue-rotate(0deg); }
  100% { filter: blur(1px) hue-rotate(360deg); }
}

/* ── Animation utility classes ───────────────────── */
.slam-in-left  { animation: leftSlamIn  450ms cubic-bezier(0.2, 0, 0.3, 1) both; }
.slam-in-right { animation: rightSlamIn 450ms cubic-bezier(0.2, 0, 0.3, 1) both; }

.rush-in      { animation: vsRushIn    520ms cubic-bezier(0.12, 0, 0.2, 1) both; }
.knock-right  { animation: vsKnockRight 380ms cubic-bezier(0.55, 0, 1, 0.45) forwards; }
.knock-left   { animation: vsKnockLeft  380ms cubic-bezier(0.55, 0, 1, 0.45) forwards; }

.slide-left-out  { animation: hardCutLeft  320ms cubic-bezier(0.55, 0, 1, 0) forwards; }
.slide-right-out { animation: hardCutRight 320ms cubic-bezier(0.55, 0, 1, 0) forwards; }

.slide-down { animation: slideDown 480ms cubic-bezier(0.34, 1.3, 0.64, 1) forwards; }
.slide-up   { animation: slideUp   300ms cubic-bezier(0.2,  0,   1,   0) forwards; }
</style>
```

- [ ] **Step 6: Run the smoke tests**

Run: `cd BES-frontend && npm test -- src/views/__tests__/BattleOverlay.test.js`
Expected: All 3 tests PASS.

- [ ] **Step 7: Run full frontend test suite**

Run: `cd BES-frontend && npm test`
Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue BES-frontend/src/views/__tests__/BattleOverlay.test.js
git commit -m "feat: rewrite BattleOverlay — new UI, angular design, hard-impact animations, overlay config, voting indicator"
```

---

### Task 10: Manual verification

- [ ] **Step 1: Start both services**

```bash
# Terminal 1 (backend)
cd BES && mvn spring-boot:run

# Terminal 2 (frontend)
cd BES-frontend && npm run dev
```

- [ ] **Step 2: Verify overlay config endpoint**

Visit `http://localhost:5050/api/v1/battle/overlay-config` in browser.
Expected: `{"showImages":true,"leftColor":"#dc2626","rightColor":"#2563eb"}`

- [ ] **Step 3: Verify OBS overlay at `/battle/overlay`**

Open `http://localhost:5173/battle/overlay` in browser.
Expected:
- Page background is transparent (checkerboard visible if using OBS browser source simulator)
- Both panels are positioned correctly (left 46%, right from 54%)
- No panel visible until a battle pair is set

- [ ] **Step 4: End-to-end battle flow**

1. Log in as organiser, navigate to `/battle/control`
2. Set up a battle pair → `initiateBattlePair` fires
3. Overlay: both name panels slam in from edges, VS rushes in from front, stage shakes at ~340ms
4. Click "Open Voting" → overlay shows pulsing red dot + "JUDGES VOTING" text; judge panel stays off-screen
5. Submit judge votes → votes stored internally, nothing visible on overlay
6. Click "Get Score" → judge panel slams down, vote cards burst in (staggered 55ms), winner expands, loser hard-cuts off screen
7. VS rockets off toward loser side with 55° spin
8. WINNER tag stamps in
9. Click "Next" → glitch overlay fires, panels exit, new entrance sequence

- [ ] **Step 5: Verify overlay config panel in BattleControl**

In BattleControl, open "Overlay Settings", change left color to `#00ff00`.
Expected: Overlay at `/battle/overlay` immediately reflects the new color on both panels, color bleeds, fill bars, and glow effects.

- [ ] **Step 6: Verify picture/no-picture toggle**

Toggle "Show Images" off in BattleControl.
Expected: Overlay switches to giant Anton SC names centered on each half, with team-color text shadows.
Toggle back on.
Expected: Overlay returns to portrait-image layout.

- [ ] **Step 7: Commit final**

```bash
git add .
git commit -m "chore: manual verification complete"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] §1 Architecture — Unified Overlay Config: Tasks 1–7 implement the full GET/POST/WS stack
- [x] §1 Overlay panel in BattleControl: Task 8
- [x] §2 UI Design — CSS custom properties, left-only panel positioning: Task 9 CSS
- [x] §2 No-picture mode (giant Anton SC, vertically centered): Task 9 template + CSS
- [x] §2 Picture mode (3/4 portrait, name overlaid at bottom): Task 9 template + CSS
- [x] §2 Structural elements (divider, corner bars, bottom edges, color bleeds, scanlines): Task 9 CSS
- [x] §2 Judge cards (parallelogram clip-path, frosted glass unvoted, color-variable glow): Task 9 CSS
- [x] §3 Animation: Entrance (slam-in + VS rush-in + stage shake): Task 9 script + CSS
- [x] §3 Animation: Voting indicator wired to `/topic/battle/phase` VOTING: Task 9 script
- [x] §3 Animation: Score reveal (panel drop + card burst stagger): Task 9 script + CSS
- [x] §3 Animation: Winner expansion + loser hard-cut + VS knock-off: Task 9 script + CSS
- [x] §3 Animation: Glitch cut + reset + new entrance: Task 9 script + CSS
- [x] §3 Judge votes hidden during voting, `votesVisible` only true after score: Task 9 script
- [x] §4 Smoke mode untouched: Task 9 template preserves `<template v-else><Chart /></template>`
- [x] §5 DB Migration V17: Task 1

**Type/name consistency check:**
- `votesVisible` — used in script (Task 9) and template (Task 9): ✓
- `showVotingIndicator` — used in script (Task 9) and template (Task 9): ✓
- `vsAnim` values: `'rush-in'`, `'knock-left'`, `'knock-right'` — match CSS classes `.rush-in`, `.knock-left`, `.knock-right`: ✓
- `slam-in-left` / `slam-in-right` — CSS class names match template `:class` bindings: ✓
- `slide-left-out` / `slide-right-out` — CSS class names match template `:class` bindings for `leftReset` / `rightReset`: ✓
- `getOverlayConfig` / `setOverlayConfig` — imported in api.js (Task 7) and used in BattleControl (Task 8) and BattleOverlay (Task 9): ✓
- `SetOverlayConfigDto` — created Task 2, imported in BattleController Task 4, tested Task 5: ✓
- `card-unvoted` CSS class — defined in style (Task 9), applied in template when `!votesVisible`: ✓
- `card-burst` CSS class — defined in style (Task 9), applied in template when `votesVisible`: ✓
