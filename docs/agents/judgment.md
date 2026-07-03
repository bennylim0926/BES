# Judgment Rubrics — decisions weaker models must get right

> Loaded on demand via CLAUDE.md routing. Read when deciding: **is this done? am I stuck? should I escalate? should I ask the user? am I heading the wrong way?** Every rubric has a ✅ example (apply the rule) and a ❌ example (don't misapply it). Most examples are real incidents from this repo's history. Written 2026-07-03.

## 1. When to escalate to a stronger model

(Numeric protocol — who escalates to whom, with what attached — lives in `dispatch.md` §6. This section is the *signals*.)

Escalate when:
- The same subtask failed twice with different-looking errors
- The evidence contradicts itself (tests pass but behavior is wrong; logs disagree with code reading)
- The change is cross-cutting (≥3 architectural layers at once) and there's no existing pattern to copy
- Security, auth, concurrency, or data-loss surface is involved and you're not certain
- You notice you're *guessing* between two designs rather than *deciding* from evidence

✅ **Escalate:** two attempts to fix battle state leaking between categories each passed the targeted test but broke category switching in a different way. That's a shared-in-memory-state design problem (battle-system.md pitfall #0) — escalate with both failure traces.
❌ **Don't escalate:** first attempt produced a compile error about a missing import. That's not a capability gap; fix it and continue. Escalating single trivial failures wastes the budget escalation exists for.

## 2. When something counts as truly done

"Done" is a property of **evidence**, not confidence. All that apply must hold:

1. Acceptance criteria written *before* the work all pass — checked one by one, not "looks good overall"
2. Build passes: `mvn clean package -DskipTests -f BES/pom.xml` (with Java 17) and/or `npm run build`
3. Relevant tests pass, with the command output actually read: `mvn test -f BES/pom.xml` / `npm test`
4. The feature was **exercised**, not just compiled — real screen loaded (`verify`/`run` skill, puppeteer) or endpoint curled; for UI: both themes + mobile viewport
5. No new console errors / backend log errors during the exercise
6. At least one edge touched: empty list, non-privileged role, page refresh
7. The `superpowers:verification-before-completion` skill was run before claiming success

✅ **Done:** score-display change verified by rebuilding, loading Score view in Broadcast mode with seeded data, screenshotting dark + light, confirming WS update still fires on score save.
❌ **Not done (real incident):** `UpdateScoreForm` emitted success unconditionally after `await submit(...)` — the form closed, the parent refreshed, and it shipped as "working". The POST was returning 400. Compiles + closes-the-dialog is not evidence. (common_mistakes #7)

Reporting rule: if a criterion was skipped, say so explicitly ("not verified on mobile"). A partial-done honestly reported is fine; a claimed-done that isn't destroys the user's trust in every future claim.

## 3. When to stop and ask the user

Ask **before**, not after, when any of these hold:
- Destructive/irreversible: dropping or altering DB columns with data, force-push, deleting files you didn't create, anything on prod
- Product intent has ≥2 plausible readings whose implementations *diverge visibly* — e.g. "add judge assignment mode": are judges assigned per category, per participant pool, or per event? Wrong guess = rework of migration + service + UI
- Spending money, sending emails to real participants, publishing anything external
- Mid-task scope growth: the real fix requires touching ~2× more than agreed
- Credentials/secrets are involved in any way
- The task requires taste (see §6)

Do **NOT** ask when:
- The action is reversible and follows from the request (which util file a helper goes in — follow conventions, note the choice in your report)
- The answer is derivable from code, git history, or docs (look it up)
- You want reassurance ("shall I proceed with step 2?") — proceed

✅ **Ask (real incident class):** a migration needs to drop an FK constraint. The V41 deploy broke prod because Hibernate hash-named constraints differ per database — rollback required. Anything touching prod schema semantics deserves a confirm + the pg_constraint dynamic-lookup pattern (memory: feedback_migration_hash_constraints).
❌ **Don't ask:** "Should I add a null check here?" Defensive guard, reversible, convention-backed (common_mistakes #5). Just do it and mention it.

## 4. Wrong-direction signals — change approach, don't retry

Retrying harder is for flaky infrastructure. These signals mean the *approach* is wrong:

- **Third distinct error from the same edit site** — the mental model is wrong, not the syntax
- **Whack-a-mole:** each fix moves the failure somewhere else — you're patching a layer above the real cause
- **The diff keeps growing** past ~2× the initial estimate for a "small" change
- **You're fighting the framework** — e.g. working around Spring Security instead of adding a rule, or suppressing Hibernate validation instead of fixing the schema
- **You're adding special cases per test** to make a suite pass
- **"It works only if I also change unrelated X"** — X is load-bearing in a way you don't understand yet

Correct response: STOP editing. Write down (a) what you believed, (b) what the evidence now shows, (c) the discarded hypotheses. Re-diagnose from one abstraction level up — usually via the `superpowers:systematic-debugging` skill — or escalate with that write-up attached.

✅ **Recognized correctly (real incident):** backend wouldn't start with Flyway+Hibernate errors; the session patched entities, added `*:missing` ignore patterns, created a workaround migration — symptoms all. The actual cause was **being on the wrong git branch**. The tell was fixes-not-sticking. First moves should have been `git branch`, `git log -5`, `git stash list` (common_mistakes #9/#10).
❌ **Misread:** one WebSocket test failed, passed on rerun, failed again on a different assertion. That's flake — isolate and stabilize the test; don't redesign the WebSocket layer.

## 5. Quality floor checklists by change type

Run the matching list before claiming done. These encode this repo's actual recurring bugs.

**Backend change:**
- [ ] Request DTOs (`Add*`/`Update*`/`Delete*`) have getters AND setters (Jackson silently nulls fields otherwise)
- [ ] Every `findBy...().orElse(null)` is null-guarded before `save()` (silent NULL-FK rows otherwise)
- [ ] New/changed endpoint: `@PreAuthorize` roles decided explicitly — for battle endpoints, decide EMCEE inclusion + wire `TierAccessService.requireBattleAccess()` + update the matrix in battle-system.md
- [ ] `mvn test -f BES/pom.xml` green; new logic has a test
- [ ] Debugging a 400? `docker logs bes_backend --tail 30` FIRST — the catch block hides the real exception

**DB migration:**
- [ ] New file only, never edit an applied one; number = (`ls BES/src/main/resources/db/migration | sort -V | tail -1`) + 1 — plain `tail` without `sort -V` lies
- [ ] No Hibernate hash-named constraint references — use the pg_constraint dynamic lookup (memory: feedback_migration_hash_constraints)
- [ ] H2 tests don't run Flyway — new migrations need a real-Postgres run (CI does this; locally: docker compose)
- [ ] The `.sql` file is committed in the same session it was applied — untracked applied migrations are a time bomb

**Frontend change:**
- [ ] Checked in dark AND light mode (`text-white` on `bg-surface-*` goes invisible in light)
- [ ] API helpers return safe defaults (`[]`/`null`) on ALL non-OK statuses and in catch — never `undefined`
- [ ] No success emit / dialog close before checking the response
- [ ] `credentials: 'include'` on new fetches (session auth, not JWT)
- [ ] Design system respected → design-system.md done-checklist
- [ ] `npm test` green

**Docker/deploy:**
- [ ] Plain `docker compose up -d --build` (multi-stage builds the JAR in-container); `--no-cache` only for pom.xml/package.json/Dockerfile changes or proven staleness
- [ ] After rebuild: containers healthy + `docker logs` clean, not just "command exited 0"
- [ ] Releases go through the `release` skill (tag-based deploy), never ad-hoc

## 6. What cannot be compensated — and what to do instead

Decomposition, checklists, and multi-sample review compensate for **execution** quality. They do NOT compensate for:

| Gap | What to do |
|---|---|
| **Ambiguous requirements** | Don't average interpretations into mush. Implement nothing; present the 2–3 readings with one-line consequences and let the user pick (§3). If truly blocked and the user is away: pick the *most reversible* reading, flag it loudly at the top of your report |
| **Visual/product taste** (does this look right? is this flow annoying?) | Ship to a judgment call only via: user decision, or opus with 2–3 alternatives rendered/screenshotted for the user to choose. Never let a checklist "pass" substitute for someone looking at it |
| **Naming and API aesthetics** | Copy the nearest existing pattern in this repo (`Add{Entity}Dto`, controller/service naming). When no pattern exists, propose in the PR/report rather than silently inventing |
| **Priority calls** (what to build next, what's release-blocking) | Always the user's. Provide the evidence, not the verdict |
| **A model detecting its own subtle reasoning errors** | Structural, not introspective, defense: fresh-context review, tests, read-back — that's why dispatch.md §10 exists |

If you can't do a thing well and can't escalate it, **say it can't be done well right now** — a flagged gap is recoverable; a confident wrong answer often isn't.
