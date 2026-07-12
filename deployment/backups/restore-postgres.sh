#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${1:-}"
FILE="${2:-}"

[[ -f "$FILE" ]] || {
  echo "Usage: restore-postgres.sh local|production <backup-file>" >&2
  exit 1
}

echo "Restore will overwrite the $MODE database."
read -r -p "Type RESTORE to continue: " CONFIRM
[[ "$CONFIRM" == "RESTORE" ]] || {
  echo "Restore cancelled" >&2
  exit 1
}

case "$MODE" in
  local)
    docker exec -i waad-postgres-dev pg_restore --clean --if-exists --no-owner -U postgres -d tba_waad_system < "$FILE"
    ;;
  production|prod)
    cd "$ROOT"
    docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.production exec -T db pg_restore --clean --if-exists --no-owner -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-tba_waad_system}" < "$FILE"
    ;;
  *)
    echo "Usage: restore-postgres.sh local|production <backup-file>" >&2
    exit 1
    ;;
esac

echo "Restore completed"
