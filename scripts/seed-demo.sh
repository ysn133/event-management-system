#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_NAME_VALUE="${DB_NAME:-eventflow}"
DB_USER_VALUE="${DB_USER:-eventflow}"
RESET_DEMO_DATA=false

if [[ "${1:-}" == "--reset" ]]; then
  RESET_DEMO_DATA=true
elif [[ -n "${1:-}" ]]; then
  printf 'Usage: %s [--reset]\n' "$0" >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  printf 'Docker Compose was not found.\n' >&2
  exit 1
fi

printf 'Starting PostgreSQL if necessary...\n'
"${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" up -d postgres

for _ in {1..60}; do
  if "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
    pg_isready -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
  pg_isready -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" >/dev/null 2>&1; then
  printf 'PostgreSQL did not become ready in time.\n' >&2
  exit 1
fi

if [[ "$RESET_DEMO_DATA" == "true" ]]; then
  printf 'Removing existing EventFlow demo records...\n'
  "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" \
    < "$PROJECT_DIR/database/demo/000_remove_demo_data.sql"
fi

printf 'Loading EventFlow demo records...\n'
"${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" \
  < "$PROJECT_DIR/database/demo/001_demo_data.sql"

"${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
  psql -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" -Atqc \
  "SELECT format('Demo ready: %s events, %s providers, %s rooms, %s guests, %s budgets, %s expenses',
      (SELECT count(*) FROM evenements WHERE id BETWEEN 1001 AND 1099),
      (SELECT count(*) FROM prestataires WHERE id BETWEEN 1001 AND 1099),
      (SELECT count(*) FROM salles WHERE id BETWEEN 1001 AND 1099),
      (SELECT count(*) FROM invites WHERE id BETWEEN 1001 AND 1099),
      (SELECT count(*) FROM budgets WHERE id BETWEEN 1001 AND 1099),
      (SELECT count(*) FROM depenses WHERE id BETWEEN 1001 AND 1099))"
