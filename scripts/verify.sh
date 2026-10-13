#!/usr/bin/env bash
# Verifies the gateway rewrites public paths and the backend receives the modified path.
# Usage: ./scripts/verify.sh [gateway_base_url]
set -u
GW="${1:-http://localhost:8080}"
pass=0; fail=0

check() { # name, expected_status, expected_received_path(or -), curl args...
  local name="$1" exp_status="$2" exp_path="$3"; shift 3
  local hdrs; hdrs=$(mktemp)
  local status; status=$(curl -s -o /dev/null -D "$hdrs" -w '%{http_code}' "$@")
  local got_path; got_path=$(grep -i '^X-Received-Path:' "$hdrs" | awk '{print $2}' | tr -d '\r')
  rm -f "$hdrs"
  if [[ "$status" == "$exp_status" && ( "$exp_path" == "-" || "$got_path" == "$exp_path" ) ]]; then
    echo "PASS  $name  (status=$status, backend received=${got_path:-n/a})"; pass=$((pass+1))
  else
    echo "FAIL  $name  (status=$status expected $exp_status, backend received=${got_path:-n/a} expected $exp_path)"; fail=$((fail+1))
  fi
}

echo "Gateway: $GW"
echo "-------------------------------------------------------------"
check "GET  /accounts                 -> rewrite" 200 /api/v1/internal/accounts         "$GW/accounts"
check "GET  /accounts/ACC1001         -> rewrite" 200 /api/v1/internal/accounts/ACC1001 "$GW/accounts/ACC1001"
check "POST /accounts                 -> rewrite" 201 /api/v1/internal/accounts \
      -X POST -H 'Content-Type: application/json' \
      -d '{"holderName":"Neha Kapoor","type":"SAVINGS","openingBalance":5000,"currency":"INR"}' "$GW/accounts"
check "GET  /account-service/api/v1/internal/accounts -> StripPrefix" 200 /api/v1/internal/accounts \
      "$GW/account-service/api/v1/internal/accounts"
check "GET  /api/v1/internal/accounts -> blocked at edge" 403 - "$GW/api/v1/internal/accounts"
echo "-------------------------------------------------------------"
echo "Passed: $pass  Failed: $fail"
echo
echo "Sample response body from GET $GW/accounts:"
curl -s "$GW/accounts"; echo
[[ $fail -eq 0 ]]
