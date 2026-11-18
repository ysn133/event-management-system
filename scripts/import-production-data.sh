#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$#" != 2 || "$1" != --confirm-empty-target ]]; then
  echo "Usage: bash scripts/import-production-data.sh --confirm-empty-target /path/to/trusted-local-export.sql.gz" >&2
  echo 'Imports business data into an empty production database, preserving VPS login accounts.' >&2
  exit 1
fi
input=$(realpath "$2")
[[ -f "$input" ]] || { echo 'Export file missing.' >&2; exit 1; }
gzip -t "$input"
cd "$PROJECT_DIR"
umask 077
exec 9>deploy/.deploy.lock
flock -n 9 || { echo 'Another deployment or import is running.' >&2; exit 1; }
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else COMPOSE=(docker-compose); fi
COMPOSE+=(--env-file "${EVENTFLOW_ENV_FILE:-.env.production}" -f compose.production.yaml)
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" stop gateway auth-service evenement-service budget-service salles-service prestataires-service invites-service
mkdir -p deploy/backups
backup="deploy/backups/before-import-$(date -u +%Y%m%dT%H%M%SZ).dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
echo "VPS backup: $backup"
# A failed import rolls back all rows; leave the app stopped for inspection.
{ cat deploy/import-empty-business.sql; gzip -dc "$input"; } | \
  "${COMPOSE[@]}" exec -T postgres sh -c \
    'psql -X -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
"${COMPOSE[@]}" up -d --wait --wait-timeout 300
echo 'Business data imported. Existing VPS login and bootstrap organizer were preserved.'
