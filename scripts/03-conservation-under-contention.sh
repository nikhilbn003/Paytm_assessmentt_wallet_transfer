#!/usr/bin/env bash
# Gate 3 - Conservation + no-overdraft under contention.
# Seeds a small set of wallets, then fires many concurrent transfers among them - including
# A->B and B->A at the same instant, and some that would overdraw. Asserts: total balance is
# unchanged, no balance goes negative, no 5xx.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
N="${1:-300}"          # total concurrent transfer attempts
STARTING_BALANCE=10000 # paise, per wallet
WALLET_COUNT=4

echo "== Gate 3: conservation under contention =="
echo "BASE_URL=$BASE_URL  transfers=$N  wallets=$WALLET_COUNT  starting_balance=$STARTING_BALANCE"

declare -a WALLET_IDS
declare -a USER_IDS
for i in $(seq 1 "$WALLET_COUNT"); do
  u="burst-cons-$i-$(date +%s%N)-$RANDOM"
  id=$(curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $u" | jq -r '.id')
  curl -s -X POST "$BASE_URL/wallets/$id/deposit" \
    -H "Authorization: Bearer $u" -H "Content-Type: application/json" \
    -d "{\"amount_paise\": $STARTING_BALANCE}" > /dev/null
  WALLET_IDS+=("$id")
  USER_IDS+=("$u")
done

total_before=$((STARTING_BALANCE * WALLET_COUNT))
echo "wallets: ${WALLET_IDS[*]}"
echo "total balance before: $total_before"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

pids=()
for i in $(seq 1 "$N"); do
  from_idx=$((RANDOM % WALLET_COUNT))
  to_idx=$((RANDOM % WALLET_COUNT))
  while [ "$to_idx" -eq "$from_idx" ]; do to_idx=$((RANDOM % WALLET_COUNT)); done
  from_id="${WALLET_IDS[$from_idx]}"
  to_id="${WALLET_IDS[$to_idx]}"
  from_user="${USER_IDS[$from_idx]}"
  # amount sometimes exceeds balance on purpose, to exercise the decline path
  amount=$(( (RANDOM % (STARTING_BALANCE / 2)) + 1 ))
  key="cons-$i-$(date +%s%N)-$RANDOM"
  body="{\"from\":\"$from_id\",\"to\":\"$to_id\",\"amount_paise\":$amount,\"idempotency_key\":\"$key\"}"
  curl -s -o "$tmp_dir/resp_$i.json" -w '%{http_code}' \
    -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $from_user" -H "Content-Type: application/json" \
    -d "$body" > "$tmp_dir/status_$i.txt" &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid"; done

fivexx=$(cat "$tmp_dir"/status_*.txt | grep -c '^5' || true)

total_after=0
negative=0
for i in "${!WALLET_IDS[@]}"; do
  id="${WALLET_IDS[$i]}"
  u="${USER_IDS[$i]}"
  bal=$(curl -s "$BASE_URL/wallets/$id" -H "Authorization: Bearer $u" | jq -r '.balance_paise')
  echo "wallet $id balance: $bal"
  total_after=$((total_after + bal))
  [ "$bal" -lt 0 ] && negative=1
done

echo "total balance after: $total_after (expected: $total_before)"
echo "5xx responses: $fivexx (expected: 0)"
echo "any negative balance: $negative (expected: 0)"

if [ "$total_after" -eq "$total_before" ] && [ "$negative" -eq 0 ] && [ "$fivexx" -eq 0 ]; then
  echo "PASS: conservation held, no overdraft, no server errors under $N concurrent transfers."
else
  echo "FAIL: conservation/no-overdraft/clean-decline violated."
  exit 1
fi
