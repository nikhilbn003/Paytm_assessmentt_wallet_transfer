#!/usr/bin/env bash
# Gate 1 - Race-free get-or-create.
# Fires N concurrent POST /wallets for one brand-new user and asserts exactly one wallet id
# comes back across all responses.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
N="${1:-50}"
USER="burst-user-$(date +%s%N)-$RANDOM"

echo "== Gate 1: concurrent get-or-create =="
echo "BASE_URL=$BASE_URL  user=$USER  concurrency=$N"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

pids=()
for i in $(seq 1 "$N"); do
  curl -s -o "$tmp_dir/resp_$i.json" -w '%{http_code}' \
    -X POST "$BASE_URL/wallets" \
    -H "Authorization: Bearer $USER" \
    -H "Content-Type: application/json" \
    > "$tmp_dir/status_$i.txt" &
  pids+=($!)
done

for pid in "${pids[@]}"; do wait "$pid"; done

distinct_ids=$(for i in $(seq 1 "$N"); do jq -r '.id' "$tmp_dir/resp_$i.json"; done | sort -u | wc -l)
echo "distinct wallet ids returned: $distinct_ids (expected: 1)"

if [ "$distinct_ids" -eq 1 ]; then
  echo "PASS: exactly one wallet created under $N concurrent get-or-create calls."
else
  echo "FAIL: race detected - $distinct_ids distinct wallets created."
  exit 1
fi
