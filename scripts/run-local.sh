#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${PROMPT_OPTIMIZER_ENV_LOADED:-}" != "1" ]]; then
  exec python3 "$ROOT_DIR/scripts/load-env.py" "${APP_ENV_FILE:-$ROOT_DIR/.env}" bash "$ROOT_DIR/scripts/run-local.sh"
fi
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
HOST="${HOST:-${APP_BIND_ADDRESS:-127.0.0.1}}"
DATA_DIR="${APP_DATA_DIR:-$ROOT_DIR/data}"
if [[ "$DATA_DIR" != /* ]]; then
  DATA_DIR="$ROOT_DIR/$DATA_DIR"
fi

cleanup() {
  trap - INT TERM EXIT
  [[ -n "${frontend_pid:-}" ]] && kill "$frontend_pid" 2>/dev/null || true
  [[ -n "${backend_pid:-}" ]] && kill "$backend_pid" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup INT TERM EXIT

# Fail before starting either service if a port is occupied (do not reuse another app).
python3 - "$HOST" "$BACKEND_PORT" "$FRONTEND_PORT" <<'PYPORT'
import socket, sys
host, *ports = sys.argv[1:]
if len(set(ports)) != 2:
    sys.exit('Backend and frontend ports must differ.')
for port in ports:
    try:
        with socket.socket() as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind((host, int(port)))
    except (OSError, ValueError, OverflowError):
        sys.exit(f'Cannot bind {host}:{port}; check the address and stop the existing service or choose another port.')
PYPORT

# Maven's incremental build ensures source changes are included on restart.
(cd "$BACKEND_DIR" && mvn -q package -DskipTests)

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  (cd "$FRONTEND_DIR" && npm ci)
fi

echo "Starting backend on http://$HOST:$BACKEND_PORT"
(cd "$BACKEND_DIR" && LLM_MODE="${LLM_MODE:-mock}" APP_DATA_DIR="$DATA_DIR" SERVER_ADDRESS="$HOST" SERVER_PORT="$BACKEND_PORT" exec java -jar target/prompt-optimizer-api-0.0.1.jar) &
backend_pid=$!

backend_ready=0
for _ in {1..30}; do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Backend exited during startup." >&2
    exit 1
  fi
  if curl --max-time 2 -fsS "http://$HOST:$BACKEND_PORT/api/health" >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  sleep 1
done
if (( backend_ready == 0 )); then
  echo "Backend did not become healthy within 30 seconds." >&2
  exit 1
fi

echo "Starting frontend on http://$HOST:$FRONTEND_PORT"
(cd "$FRONTEND_DIR" && BACKEND_PORT="$BACKEND_PORT" exec node node_modules/vite/bin/vite.js --host "$HOST" --port "$FRONTEND_PORT" --strictPort) &
frontend_pid=$!

echo "Press Ctrl+C to stop both services."
wait -n "$backend_pid" "$frontend_pid"
