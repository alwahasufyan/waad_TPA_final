#!/usr/bin/env bash
set -Eeuo pipefail

MODE="${1:-local}"

check_url() {
  local name="$1"
  local url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null; then
    echo "[OK] $name $url"
  else
    echo "[FAIL] $name $url" >&2
    return 1
  fi
}

case "$MODE" in
  local)
    docker exec waad-postgres-dev pg_isready -U postgres -d tba_waad_system >/dev/null
    echo "[OK] local database waad-postgres-dev"
    check_url "backend" "http://localhost:8081/actuator/health"
    check_url "frontend" "http://localhost:3001/health"
    ;;
  production|prod)
    check_url "frontend HTTPS" "https://waadapp.ly/health"
    check_url "frontend HTTPS www" "https://www.waadapp.ly/health"
    ;;
  *)
    echo "Usage: check-health.sh local|production" >&2
    exit 1
    ;;
esac
