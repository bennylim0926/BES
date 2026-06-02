---
name: mrl-skill-dispatcher
description: >
  Use this skill whenever the user wants to perform a task that may involve
  multiple sub-skills discovered at runtime. This skill searches a local MRL
  skill index using a Python script, fans out background tasks — one per
  returned skill — waits for all of them to complete, then consolidates the
  results into a single unified plan and task list for the user to review.
  Trigger this skill when the user says things like "run my skills pipeline",
  "dispatch skills for this task", "look up relevant skills and plan", or any
  time a task should be decomposed via the MRL skill index at
  /Users/bennylim/Documents/k3-agentic-skills.
---

# MRL Skill Dispatcher

A skill that discovers relevant sub-skills for a given task via a local Python
search index, runs each skill as a parallel background process, then
consolidates all outputs into one unified plan for human review.

---

## Overview

```
User task (argument)
       │
       ▼
mrl_search.py  ──►  list of relevant skill paths
       │
       ▼
For each skill ──► background task (reads skill, executes it against the task)
       │
       ▼
Wait for ALL background tasks to complete
       │
       ▼
Consolidate outputs ──► unified plan + task list
       │
       ▼
Present to user for review
```

---

## Step 1 — Clarify the Argument

Before running anything, confirm the **task argument** you will pass to
`mrl_search.py`. This is a short natural-language description of what the user
wants to accomplish, e.g.:

> "set up a CI/CD pipeline for a Node.js project"

If it is ambiguous, ask the user one clarifying question. Do not proceed until
the argument is clear.

---

## Step 2 — Run the MRL Index Search

```bash
cd /Users/bennylim/Documents/k3-agentic-skills
source venv/bin/activate
python mrl-indexer/mrl_search.py "<ARGUMENT>"
```

Parse the output — it is a list of skill paths (or skill names/descriptions).
Store the full list. If the list is empty, tell the user no relevant skills
were found and stop.

---

## Step 3 — Fan Out: One Background Task Per Skill

For each skill returned:

1. Read the skill's `SKILL.md` (or equivalent entry point).
2. Launch a **background task** that applies that skill to the user's original
   task argument. Each background task should:
   - Follow the skill's own instructions
   - Produce a structured output: a list of concrete steps or actions relevant
     to the user's task
   - Record which skill it came from

Run all background tasks **in parallel**. Do not wait for one to finish before
starting the next.

> **Note for Claude.ai / non-subagent environments:** True parallelism is not
> available. Run each skill sequentially, but treat each as an isolated
> execution — do not carry context from one skill run into the next. Complete
> all skill runs before moving to Step 4.

---

## Step 4 — Wait for Completion

Block until **all** background tasks have finished. Do not proceed to
consolidation until every skill task has produced output (or reported a
failure).

If a skill task fails or produces no output, log it as `SKIPPED: <skill name>
— reason` and continue with the rest.

---

## Step 5 — Consolidate into a Unified Plan

Merge all skill outputs into a single coherent plan:

1. **Deduplicate** — remove identical or near-identical steps that appear
   across multiple skills.
2. **Order** — sequence steps logically (setup before execution, dependencies
   before dependents).
3. **Attribute** — note which skill each step or section came from (a short
   label is enough, e.g. `[ci-skill]`).
4. **Flag conflicts** — if two skills recommend contradictory approaches,
   surface the conflict clearly rather than silently picking one.

Output format:

```
## Unified Plan: <task argument>

### Summary
<2–3 sentence overview of what will be done>

### Task List
- [ ] Step 1 — <description> [source: skill-name]
- [ ] Step 2 — <description> [source: skill-name]
...

### Conflicts / Decisions Needed
- <conflict or open question, if any>

### Skipped Skills
- <skill-name> — <reason> (if any)
```

---

## Step 6 — Present to User for Review

Show the unified plan and explicitly ask the user to review it before any
execution begins:

> "Here's the consolidated plan from all relevant skills. Please review — let
> me know if you'd like to adjust, remove, or reorder any steps before we
> proceed."

**Do not begin executing the plan until the user approves.**

---

## Edge Cases

| Situation | Behaviour |
|---|---|
| `mrl_search.py` returns 0 skills | Inform user, stop. |
| Only 1 skill returned | Skip "consolidate" step, present that skill's output directly. |
| Skill has no `SKILL.md` | Log as skipped, note missing entry point. |
| Argument is vague | Ask one clarifying question before running Step 2. |
| Background task times out | Mark as `SKIPPED: timeout`, continue. |

---

## Notes for Skill Authors

When this dispatcher reads your skill, it will look for:
- A clear list of **steps or actions** it should produce given a task argument
- An **output section** or structured conclusion it can extract

Design your skills to produce concrete, extractable step lists so the
consolidation in Step 5 works cleanly.