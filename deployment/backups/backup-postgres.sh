#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${1:-production}"
OUT_DIR="$ROOT/deployment/backups/files"
mkdir -p "$OUT_DIR"

case "$MODE" in
  local)
    FILE="$OUT_DIR/waad-local-$(date +%Y%m%d-%H%M%S).dump"
    docker exec waad-postgres-dev pg_dump -U postgres -d tba_waad_system -Fc > "$FILE"
    echo "$FILE"
    ;;
  production|prod)
    cd "$ROOT"
    FILE="$OUT_DIR/waad-production-$(date +%Y%m%d-%H%M%S).dump"
    docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.production exec -T db pg_dump -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-tba_waad_system}" -Fc > "$FILE"
    echo "$FILE"
    ;;
  *)
    echo "Usage: backup-postgres.sh local|production" >&2
    exit 1
    ;;
esac
