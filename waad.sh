#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT/.env.production"
COMPOSE=(docker compose -f compose.yaml -f compose.prod.yaml --env-file .env.production)
DOMAIN="${DOMAIN:-waadapp.ly}"
WWW_DOMAIN="${WWW_DOMAIN:-www.waadapp.ly}"
REQUIRED_VARS=(POSTGRES_PASSWORD DB_PASSWORD JWT_SECRET ADMIN_DEFAULT_PASSWORD SPRING_PROFILES_ACTIVE)

ok() { printf '[OK] %s\n' "$1"; }
info() { printf '[..] %s\n' "$1"; }
warn() { printf '[!!] %s\n' "$1"; }
fail() { printf '[FAIL] %s\n' "$1" >&2; exit 1; }

cd "$ROOT"

load_env() {
  [[ -f "$ENV_FILE" ]] || fail ".env.production not found"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

random_secret() {
  local bytes="${1:-48}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "$bytes"
  else
    head -c "$bytes" /dev/urandom | base64
  fi
}

set_env_value() {
  local file="$1"
  local name="$2"
  local value="$3"
  if grep -q "^${name}=" "$file"; then
    sed -i "s|^${name}=.*|${name}=${value}|" "$file"
  else
    printf '%s=%s\n' "$name" "$value" >> "$file"
  fi
}

init_prod() {
  if [[ -f "$ENV_FILE" ]]; then
    ok ".env.production already exists; leaving it unchanged"
    return 0
  fi
  [[ -f "$ROOT/.env.production.example" ]] || fail ".env.production.example is missing"
  cp "$ROOT/.env.production.example" "$ENV_FILE"
  local db_password jwt_secret admin_password
  db_password="$(random_secret 36 | tr -d '\n')"
  jwt_secret="$(random_secret 48 | tr -d '\n')"
  admin_password="$(random_secret 24 | tr -d '\n')"
  set_env_value "$ENV_FILE" "POSTGRES_PASSWORD" "$db_password"
  set_env_value "$ENV_FILE" "DB_PASSWORD" "$db_password"
  set_env_value "$ENV_FILE" "JWT_SECRET" "$jwt_secret"
  set_env_value "$ENV_FILE" "ADMIN_DEFAULT_PASSWORD" "$admin_password"
  chmod 600 "$ENV_FILE" 2>/dev/null || true
  ok "Created .env.production with generated strong secrets"
  warn "Save the generated ADMIN_DEFAULT_PASSWORD from .env.production securely, then rotate it after first successful deployment."
  warn ".env.production is ignored by Git; do not commit it."
}

require_docker() {
  command -v docker >/dev/null 2>&1 || fail "docker is not installed"
  docker info >/dev/null 2>&1 || fail "Docker daemon is not running"
  docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin is not available"
  ok "Docker and Compose are available"
}

validate_env() {
  load_env
  for name in "${REQUIRED_VARS[@]}"; do
    [[ -n "${!name:-}" ]] || fail "$name is required in .env.production"
  done
  [[ "${SPRING_PROFILES_ACTIVE}" == "prod" ]] || fail "Production requires SPRING_PROFILES_ACTIVE=prod"
  [[ "$POSTGRES_PASSWORD" == "$DB_PASSWORD" ]] || fail "POSTGRES_PASSWORD and DB_PASSWORD must match while using the postgres DB user"
  for value in "$POSTGRES_PASSWORD" "$DB_PASSWORD" "$JWT_SECRET" "$ADMIN_DEFAULT_PASSWORD"; do
    case "$value" in
      CHANGE_ME*|Admin@123|password|postgres|12345|your_*|*_here) fail "Refusing insecure/default production secret values" ;;
    esac
  done
  if [[ "${EMAIL_ENABLED:-false}" == "true" ]]; then
    [[ -n "${EMAIL_USERNAME:-}" ]] || fail "EMAIL_USERNAME is required when EMAIL_ENABLED=true"
    [[ -n "${EMAIL_PASSWORD:-}" ]] || fail "EMAIL_PASSWORD is required when EMAIL_ENABLED=true"
  fi
  ok ".env.production is valid without printing secret values"
}

check_disk() {
  local avail_kb
  avail_kb="$(df -Pk . | awk 'NR==2 {print $4}')"
  [[ "$avail_kb" -gt 2097152 ]] || fail "Less than 2GB free disk space"
  ok "Disk space OK"
}

check_ssl_files() {
  [[ -f "$ROOT/ssl/fullchain.pem" ]] || fail "Missing ssl/fullchain.pem. Place certificates there or terminate TLS with an external reverse proxy."
  [[ -f "$ROOT/ssl/privkey.pem" ]] || fail "Missing ssl/privkey.pem. Place certificates there or terminate TLS with an external reverse proxy."
  ok "SSL certificate files are present"
}

container_id() {
  "${COMPOSE[@]}" ps -q "$1" 2>/dev/null || true
}

wait_for_container_health() {
  local service="$1"
  local name="$2"
  local tries="${3:-60}"
  info "Waiting for $name container health"
  for _ in $(seq 1 "$tries"); do
    local id status
    id="$(container_id "$service")"
    if [[ -n "$id" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || true)"
      if [[ "$status" == "healthy" || "$status" == "running" ]]; then
        ok "$name healthy"
        return 0
      fi
      if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
        warn "$name status: $status"
      fi
    fi
    sleep 2
  done
  fail "$name did not become healthy. Run: bash waad.sh logs $service"
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local tries="${3:-60}"
  info "Waiting for $name at $url"
  for _ in $(seq 1 "$tries"); do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
      ok "$name healthy"
      return 0
    fi
    sleep 2
  done
  fail "$name did not become healthy"
}

backup() {
  validate_env
  mkdir -p deployment/backups/files
  local file="deployment/backups/files/waad-production-$(date +%Y%m%d-%H%M%S).dump"
  "${COMPOSE[@]}" exec -T db pg_dump -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-tba_waad_system}" -Fc > "$file"
  ok "Backup created: $file"
}

backup_if_existing_db() {
  if docker volume inspect waad_prod_pgdata >/dev/null 2>&1 || [[ -n "$(container_id db)" ]]; then
    info "Existing production database state detected; starting db for backup"
    "${COMPOSE[@]}" up -d db
    wait_for_container_health db "PostgreSQL" 60
    backup
  else
    warn "No existing waad_prod_pgdata volume detected; skipping pre-deploy database backup for first deployment"
  fi
}

restore() {
  local file="${1:-}"
  [[ -f "$file" ]] || fail "Usage: bash waad.sh restore <backup-file>"
  validate_env
  warn "Restore will overwrite data in the production db service."
  read -r -p "Type RESTORE to continue: " confirm
  [[ "$confirm" == "RESTORE" ]] || fail "Restore cancelled"
  "${COMPOSE[@]}" exec -T db pg_restore --clean --if-exists --no-owner -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-tba_waad_system}" < "$file"
  ok "Restore completed"
}

deploy() {
  require_docker
  validate_env
  check_ssl_files
  check_disk
  local commit
  commit="$(git rev-parse --short HEAD 2>/dev/null || true)"
  if ! git diff --quiet || ! git diff --cached --quiet; then
    warn "Working tree has uncommitted changes; deploying local tree"
  fi
  info "Deploying commit: ${commit:-unknown}"
  backup_if_existing_db
  "${COMPOSE[@]}" build --pull
  "${COMPOSE[@]}" up -d db
  wait_for_container_health db "PostgreSQL" 60
  "${COMPOSE[@]}" up -d backend
  wait_for_container_health backend "Backend" 90 || {
    warn "Backend health failed; attempting application rollback by restarting previous containers"
    "${COMPOSE[@]}" up -d backend frontend || true
    exit 1
  }
  "${COMPOSE[@]}" up -d frontend
  wait_for_container_health frontend "Frontend/Nginx" 60
  wait_for_url "HTTPS app" "https://${DOMAIN}/health" 60
  wait_for_url "HTTPS www app" "https://${WWW_DOMAIN}/health" 60
  "${COMPOSE[@]}" ps
  ok "Deployment complete at commit ${commit:-unknown}"
  printf 'Frontend: https://%s\n' "$DOMAIN"
  printf 'Frontend: https://%s\n' "$WWW_DOMAIN"
}

doctor() {
  echo "WAAD production Docker doctor"
  require_docker || true
  if [[ -f "$ENV_FILE" ]]; then
    validate_env || true
  else
    warn ".env.production missing"
  fi
  [[ -f "$ROOT/ssl/fullchain.pem" ]] && ok "ssl/fullchain.pem present" || warn "ssl/fullchain.pem missing"
  [[ -f "$ROOT/ssl/privkey.pem" ]] && ok "ssl/privkey.pem present" || warn "ssl/privkey.pem missing"
  docker compose version || true
  df -h . || true
  git branch --show-current 2>/dev/null || true
  git rev-parse --short HEAD 2>/dev/null || true
  "${COMPOSE[@]}" ps || true
  curl -fsS "https://${DOMAIN}/health" >/dev/null 2>&1 && ok "$DOMAIN health OK" || warn "$DOMAIN health unavailable"
  curl -fsS "https://${WWW_DOMAIN}/health" >/dev/null 2>&1 && ok "$WWW_DOMAIN health OK" || warn "$WWW_DOMAIN health unavailable"
}

case "${1:-help}" in
  init-prod) init_prod ;;
  deploy) deploy ;;
  up) require_docker; validate_env; "${COMPOSE[@]}" up -d ;;
  down) require_docker; "${COMPOSE[@]}" down --remove-orphans ;;
  restart) require_docker; validate_env; "${COMPOSE[@]}" restart ;;
  rebuild) require_docker; validate_env; "${COMPOSE[@]}" build "${2:-}"; "${COMPOSE[@]}" up -d "${2:-}" ;;
  logs) "${COMPOSE[@]}" logs -f "${2:-}" ;;
  status) "${COMPOSE[@]}" ps ;;
  health) wait_for_url "HTTPS app" "https://${DOMAIN}/health" 30 ;;
  doctor) doctor ;;
  backup) backup ;;
  restore) restore "${2:-}" ;;
  config) validate_env; "${COMPOSE[@]}" config -q; ok "Production Compose config is valid" ;;
  *)
    echo "Usage: bash waad.sh init-prod|deploy|up|down|restart|rebuild [service]|logs [service]|status|health|doctor|backup|restore <file>|config"
    ;;
esac
