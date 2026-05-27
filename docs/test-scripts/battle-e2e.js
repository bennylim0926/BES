#!/usr/bin/env node
/**
 * BES E2E Battle Test Script
 *
 * Automatically sets up a full battle event from scratch and runs through
 * the entire bracket with automated judge votes.
 *
 * Usage:  node docs/test-scripts/battle-e2e.js
 * Requires: Node 18+, BES running at http://localhost (Docker)
 *
 * What it does:
 *   1. Creates genre + 3 judges + event (access code 1234)
 *   2. Adds 16 walk-in participants with real stage names
 *   3. Submits audition scores from all 3 judges
 *   4. Seeds a 16-player bracket by rank (score descending)
 *   5. Assigns battle judges
 *   6. Runs all 15 matches (R16 → QF → SF → Final) with delays
 *   7. Votes mix of left win / right win / tie each match
 *
 * Open in browser while running:
 *   http://localhost/battle/overlay   (stream overlay)
 *   http://localhost/battle/judge     (judge view — pick a name)
 *   http://localhost/battle/bracket   (bracket visualization)
 */

'use strict';

const BASE            = 'http://localhost';
const EVENT_NAME      = 'E2E Battle Test';
const GENRE_NAME      = 'popping';  // must match existing genre name (case-sensitive)
const ACCESS_CODE     = '1234';
const ADMIN_USER      = process.env.BES_ADMIN_USER ?? 'admin';
const ADMIN_PASS      = process.env.BES_ADMIN_PASSWORD ?? 'change-me-admin';
const DELAY_LOCKED_MS = 3_000;   // pause in LOCKED before opening voting
const DELAY_VOTED_MS  = 1_500;   // pause after all votes before tallying
const DELAY_REVEAL_MS = 5_000;   // pause in REVEALED so you can see the result

// ─── Participants ────────────────────────────────────────────────────────────
// 16 real-feeling dance-scene stage names, varied lengths
const STAGE_NAMES = [
  'Lil Flex',          // short
  'Nova',              // very short
  'Thunderfoot',       // medium
  'Ray',               // very short
  'Mikael Breakz',     // medium-long
  'SoulsnatcH',        // medium
  'Dyzee',             // short
  'Victor Manuelle',   // long
  'Frosty',            // short
  'KID KRAZZY',        // medium
  'Morphine',          // medium
  'BeatRockah',        // medium
  'Wickedboy',         // medium
  'TONIIBOI',          // medium
  'Shinsekai no Ryuu', // long
  'El Fuego del Sur',  // long
];

// Prefix with "E2E" so we can clean up dupes reliably on each run
const JUDGE_NAMES = ['E2E Alpha', 'E2E Beta', 'E2E Gamma'];

// Scores per judge per participant (index 0 = highest scorer = seed 1)
// Small variance between judges to keep it realistic
const JUDGE_SCORES = [
  [97, 94, 91, 88, 85, 83, 81, 79, 77, 75, 73, 71, 69, 67, 65, 63],
  [95, 92, 90, 87, 84, 82, 80, 78, 76, 74, 72, 70, 68, 66, 64, 62],
  [96, 93, 89, 86, 83, 81, 79, 78, 75, 73, 71, 70, 68, 65, 63, 61],
];

// ─── Vote pattern ────────────────────────────────────────────────────────────
// 15 matches total: 8 (R16) + 4 (QF) + 2 (SF) + 1 (Final)
// Each entry: [judge0_vote, judge1_vote, judge2_vote]
// 0 = left wins,  1 = right wins,  2 = tie
// Winner = majority; tie in counts → left wins (predictable champion: Lil Flex)
const MATCH_VOTES = [
  /* R16 */ [0, 0, 0],  // M1  left wins   (seed  1 beats seed 16)
             [1, 1, 0],  // M2  right wins  (upset: seed 15 beats seed  2)
             [0, 0, 1],  // M3  left wins   (seed  3 beats seed 14)
             [1, 1, 1],  // M4  right wins  (upset: seed 13 beats seed  4)
             [0, 0, 0],  // M5  left wins   (seed  5 beats seed 12)
             [0, 2, 1],  // M6  left wins   (seed  6 beats seed 11, one tie vote)
             [2, 1, 1],  // M7  right wins  (seed 10 beats seed  7, one tie vote)
             [0, 0, 0],  // M8  left wins   (seed  8 beats seed  9)
  /* QF  */ [0, 0, 0],  // M9  left wins
             [1, 1, 0],  // M10 right wins
             [0, 1, 0],  // M11 left wins
             [1, 1, 1],  // M12 right wins
  /* SF  */ [0, 0, 0],  // M13 left wins
             [1, 0, 1],  // M14 right wins
  /* FIN */ [0, 0, 0],  // M15 left wins  → champion
];

// ─── Networking ──────────────────────────────────────────────────────────────
const jar = {};

function parseCookies(res) {
  for (const raw of (res.headers.getSetCookie?.() ?? [])) {
    const [pair] = raw.split(';');
    const eq = pair.indexOf('=');
    if (eq > 0) jar[pair.slice(0, eq).trim()] = pair.slice(eq + 1).trim();
  }
}

function cookieHeader() {
  return Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; ');
}

async function req(method, path, body) {
  const headers = { Accept: 'application/json', Cookie: cookieHeader() };
  if (method !== 'GET') {
    headers['Content-Type'] = 'application/json';
    // CSRF is disabled in SecurityConfig — no X-XSRF-TOKEN needed
  }
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
    redirect: 'manual',
  });
  parseCookies(res);
  return res;
}

const get  = (p)    => req('GET',    p);
const post = (p, b) => req('POST',   p, b);
const del  = (p, b) => req('DELETE', p, b);

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// ─── Logging helpers ─────────────────────────────────────────────────────────
const C = { reset: '\x1b[0m', bold: '\x1b[1m', cyan: '\x1b[36m', green: '\x1b[32m',
            red: '\x1b[31m', yellow: '\x1b[33m', dim: '\x1b[2m' };

function ts()  { return `${C.dim}[${new Date().toISOString().slice(11,19)}]${C.reset}`; }
function log(m)  { console.log(`${ts()} ${m}`); }
function ok(m)   { console.log(`${ts()} ${C.green}✓${C.reset} ${m}`); }
function warn(m) { console.log(`${ts()} ${C.yellow}⚠${C.reset}  ${m}`); }
function fail(m) { console.log(`${ts()} ${C.red}✗${C.reset} ${m}`); }
function hdr(m)  { console.log(`\n${C.bold}${C.yellow}══════ ${m} ══════${C.reset}`); }

// ─── Helpers ─────────────────────────────────────────────────────────────────
function majority(votes) {
  const l = votes.filter(v => v === 0).length;
  const r = votes.filter(v => v === 1).length;
  return r > l ? 'right' : 'left'; // ties → left
}

function pName(p) {
  return p?.participantName ?? p?.stageName ?? p?.name ?? p?.displayName ?? '???';
}

function pScore(s) {
  return s?.averageScore ?? s?.avgScore ?? s?.average ?? s?.totalScore ?? s?.score ?? 0;
}

function emptyRounds() {
  return {
    Top16: Array(8).fill(null).map(() => [null, null, null]),
    Top8:  Array(4).fill(null).map(() => [null, null, null]),
    Top4:  Array(2).fill(null).map(() => [null, null, null]),
    Top2:  [[null, null, null]],
    Top1:  [[null]],
  };
}

// ─── Main ─────────────────────────────────────────────────────────────────────
async function main() {

  // ── 1. Login ──────────────────────────────────────────────────────────────
  hdr('LOGIN');
  const loginRes = await post('/api/v1/auth/login', { username: ADMIN_USER, password: ADMIN_PASS });
  if (!loginRes.ok) {
    fail(`Login failed (status ${loginRes.status}) — is Docker running?`);
    process.exit(1);
  }
  ok(`Logged in as ${ADMIN_USER}`);

  // ── 2. Genre ──────────────────────────────────────────────────────────────
  hdr('GENRE');
  // Genre "popping" already exists in DB — just verify it's there
  const genreListR = await get('/api/v1/event/genre');
  const genreList  = genreListR.ok ? await genreListR.json() : [];
  const genreExists = genreList.some(g => g.genreName === GENRE_NAME);
  if (!genreExists) {
    // Create it if missing
    const gr = await post('/api/v1/admin/genre', { name: GENRE_NAME });
    gr.ok ? ok(`Genre "${GENRE_NAME}" created`) : warn(`Genre create returned ${gr.status}`);
  } else {
    ok(`Genre "${GENRE_NAME}" found in global list`);
  }

  // ── 3. Judges (clean up first, then create fresh) ─────────────────────────
  hdr('JUDGES');
  // Remove E2E judges cleanly:
  //  1. Delete all scores for the test event (removes FK score→judge references)
  //  2. Unlink E2E judges from every event (removes FK event_judge→judge references)
  //  3. Delete judges globally
  {
    // Delete test event scores first so score FK doesn't block judge deletion
    const evListR0 = await get('/api/v1/event/events');
    const evList0  = evListR0.ok ? await evListR0.json() : [];
    const testEv   = evList0.find(e => (e.eventName ?? e.name) === EVENT_NAME);
    if (testEv) {
      const sdR = await del('/api/v1/admin/score', { event_id: testEv.id });
      sdR.ok ? log(`  Cleared scores for "${EVENT_NAME}" (event id=${testEv.id})`)
             : warn(`  Score clear returned ${sdR.status} (may be empty)`);
    }

    // Find all E2E judges globally
    const allR  = await get('/api/v1/event/judges');
    const all   = allR.ok ? await allR.json() : [];
    const e2eJs = all.filter(j => JUDGE_NAMES.includes(j.judgeName));

    if (e2eJs.length) {
      // Unlink from every event
      const evListR = await get('/api/v1/event/events');
      const evList  = evListR.ok ? await evListR.json() : [];
      for (const ev of evList) {
        const evName  = ev.eventName ?? ev.name;
        const evJdgR  = await get(`/api/v1/event/${encodeURIComponent(evName)}/judges`);
        const evJdgs  = evJdgR.ok ? await evJdgR.json() : [];
        for (const j of evJdgs.filter(j => JUDGE_NAMES.includes(j.judgeName))) {
          await del(`/api/v1/event/${encodeURIComponent(evName)}/judge/${j.judgeId}`);
          log(`  Unlinked "${j.judgeName}" from event "${evName}"`);
        }
      }

      // Delete globally (scores already removed, so no FK block)
      for (const j of e2eJs) {
        const dr = await del('/api/v1/admin/judge', { id: j.judgeId });
        dr.ok
          ? log(`  Deleted stale judge "${j.judgeName}" id=${j.judgeId}`)
          : warn(`  Could not delete judge ${j.judgeId} — may still have refs`);
      }
      ok(`Cleaned up ${e2eJs.length} stale test judge(s)`);
    } else {
      log('  No stale E2E judges found');
    }
  }

  const judgeIds = [];
  for (const name of JUDGE_NAMES) {
    const r   = await post('/api/v1/admin/judge', { judgeName: name });
    const all = r.ok ? await r.json() : [];
    const found = Array.isArray(all) ? all.find(j => j.judgeName === name) : null;
    if (found) {
      judgeIds.push(found.judgeId);
      ok(`Created judge "${name}" id=${found.judgeId}`);
    } else {
      fail(`Cannot create judge "${name}"`);
      process.exit(1);
    }
  }

  // ── 4. Event ──────────────────────────────────────────────────────────────
  hdr('EVENT');
  const evListR = await get('/api/v1/event/events');
  const evList  = evListR.ok ? await evListR.json() : [];
  let event = evList.find(e => (e.eventName ?? e.name) === EVENT_NAME);

  if (!event) {
    const r = await post('/api/v1/event', { eventName: EVENT_NAME, paymentRequired: false });
    if (!r.ok) { fail('Failed to create event'); process.exit(1); }
    const evList2R = await get('/api/v1/event/events');
    const evList2  = evList2R.ok ? await evList2R.json() : [];
    event = evList2.find(e => (e.eventName ?? e.name) === EVENT_NAME);
    ok(`Event "${EVENT_NAME}" created`);
  } else {
    warn(`Event "${EVENT_NAME}" already exists (id=${event.id})`);
  }

  if (!event) { fail('Event not found after creation'); process.exit(1); }
  const eventId = event.id;
  log(`Event ID: ${eventId}`);

  // Access code
  const acR = await post('/api/v1/event/access-code', { eventId, newCode: ACCESS_CODE });
  acR.ok ? ok(`Access code set to ${ACCESS_CODE}`) : warn('Access code update returned non-OK (may already be set)');

  // Link genre (genreName must be an array per backend DTO)
  const linkedR = await get(`/api/v1/event/${encodeURIComponent(EVENT_NAME)}/genres`);
  const linked  = linkedR.ok ? await linkedR.json() : [];
  if (!linked.some(g => g.genreName === GENRE_NAME)) {
    const lgR = await post('/api/v1/event/genre', {
      eventName: EVENT_NAME,
      genreName: [GENRE_NAME],
      genreFormats: {},
    });
    lgR.ok ? ok(`Genre "${GENRE_NAME}" linked to event`) : warn(`Genre link returned ${lgR.status}`);
  } else {
    ok(`Genre "${GENRE_NAME}" already linked`);
  }

  // Note: do NOT call POST /{event}/judge — that endpoint always creates a new judge row
  // (see JudgeService.addJudgeToEvent). ScoreService looks up judges globally by name,
  // so the globally-created judges above are sufficient for score submission.

  // ── 5. Walk-in participants ───────────────────────────────────────────────
  hdr('PARTICIPANTS');
  const ptCheckR = await get(`/api/v1/event/participants/${encodeURIComponent(EVENT_NAME)}`);
  const existing = ptCheckR.ok ? await ptCheckR.json() : [];
  const existingNames = new Set(existing.map(p => pName(p)));

  let added = 0;
  for (let i = 0; i < STAGE_NAMES.length; i++) {
    const name  = STAGE_NAMES[i];
    const judge = JUDGE_NAMES[i % JUDGE_NAMES.length];
    if (existingNames.has(name)) {
      log(`  ${C.dim}skip${C.reset} ${name} (already exists)`);
      continue;
    }
    // Use empty judgeName for walk-ins to avoid findByName duplicate issues;
    // judge assignment happens implicitly via score submission
    const r = await post('/api/v1/event/walkins/', {
      name,
      eventName: EVENT_NAME,
      genre:     GENRE_NAME,
      judgeName: '',
      teamMembers: [],
      teamName:    '',
    });
    r.ok ? log(`  + ${name}`) : warn(`  Failed to add ${name} (${r.status})`);
    added++;
  }
  ok(`Participants ready (${added} added, ${existingNames.size} already existed)`);

  // ── 6. Scores ─────────────────────────────────────────────────────────────
  hdr('AUDITION SCORES');
  const ptR    = await get(`/api/v1/event/participants/${encodeURIComponent(EVENT_NAME)}`);
  const ptList = ptR.ok ? await ptR.json() : [];
  log(`Participants found: ${ptList.length}`);

  for (let ji = 0; ji < JUDGE_NAMES.length; ji++) {
    const judgeName = JUDGE_NAMES[ji];
    const participantScore = ptList.map(p => {
      const name = pName(p);
      const idx  = STAGE_NAMES.indexOf(name);
      // ParticipantScoreDto expects "participantName" (not "name")
      return { participantName: name, score: idx >= 0 ? JUDGE_SCORES[ji][idx] : 75 };
    });
    const r = await post('/api/v1/event/scores', {
      eventName: EVENT_NAME,
      genreName: GENRE_NAME,
      judgeName,
      participantScore,
    });
    r.ok ? ok(`Scores from ${judgeName}`) : warn(`Score submit by ${judgeName} returned ${r.status}`);
  }

  // ── 7. Bracket seeding ────────────────────────────────────────────────────
  hdr('BRACKET SEEDING');
  const sdR   = await get(`/api/v1/event/scores/${encodeURIComponent(EVENT_NAME)}`);
  const sdRaw = sdR.ok ? await sdR.json() : [];

  // Aggregate — handle both "one row per participant" and "one row per judge×participant"
  const scoreMap = {};
  for (const s of sdRaw) {
    const name = pName(s);
    if (!scoreMap[name]) scoreMap[name] = { name, ...s, _total: 0, _count: 0 };
    const v = pScore(s);
    scoreMap[name]._total += v;
    scoreMap[name]._count += 1;
  }

  const ranked = Object.values(scoreMap)
    .map(s => ({ ...s, _avg: s._count > 0 ? s._total / s._count : 0 }))
    .sort((a, b) => b._avg - a._avg)
    .slice(0, 16);

  if (ranked.length < 16) {
    fail(`Only ${ranked.length} scored participants — need exactly 16`);
    process.exit(1);
  }

  log('Seeding (rank → name → avg):');
  ranked.forEach((p, i) => log(`  ${String(i+1).padStart(2)}. ${pName(p).padEnd(20)} ${p._avg.toFixed(1)}`));

  // Build bracket: seed 1 vs 16, 2 vs 15, …, 8 vs 9
  const rounds = emptyRounds();
  for (let i = 0; i < 8; i++) {
    rounds.Top16[i] = [ranked[i], ranked[15 - i], null];
  }

  const seedR = await post('/api/v1/battle/bracket', { rounds, topSize: '16' });
  seedR.ok ? ok('Bracket posted to server') : warn(`Bracket post returned ${seedR.status}`);

  // ── 8. Battle judges ──────────────────────────────────────────────────────
  hdr('BATTLE JUDGES');

  // Response shape: { judges: [{ id, name, vote }, …] }
  function parseBJudges(raw) {
    if (Array.isArray(raw)) return raw;
    if (raw && Array.isArray(raw.judges)) return raw.judges;
    return [];
  }

  // Clear any stale battle judges
  const staleR = await get('/api/v1/battle/judges');
  const stale  = parseBJudges(staleR.ok ? await staleR.json() : []);
  for (const bj of stale) {
    await del('/api/v1/battle/judge', { id: Number(bj.id) });
  }
  if (stale.length) log(`Cleared ${stale.length} stale battle judge(s)`);

  // Assign our 3 judges
  for (const jid of judgeIds) {
    const r = await post('/api/v1/battle/judge', { id: Number(jid) });
    r.ok ? ok(`Battle judge id=${jid} assigned`) : warn(`Battle judge assign returned ${r.status}`);
  }

  const bjR     = await get('/api/v1/battle/judges');
  const bJudges = parseBJudges(bjR.ok ? await bjR.json() : []);
  if (bJudges.length < 1) {
    fail('No battle judges registered — cannot vote');
    process.exit(1);
  }
  log(`Battle judges: ${bJudges.map(j => `${j.name ?? j.judgeName}(${j.id})`).join(', ')}`);

  // ── 9. Reset phase ────────────────────────────────────────────────────────
  await post('/api/v1/battle/phase', { phase: 'IDLE' });
  log('Phase reset to IDLE');

  // ── 10. Battle sequence ───────────────────────────────────────────────────
  hdr('READY');
  console.log(`
  ${C.bold}Open these in your browser now:${C.reset}
    ${C.cyan}http://localhost/battle/overlay${C.reset}   ← stream overlay
    ${C.cyan}http://localhost/battle/bracket${C.reset}   ← live bracket
    ${C.cyan}http://localhost/battle/judge${C.reset}     ← judge view (open 3 tabs, pick a name each)

  Starting in 8 seconds…
`);
  await sleep(8_000);

  const roundOrder = [
    { key: 'Top16', label: 'ROUND OF 16',  count: 8, nextKey: 'Top8' },
    { key: 'Top8',  label: 'QUARTER-FINAL',count: 4, nextKey: 'Top4' },
    { key: 'Top4',  label: 'SEMI-FINAL',   count: 2, nextKey: 'Top2' },
    { key: 'Top2',  label: 'FINAL',        count: 1, nextKey: 'Top1' },
  ];

  let matchIndex = 0;

  for (const { key, label, count, nextKey } of roundOrder) {
    hdr(label);

    for (let pairIdx = 0; pairIdx < count; pairIdx++) {
      const pair   = rounds[key][pairIdx];
      const left   = pair[0];
      const right  = pair[1];
      const votes  = MATCH_VOTES[matchIndex];
      const side   = majority(votes);
      const winner = side === 'left' ? left : right;

      const lName = pName(left);
      const rName = pName(right);

      console.log(`\n  ${C.bold}Match ${matchIndex + 1}${C.reset}  ${C.cyan}${lName}${C.reset} vs ${C.cyan}${rName}${C.reset}`);

      // ── LOCKED ──
      await post('/api/v1/battle/phase', { phase: 'LOCKED' });
      await post('/api/v1/battle/battle-pair', { leftBattler: left, rightBattler: right });
      log(`  → LOCKED  (${lName} ↔ ${rName})`);
      await sleep(DELAY_LOCKED_MS);

      // ── VOTING ──
      await post('/api/v1/battle/phase', { phase: 'VOTING' });
      log(`  → VOTING`);
      await sleep(500); // tiny gap so overlay can show VOTING before votes arrive

      for (let ji = 0; ji < bJudges.length; ji++) {
        const vote    = votes[ji] ?? 0;
        const jName   = bJudges[ji].name ?? bJudges[ji].judgeName ?? `Judge ${ji+1}`;
        const voteStr = ['LEFT ←', 'RIGHT →', 'TIE ~'][vote] ?? '?';
        await post('/api/v1/battle/vote', { id: Number(bJudges[ji].id), vote: Number(vote) });
        log(`  → ${jName} voted ${voteStr}`);
        await sleep(300);
      }

      await sleep(DELAY_VOTED_MS);

      // Tally
      await post('/api/v1/battle/score');

      // Update bracket
      rounds[key][pairIdx][2] = winner;
      const nextPairIdx = Math.floor(pairIdx / 2);
      const nextSide    = pairIdx % 2; // 0=left slot, 1=right slot
      if (nextKey === 'Top1') {
        rounds['Top1'][0][0] = winner;
      } else if (nextKey) {
        if (!rounds[nextKey][nextPairIdx]) rounds[nextKey][nextPairIdx] = [null, null, null];
        rounds[nextKey][nextPairIdx][nextSide] = winner;
      }
      await post('/api/v1/battle/bracket', { rounds, topSize: '16' });

      // ── REVEALED ──
      await post('/api/v1/battle/phase', { phase: 'REVEALED' });
      console.log(`  → REVEALED  ${C.bold}${C.green}${pName(winner)} WINS${C.reset}`);
      await sleep(DELAY_REVEAL_MS);

      matchIndex++;
    }
  }

  // ── 11. Finish ────────────────────────────────────────────────────────────
  const champion = rounds['Top1'][0][0];
  await post('/api/v1/battle/phase', { phase: 'IDLE' });

  console.log(`
${C.bold}${C.yellow}════════════════════════════════${C.reset}
${C.bold}${C.yellow}  🏆  CHAMPION: ${pName(champion)}${C.reset}
${C.bold}${C.yellow}════════════════════════════════${C.reset}
`);
  ok('Phase set to IDLE — done!');
}

main().catch(e => {
  console.error('\nFatal error:', e.message ?? e);
  process.exit(1);
});
