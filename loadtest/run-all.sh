#!/usr/bin/env bash
# Run all four k6 scenarios in parallel. Exit status is the OR of all four.
#
# Usage:
#   cd loadtest && ./run-all.sh
#
# Prerequisites:
#   - k6 installed (brew install k6 / https://k6.io/docs/get-started/installation)
#   - loadtest/.env filled in (copy from .env.example)
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "ERROR: loadtest/.env not found. Copy .env.example to .env and fill it in." >&2
  exit 1
fi

# Export every var in .env so k6 child processes inherit them via __ENV
set -a
# shellcheck disable=SC1091
source .env
set +a

mkdir -p results
ts=$(date +%Y%m%d-%H%M%S)

# Pass env explicitly so k6 sees them under __ENV regardless of shell quirks
env_flags=(
  -e BASE_URL="$BASE_URL"
  -e EVENT_NAME="$EVENT_NAME"
  -e CATEGORIES="$CATEGORIES"
  -e DURATION="$DURATION"
  -e ENABLE_WRITES="$ENABLE_WRITES"
  -e HELPER_TOKEN="$HELPER_TOKEN"
  -e EMCEE_TOKEN="$EMCEE_TOKEN"
  -e JUDGE_TOKEN_1="$JUDGE_TOKEN_1"
  -e JUDGE_TOKEN_2="$JUDGE_TOKEN_2"
  -e JUDGE_TOKEN_3="$JUDGE_TOKEN_3"
  -e JUDGE_TOKEN_4="$JUDGE_TOKEN_4"
  -e JUDGE_TOKEN_5="$JUDGE_TOKEN_5"
  -e ORGANISER_USERNAME="$ORGANISER_USERNAME"
  -e ORGANISER_PASSWORD="$ORGANISER_PASSWORD"
)

echo "==> Starting load test against $BASE_URL (event=$EVENT_NAME, duration=$DURATION, writes=$ENABLE_WRITES)"
echo "==> Results will be written to loadtest/results/$ts.*"

pids=()
k6 run "${env_flags[@]}" --summary-export "results/$ts.helper.json"     k6/helper.js     > "results/$ts.helper.log"     2>&1 & pids+=($!)
k6 run "${env_flags[@]}" --summary-export "results/$ts.emcee.json"      k6/emcee.js      > "results/$ts.emcee.log"      2>&1 & pids+=($!)
k6 run "${env_flags[@]}" --summary-export "results/$ts.judge.json"      k6/judge.js      > "results/$ts.judge.log"      2>&1 & pids+=($!)
k6 run "${env_flags[@]}" --summary-export "results/$ts.organiser.json"  k6/organiser.js  > "results/$ts.organiser.log"  2>&1 & pids+=($!)

rc=0
for pid in "${pids[@]}"; do
  if ! wait "$pid"; then rc=1; fi
done

echo
echo "==> Done. Per-scenario logs + summaries in loadtest/results/$ts.*"
if [[ $rc -ne 0 ]]; then
  echo "==> One or more scenarios failed thresholds — inspect the .log files." >&2
fi
exit "$rc"
