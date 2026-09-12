#!/usr/bin/env bash
# One-command burst script: reproduces all three graded invariants against a running instance.
# Usage: BASE_URL=https://your-app.onrender.com ./scripts/burst.sh
set -euo pipefail
cd "$(dirname "$0")"

echo "Target: ${BASE_URL:-http://localhost:8080}"
echo

./01-concurrent-get-or-create.sh 50
echo
./02-idempotent-retry-storm.sh 30
echo
./03-conservation-under-contention.sh 300
echo
echo "ALL GATES PASSED"
