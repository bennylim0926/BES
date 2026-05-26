# CI Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up a parallel GitHub Actions CI pipeline that validates every push/PR, posts a unified status comment, and auto-reports CI results back to the developer via a Claude Code post-push hook.

**Architecture:** Five parallel CI jobs (backend build, backend tests, frontend build, frontend tests, analysis) feed a `report` job that posts a PR comment. A local `PostToolUse` hook fires after every `git push` I make, polls `gh run watch` until CI completes, and injects the results into the CLI conversation.

**Tech Stack:** GitHub Actions, Maven (SpotBugs), ESLint 9 (flat config), `actions/github-script`, Claude Code hooks (`~/.claude/settings.json`)

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `.github/workflows/ci.yml` | Create | Parallel CI pipeline |
| `.github/workflows/claude.yml` | Modify | Add `contents: write`, `pull-requests: write`, `issues: write` permissions |
| `BES/pom.xml` | Modify | Add `spotbugs-maven-plugin` after jacoco plugin |
| `BES-frontend/package.json` | Modify | Add `eslint`, `eslint-plugin-vue`, `@eslint/js` to devDependencies |
| `BES-frontend/eslint.config.js` | Create | ESLint flat config for Vue 3 |
| `~/.claude/hooks/ci-watch.sh` | Create | PostToolUse hook script that polls CI after push |
| `~/.claude/settings.json` | Modify | Register hook — add `hooks.PostToolUse` entry |

---

## Task 1: Add SpotBugs to pom.xml

**Files:**
- Modify: `BES/pom.xml` (after line 219, before `</plugins>`)

- [ ] **Step 1: Add the SpotBugs plugin**

Open `BES/pom.xml`. After the closing `</executions>` of the jacoco plugin (line 218) and before `</plugins>` (line 220), add:

```xml
		<plugin>
				<groupId>com.github.spotbugs</groupId>
				<artifactId>spotbugs-maven-plugin</artifactId>
				<version>4.8.6.4</version>
				<configuration>
					<effort>Default</effort>
					<threshold>High</threshold>
					<failOnError>true</failOnError>
				</configuration>
			</plugin>
```

The full closing context should read:
```xml
			</plugin>  <!-- end jacoco -->
			<plugin>
				<groupId>com.github.spotbugs</groupId>
				<artifactId>spotbugs-maven-plugin</artifactId>
				<version>4.8.6.4</version>
				<configuration>
					<effort>Default</effort>
					<threshold>High</threshold>
					<failOnError>true</failOnError>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
```

- [ ] **Step 2: Run SpotBugs locally to verify it works**

```bash
cd BES && mvn spotbugs:check -DskipTests
```

Expected: `BUILD SUCCESS` (or a list of High findings to fix before continuing). If it finds High bugs, fix them before proceeding — CI will fail on the same issues.

- [ ] **Step 3: Commit**

```bash
git add BES/pom.xml
git commit -m "build: add spotbugs-maven-plugin with High threshold"
```

---

## Task 2: Add ESLint to the frontend

**Files:**
- Modify: `BES-frontend/package.json`
- Create: `BES-frontend/eslint.config.js`

- [ ] **Step 1: Install ESLint dependencies**

```bash
cd BES-frontend && npm install --save-dev eslint@^9.0.0 eslint-plugin-vue@^9.0.0 @eslint/js@^9.0.0
```

Expected: `package.json` and `package-lock.json` updated.

- [ ] **Step 2: Create `BES-frontend/eslint.config.js`**

```js
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    rules: {
      'no-unused-vars': 'error',
      'no-undef': 'error',
      'vue/no-unused-vars': 'error',
    },
  },
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
]
```

- [ ] **Step 3: Run ESLint locally to establish baseline**

```bash
cd BES-frontend && npx eslint . --max-warnings 0
```

Expected: Either exits 0 (clean) or lists errors. Fix any errors before continuing — CI will fail on the same issues. Warnings are acceptable and will not fail CI.

- [ ] **Step 4: Add lint script to package.json**

In `BES-frontend/package.json`, add to `"scripts"`:

```json
"lint": "eslint . --max-warnings 0"
```

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/package.json BES-frontend/package-lock.json BES-frontend/eslint.config.js
git commit -m "build: add eslint with vue3 flat config"
```

---

## Task 3: Create the CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: ['**']
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]

jobs:
  backend-build:
    name: Backend / Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'corretto'
      - uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('BES/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2
      - name: Build JAR
        run: mvn clean package -DskipTests -f BES/pom.xml

  backend-test:
    name: Backend / Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'corretto'
      - uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('BES/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2
      - name: Run tests
        run: mvn test -f BES/pom.xml

  frontend-build:
    name: Frontend / Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '24'
          cache: 'npm'
          cache-dependency-path: BES-frontend/package-lock.json
      - name: Install dependencies
        run: npm ci
        working-directory: BES-frontend
      - name: Build
        run: npm run build
        working-directory: BES-frontend

  frontend-test:
    name: Frontend / Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '24'
          cache: 'npm'
          cache-dependency-path: BES-frontend/package-lock.json
      - name: Install dependencies
        run: npm ci
        working-directory: BES-frontend
      - name: Run tests
        run: npm test
        working-directory: BES-frontend

  analysis:
    name: Analysis
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'corretto'
      - uses: actions/setup-node@v4
        with:
          node-version: '24'
          cache: 'npm'
          cache-dependency-path: BES-frontend/package-lock.json
      - uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('BES/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2
      - name: SpotBugs
        run: mvn spotbugs:check -DskipTests -f BES/pom.xml
      - name: Install frontend deps
        run: npm ci
        working-directory: BES-frontend
      - name: ESLint
        run: npm run lint
        working-directory: BES-frontend

  report:
    name: Report
    runs-on: ubuntu-latest
    needs: [backend-build, backend-test, frontend-build, frontend-test, analysis]
    if: always() && github.event_name == 'pull_request'
    permissions:
      pull-requests: write
    steps:
      - name: Post CI results comment
        uses: actions/github-script@v7
        with:
          script: |
            const results = {
              'Backend / Build':  '${{ needs.backend-build.result }}',
              'Backend / Tests':  '${{ needs.backend-test.result }}',
              'Frontend / Build': '${{ needs.frontend-build.result }}',
              'Frontend / Tests': '${{ needs.frontend-test.result }}',
              'Analysis':         '${{ needs.analysis.result }}',
            }

            const icon = r => r === 'success' ? '✅ Pass' : r === 'skipped' ? '⏭️ Skipped' : '❌ Fail'
            const allPassed = Object.values(results).every(r => r === 'success')

            let body = '## CI Results\n\n| Check | Status |\n|-------|--------|\n'
            for (const [name, result] of Object.entries(results)) {
              body += `| ${name} | ${icon(result)} |\n`
            }

            if (allPassed) {
              body += '\n---\n✅ All checks passed.'
            } else {
              body += '\n---\nCI found issues. Want me to investigate and fix the failing checks?\nReply: `@claude investigate and fix the failing CI checks`'
            }

            const { data: comments } = await github.rest.issues.listComments({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
            })
            const existing = comments.find(c => c.body.startsWith('## CI Results'))

            if (existing) {
              await github.rest.issues.updateComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                comment_id: existing.id,
                body,
              })
            } else {
              await github.rest.issues.createComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: context.issue.number,
                body,
              })
            }
```

- [ ] **Step 2: Verify YAML syntax**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo "YAML valid"
```

Expected: `YAML valid`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add parallel CI pipeline with report job"
```

---

## Task 4: Update `claude.yml` permissions

**Files:**
- Modify: `.github/workflows/claude.yml`

The agent needs write permissions to push fix commits back to the branch when invoked via `@claude`.

- [ ] **Step 1: Update permissions block in `claude.yml`**

Replace the existing `permissions:` block:

```yaml
    permissions:
      contents: read
      pull-requests: read
      issues: read
      id-token: write
      actions: read # Required for Claude to read CI results on PRs
```

With:

```yaml
    permissions:
      contents: write
      pull-requests: write
      issues: write
      id-token: write
      actions: read
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/claude.yml
git commit -m "ci: upgrade claude.yml permissions for write access"
```

---

## Task 5: Create the post-push CI watch hook

**Files:**
- Create: `~/.claude/hooks/ci-watch.sh`
- Modify: `~/.claude/settings.json`

- [ ] **Step 1: Create the hooks directory**

```bash
mkdir -p ~/.claude/hooks
```

- [ ] **Step 2: Create `~/.claude/hooks/ci-watch.sh`**

```bash
#!/usr/bin/env bash
# PostToolUse hook: watch GitHub CI after git push
# Claude Code passes JSON to stdin: { tool_name, tool_input: { command }, tool_response }

set -uo pipefail

INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | python3 -c "
import sys, json
try:
    print(json.load(sys.stdin).get('tool_name', ''))
except:
    print('')
" 2>/dev/null || echo "")

COMMAND=$(echo "$INPUT" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('command', ''))
except:
    print('')
" 2>/dev/null || echo "")

# Only act on git push commands
if [[ "$TOOL_NAME" != "Bash" ]] || ! echo "$COMMAND" | grep -qE "git push"; then
  exit 0
fi

# Wait for GitHub to register the new run
sleep 5

BRANCH=$(git branch --show-current 2>/dev/null || echo "")
if [[ -z "$BRANCH" ]]; then
  echo "[CI Watch] Cannot determine current branch — skipping."
  exit 0
fi

RUN_ID=$(gh run list --branch "$BRANCH" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || echo "")
if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
  echo "[CI Watch] No CI run found for branch '$BRANCH' — skipping."
  exit 0
fi

echo "[CI Watch] Monitoring run #$RUN_ID on branch '$BRANCH'..."

if gh run watch "$RUN_ID" --exit-status 2>&1; then
  CONCLUSION="success"
else
  CONCLUSION="failure"
fi

echo ""
echo "=== CI Results ($BRANCH) ==="
gh run view "$RUN_ID" --json jobs 2>/dev/null | python3 -c "
import sys, json
try:
    jobs = json.load(sys.stdin).get('jobs', [])
    all_pass = True
    for job in jobs:
        name = job.get('name', 'Unknown')
        conclusion = job.get('conclusion', 'unknown')
        icon = '✅' if conclusion == 'success' else ('❌' if conclusion == 'failure' else '⏭️')
        print(f'  {icon} {name}: {conclusion}')
        if conclusion not in ('success', 'skipped'):
            all_pass = False
    print()
    if all_pass:
        print('All checks passed.')
    else:
        print('CI failures detected. Ask me to investigate and fix.')
except Exception as e:
    print(f'(could not parse job details: {e})')
" 2>/dev/null || echo "(could not fetch job details)"
```

- [ ] **Step 3: Make the script executable**

```bash
chmod +x ~/.claude/hooks/ci-watch.sh
```

- [ ] **Step 4: Test the script manually (dry run)**

```bash
echo '{"tool_name":"Bash","tool_input":{"command":"echo hello"},"tool_response":"hello"}' | ~/.claude/hooks/ci-watch.sh
```

Expected: No output (command is not a git push — hook exits silently).

```bash
echo '{"tool_name":"Bash","tool_input":{"command":"git push origin fix/test"},"tool_response":""}' | ~/.claude/hooks/ci-watch.sh
```

Expected: `[CI Watch] Monitoring run #...` or `[CI Watch] No CI run found` (depending on whether a run exists). No crash.

- [ ] **Step 5: Add the hook to `~/.claude/settings.json`**

Current `~/.claude/settings.json`:
```json
{
  "enabledPlugins": {
    "frontend-design@claude-plugins-official": true,
    "superpowers@claude-plugins-official": true
  }
}
```

Update to:
```json
{
  "enabledPlugins": {
    "frontend-design@claude-plugins-official": true,
    "superpowers@claude-plugins-official": true
  },
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "/Users/bennylim/.claude/hooks/ci-watch.sh"
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 6: Verify settings.json is valid JSON**

```bash
python3 -c "import json; json.load(open(os.path.expanduser('~/.claude/settings.json'))); print('JSON valid')" 2>/dev/null || python3 -c "import json,os; json.load(open(os.path.expanduser('~/.claude/settings.json'))); print('JSON valid')"
```

Expected: `JSON valid`

---

## Task 6: End-to-end smoke test

- [ ] **Step 1: Push the current branch to GitHub**

```bash
git push -u origin fix/codebase-audit
```

Expected: Hook fires, outputs `[CI Watch] Monitoring run #...`, tails the run, then prints the CI results table.

- [ ] **Step 2: Open a PR (if not already open) and verify the report comment appears**

```bash
gh pr create --title "ci: add parallel CI pipeline" --body "$(cat <<'EOF'
## Summary
- Adds parallel GitHub Actions CI (backend build/test, frontend build/test, analysis)
- Adds SpotBugs (High threshold) and ESLint (Vue 3) analysis
- Posts unified CI results comment on PRs
- Configures post-push hook for automatic CLI reporting

## Test Plan
- [ ] All 5 CI jobs appear as status checks on the PR
- [ ] Report comment posted with pass/fail table
- [ ] Hook outputs CI results in terminal after push
EOF
)"
```

- [ ] **Step 3: Verify all 5 status checks appear on GitHub**

```bash
gh pr checks
```

Expected output lists: `Backend / Build`, `Backend / Tests`, `Frontend / Build`, `Frontend / Tests`, `Analysis` — all passing.

- [ ] **Step 4: Verify the report comment on the PR**

```bash
gh pr view --comments | grep -A 20 "CI Results"
```

Expected: The results table with ✅ for all jobs.

---

## Prerequisites Checklist

Before starting Task 1, confirm:

```bash
gh auth status        # must show: Logged in to github.com
mvn --version         # must show: Apache Maven 3.x, Java 17
node --version        # must show: v24.x or v20.x
```
