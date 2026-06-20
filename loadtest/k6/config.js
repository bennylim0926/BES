// Shared env loading + helpers. Imported by every scenario script.
//
// k6 exposes env vars via __ENV. The run-all.sh script source's .env and
// passes them through with -e KEY=VALUE.

export const BASE_URL = __ENV.BASE_URL || 'http://localhost';
export const EVENT_NAME = __ENV.EVENT_NAME || 'LOADTEST';
export const CATEGORIES = (__ENV.CATEGORIES || '').split(',').filter(Boolean);
export const DURATION = __ENV.DURATION || '5m';
export const ENABLE_WRITES = __ENV.ENABLE_WRITES === 'true';

export const HELPER_TOKEN = __ENV.HELPER_TOKEN || '';
export const EMCEE_TOKEN = __ENV.EMCEE_TOKEN || '';

export const JUDGE_TOKENS = [
  __ENV.JUDGE_TOKEN_1, __ENV.JUDGE_TOKEN_2, __ENV.JUDGE_TOKEN_3,
  __ENV.JUDGE_TOKEN_4, __ENV.JUDGE_TOKEN_5,
].filter(Boolean);

export const ORGANISER_USERNAME = __ENV.ORGANISER_USERNAME || '';
export const ORGANISER_PASSWORD = __ENV.ORGANISER_PASSWORD || '';

export const JSON_HEADERS = {
  'Accept': 'application/json',
  'Content-Type': 'application/json',
};

// k6's http.CookieJar is per-VU by default. Cookies set on login persist
// for subsequent requests in the same VU. No manual cookie handling needed.

export function requireEnv(...names) {
  for (const n of names) {
    if (!__ENV[n]) throw new Error(`Missing env var: ${n}. Copy loadtest/.env.example to loadtest/.env and fill it in.`);
  }
}
