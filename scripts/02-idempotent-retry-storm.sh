#!/usr/bin/env bash
# Gate 2 - Idempotent exactly-once transfer.
# Creates two funded wallets, then fires the SAME transfer (same idempotency_key) K times
# concurrently. Asserts exactly one debit/credit happened and all responses agree on the
# resulting transfer id/status.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
K="${1:-30}"
AMOUNT="${2:-1000}"   # paise
STARTING_BALANCE=100000

USER_A="burst-idem-a-$(date +%s%N)-$RANDOM"
USER_B="burst-idem-b-$(date +%s%N)-$RANDOM"
IDEMPOTENCY_KEY="idem-$(date +%s%N)-$RANDOM"

echo "== Gate 2: idempotent retry storm =="
echo "BASE_URL=$BASE_URL  K=$K  amount_paise=$AMOUNT"

wallet_a=$(curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $USER_A" | jq -r '.id')
wallet_b=$(curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $USER_B" | jq -r '.id')
echo "wallet_a=$wallet_a wallet_b=$wallet_b"

curl -s -X POST "$BASE_URL/wallets/$wallet_a/deposit" \
  -H "Authorization: Bearer $USER_A" -H "Content-Type: application/json" \
  -d "{\"amount_paise\": $STARTING_BALANCE}" > /dev/null

body="{\"from\":\"$wallet_a\",\"to\":\"$wallet_b\",\"amount_paise\":$AMOUNT,\"idempotency_key\":\"$IDEMPOTENCY_KEY\"}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

pids=()
for i in $(seq 1 "$K"); do
  curl -s -o "$tmp_dir/resp_$i.json" \
    -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $USER_A" \
    -H "Content-Type: application/json" \
    -d "$body" &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid"; done

distinct_transfer_ids=$(for i in $(seq 1 "$K"); do jq -r '.id' "$tmp_dir/resp_$i.json"; done | sort -u | wc -l)
distinct_statuses=$(for i in $(seq 1 "$K"); do jq -r '.status' "$tmp_dir/resp_$i.json"; done | sort -u | wc -l)

balance_a=$(curl -s "$BASE_URL/wallets/$wallet_a" -H "Authorization: Bearer $USER_A" | jq -r '.balance_paise')
balance_b=$(curl -s "$BASE_URL/wallets/$wallet_b" -H "Authorization: Bearer $USER_B" | jq -r '.balance_paise')

echo "distinct transfer ids across $K responses: $distinct_transfer_ids (expected: 1)"
echo "distinct statuses across $K responses: $distinct_statuses (expected: 1)"
echo "wallet_a balance: $balance_a (expected: $((STARTING_BALANCE - AMOUNT)))"
echo "wallet_b balance: $balance_b (expected: $AMOUNT)"

ok=1
[ "$distinct_transfer_ids" -eq 1 ] || ok=0
[ "$distinct_statuses" -eq 1 ] || ok=0
[ "$balance_a" -eq $((STARTING_BALANCE - AMOUNT)) ] || ok=0
[ "$balance_b" -eq "$AMOUNT" ] || ok=0

if [ "$ok" -eq 1 ]; then
  echo "PASS: $K concurrent identical-key requests produced exactly one debit/credit."
else
  echo "FAIL: idempotency storm produced inconsistent results (double-apply or divergent responses)."
  exit 1
fi

echo
echo "-- same-key-different-body must return 409 --"
different_body="{\"from\":\"$wallet_a\",\"to\":\"$wallet_b\",\"amount_paise\":$((AMOUNT + 1)),\"idempotency_key\":\"$IDEMPOTENCY_KEY\"}"
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/transfers" \
  -H "Authorization: Bearer $USER_A" -H "Content-Type: application/json" -d "$different_body")
echo "status=$status (expected: 409)"
[ "$status" -eq 409 ] && echo "PASS: reused key with different body correctly rejected." || { echo "FAIL"; exit 1; }
