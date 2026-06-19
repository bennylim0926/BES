// HELPER scenario — 5 VUs
//
// Real-world load per VU: one persistent STOMP WS subscribed to global
// broadcasts (app config) + periodic HTTP polls of the check-in list +
// heartbeat. HELPER token shared across all 5 VUs (ROLE_HELPER is in
// UNLIMITED_ROLES).

import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import {
  BASE_URL, EVENT_NAME, DURATION, ENABLE_WRITES, HELPER_TOKEN, requireEnv,
} from './config.js';
import { redeemToken, heartbeat } from './auth.js';
import {
  wsUrl, hostOf, connectFrame, subscribeFrame, disconnectFrame,
  cookieHeader, isConnected,
} from './stomp.js';

export const options = {
  scenarios: {
    helpers: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      maxDuration: DURATION,
    },
  },
  thresholds: {
    'http_req_duration{name:checkin:list}': ['p(95)<1500'],
    'http_req_failed': ['rate<0.05'],
    'ws_connecting': ['p(95)<2000'],
  },
};

export function setup() {
  requireEnv('HELPER_TOKEN', 'EVENT_NAME');
  return {};
}

export default function () {
  redeemToken(HELPER_TOKEN);

  const host = hostOf(BASE_URL);
  const params = { headers: { Cookie: cookieHeader() } };

  const res = ws.connect(wsUrl(), params, (socket) => {
    socket.on('open', () => socket.send(connectFrame(host)));

    socket.on('message', (msg) => {
      if (isConnected(msg)) {
        socket.send(subscribeFrame(0, '/topic/app-config'));
      }
    });

    // Poll every 5s, heartbeat every 30s.
    socket.setInterval(() => {
      http.get(
        `${BASE_URL}/api/v1/event/${encodeURIComponent(EVENT_NAME)}/checkin-list`,
        { tags: { name: 'checkin:list' } },
      );
    }, 5000);
    socket.setInterval(() => heartbeat(), 30000);

    // Close when the iteration's maxDuration is about to elapse.
    const runMs = durationToMs(DURATION) - 2000;
    socket.setTimeout(() => {
      socket.send(disconnectFrame());
      socket.close();
    }, runMs);

    socket.on('error', (e) => console.error(`ws error: ${e.error()}`));
  });

  check(res, { 'ws handshake 101': (r) => r && r.status === 101 });

  if (ENABLE_WRITES) {
    // Optional check-in writes — requires participant pool. See README TODO.
  }
}

function durationToMs(s) {
  const m = String(s).match(/^(\d+)\s*(ms|s|m|h)?$/);
  if (!m) return 60000;
  const n = Number(m[1]);
  return { ms: n, s: n * 1000, m: n * 60000, h: n * 3600000 }[m[2] || 's'];
}
