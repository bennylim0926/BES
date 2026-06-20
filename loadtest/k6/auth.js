// Auth helpers — redeem session token or username/password login.
// Both write the session cookie into k6's per-VU cookie jar so subsequent
// requests are authenticated automatically.

import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL, JSON_HEADERS } from './config.js';

export function redeemToken(tokenId) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/token`,
    JSON.stringify({ tokenId }),
    { headers: JSON_HEADERS, tags: { name: 'auth:token' } },
  );
  const ok = check(res, { 'token redeem 200': (r) => r.status === 200 });
  if (!ok) fail(`token redeem failed: status=${res.status} body=${res.body}`);
  return res.json();
}

export function passwordLogin(username, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: JSON_HEADERS, tags: { name: 'auth:login' } },
  );
  const ok = check(res, { 'login 200': (r) => r.status === 200 });
  if (!ok) fail(`login failed: status=${res.status} body=${res.body}`);
  return res.json();
}

export function heartbeat() {
  return http.post(`${BASE_URL}/api/v1/auth/heartbeat`, null, {
    tags: { name: 'auth:heartbeat' },
  });
}
