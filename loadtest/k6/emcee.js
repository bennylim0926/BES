// EMCEE scenario — 5 VUs, one category each
//
// Per VU: redeem EMCEE token → claim category → open STOMP WS subscribed
// to active-categories + battle/state + overlay-config → heartbeat every
// 30s → poll active-categories every 5s.

import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import {
  BASE_URL, EVENT_NAME, CATEGORIES, DURATION, ENABLE_WRITES,
  EMCEE_TOKEN, JSON_HEADERS, requireEnv,
} from './config.js';
import { redeemToken, heartbeat } from './auth.js';
import {
  wsUrl, hostOf, connectFrame, subscribeFrame, disconnectFrame,
  cookieHeader, isConnected,
} from './stomp.js';

export const options = {
  scenarios: {
    emcees: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      maxDuration: DURATION,
    },
  },
  thresholds: {
    'http_req_duration{name:emcee:active-categories}': ['p(95)<1500'],
    'http_req_failed': ['rate<0.05'],
    'ws_connecting': ['p(95)<2000'],
  },
};

export function setup() {
  requireEnv('EMCEE_TOKEN', 'EVENT_NAME', 'CATEGORIES');
  if (CATEGORIES.length < 5) {
    throw new Error(`Need at least 5 CATEGORIES; got ${CATEGORIES.length}`);
  }
  return {};
}

export default function () {
  const myCategory = CATEGORIES[(__VU - 1) % CATEGORIES.length];

  redeemToken(EMCEE_TOKEN);
  http.post(
    `${BASE_URL}/api/v1/emcee/active-category`,
    JSON.stringify({ eventName: EVENT_NAME, categoryName: myCategory }),
    { headers: JSON_HEADERS, tags: { name: 'emcee:claim' } },
  );

  const host = hostOf(BASE_URL);
  const params = { headers: { Cookie: cookieHeader() } };

  const res = ws.connect(wsUrl(), params, (socket) => {
    socket.on('open', () => socket.send(connectFrame(host)));

    socket.on('message', (msg) => {
      if (isConnected(msg)) {
        socket.send(subscribeFrame(0, `/topic/emcee/active-categories/${EVENT_NAME}`));
        socket.send(subscribeFrame(1, '/topic/battle/state'));
        socket.send(subscribeFrame(2, `/topic/battle/${EVENT_NAME}/overlay-config`));
        socket.send(subscribeFrame(3, '/topic/app-config'));
      }
    });

    socket.setInterval(() => {
      http.get(
        `${BASE_URL}/api/v1/emcee/active-categories?eventName=${encodeURIComponent(EVENT_NAME)}`,
        { tags: { name: 'emcee:active-categories' } },
      );
    }, 5000);
    socket.setInterval(() => heartbeat(), 30000);

    if (ENABLE_WRITES) {
      // Phase flip every 30s — exercises BattleService.setBattlePhaseService
      // and the /topic/battle/state broadcast fanout.
      let phaseIdx = 0;
      const phases = ['IDLE', 'LOCKED', 'VOTING', 'REVEALED'];
      socket.setInterval(() => {
        http.post(
          `${BASE_URL}/api/v1/battle/phase`,
          JSON.stringify({ phase: phases[phaseIdx++ % 4], eventName: EVENT_NAME }),
          { headers: JSON_HEADERS, tags: { name: 'emcee:phase' } },
        );
      }, 30000);
    }

    const runMs = durationToMs(DURATION) - 2000;
    socket.setTimeout(() => {
      socket.send(disconnectFrame());
      socket.close();
    }, runMs);

    socket.on('error', (e) => console.error(`ws error: ${e.error()}`));
  });

  check(res, { 'ws handshake 101': (r) => r && r.status === 101 });

  // Best-effort release after WS closes
  http.del(`${BASE_URL}/api/v1/emcee/active-category`, null, {
    tags: { name: 'emcee:release' },
  });
}

function durationToMs(s) {
  const m = String(s).match(/^(\d+)\s*(ms|s|m|h)?$/);
  if (!m) return 60000;
  const n = Number(m[1]);
  return { ms: n, s: n * 1000, m: n * 60000, h: n * 3600000 }[m[2] || 's'];
}
