# Harness Diagnosis — the 3 biggest failure modes and their fixes

Written 2026-07-03 by Claude Fable 5 after a full audit of this environment (CLAUDE.md, `~/.claude/settings.json`, project skills/commands, auto-memory, plugins, repo state). Every claim below was verified against the actual environment on that date — nothing is from memory.

This file is the **rationale** for the whole `docs/agents/` system. Read it when you want to know *why* a rule exists, or before proposing to change one.

---

## Failure mode 1 — Contradictory and rotten instructions across the four instruction layers

There are four layers that inject instructions into every session: **CLAUDE.md → auto-memory → skills/commands → plugin hooks**. They were written at different times and never reconciled. A weaker model cannot tell which rule is current — it obeys whichever it read last, or whichever is phrased most emphatically.

Concrete contradictions found in a single audit pass (2026-07-03):

| # | Contradiction / rot | Cost if obeyed |
|---|---|---|
| 1 | CLAUDE.md said "Latest migration: V43 → next is V44". Actual latest: **V50**. | New migration numbered V44 → Flyway collision on someone else's file |
| 2 | MEMORY.md index line said "**Always** use --no-cache" while the file it points to (`feedback_docker_frontend_rebuild.md`) was corrected 15 days earlier to say the **opposite**. | ~1 GB disk per rebuild, minutes of wasted build time |
| 3 | MEMORY.md said organiser tier is "paused mid-flow, next is writing-plans". The memory file itself says **SHIPPED, PR #143, 2026-06-15**. | A session could try to re-plan a shipped feature |
| 4 | MEMORY.md line "Run pre-push-verify before every push" points at `feedback_issue_breakdown.md`, which contains no such rule. | Broken pointer; the actual rule had no home |
| 5 | `project_design_system.md` memory (Apr 2026) prescribed **red-primary Tesla palette**; the shipped design system makes red **error-only**. | A model recalling memory first paints off-system UI |
| 6 | `common_mistakes.md` #4/#13 command "mvn clean package before docker build" because "Docker COPYs the host-built JAR" — but the root `Dockerfile` is now **multi-stage and builds the JAR in-container**. | Needless local builds; #13 even commands `--no-cache` |
| 7 | `.claude/commands/docker.md` = `down && up -d --build --no-cache` — the exact pattern CLAUDE.md's own docker section forbids. | Full teardown + cache bypass on every `/docker` |
| 8 | `.claude/commands/git_branch.md` rebases onto `origin/main` — the remote only has `origin/master`. | The command errors every time it is used |

**Fixes (applied 2026-07-03):**
- **Precedence order** written into CLAUDE.md: user's live instruction > CLAUDE.md + docs/agents > auto-memory > skill/command text. When layers conflict, the higher layer wins *and* the conflict gets reported (see `maintenance.md`).
- Memory corrected: MEMORY.md index rewritten to match its files; stale design memory rewritten; obsolete common-mistakes entries marked **superseded** (never silently deleted — the history explains why the rule changed).
- Volatile facts replaced with **derive commands**: e.g. "next migration number" is now `ls BES/src/main/resources/db/migration | sort -V | tail -1` + 1. (Plain `ls | tail -1` is a trap — it sorts lexically, putting V9 after V50.)
- `git_branch.md` fixed (`origin/main` → `origin/master`); backup at `docs/agents/backups/`.
- The `/docker` command and the `local-docker-verify` skill's YAML `description:` line initially said `--no-cache` (the skill *body* already had the correct gated rule) — first left unedited as user-invoked muscle memory, then **aligned on 2026-07-03 with the user's explicit approval** (backups in `docs/agents/backups/`). No known cross-layer conflicts remain.

**Health check (run when things feel inconsistent):**
```bash
grep -rn "no-cache" CLAUDE.md .claude/commands/ .claude/skills/*/SKILL.md   # should agree with CLAUDE.md docker section
ls BES/src/main/resources/db/migration | sort -V | tail -1                  # compare against any doc that names a migration number
```

---

## Failure mode 2 — Every session pays a large fixed context tax, and the payload is undifferentiated

Evidence: old CLAUDE.md was 520 lines covering *every* domain (battle persistence internals, design tokens, role workflows) and was loaded for every session including "fix a typo". On top of it, the superpowers SessionStart hook injects its full skill-usage doctrine plus a catalog of ~60 skills, plus MEMORY.md, plus MCP instructions. Estimated fixed overhead: **~15–20k tokens before the first word of the user's request** (estimate — see health check).

Why this hurts weaker models more: they weight salient text over relevant text. When battle-system pitfalls sit next to a CSS task, they leak into the plan. And the superpowers rule "if there is a 1% chance a skill applies you MUST invoke it" pushes small models into invoking ceremony skills (brainstorming, plan-writing) for one-line fixes, burning turns and tokens.

**Fixes (applied 2026-07-03):**
- CLAUDE.md rewritten to a ~⅓-size core whose main mechanism is a **trigger-routed table**: "task mentions battle → read docs/agents/battle-system.md first". Domain depth moved to `docs/agents/{battle-system,design-system,routes-and-roles}.md` and loaded only when the trigger matches.
- The three most expensive traps stay **inline** in CLAUDE.md (branch discipline, migration numbering, battle-403 first-check) — routing only works if followed, so the worst traps must not depend on it.
- Skill rule scoped in CLAUDE.md: invoke the skill whose description matches the task; never invoke skills speculatively; at most one workflow-orchestration skill (brainstorming / writing-plans / executing-plans / subagent-driven-development) per task — mandated gates like git-scope-guard and verification-before-completion don't count toward the cap.

**Trade-off stated honestly:** routed files are invisible until read. A model that skips the routing table is *worse* off than with the old monolith. That is why every routing row is phrased as an imperative trigger ("BEFORE touching X, read Y"), not a description.

**Health check:**
```bash
wc -l CLAUDE.md                        # should stay ≤ 200
ls docs/agents/                        # every file named in CLAUDE.md's routing table must exist
```

---

## Failure mode 3 — Fieldwork, verification, and the memory of failures all live inside one context

Evidence:
- No dispatch protocol existed. The codegraph MCP server (pre-indexed, sub-millisecond, already on the permissions allowlist) sits unused while sessions grep+read whole files into the main conversation.
- The permission log (`settings.local.json`) shows one-off `sed '1018d'`-style line deletions on `BattleControl.vue` issued from the main conversation — the fossil record of long hand-editing sessions done inline.
- The implementing model declares its own work "done": `common_mistakes.md` #6 (undefined on API error) and #7 (emit success before checking the response) both shipped as "done". Self-review by the model that wrote the bug rarely finds the bug.
- When a long session's context gets summarized, conclusions that were never written to a file are silently lost.

**Fixes (applied 2026-07-03):**
- `docs/agents/dispatch.md` — commander pattern: the main conversation decides and integrates; bulk reading, scanning, research, and batch edits go to subagents or the DeepSeek container. Includes an **exploration ladder** that starts with codegraph (cheapest), numeric thresholds for when delegation pays, an escalation/de-escalation ladder, and a reporting contract (conclusions + `file:line`; anything >50 lines goes to a file).
- `docs/agents/judgment.md` — "done" is defined by evidence, not confidence; verification goes to a fresh-context agent, never the author.
- `docs/agents/templates.md` — fill-in delegation prompts so weak models don't have to invent good briefs.
- Write-as-you-go rule: any conclusion that must survive the session gets written to a file the moment it exists.

**Honesty about cost:** delegation saves *main-context* tokens but costs *total* tokens (each spawn starts cold). On this plan, agent spawns are flagged expensive. That is why the ladder starts with codegraph and targeted reads, and why dispatch.md sets thresholds instead of "always delegate".

**Health check:** if the main conversation contains a raw dump >100 lines, or the same model both implemented and approved a merge-worthy change, the protocol was bypassed.

---

## Minor leaks (worth knowing, not worth a section)

| Leak | One-line fix |
|---|---|
| Verbose `mvn`/`docker build` output flooding context | Append `2>&1 | tail -20` to build commands |
| Re-reading a file right after editing it "to check" | Edit/Write error loudly on failure — don't re-read |
| Puppeteer screenshots at full page size | Screenshot the smallest region that answers the question |
| `mrl-skill-dispatcher` fans out one background task per indexed skill | Only invoke on the user's explicit trigger phrases |
| `settings.local.json` permission sprawl (100+ one-off entries) | Harness-side only (no token cost); run the `fewer-permission-prompts` skill someday to rebuild it clean |
| Duplicate docker knowledge in 4 places | CLAUDE.md docker section is canonical; others must point to it |
