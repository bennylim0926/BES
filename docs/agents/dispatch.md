# Model Dispatch Protocol

> Loaded on demand via CLAUDE.md routing. Read when a task involves **spawning agents, exploring more than a few files, web research, batch edits, or executing a plan**. Companion file: `docs/agents/templates.md` (ready-to-fill delegation prompts). Written 2026-07-03; every tool/agent/model named here was verified against the live environment on that date. If a call errors with "unknown model/agent", re-verify against the current Agent tool schema and update this file (see maintenance.md).

## 1. The commander rule

The main conversation is the **commander**: it makes decisions, talks to the user, integrates results, and performs small precise edits. It does **not** do bulk fieldwork. Fieldwork = reading many files end-to-end, repo-wide scanning, web research, batch/mechanical edits, long build-test loops. Those go to workers (subagents or the DeepSeek container), and only **conclusions** come back.

Why: the main context is the scarcest resource in the session. Once it fills with file dumps, it gets summarized and you lose the thread mid-task. A worker's context is disposable; yours is not.

**Cost honesty:** delegation saves *main-context* tokens but costs *total* tokens — every spawn starts cold and re-derives context. On this plan, spawns are the expensive path. So the protocol is not "always delegate"; it is "never do bulk work inline, never spawn for small work", with the thresholds below.

## 2. The exploration ladder (always start at rung 1)

For any "how does X work / where is X / what calls X" question:

1. **codegraph MCP** (cheapest, pre-indexed, already permission-allowlisted):
   - `codegraph_explore` with a natural-language question or symbol names — returns verbatim source of relevant symbols; usually the ONLY call needed
   - `codegraph_callers` / `codegraph_callees` / `codegraph_impact` for "what breaks if I change this"
   - Caveats: index lags file writes by ~1s; if results look stale run `codegraph_status`. If the MCP server is down, fall back to rung 2.
2. **Targeted Read/Grep** — when you know the file (≤3 files, specific line ranges).
3. **Explore agent** — when the answer requires sweeping >3 files, unknown locations, or multiple naming conventions, and you need only the conclusion. Read-only; cannot edit.
4. **general-purpose agent** — multi-step investigation that needs tools beyond reading.

**Anti-pattern:** delegating a question that one `codegraph_explore` call answers. The graph IS the pre-built search index — a spawned agent re-greps what codegraph already indexed, at ~50× the cost.

## 3. When to delegate vs do inline

Do it **inline** when ALL of these hold:
- ≤3 files need to be read, and you know which files
- The edit touches ≤2 files at known locations
- Expected output (diff, findings) fits in ~100 lines

**Delegate** when ANY of these hold:
- Pure exploration would take >10 tool calls or pull >3 whole files into main context
- ≥2 independent chunks could run in parallel
- Batch-mechanical work: same pattern applied across >3 files
- Web research with unknown source count
- You are past ~50% of context and the task has distinct sub-parts
- Executing a written plan (use superpowers:subagent-driven-development or container dispatch — see §9)

**Harness override:** if the harness/tool guidance on the current plan says not to spawn agents unless the user asks, that outranks these thresholds — stay on ladder rungs 1–2 and tell the user when delegation would have helped.

## 4. Worker catalog (verified 2026-07-03)

| Worker | Invocation | Use for | Notes |
|---|---|---|---|
| `Explore` agent | Agent tool, `subagent_type: "Explore"` | Fan-out search, "find every place that…" | Read-only; state search breadth ("medium" / "very thorough") |
| `Plan` agent | `subagent_type: "Plan"` | Implementation-strategy design, architectural trade-offs | Read-only; returns step-by-step plan |
| `general-purpose` agent | `subagent_type: "general-purpose"` | Multi-step tasks, implementation, review | All tools |
| `claude` agent | `subagent_type: "claude"` | Catch-all | All tools |
| `claude-code-guide` agent | `subagent_type: "claude-code-guide"` | Questions about Claude Code/SDK/API itself | Continue an existing one via SendMessage instead of respawning |
| DeepSeek container | `container-dispatch` skill (`.agent-dispatch/` submodule) | Bulk implementation grind + fix loops, off Claude billing | See §9 |

Agent tool options: `model` (`haiku` / `sonnet` / `opus` / `fable` — availability may change; on an "unknown model" error fall back to omitting the field), `isolation: "worktree"` (own git worktree — use for any parallel implementers), `run_in_background: true` (long tasks; you're notified on completion). Omitting `model` inherits the main model. `SendMessage` (deferred tool — load via ToolSearch `select:SendMessage`) continues a previous agent with its context intact; a new Agent call starts cold.

**Effort is NOT settable per-subagent** — there is no effort parameter in the Agent schema. Effort applies to the main model only (`effortLevel` in `~/.claude/settings.json`, or `/model`). Do not claim to set worker effort; you can only pick the model.

## 5. Model assignment

| Model | Assign | Examples |
|---|---|---|
| `haiku` | Mechanical work with an exact known pattern | Apply an already-solved diff pattern to N files; generate boilerplate from a filled template; format/rename with explicit rules |
| `sonnet` | **Default for everything** | Search, standard implementation, first-pass review, research |
| `opus` | Judgment-heavy or previously-failed work | Architecture/plan review, cross-cutting refactor design, debugging that survived 2 sonnet attempts, adversarial review, security-sensitive changes |
| the user | Taste and product intent | See judgment.md §6 — no model substitutes |

## 6. Escalation / de-escalation ladder

Definition of a **failed attempt**: acceptance criteria unmet, reviewer rejected it, or the same error appeared twice.

- **haiku fails once** → resend to `sonnet`, attaching what haiku produced and why it failed.
- **sonnet fails twice on the same subtask** → escalate to `opus` with the **full failure trace**: both attempts' diffs/outputs, exact error messages verbatim, files touched, and your current hypothesis. Never escalate with just the original task text — the failures are the most valuable context.
- **opus fails, or the failure smells like ambiguity rather than difficulty** → stop; write up the state; ask the user. That's not a capability gap, it's a specification gap (judgment.md §3).
- **Hard cap: two escalation hops per issue.** After that, stop and surface to the user with the write-up.
- **De-escalate after a breakthrough:** when a higher model solves the pattern once, hand the *remaining instances* to `haiku`/`sonnet` with the solved example pasted into the prompt. Example: opus figures out the JPA null-FK guard pattern on one service → haiku applies the identical guard to the other services → sonnet reviews the batch.

## 7. The delegation package (all three parts, every time)

Never dispatch a bare instruction. Every delegation carries:

1. **Goal + motivation** — what to produce and *why it's wanted*, so the worker can resolve small ambiguities in the right direction.
2. **Acceptance criteria** — checkable statements the worker can self-test against ("`mvn test -f BES/pom.xml` exits 0", "returns file:line for every match"), written BEFORE dispatch.
3. **Report format** — exactly what to send back (see §8).

Also include: which docs/agents file(s) the worker must read first (battle-system.md for battle work, design-system.md for UI), scope fences ("do NOT touch BattleOverlay.vue"), and the branch it must work on. Fill-in templates: `docs/agents/templates.md`.

## 8. Reporting contract (put this in every worker prompt)

- Conclusions first, ≤10 lines; evidence as `file:line` references, not pasted file bodies
- Any output >50 lines → write it to a file (scratchpad for throwaway, `docs/` for durable) and return the path + a 5-line abstract
- State explicitly what you did NOT verify
- If blocked or uncertain, say so and stop — do not improvise around the acceptance criteria

The agent's final message comes back to the commander only — the user never sees it. Relay what matters.

## 9. Container dispatch (DeepSeek) — the token-saving implementation path

For plan execution / implementation grind, prefer the DeepSeek container over Claude subagents when the user says "container dispatch" / "use DeepSeek" (its billing is separate; that's the whole point of the `.agent-dispatch/` submodule). Mechanics live in the `container-dispatch` skill: you write a 15–30-line BRIEF (goal, constraints, done-criteria checklist, exact test command) to `$AGENT_SHARED_DIR/BRIEF-{id}.md`; the container explores, implements, and runs the fix loop autonomously; local Claude reviews the summary and the diff. Division of labor: **Claude plans and reviews; the container grinds.** Known constraints (from memory `project_agent_dispatch.md`): claude CLI pinned to 1.0.128 in-container; default model deepseek-v4-flash; per-task test command read from the brief.

## 10. Verification is never self-verification

The author of a change — model or human — does not sign off on it. For anything merge-worthy:

| Work product | Verification |
|---|---|
| Written file / doc | A **fresh-context** agent reads it back against the acceptance criteria (it gets the criteria, not the author's reasoning) |
| Code change | Implementer runs build+tests and reports output; then a fresh-context reviewer reads the actual `git diff`, runs the tests itself, and checks against criteria — it must not trust the implementer's summary. Use the `code-review` skill or the review template |
| UI change | `verify`/`run` skill or puppeteer: exercise the actual screen, both themes, mobile viewport |
| High-risk judgment call | Second opinion from a different model, or 2–3 independent samples compared before acting |

A reviewer that only reads the implementer's report is theater. Reviewers get: the diff/files, the acceptance criteria, the test command — and fresh context by construction.

## 11. Parallelism rules

- Independent chunks → dispatch multiple agents in ONE message (parallel tool calls)
- Implementers that write files → each gets `isolation: "worktree"`, or strictly disjoint file sets
- Cap ~3 concurrent agents — beyond that, integration of results costs more than the parallelism saves
- Long-running (>5 min: builds, full test suites, container runs) → `run_in_background: true`; don't poll — you're re-invoked when it finishes

## 12. Dispatch anti-patterns (each observed or near-missed in this repo)

- Spawning an agent to read one known file (a Read call costs ~nothing)
- Delegating a question `codegraph_explore` answers in one call
- Sequential spawns for independent chunks
- Dispatching without acceptance criteria, then accepting "done" on faith
- "Investigate and fix everything you find" briefs — unbounded scope, unreviewable result
- Escalating with the task text but without the failure trace
- The implementer approving its own diff
