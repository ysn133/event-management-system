#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
command -v flock >/dev/null || { echo 'flock (util-linux) is required.' >&2; exit 1; }
exec 9>deploy/.deploy.lock
flock -n 9 || { echo 'Another production deployment is already running.' >&2; exit 1; }
ENV_FILE="${EVENTFLOW_ENV_FILE:-.env.production}"
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo 'Docker Compose v2 is required.' >&2; exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Copy deploy/production.env.example to $ENV_FILE and fill in the settings first." >&2
  exit 1
fi
COMPOSE+=(--env-file "$ENV_FILE" -f compose.production.yaml)
"${COMPOSE[@]}" config --quiet
# Build before stopping anything. A failed build leaves the running app alone.
"${COMPOSE[@]}" build
"${COMPOSE[@]}" up -d --wait postgres

# Save an existing database before migration. The directory is private and ignored.
schema_ready="$("${COMPOSE[@]}" exec -T postgres sh -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT to_regclass('\''public.evenements'\'') IS NOT NULL"')"
if [[ "$schema_ready" == "t" ]]; then
  umask 077
  mkdir -p deploy/backups
  backup="deploy/backups/eventflow-$(date -u +%Y%m%dT%H%M%SZ).dump"
  "${COMPOSE[@]}" exec -T postgres sh -c \
    'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
  echo "Database backup: $backup"
fi

# Never migrate while application containers can write to the database.
"${COMPOSE[@]}" stop gateway auth-service evenement-service budget-service \
  prestataires-service salles-service invites-service
"${COMPOSE[@]}" up --no-deps --force-recreate --exit-code-from migrate migrate
"${COMPOSE[@]}" up -d --wait --wait-timeout 300
"${COMPOSE[@]}" ps
echo 'EventFlow is ready. Configure your existing Nginx using deploy/nginx-site.conf.example.'
