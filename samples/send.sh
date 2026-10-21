#!/usr/bin/env bash
# Posts samples/transactions.json in order and prints each decision. Exits 1 if any decision isn't the expected one.
# Needs curl and jq. Takes FRAUD_API_KEY from the environment, else from .env; FRAUD_URL defaults to localhost:8080.
set -euo pipefail
cd "$(dirname "$0")/.."
# The loop reads jq through process substitution, which set -e ignores, so a missing jq would post nothing and pass.
command -v jq >/dev/null || { echo "send.sh needs jq" >&2; exit 1; }

if [[ -z "${FRAUD_API_KEY:-}" && -f .env ]]; then
    source .env
fi
url="${FRAUD_URL:-http://localhost:8080}"
failed=0

# Move every occurredAt so the last event lands at today's midnight (UTC), keeping the gaps between events. The app
# rejects events older than fraud.max-event-age, and a second run the same day is a replay.
while read -r txn; do
    expected=$(jq -r .expect <<<"$txn")
    response=$(curl -sS --fail-with-body "$url/api/v1/transactions" -H "X-API-Key: $FRAUD_API_KEY" \
        -H 'Content-Type: application/json' -d "$(jq -c 'del(.expect)' <<<"$txn")") \
        || { echo "$response" >&2; exit 1; }
    decision=$(jq -r .decision <<<"$response")
    mark=""
    if [[ "$decision" != "$expected" ]]; then
        mark="  <-- expected $expected"
        failed=1
    fi
    printf '%-16s %-10s %s%s\n' "$(jq -r .transactionId <<<"$response")" "$decision" \
        "$(jq -r '[.hits[].ruleId] | join(" ")' <<<"$response")" "$mark"
done < <(jq -c '((now | floor | . - . % 86400) - (map(.occurredAt | fromdateiso8601) | max)) as $shift
    | .[] | .occurredAt |= (fromdateiso8601 + $shift | todateiso8601)' samples/transactions.json)

exit "$failed"
