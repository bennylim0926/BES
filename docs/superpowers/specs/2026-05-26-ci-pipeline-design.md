# CI Pipeline Design

**Date:** 2026-05-26
**Branch:** fix/codebase-audit
**Status:** Approved

## Overview

A GitHub Actions CI pipeline for BES that validates every push and PR with parallel checks, posts a unified status comment, and automatically reports results back to the developer via a Claude Code post-push hook — no manual CI polling required.

No deployment target exists yet; this is CI only (no CD).

---

## Triggers

- Every push to any branch
- Every PR (opened, synchronize, reopened, ready_for_review)

---

## Workflow: `ci.yml`

Single workflow file with 6 jobs. Five run in parallel; the sixth (`report`) runs after all complete.

### Jobs

| Job | Working dir | Steps | Status check name |
|-----|-------------|-------|-------------------|
| `backend-build` | `BES/` | Java 17 → `mvn clean package -DskipTests` | Backend / Build |
| `backend-test` | `BES/` | Java 17 → `mvn test` (H2 in-memory, no DB container needed) | Backend / Tests |
| `frontend-build` | `BES-frontend/` | Node 24 → `npm ci` → `npm run build` | Frontend / Build |
| `frontend-test` | `BES-frontend/` | Node 24 → `npm ci` → `npm test` | Frontend / Tests |
| `analysis` | root | Java 17 + Node 24 → SpotBugs → ESLint | Analysis |
| `report` | — | Runs after all 5 (even on failure) → posts PR comment | Report |

**Backend Dockerfile note:** The existing `Dockerfile` expects a pre-built JAR (`ARG JAR_FILE=BES/target/*.jar`). CI does not build Docker images — `backend-build` runs Maven directly. This matches the existing local workflow.

### Job: `analysis`

Runs two tools in sequence:

**SpotBugs (Java)**
- Added to `BES/pom.xml` as `spotbugs-maven-plugin`
- Threshold: `High` — only high-confidence bugs fail the build (null dereferences, unclosed streams, bad `equals()`)
- Effort: `Default`
- CI command: `mvn spotbugs:check`
- Exits non-zero on High findings → job fails

**ESLint (Vue/JS)**
- Added to `BES-frontend/` devDependencies: `eslint`, `eslint-plugin-vue`, `@eslint/js`
- Config file: `BES-frontend/eslint.config.js` (flat config format, Vue 3 rules)
- Errors block the job; warnings are reported but do not fail
- CI command: `npx eslint . --max-warnings 0`

### Job: `report`

- Runs on PRs only (skipped for plain branch pushes with no open PR)
- Uses `if: always()` so it runs regardless of upstream job outcomes
- Posts one comment per PR (updates existing comment on re-run rather than creating a new one)
- Comment format:

```
## CI Results

| Check            | Status  |
|------------------|---------|
| Backend / Build  | ✅ Pass |
| Backend / Tests  | ✅ Pass |
| Frontend / Build | ✅ Pass |
| Frontend / Tests | ❌ Fail |
| Analysis         | ✅ Pass |

---
CI found issues. Want me to investigate and fix the failing checks?
Reply: `@claude investigate and fix the failing CI checks`
```

- If all pass, posts a clean green summary with no action prompt.

---

## Existing Workflows

### `claude.yml` — Claude Agentic Agent

Triggers on `@claude` mentions in issues, PR comments, PR reviews. Unchanged except:
- Add `contents: write` and `pull-requests: write` permissions so the agent can push fix commits directly to the branch when invoked.

### `claude-code-review.yml` — Automated Code Review

**Disabled.** Trigger changed to `workflow_dispatch` only (manual run from GitHub Actions UI). Re-enable by restoring `pull_request:` trigger.

---

## Post-Push Hook (Claude Code CLI)

A `PostToolUse` hook in Claude Code settings that fires after every Bash tool call I make.

### Behaviour

1. Inspect the Bash command — if it does not contain `git push`, exit immediately (no-op)
2. Wait 5 seconds for GitHub to register the run
3. Get the current branch: `git branch --show-current`
4. Get the latest run ID: `gh run list --branch <branch> --limit 1 --json databaseId`
5. Run `gh run watch <id> --exit-status` — blocks until CI completes (~2-3 min)
6. Run `gh run view <id> --json jobs` — get per-job pass/fail
7. Output structured summary → injected into the CLI conversation automatically

### What I do with the output

- **All pass:** "CI green — all checks passed."
- **Any fail:** Report which jobs failed with a brief reason, ask "Want me to fix this?"
- If yes → read the failed job logs, locate the root cause in the codebase, apply fix locally, push again → hook re-runs

### Interaction model

```
Developer (or me) runs: git push
        │
        ├── GitHub: ci.yml kicks off (parallel jobs, ~2 min)
        │
        └── Hook: polls gh run watch → outputs results into CLI conversation
                              │
                    All pass? → "CI green"
                    Failures? → explain + "Want me to fix?"
                                      │
                              Fix locally → push again → hook re-runs
```

The `@claude` comment path on GitHub (via `claude.yml`) remains available as a fallback — useful if someone else pushes and wants to trigger a fix from the GitHub UI.

---

## Files Changed / Added

| File | Change |
|------|--------|
| `.github/workflows/ci.yml` | New — parallel CI pipeline |
| `.github/workflows/claude.yml` | Update permissions (add `contents: write`, `pull-requests: write`) |
| `.github/workflows/claude-code-review.yml` | Already disabled (trigger → `workflow_dispatch`) |
| `BES/pom.xml` | Add `spotbugs-maven-plugin` |
| `BES-frontend/package.json` | Add ESLint devDependencies |
| `BES-frontend/eslint.config.js` | New — ESLint flat config for Vue 3 |
| Claude Code settings (`~/.claude/settings.json`) | Add `PostToolUse` hook for git push CI polling |

---

## Prerequisites

- `ANTHROPIC_API_KEY` secret set in GitHub repo settings (already used by `claude.yml`)
- `gh` CLI authenticated locally (`gh auth status`) for the post-push hook to work
- No deployment server required — CI validation only
