# Delegation Prompt Templates

> Loaded on demand via CLAUDE.md routing (usually alongside `dispatch.md`). Copy the skeleton, fill every `{BLANK}`, delete inapplicable lines — but never delete the ACCEPTANCE CRITERIA or REPORT sections. If you can't fill the acceptance criteria, you don't understand the task yet — stop and clarify before dispatching. Verified against the live Agent tool 2026-07-03.

Conventions used below:
- Invocation lines show the Agent tool fields to set. Omit `model` to inherit the main model; see dispatch.md §5–6 for choosing/escalating.
- Every prompt ends with the same REPORT block — workers return conclusions, not dumps.

The universal REPORT block (paste into every prompt):

```
REPORT BACK (nothing else):
1. Verdict/answer in ≤5 lines, first.
2. Evidence as file:line references — do not paste file bodies.
3. Anything >50 lines goes into a file under {scratchpad or docs path}; return the path + 5-line abstract.
4. List explicitly what you did NOT check or verify.
5. If blocked, stop and report the blocker — do not improvise outside the acceptance criteria.
```

---

## 1. Search / locate

First ask: would one `codegraph_explore` call answer this? If yes, don't spawn (dispatch.md §2).

**Invocation:** `subagent_type: "Explore"` · model: sonnet · read-only. (A single well-named symbol is a `codegraph_search` call, not a spawn — only fall back to a haiku Explore if codegraph is down.)

```
GOAL: Find {what} so that {why the commander needs it — e.g. "we can add the same guard everywhere scores are saved"}.

SEARCH FOR:
- {pattern / symbol / behavior 1}
- {pattern / symbol / behavior 2}
Search breadth: {"medium" | "very thorough — multiple naming conventions and locations"}.
Likely areas (hints, not fences): {e.g. BES/src/main/java/.../services/, BES-frontend/src/utils/}

ACCEPTANCE CRITERIA:
- [ ] Every match reported as file:line + one-line role description
- [ ] Explicit statement of where you searched and found nothing
- [ ] {domain-specific: e.g. "distinguish 7-to-Smoke path matches from standard-bracket path matches"}

+ REPORT block
```

*Filled example (abridged):* GOAL: find every write path to `battle_category_state` so we can audit format guards. SEARCH FOR: `persistActiveState` callers; direct repo `.save(` on BattleCategoryState. Breadth: very thorough.

---

## 2. Implementation

**Invocation:** `subagent_type: "general-purpose"` · model: sonnet · `isolation: "worktree"` if anything else runs in parallel · `run_in_background: true` if build+tests >5 min. For plan execution prefer superpowers:subagent-driven-development; for grind off Claude billing prefer container-dispatch (dispatch.md §9).

```
GOAL: {behavior to exist, 2–4 sentences — behavior, not implementation}. Motivation: {why / what breaks without it}.

BRANCH: {current feature branch — verify with `git branch --show-current` before editing; do not create branches}

READ FIRST (mandatory, in order):
- CLAUDE.md
- {docs/agents/battle-system.md if battle | docs/agents/design-system.md if UI | docs/API_CONVENTIONS.md if new endpoints}

CONSTRAINTS:
- Layer order bottom-up: migration → entity → service → controller → DTO → frontend
- Do NOT touch: {files/areas out of scope, e.g. BattleOverlay.vue, unrelated WIP files listed by `git status`}
- Follow test-driven development (superpowers:test-driven-development): failing test first
- {non-obvious constraint, e.g. "endpoint must include 'EMCEE' in @PreAuthorize and wire TierAccessService"}

ACCEPTANCE CRITERIA:
- [ ] {observable outcome, e.g. "POST /api/v1/event/{name}/x returns 200 and persists y"}
- [ ] {edge case, e.g. "returns [] not 500 when no participants"}
- [ ] `mvn test -f BES/pom.xml` exits 0  {and/or}  `npm test` exits 0
- [ ] New/changed behavior covered by a test that fails without the change
- [ ] judgment.md §5 checklist for {backend|frontend|migration} passes

+ REPORT block, plus: exact test commands run with their tail output, and the `git diff --stat`.
```

---

## 3. Refactoring

**Invocation:** `subagent_type: "general-purpose"` · model: sonnet (opus if the *design* of the refactor is the hard part — then have opus design, sonnet execute)

```
GOAL: Restructure {what} into {shape}, with ZERO behavior change. Motivation: {pain it removes}.

READ FIRST: CLAUDE.md + {relevant docs/agents file}.

BEHAVIOR-PRESERVATION PROTOCOL:
1. Run {test command} BEFORE any edit; record pass count. If already red, STOP and report.
2. Refactor in small compilable steps.
3. Run the same tests after; counts must match exactly.

CONSTRAINTS:
- No public API/JSON contract changes ({list DTOs/endpoints that must stay stable})
- No behavior fixes, even tempting ones — report them instead
- Update ALL call sites: grep for {old names} afterward; zero stragglers

ACCEPTANCE CRITERIA:
- [ ] Same test pass count before/after ({command})
- [ ] `grep -rn "{old symbol}" --include=*.{java,vue,js}` returns 0 hits outside comments/history
- [ ] Build green: {build command}
- [ ] Diff contains no logic changes (a reviewer will read it with exactly this question)

+ REPORT block, plus: before/after test counts and any behavior bugs noticed-but-not-fixed.
```

---

## 4. Research (web / external)

**Invocation:** `subagent_type: "general-purpose"` (WebSearch/WebFetch) · model: sonnet. For questions about Claude Code/SDK/API itself use `subagent_type: "claude-code-guide"` instead — and continue an existing one via SendMessage rather than respawning.

```
GOAL: Answer: {precise question}. Motivation: {decision this feeds}.

SCOPE:
- Time-sensitive: prefer sources from {year}+; note publication dates
- Our stack: {e.g. "Spring Boot 3.x + Flyway + Postgres 16 — ignore advice for other stacks"}
- If sources conflict, present both sides with dates; do not average them into a fake consensus

ACCEPTANCE CRITERIA:
- [ ] Every claim carries a source URL
- [ ] Each claim labeled: confirmed (2+ independent sources) / single-source / inference
- [ ] Explicit "could not verify" list
- [ ] Direct answer to the question stated first, hedges after

+ REPORT block (findings >50 lines → write to {scratchpad}/research-{topic}.md and return the path).
```

---

## 5. Review (fresh-context — the acceptance gate from dispatch.md §10)

**Invocation:** `subagent_type: "general-purpose"` · model: sonnet routinely, **opus** for security/migration/battle-state or twice-failed work. The reviewer gets criteria + diff — never the implementer's chat log or summary-as-truth.

```
ROLE: Independent reviewer. You did not write this code. Trust nothing you cannot re-derive.

REVIEW TARGET: {branch / `git diff master...HEAD` / file list}
ORIGINAL ACCEPTANCE CRITERIA (verify each yourself):
- [ ] {paste criteria from the implementation dispatch}

PROTOCOL:
1. Read the actual diff. 2. Run {test command} yourself — do not trust reported results. 3. Check judgment.md §5 checklist for {change type}. 4. For battle code: check battle-system.md matrix + pitfalls. For UI: design-system.md done-checklist.

CLASSIFY FINDINGS:
- BLOCKER: breaks a criterion, loses data, security/permission hole, contradicts CLAUDE.md or docs/agents rules
- WARNING: works but fragile (missing null guard, missing test, drift from conventions)
- NIT: style only

ACCEPTANCE CRITERIA for your review:
- [ ] Verdict: APPROVE or REQUEST-CHANGES, first line
- [ ] Every finding: file:line + what + why + concrete suggested fix
- [ ] Explicit statement of what you ran (commands + outcomes) and what you could not verify

+ REPORT block.
```

---

**After any template dispatch:** check the report against the criteria YOU wrote. Unmet or unverifiable → one clarifying round-trip via SendMessage, then the escalation ladder (dispatch.md §6). Never re-dispatch the identical prompt hoping for different results.
