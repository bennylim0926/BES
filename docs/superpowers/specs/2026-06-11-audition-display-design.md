# Audition Display Screen — Design Spec

**Issue:** [#135](https://github.com/cyanogenicb/BES/issues/135)  
**Branch:** `feat/audition-display-screen`  
**Date:** 2026-06-11

## Overview

New public page `/audition/display` (no auth) — a full-screen OBS browser source that mirrors the emcee's current audition round + timer in real time via WebSocket.

## Architecture

```
EmceeRoundView (frontend)
  ├── on round change → POST /api/v1/event/audition-display
  └── on timer start/stop/reset → POST /api/v1/event/audition-display

Backend EventAuditionDisplayService
  ├── In-memory store: { eventName, genreName, mode, currentRound, totalRounds,
  │     currentSlots, nextSlots, timerStartedAt, timerDuration, timerRunning }
  └── Broadcasts to /topic/audition/{eventName}/display

AuditionDisplay.vue (new)
  ├── GET /api/v1/event/audition-display?event=X  → restore state on OBS refresh
  └── Subscribe /topic/audition/{eventName}/display → live updates
```

## Timer Persistence (survives page refresh on BOTH screens)

Backend stores `timerStartedAt` (epoch ms) + `timerDuration` (seconds) + `timerRunning` (bool). On mount, both EmceeRoundView and AuditionDisplay fetch current state and reconstruct:

```
timeLeft = max(0, timerDuration - floor((now - timerStartedAt) / 1000))
```

If `timerRunning` is true and `timeLeft > 0`, the client resumes the countdown from `timeLeft`. If `timeLeft <= 0`, shows finished state.

## Timer Efficiency

Instead of POSTing every second, the frontend only POSTs on start/stop/reset events. The display runs its own `setInterval` to tick down `timeLeft` locally. This eliminates ~60 req/min.

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/event/audition-display` | EmceeRoundView publishes state (round + timer) |
| GET | `/api/v1/event/audition-display?event=X` | Display fetches current state on mount/refresh |

Both are public (no auth) — same as battle overlay.

## Frontend Changes

### Timer.vue
- Add `started` / `stopped` / `tick` emits so parent knows timer state
- Add `resumeTimer(remainingSeconds, totalDuration)` exposed method for refresh recovery

### EmceeRoundView.vue
- New props: `eventName` (String), `genreName` (String)
- Watch `currentRound` → POST state to backend
- Handle timer events (start/stop) → POST state to backend
- On mount: fetch current state, resume timer if `timerRunning`

### AuditionList.vue
- Pass `eventName` and `genreName` props down to `<EmceeRoundView>`

## Files

| File | Action |
|------|--------|
| `BES/.../controllers/EventAuditionDisplayController.java` | New |
| `BES/.../services/EventAuditionDisplayService.java` | New |
| `BES/.../dtos/AuditionDisplayStateDto.java` | New |
| `BES-frontend/src/views/AuditionDisplay.vue` | New |
| `BES-frontend/src/components/EmceeRoundView.vue` | Modify |
| `BES-frontend/src/components/Timer.vue` | Modify |
| `BES-frontend/src/views/AuditionList.vue` | Modify |
| `BES-frontend/src/router/index.js` | Modify |
| `BES-frontend/src/utils/api.js` | Modify |
| `CLAUDE.md` | Modify |
