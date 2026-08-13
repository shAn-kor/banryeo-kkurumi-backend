#!/usr/bin/env bash
set -euo pipefail

allowlist="config/public-api-allowlist.txt"
test -s "$allowlist"
test -s "config/openapi-payment-snapshot.json"
jq -e '.path == "/api/v1/orders/{orderId}/payment" and .method == "get" and .schema == "PaymentView"' \
  config/openapi-payment-snapshot.json >/dev/null
LC_ALL=C sort -c "$allowlist"
if rg -n '^/api|^(GET|POST|PUT|PATCH|DELETE)  ' "$allowlist"; then
  echo "API allowlist 형식이 올바르지 않습니다." >&2
  exit 1
fi
echo "API allowlist: OK"
