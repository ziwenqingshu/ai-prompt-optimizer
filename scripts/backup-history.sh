#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${PROMPT_OPTIMIZER_ENV_LOADED:-}" != "1" ]]; then
  exec python3 "$ROOT_DIR/scripts/load-env.py" "${APP_ENV_FILE:-$ROOT_DIR/.env}" bash "$ROOT_DIR/scripts/backup-history.sh"
fi
DATA_DIR="${APP_DATA_DIR:-$ROOT_DIR/data}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
if [[ "$DATA_DIR" != /* ]]; then DATA_DIR="$ROOT_DIR/$DATA_DIR"; fi
if [[ "$BACKUP_DIR" != /* ]]; then BACKUP_DIR="$ROOT_DIR/$BACKUP_DIR"; fi

SOURCE="$DATA_DIR/prompt-history.json"
if [[ ! -f "$SOURCE" ]]; then
  echo "No history file found at $SOURCE; nothing to back up."
  exit 0
fi

mkdir -p "$BACKUP_DIR"
TARGET="$BACKUP_DIR/prompt-history-$(date +%Y%m%d-%H%M%S).json"
cp "$SOURCE" "$TARGET"
echo "History backup written to $TARGET"
