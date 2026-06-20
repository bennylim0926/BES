// STOMP-over-raw-WebSocket helpers for k6.
//
// Kyrove's WebSocketConfig.java does NOT use .withSockJS() — clients open
// a raw WS at /ws and speak STOMP text frames directly.
//
// STOMP frame format: COMMAND\n  header:value\n  ...  \n  body  \0
// We never send a body, so most frames are just headers.

import http from 'k6/http';
import { BASE_URL } from './config.js';

const NULL = String.fromCharCode(0);

export function wsUrl() {
  return BASE_URL.replace(/^http/, 'ws') + '/ws';
}

// k6's goja runtime has no WHATWG URL constructor, so strip the scheme
// and any path with a regex instead of `new URL(BASE_URL).host`.
export function hostOf(url) {
  return String(url).replace(/^https?:\/\//, '').replace(/\/.*$/, '');
}

export function connectFrame(host) {
  return [
    'CONNECT',
    'accept-version:1.2',
    `host:${host}`,
    'heart-beat:10000,10000',
    '', '',
  ].join('\n').replace(/\n$/, NULL);
}

export function subscribeFrame(id, destination) {
  return [
    'SUBSCRIBE',
    `id:sub-${id}`,
    `destination:${destination}`,
    '', '',
  ].join('\n').replace(/\n$/, NULL);
}

export function disconnectFrame() {
  return ['DISCONNECT', '', ''].join('\n').replace(/\n$/, NULL);
}

// Spring Security session lives in JSESSIONID cookie. The HTTP cookie jar
// is per-VU but is NOT auto-shared with k6/ws — pass cookies explicitly.
export function cookieHeader() {
  const jar = http.cookieJar();
  const cookies = jar.cookiesForURL(BASE_URL);
  return Object.entries(cookies)
    .map(([k, vs]) => `${k}=${Array.isArray(vs) ? vs[0] : vs}`)
    .join('; ');
}

// True when an inbound STOMP frame is a CONNECTED ack.
export function isConnected(frame) {
  return typeof frame === 'string' && frame.startsWith('CONNECTED');
}
