#!/usr/bin/env bash
set -euo pipefail

: "${API_BASE_URL:?Set API_BASE_URL to the deployed HTTPS origin, for example https://ledger.example.com}"
: "${MINIAPP_JWT:?Set MINIAPP_JWT to a valid miniapp Bearer token}"

for command in curl jq awk grep mktemp; do
  command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 2; }
done

COOKIE_JAR="$(mktemp)"
SSO_COOKIE_JAR="$(mktemp)"
cleanup() { rm -f "$COOKIE_JAR" "$SSO_COOKIE_JAR"; }
trap cleanup EXIT

echo "[1/5] Create and approve a browser login request"
LOGIN_JSON="$(curl -fsS -X POST "$API_BASE_URL/api/web-auth/requests" -H 'Content-Type: application/json' -d '{}')"
REQUEST_ID="$(jq -er '.data.requestId' <<<"$LOGIN_JSON")"
BROWSER_PROOF="$(jq -er '.data.browserProof' <<<"$LOGIN_JSON")"
curl -fsS -X POST "$API_BASE_URL/api/web-auth/requests/$REQUEST_ID/approve" -H "Authorization: Bearer $MINIAPP_JWT" -H 'Content-Type: application/json' -d '{}' | jq -e '.code == 200' >/dev/null
curl -fsS -c "$COOKIE_JAR" -X POST "$API_BASE_URL/api/web-auth/requests/$REQUEST_ID/exchange" -H 'Content-Type: application/json' -d "$(jq -cn --arg proof "$BROWSER_PROOF" '{browserProof:$proof}')" | jq -e '.code == 200' >/dev/null

echo "[2/5] Verify one-time browser exchange and CSRF rejection"
HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API_BASE_URL/api/web-auth/requests/$REQUEST_ID/exchange" -H 'Content-Type: application/json' -d "$(jq -cn --arg proof "$BROWSER_PROOF" '{browserProof:$proof}')")"
test "$HTTP_CODE" = 409
HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/chat" -H 'Content-Type: application/json' -d '{"message":"测试 CSRF","thinkingEnabled":false}')"
test "$HTTP_CODE" = 403

echo "[3/5] Verify Web session access"
curl -fsS -b "$COOKIE_JAR" "$API_BASE_URL/api/agent/sessions" | jq -e '.code == 200 and (.data | type == "array")' >/dev/null

echo "[4/5] Verify the miniapp one-time link"
LINK_JSON="$(curl -fsS -X POST "$API_BASE_URL/api/web-auth/miniapp-links" -H "Authorization: Bearer $MINIAPP_JWT" -H 'Content-Type: application/json' -d '{}')"
LOGIN_URL="$(jq -er '.data.loginUrl' <<<"$LINK_JSON")"
TICKET="${LOGIN_URL##*#ticket=}"
curl -fsS -c "$SSO_COOKIE_JAR" -X POST "$API_BASE_URL/api/web-auth/sso/exchange" -H 'Content-Type: application/json' -d "$(jq -cn --arg ticket "$TICKET" '{ticket:$ticket}')" | jq -e '.code == 200' >/dev/null
HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API_BASE_URL/api/web-auth/sso/exchange" -H 'Content-Type: application/json' -d "$(jq -cn --arg ticket "$TICKET" '{ticket:$ticket}')")"
test "$HTTP_CODE" = 409

echo "[5/5] Optional DeepSeek normal/thinking stream checks"
if [[ "${RUN_MODEL_CHECKS:-false}" == "true" ]]; then
  XSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { value=$7 } END { print value }' "$COOKIE_JAR")"
  for THINKING in false true; do
    STREAM="$(curl -fsS -N -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/chat" -H "X-CSRF-TOKEN: $XSRF_TOKEN" -H 'Content-Type: application/json' -d "$(jq -cn --argjson thinking "$THINKING" '{message:"查询本月预算执行情况",thinkingEnabled:$thinking}')")"
    grep -q 'event:message_start\|event: message_start' <<<"$STREAM"
    grep -q 'event:done\|event: done' <<<"$STREAM"
    if grep -Eq 'reasoning|tool_call|tool_result' <<<"$STREAM"; then echo "Internal model data leaked into public SSE" >&2; exit 1; fi
  done

  if [[ "${RUN_WRITE_ACTION_CHECKS:-false}" == "true" ]]; then
    WRITE_PROMPT="${E2E_WRITE_PROMPT:-创建一个名为E2E验收的科目}"
    ACTION_STREAM="$(curl -fsS -N -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/chat" -H "X-CSRF-TOKEN: $XSRF_TOKEN" -H 'Content-Type: application/json' -d "$(jq -cn --arg prompt "$WRITE_PROMPT" '{message:$prompt,thinkingEnabled:false}')")"
    ACTION_JSON="$(awk '/^data:/{sub(/^data:[[:space:]]*/, ""); if ($0 ~ /"actionId"/) value=$0} END {print value}' <<<"$ACTION_STREAM")"
    ACTION_ID="$(jq -er '.actionId' <<<"$ACTION_JSON")"
    if [[ "${E2E_ALLOW_LEDGER_WRITES:-false}" == "true" ]]; then
      FIRST="$(curl -fsS -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/actions/$ACTION_ID/confirm" -H "X-CSRF-TOKEN: $XSRF_TOKEN" -H 'Content-Type: application/json' -d '{}')"
      SECOND="$(curl -fsS -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/actions/$ACTION_ID/confirm" -H "X-CSRF-TOKEN: $XSRF_TOKEN" -H 'Content-Type: application/json' -d '{}')"
      test "$(jq -er '.data.status' <<<"$FIRST")" = "EXECUTED"
      test "$(jq -er '.data.status' <<<"$SECOND")" = "EXECUTED"
      test "$(jq -er '.data.actionId' <<<"$FIRST")" = "$(jq -er '.data.actionId' <<<"$SECOND")"
    else
      curl -fsS -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/actions/$ACTION_ID/cancel" -H "X-CSRF-TOKEN: $XSRF_TOKEN" -H 'Content-Type: application/json' -d '{}' | jq -e '.data.status == "CANCELLED"' >/dev/null
      echo "Write action was cancelled. Set E2E_ALLOW_LEDGER_WRITES=true with an isolated test ledger to verify confirm and repeated confirm."
    fi
  fi

  if [[ "${RUN_DISCONNECT_CHECK:-false}" == "true" ]]; then
    curl -sS -N --max-time 1 -b "$COOKIE_JAR" -X POST "$API_BASE_URL/api/agent/chat" -H "X-CSRF-TOKEN: $XSRF_TOKEN" -H 'Content-Type: application/json' -d '{"message":"分析近期趋势并给出详细建议","thinkingEnabled":true}' >/dev/null || true
    curl -fsS -b "$COOKIE_JAR" "$API_BASE_URL/api/agent/sessions" | jq -e '.code == 200' >/dev/null
  fi
else
  echo "Skipped: set RUN_MODEL_CHECKS=true with DeepSeek configured to exercise normal and thinking streams."
fi

if [[ "${RUN_EXPIRY_CHECK:-false}" == "true" ]]; then
  EXPIRED_JSON="$(curl -fsS -X POST "$API_BASE_URL/api/web-auth/requests" -H 'Content-Type: application/json' -d '{}')"
  EXPIRED_ID="$(jq -er '.data.requestId' <<<"$EXPIRED_JSON")"
  echo "Waiting 121 seconds to verify the two-minute login expiry..."
  sleep 121
  HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API_BASE_URL/api/web-auth/requests/$EXPIRED_ID/approve" -H "Authorization: Bearer $MINIAPP_JWT" -H 'Content-Type: application/json' -d '{}')"
  test "$HTTP_CODE" = 409
fi

echo "Agent Web API smoke checks passed. Dynamic/fixed QR UI and expiry timing still require the documented manual/real-device checks."
