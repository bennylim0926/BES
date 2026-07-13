# Letter to Future Sessions

Written 2026-07-03 by Claude Fable 5, in the one session this environment will have with this model tier. The user asked me to spend it converting judgment into institutions — the result is CLAUDE.md (rewritten) plus this `docs/agents/` directory. This letter is orientation, not rules; the rules live in the other files. Read CLAUDE.md's routing table first.

## State of the world when I left (2026-07-03)

- The judge-assignment-mode feature **shipped**: PR #187 squash-merged to master 2026-07-03 (`db97386`), branch deleted. During its pre-push verification, two feature-introduced bugs were caught and fixed (dead `getAllJudges` fetch in `CreateParticipantForm`, unused computed in `AuditionDisplay`), and issue **#186** was filed for a pre-existing CI hole: the SpotBugs job never compiles first, so it passes vacuously — 2 real High findings in `DemoService` will surface when someone fixes it.
- The `docs/agents/` directory + rewritten CLAUDE.md + the `.claude` command/skill fixes were committed on `chore/agent-os` immediately after that merge. Still: **orient with `git status` before touching anything** — this user runs concurrent streams.
- The `--no-cache` conflicts (`/docker` command, `local-docker-verify` description) were resolved 2026-07-03 with the user's approval — both now match CLAUDE.md's gated Docker rule (backups in `docs/agents/backups/`). The user's underlying worry ("changes not reflected in the build") is addressed by the stale-build checklist now in CLAUDE.md's Docker section.
- `credentials.json`, `beskey.pem`, `.env` sit in the working dir but are untracked and read-denied in settings — keep them that way; never loosen those deny rules.
- Deferred security gap (from the tier-system memory): STOMP `BattleTimerController` is unauthenticated (HTTP `POST /battle/timer` is gated; the STOMP path is not). Known, deliberate, waiting on an app-wide STOMP-auth interceptor.

## Three things the user didn't ask me to write down, but you need

**1. The user runs multiple concurrent streams; orientation is the first deliverable of every session.** Feature WIP, bugfix detours, release work, and infra experiments coexist. The repo's own history includes: hours lost to a silent wrong-branch state; a release that shipped without 18 locally-stranded commits; migrations applied in a container but never committed. That's why session-start checks, the scope guard, and the unmerged-branch audit exist — they are scar tissue, not bureaucracy. The single highest-leverage habit: **before the first edit, know your branch, your uncommitted files, and whether a plan doc already exists for this work** (`docs/superpowers/plans/` is extensive — the answer to "how should I build X" is often already written there).

**2. The economics of this environment are deliberate: judgment on Claude, grind elsewhere.** The user built the `.agent-dispatch` DeepSeek container specifically so Claude tokens go to planning and review, not implementation loops. Your default posture: codegraph before grep, targeted reads before agents, container dispatch for bulk implementation (on the user's explicit trigger — dispatch.md §9), and no ceremonial agent spawns. When a task is heavy, the right move is often a 20-line brief to the container — not 200 tool calls in the main context. Respect this even when you *could* do it all yourself; capability isn't the constraint, cost is.

**3. Trust here is earned through verified claims, not fast ones.** Nearly every entry in `common_mistakes.md` is a verification failure — things declared working that weren't. The user notices. Report evidence ("tests: 128 passed, output below"), state what you did NOT check, and never let a dialog-closes-so-it-works claim ship. A slower, honestly-hedged answer beats a fast wrong one in this relationship — the user's own instructions ask for flagged limits rather than confident invention.

## How this system will degrade, and the countermeasures

| Degradation | How it starts | Countermeasure (already built) |
|---|---|---|
| **Routing rot** | Files move/rename; CLAUDE.md rows point at ghosts; models stop trusting the table | Staleness sweep + read-back rules (maintenance.md); derive-commands instead of hardcoded facts |
| **Rule inflation** | Every incident adds a rule until CLAUDE.md is a monolith again and nothing stands out | Condensation thresholds (maintenance.md); one-home-per-topic; CLAUDE.md hard-capped ~200 lines |
| **Contradiction creep** | A new memory/skill quietly disagrees with docs; models obey whichever they saw last | Precedence order in CLAUDE.md; contradiction-check grep before adding any rule; index-sync rule |
| **Ceremony drift** | Weak models run every skill and spawn reviewers for one-line diffs; sessions get slow and expensive | judgment.md thresholds are the *license to skip* ceremony for small reversible work; dispatch.md §3 inline criteria |
| **Silent bypass** | Under pressure ("quick fix!"), a model skips the routing reads and re-learns old bugs | The worst traps are inline in CLAUDE.md itself, not routed; keep them there whenever CLAUDE.md is edited |
| **Ossification** | The system treated as scripture; nobody updates it; it drifts from reality until ignored | It's a tool: the user's live word always wins; maintenance.md gives cheap, safe update paths — use them the moment a rule proves wrong |

The meta-countermeasure: **when you catch this system being wrong, fixing it is in scope for any session** (maintenance.md Tier 2). A doc system nobody corrects converges to noise.

## Honest limits I could not engineer away

- **I could not measure the true session-start token overhead** (no token meter in-harness); the 15–20k figure in diagnosis.md is an estimate from file sizes. If the harness ever exposes real numbers, replace the estimate.
- **Model availability will shift.** The dispatch tables were verified 2026-07-03 (haiku/sonnet/opus/fable). When a model name errors, don't debug — re-check the Agent tool schema and update dispatch.md.
- **The superpowers plugin updates itself** (6.0.2 → 6.1.1 observed in the cache). Its skill texts may drift from what CLAUDE.md assumes; the precedence order handles conflicts, but a majorly changed skill deserves a fresh look.
- **Checklists don't confer taste.** judgment.md §6 says what to do when a call is genuinely aesthetic or ambiguous: present options, escalate, or say plainly it needs the user. That section is the honest boundary of this whole system — everything else compensates for execution, nothing compensates for intent.

## Handoff

No unfinished deliverables. All seven were written, adversarially reviewed (fresh-context agent; 3 blockers + 8 warnings found and fixed, including role-table rows this very letter's authors got wrong), and read back this session: diagnosis.md, the CLAUDE.md rewrite, dispatch.md, judgment.md, templates.md, maintenance.md, this letter, plus the three domain extractions (battle-system, design-system, routes-and-roles). The only open items are user decisions listed under "State of the world" (commit strategy; the two `--no-cache` proposals).

Work well. Verify before you claim. Write it down before the context forgets.

— Fable 5, 2026-07-03
