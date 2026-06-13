---
name: release
description: >
  Cut a versioned production release of Kyrove and trigger the tag-based
  GitHub Actions deploy. Use when the user says "release", "release this",
  "cut a release", "ship v...", "tag and push", "deploy a new version",
  "make a release", or similar release intent.

  This skill: (1) verifies clean working tree on master, (2) reads the latest
  tag, (3) analyzes commits since that tag to suggest a semver bump, (4) shows
  the user what's about to ship and asks for confirmation, (5) creates an
  annotated tag with a generated message and pushes it, (6) optionally creates
  a GitHub Release with auto-generated notes, (7) points the user at the
  Actions run.
---

# Release Kyrove

Tag-based releases drive production deploys via `.github/workflows/deploy.yml`.
Pushing a `v*` tag triggers the workflow; pushing to master alone does not.

## Step 1 — Verify ready state

Run these and stop immediately if any check fails. Tell the user exactly what's
wrong; do not try to fix it silently.

```bash
git status --short                          # MUST be empty (no uncommitted work)
git branch --show-current                   # MUST be "master"
git fetch origin master --tags
git rev-list HEAD..origin/master --count    # MUST be 0 (local is at the tip)
```

If user is on a different branch, ask whether to switch to master or release
from the current branch (rare but valid — they can override). If working tree
is dirty, refuse and list the unstaged/uncommitted files.

## Step 2 — Determine current version

```bash
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
```

- If `LATEST_TAG` is empty → this is the first release. Suggest `v0.1.0`.
- Else parse it as `v<MAJOR>.<MINOR>.<PATCH>`.

## Step 3 — Analyze commits since last tag

```bash
# If LATEST_TAG is set:
git log "$LATEST_TAG"..HEAD --pretty=format:"%h %s"
# Else (first release):
git log --pretty=format:"%h %s" -30
```

Categorize each commit's first line by Conventional Commit prefix:

| Prefix                              | Bump |
|-------------------------------------|------|
| `feat!:` or commit body has `BREAKING CHANGE:` | major |
| `feat:`                             | minor |
| `fix:` / `perf:`                    | patch |
| `chore:` / `docs:` / `refactor:` / `test:` / `style:` / `ci:` | no bump on their own |

Determine the bump:

- Any breaking change → major (e.g. `v1.2.3` → `v2.0.0`)
- Else any `feat:` → minor (e.g. `v1.2.3` → `v1.3.0`)
- Else any `fix:` / `perf:` → patch (e.g. `v1.2.3` → `v1.2.4`)
- Else (only docs/chore/etc.) → patch (still deploy-worthy if user is releasing)

## Step 4 — Present the plan and confirm

Show the user:

```
Current: v0.1.0
Suggested: v0.2.0 (minor — 2 new features)

About to ship:

  Features:
    abc1234  feat: GitHub Actions auto-deploy on push to master
    def5678  feat: multi-stage backend Dockerfile

  Fixes:
    ghi9012  fix: make Google Drive/Sheets configs tolerate missing credentials.json

  Other:
    jkl3456  docs: add pre-flight checklist
    mno7890  chore: tidy dockerignore

Tag will be created with annotated message and pushed to origin.
This triggers the production deploy workflow.

Proceed? (yes / specify version like 'v1.0.0' / no)
```

Accept:
- `yes` / `y` / `ok` → use suggested version
- A version string (e.g. `v1.0.0`) → use that instead, skip auto-bump logic
- `no` / `cancel` → abort, do nothing

## Step 5 — Create and push the tag

```bash
VERSION=v<computed-or-overridden>

# Build a short multi-line tag message from the categorized commits
git tag -a "$VERSION" -m "Release $VERSION

Features:
  - <feat commits, one per line, without the 'feat:' prefix>

Fixes:
  - <fix commits>

(omit empty sections)"

git push origin "$VERSION"
```

## Step 6 — Optional GitHub Release

Ask the user once: "Also create a GitHub Release page with auto-generated
notes? (recommended for tracking)"

If yes:
```bash
gh release create "$VERSION" --generate-notes --title "$VERSION"
```

If `gh` is not authenticated, skip and tell the user they can create the
Release manually from the tag in the GitHub UI.

## Step 7 — Point to the deploy

Tell the user:
- The tag has been pushed, which triggers the Deploy workflow
- Watch progress at: https://github.com/bennylim0926/BES/actions
- Site should be live with the new version in ~3-5 minutes (longer if Maven
  cache is cold)

## Rules

- NEVER tag without showing the commits first
- NEVER push the tag without explicit user confirmation
- ALWAYS use annotated tags (`git tag -a`), never lightweight tags
- Tag format is exactly `v<MAJOR>.<MINOR>.<PATCH>` (lowercase v, no suffixes
  like `-rc1` unless the user explicitly asks for a pre-release)
- If the commits look weird (many merge commits, no Conventional Commit
  prefixes, big mixed changes), tell the user and ask for an explicit version
- If the user gives an explicit version, validate it: must match
  `^v\d+\.\d+\.\d+$` and must be greater than the latest tag (no re-tagging)
- If the user wants to re-tag (rare, e.g. corrupted release), they must
  explicitly say "force re-tag v..."  — and even then warn them this will
  trigger a duplicate Deploy workflow run

## Failure modes to watch for

- `git push origin <tag>` fails with permission denied → user needs to push
  with their own credentials; do NOT try to bypass
- Tag already exists locally but not on remote → ask user whether to delete
  and re-create, or push the existing one
- User is on a branch with commits not in master → refuse, suggest they merge
  to master first
