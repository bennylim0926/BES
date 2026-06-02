---
name: session-start
description: >
  Run at the start of every session to verify git state, branch, stash, and
  uncommitted migration files before doing any work. Invoke when user says
  "start session", "check state", or when opening a new conversation.
---

# Session Start Checklist

Run all checks silently and report any issues before proceeding.

## Step 1 — Confirm active branch

```bash
git branch --show-current
git log --oneline -3
```

**Stop and report** if the branch is not what the user expects. Do not edit any file until branch is confirmed correct.

## Step 2 — Check for stashed WIP

```bash
git stash list
```

If stashes exist, report them. They may represent in-progress work that should be restored before starting new work.

## Step 3 — Check for uncommitted migration files

```bash
git status --short | grep migration
ls BES/src/main/resources/db/migration/ | tail -5
```

If any migration `.sql` file is **untracked (`??`)**, flag it immediately:
> "⚠️ Untracked migration file found: [filename]. This must be committed before any Docker rebuild or it will be lost."

## Step 4 — Check working tree

```bash
git status --short | grep -v "^??" | head -20
```

Report any uncommitted changes. Ask the user whether to continue with them or stash first.

## Step 5 — Report

Output a one-line status:
```
✅ Branch: <name> | No stash | Migrations committed | Working tree clean
```
or flag any issues found.

---

## Why this exists

A branch switch mid-session (while the container agent was building) caused the wrong branch to be active. The DB had migrations V21–V26 applied but the SQL files were never committed. This caused hours of Flyway/Hibernate errors and entity patching before the root cause was found. Running this checklist at session start catches all of those conditions in under 10 seconds.
