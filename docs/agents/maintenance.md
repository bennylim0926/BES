# Maintenance Protocol — how to change this system safely

> Loaded on demand via CLAUDE.md routing. Read **before editing CLAUDE.md, anything in docs/agents/, auto-memory, or .claude/ config**. The system stays useful only if updates follow these rules; the failure modes it guards against are documented in `diagnosis.md`. Written 2026-07-03.

## File inventory

| File | Role | Change tier |
|---|---|---|
| `CLAUDE.md` | Always-loaded core: identity, commands, hard rules, routing table | Structure: ask-first · Routing rows & factual fixes: autonomous+backup |
| `docs/agents/diagnosis.md` | Why the system is shaped this way | Append clarifications: autonomous · Rewrite conclusions: ask-first |
| `docs/agents/dispatch.md`, `judgment.md`, `templates.md` | Protocols & rubrics | Fix verified-wrong facts: autonomous+backup · Change thresholds/rules: ask-first |
| `docs/agents/battle-system.md`, `design-system.md`, `routes-and-roles.md` | Domain knowledge | Keep in sync with code: autonomous+backup |
| `docs/agents/letter.md` | Orientation + in-flight state | Update in-flight-state section: autonomous · Rewrite advice: ask-first |
| `docs/agents/maintenance.md` | This file | Ask-first (changing the change-rules needs the user) |
| `~/.claude/projects/.../memory/*` | Auto-memory (personal, not in git) | Autonomous per the memory rules, + the index-sync rule below |
| `.claude/skills/*`, `.claude/commands/*` | User-authored automation | **Propose, don't edit.** Exception: objectively broken (errors on every use) → fix + backup + flag in your report |
| `~/.claude/settings.json`, `.claude/settings*.json` | Harness config | Via `update-config` skill, and only on user request |

## The three change tiers

**Tier 1 — autonomous:** appending a lesson to memory; adding a `[[link]]`; updating letter.md's in-flight-state section; updating a "Last verified" stamp after actually re-verifying.

**Tier 2 — autonomous with backup + report:** correcting a fact verified wrong against the code (path moved, table out of date, count drifted); adding a routing row for a new doc the user asked for; marking an entry superseded. Procedure below.

**Tier 3 — ask the user first:** changing any threshold, precedence rule, or escalation rule; deleting or merging files; restructuring CLAUDE.md; changing branch/commit discipline; anything in judgment.md §6 territory; editing user-authored skills/commands (beyond the broken-fix exception).

## Tier-2 procedure (all five steps, in order)

1. **Verify the fact against reality** — run the `ls`/`grep`/command that proves the current doc wrong. No verification, no edit.
2. **Back up**: `cp <file> docs/agents/backups/<name>.<YYYY-MM-DD>.bak` (memory files → `~/.claude/projects/-Users-bennylim-Documents-BES/memory-backups-<YYYY-MM-DD>/`). Keep max 2 backups per file; delete the oldest.
3. **Edit minimally.** Supersede, don't erase: replace a dead rule's body with a short `**[SUPERSEDED <date>]** <why> — current rule: <where>` note so future readers know the history (see common_mistakes.md #4/#13 for the pattern).
4. **Read back the changed section** and re-run any command the new text claims is true.
5. **Report the change to the user in your final message** — file, what, why, backup path.

## Writing back lessons (after any real mistake or user correction)

Where it goes — one home each, never two:
- **Mistake with a reusable rule** → `memory/common_mistakes.md`, appended in its existing format: `## <n>. <Title>` + **Mistake** + **Rule** + (optionally **Why**) with a minimal code example. Update the MEMORY.md hook line.
- **User guidance on how to work** → new `memory/feedback_*.md` (copy the frontmatter shape of an existing one, e.g. `feedback_docker_frontend_rebuild.md`; body = fact + **Why:** + **How to apply:**) + MEMORY.md line.
- **Repo-level rule every collaborator/agent needs** → the matching `docs/agents/` file (Tier 2). Memory may *point* to it, never duplicate it.
- **Battle/UI domain fact** → battle-system.md / design-system.md, not memory.

Entry quality bar: ≤10 lines; contains a concrete trigger ("when you see X…"), the wrong move, the right move, and a way to detect the situation. "Be more careful with Y" is not a lesson — name what *specifically* to do differently.

## The index-sync rule (this rot was found live — diagnosis.md #1)

Any time a memory file changes, its MEMORY.md line must be updated **in the same turn** so the line still matches the file's current claim. When reading MEMORY.md, treat the *file* as truth, the line as a hint. Same applies to CLAUDE.md's routing table vs docs/agents files.

## Contradiction check (before adding ANY new rule anywhere)

```bash
grep -rn -i "<topic keyword>" CLAUDE.md docs/agents/ .claude/commands/ .claude/skills/*/SKILL.md \
  ~/.claude/projects/-Users-bennylim-Documents-BES/memory/ 2>/dev/null
```
- Hits agree with the new rule → add it in its one home, cross-reference the rest.
- Hits conflict → do NOT add a second opinion silently. If the new rule is the user's explicit instruction, update the losing text (its tier applies) or record the conflict in CLAUDE.md's precedence section. Otherwise ask.

## Condensation thresholds (rule inflation is degradation mode #2)

- `common_mistakes.md` > 20 entries or > 250 lines → group into patterns; keep the newest concrete example per pattern; move the rest to the memory backups dir. **Preserve entry titles when condensing** — other docs cite entries by title and number; titles are the stable key, so keep them verbatim and note any renumbering in the superseded trail
- `MEMORY.md` > 30 lines → merge related memories into one file each
- CLAUDE.md routing table > 15 rows → merge the two least-read targets
- Any docs/agents file > 300 lines → split or tighten (bloat here recreates the monolith this system replaced)

Condensation is Tier 2 for memory (yours), Tier 3 for docs/agents structure.

## Staleness sweep (when routing "feels wrong", or ~quarterly)

1. Every path in CLAUDE.md's routing table exists (`ls` each)
2. One spot-check fact per docs/agents file against the code (e.g. battle matrix row vs actual `@PreAuthorize`; a route row vs `router/index.js`)
3. letter.md in-flight section still true (`git status`, `git branch`)
4. Fix per Tier 2, bump "Last verified" stamps only for files actually re-verified

## Never

- Never edit `docs/agents/backups/` contents (append-only history)
- Never delete a rule without a superseded-note trail
- Never let CLAUDE.md grow back past ~200 lines — extract and route instead
- Never record a "fact" you didn't verify with a command in this session
