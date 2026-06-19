// ORGANISER scenario — 1 VU
//
// Per VU: password login → open STOMP WS subscribed to battle/state +
// app-config → poll battle state every 5s → heartbeat every 30s.

import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import {
  BASE_URL, EVENT_NAME, DURATION,
  ORGANISER_USERNAME, ORGANISER_PASSWORD, requireEnv,
} from './config.js';
import { passwordLogin, heartbeat } from './auth.js';
import {
  wsUrl, hostOf, connectFrame, subscribeFrame, disconnectFrame,
  cookieHeader, isConnected,
} from './stomp.js';

export const options = {
  scenarios: {
    organiser: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: DURATION,
    },
  },
  thresholds: {
    'http_req_duration{name:organiser:battle-state}': ['p(95)<1500'],
    'http_req_failed': ['rate<0.05'],
    'ws_connecting': ['p(95)<2000'],
  },
};

export function setup() {
  requireEnv('ORGANISER_USERNAME', 'ORGANISER_PASSWORD', 'EVENT_NAME');
  return {};
}

export default function () {
  passwordLogin(ORGANISER_USERNAME, ORGANISER_PASSWORD);

  const host = hostOf(BASE_URL);
  const params = { headers: { Cookie: cookieHeader() } };

  const res = ws.connect(wsUrl(), params, (socket) => {
    socket.on('open', () => socket.send(connectFrame(host)));

    socket.on('message', (msg) => {
      if (isConnected(msg)) {
        socket.send(subscribeFrame(0, '/topic/battle/state'));
        socket.send(subscribeFrame(1, '/topic/app-config'));
        socket.send(subscribeFrame(2, `/topic/emcee/active-categories/${EVENT_NAME}`));
      }
    });

    socket.setInterval(() => {
      http.get(
        `${BASE_URL}/api/v1/battle/state?eventName=${encodeURIComponent(EVENT_NAME)}`,
        { tags: { name: 'organiser:battle-state' } },
      );
      http.get(`${BASE_URL}/api/v1/auth/me`, { tags: { name: 'organiser:me' } });
    }, 5000);
    socket.setInterval(() => heartbeat(), 30000);

    const runMs = durationToMs(DURATION) - 2000;
    socket.setTimeout(() => {
      socket.send(disconnectFrame());
      socket.close();
    }, runMs);

    socket.on('error', (e) => console.error(`ws error: ${e.error()}`));
  });

  check(res, { 'ws handshake 101': (r) => r && r.status === 101 });
}

function durationToMs(s) {
  const m = String(s).match(/^(\d+)\s*(ms|s|m|h)?$/);
  if (!m) return 60000;
  const n = Number(m[1]);
  return { ms: n, s: n * 1000, m: n * 60000, h: n * 3600000 }[m[2] || 's'];
}
