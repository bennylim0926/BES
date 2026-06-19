// JUDGE scenario — 5 VUs, distinct tokens
//
// Per VU: redeem own JUDGE token → open STOMP WS subscribed to battle/state
// → poll divisions + scores every 3s → score-write every 6s (if enabled).

import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import {
  BASE_URL, EVENT_NAME, CATEGORIES, DURATION, ENABLE_WRITES,
  JUDGE_TOKENS, JSON_HEADERS, requireEnv,
} from './config.js';
import { redeemToken } from './auth.js';
import {
  wsUrl, hostOf, connectFrame, subscribeFrame, disconnectFrame,
  cookieHeader, isConnected,
} from './stomp.js';

export const options = {
  scenarios: {
    judges: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      maxDuration: DURATION,
    },
  },
  thresholds: {
    'http_req_duration{name:judge:scores-read}': ['p(95)<2000'],
    'http_req_failed': ['rate<0.05'],
    'ws_connecting': ['p(95)<2000'],
  },
};

export function setup() {
  requireEnv('EVENT_NAME', 'CATEGORIES');
  if (JUDGE_TOKENS.length < 5) throw new Error(`Need 5 JUDGE_TOKEN_1..5; got ${JUDGE_TOKENS.length}`);
  return {};
}

export default function () {
  const token = JUDGE_TOKENS[(__VU - 1) % JUDGE_TOKENS.length];
  const myCategory = CATEGORIES[(__VU - 1) % CATEGORIES.length];

  // judgeId + judgeName come back in the redeem response — no separate env var needed.
  const me = redeemToken(token);
  const judgeId = me.judgeId;
  const judgeName = me.judgeName || '';
  if (!judgeId) throw new Error(`JUDGE_TOKEN_${__VU} redeemed but no judgeId in response — is it a per-judge token?`);

  const host = hostOf(BASE_URL);
  const params = { headers: { Cookie: cookieHeader() } };

  const res = ws.connect(wsUrl(), params, (socket) => {
    socket.on('open', () => socket.send(connectFrame(host)));

    socket.on('message', (msg) => {
      if (isConnected(msg)) {
        socket.send(subscribeFrame(0, '/topic/battle/state'));
        socket.send(subscribeFrame(1, '/topic/app-config'));
      }
    });

    socket.setInterval(() => {
      http.get(
        `${BASE_URL}/api/v1/event/${encodeURIComponent(EVENT_NAME)}/judge/${judgeId}/divisions`,
        { tags: { name: 'judge:divisions' } },
      );
      http.get(
        `${BASE_URL}/api/v1/event/scores/${encodeURIComponent(EVENT_NAME)}`,
        { tags: { name: 'judge:scores-read' } },
      );
    }, 3000);

    if (ENABLE_WRITES && judgeName) {
      let i = 0;
      socket.setInterval(() => {
        const auditionNumber = ((__VU - 1) * 100) + ((i++) % 16) + 1;
        const payload = {
          eventName: EVENT_NAME,
          categoryName: myCategory,
          judgeName,
          participantScore: [{
            participantName: `LOADTEST P${auditionNumber}`,
            auditionNumber,
            score: 5 + (i % 5),
          }],
        };
        http.post(
          `${BASE_URL}/api/v1/event/scores`,
          JSON.stringify(payload),
          { headers: JSON_HEADERS, tags: { name: 'judge:scores-write' } },
        );
      }, 6000);
    }

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
