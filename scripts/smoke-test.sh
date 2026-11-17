#!/usr/bin/env bash
set -Eeuo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:5173}"

health="$(curl -fsS "$BACKEND_URL/api/health")"
[[ "$health" == *'"status":"UP"'* ]] || { echo "backend health check failed: $health" >&2; exit 1; }

page="$(curl -fsS "$FRONTEND_URL/")"
[[ "$page" == *'<div id="app"></div>'* ]] || { echo "frontend page check failed" >&2; exit 1; }

response="$(curl -fsS -X POST "$BACKEND_URL/api/prompts/optimize" \
  -H 'Content-Type: application/json' \
  --data '{"originalPrompt":"写一个登录接口","scene":"CODE","style":"PROFESSIONAL"}')"
[[ "$response" == *'"optimizedPrompt"'* && "$response" == *'"suggestions"'* ]] || {
  echo "optimization check failed: $response" >&2
  exit 1
}

history_id="$(node -e 'let input=""; process.stdin.on("data", chunk => input += chunk); process.stdin.on("end", () => console.log(JSON.parse(input).historyId || ""));' <<<"$response")"
[[ -n "$history_id" ]] || { echo "history id missing: $response" >&2; exit 1; }
history="$(curl -fsS "$BACKEND_URL/api/prompts/history")"
[[ "$history" == *"$history_id"* ]] || { echo "history record missing: $history" >&2; exit 1; }
feedback="$(curl -fsS -X PUT "$BACKEND_URL/api/prompts/history/$history_id/feedback" \
  -H 'Content-Type: application/json' \
  --data '{"rating":5}')"
[[ "$feedback" == *'"rating":5'* ]] || { echo "history feedback failed: $feedback" >&2; exit 1; }
delete_status="$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$BACKEND_URL/api/prompts/history/$history_id")"
[[ "$delete_status" == "204" ]] || { echo "history delete failed: $delete_status" >&2; exit 1; }

echo "Smoke test passed: health, frontend page, optimization, history save, and history delete."
