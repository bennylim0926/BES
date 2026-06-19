# Kyrove Load Test

Simulates concurrent role-based load against a Kyrove deployment.

| Role       | VUs | Auth          |
|------------|-----|---------------|
| Helper     | 5   | HELPER token  |
| Emcee      | 5   | EMCEE token   |
| Judge      | 5   | per-judge token (×5) |
| Organiser  | 1   | username/password |

Each role runs as its own k6 scenario script. `run-all.sh` launches all four in parallel.

## Prerequisites

1. **k6** — `brew install k6`
2. **Dedicated test event on prod** — do NOT target a live event. Create one named e.g. `LOADTEST-2026-06-19` with at least 5 categories and a few participants.
3. **Organiser tier** — set to MAX in `/admin` so battle endpoints respond instead of 403.
4. **Session tokens** — open EventDetails → Session Links and generate:
   - 1 EMCEE token (shared across all 5 emcee VUs)
   - 1 HELPER token (shared across all 5 helper VUs)
   - 5 per-judge tokens (one per judge)

## Setup

```bash
cp loadtest/.env.example loadtest/.env
# Fill in BASE_URL, EVENT_NAME, tokens, organiser creds
```

## Run

```bash
cd loadtest
./run-all.sh
```

Default duration is 5 minutes (`DURATION` in `.env`). All four scenarios run in parallel; logs and JSON summaries are written to `loadtest/results/`.

## What gets exercised

**Read-only (default, `ENABLE_WRITES=false`):**
- Auth (token redeem + password login)
- Heartbeat (every iteration on emcee, every ~6 on helper, every iteration on organiser)
- Active-emcee polling
- Check-in list polling
- Judge divisions + scores polling
- Battle state polling

**Writes (`ENABLE_WRITES=true`):**
- Score submissions (idempotent; safe to repeat)
- Battle phase flips (writes to `battlePhase` in `BattleService`)
- Check-ins (commented out — requires participant pool, see TODO)

Default is read-only because (a) writes pollute the test event DB row and (b) most production traffic is reads anyway. Flip the flag when you want to stress the write path.

## Reading results

Per scenario, k6 prints a summary like:

```
http_req_duration..............: avg=120ms  p(95)=380ms
http_req_failed................: 0.21%   ✓ 5  ✗ 2410
checks.........................: 100.00% ✓ 1205
```

Watch for:
- `http_req_failed` > 5% — server is rejecting requests (check Spring logs)
- `http_req_duration p(95)` > thresholds in each script's `options.thresholds`
- Threshold failures cause the script to exit non-zero — `run-all.sh` propagates this

JSON summaries in `results/$timestamp.<role>.json` are k6's machine-readable export — pipe them into Grafana or just read by eye.

## What each VU does (with WebSocket)

Each VU now opens one persistent STOMP WebSocket (raw WS, no SockJS — confirmed in `WebSocketConfig.java:19`) and runs periodic HTTP polls from inside the WS event loop. One VU = one socket + a poller. That's the same shape as a real browser tab.

| Role | Subscribes to | HTTP polls |
|------|---------------|------------|
| Helper    | `/topic/app-config` | `GET /event/{event}/checkin-list` every 5s, heartbeat 30s |
| Emcee     | `/topic/emcee/active-categories/{event}`, `/topic/battle/state`, `/topic/battle/{event}/overlay-config`, `/topic/app-config` | `GET /emcee/active-categories` every 5s, heartbeat 30s |
| Judge     | `/topic/battle/state`, `/topic/app-config` | `GET /event/{event}/judge/{id}/divisions` + `GET /event/scores/{event}` every 3s |
| Organiser | `/topic/battle/state`, `/topic/app-config`, `/topic/emcee/active-categories/{event}` | `GET /battle/state` + `GET /auth/me` every 5s, heartbeat 30s |

Cookies from the per-VU HTTP jar are passed manually into `ws.connect` headers — k6 does not share its cookie jar with `k6/ws` automatically. See `stomp.js:cookieHeader()`.

## Limitations

- **No real participant data.** Score writes use synthetic participant names — the backend may reject if it validates against the participant table. Provision participants on the test event first if you turn writes on.
- **No CSRF.** Confirmed disabled in `SecurityConfig` (see `docs/test-scripts/battle-e2e.js`). If that ever changes, all POST/DELETE requests will start failing with 403.
- **STOMP frame parsing is one-way.** We send CONNECT/SUBSCRIBE/DISCONNECT and detect the inbound `CONNECTED` ack by string prefix, but we don't parse `MESSAGE` payloads. That's fine for a load test — the broadcast fanout cost is paid by the server regardless.

## Teardown

After the run, on prod:
1. Delete the test event (cascades participant + score rows)
2. Revoke the session tokens (EventDetails → Session Links → delete each)
3. Optional: switch the organiser back to PRO tier if it was bumped to MAX just for testing
