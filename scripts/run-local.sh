#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The services require Java 21. Do not inherit the user's JAVA_HOME because it
# may point to Java 17 (or another version used by a different project).
JAVA_HOME_VALUE="${EVENTFLOW_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
DATABASE_URL_VALUE="${DATABASE_URL:-jdbc:postgresql://localhost:5433/eventflow}"
DATABASE_USERNAME_VALUE="${DATABASE_USERNAME:-eventflow}"
DATABASE_PASSWORD_VALUE="${DATABASE_PASSWORD:-eventflow_dev_password}"
FRONTEND_ORIGIN_VALUE="${FRONTEND_ORIGIN:-http://localhost:5173}"
EVENT_SERVICE_URL_VALUE="${EVENT_SERVICE_URL:-http://localhost:5001}"
DB_NAME_VALUE="${DB_NAME:-eventflow}"
DB_USER_VALUE="${DB_USER:-eventflow}"
LOG_DIR="$(mktemp -d /tmp/eventflow-local.XXXXXX)"

EVENT_PID=""
BUDGET_PID=""

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  printf '\nStopping EventFlow application processes...\n'
  if [[ -n "$EVENT_PID" ]] && kill -0 "$EVENT_PID" 2>/dev/null; then
    kill "$EVENT_PID" 2>/dev/null || true
  fi
  if [[ -n "$BUDGET_PID" ]] && kill -0 "$BUDGET_PID" 2>/dev/null; then
    kill "$BUDGET_PID" 2>/dev/null || true
  fi

  wait "$EVENT_PID" 2>/dev/null || true
  wait "$BUDGET_PID" 2>/dev/null || true

  printf 'Services stopped. PostgreSQL is still running.\n'
  printf 'Logs: %s\n' "$LOG_DIR"
  exit "$exit_code"
}

trap cleanup EXIT INT TERM

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$1" >&2
    exit 1
  fi
}

wait_for_service() {
  local name=$1
  local url=$2
  local pid=$3
  local log_file=$4

  for _ in {1..60}; do
    if curl --silent --fail --max-time 2 "$url" >/dev/null; then
      printf '%s is ready.\n' "$name"
      return 0
    fi

    if ! kill -0 "$pid" 2>/dev/null; then
      printf '%s stopped before becoming ready.\n' "$name" >&2
      tail -n 40 "$log_file" >&2
      return 1
    fi

    sleep 1
  done

  printf '%s did not become ready in time.\n' "$name" >&2
  tail -n 40 "$log_file" >&2
  return 1
}

prepare_database() {
  local schema_ready

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
    return 1
  fi

  schema_ready="$("${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
    psql -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" -Atqc \
    "SELECT to_regclass('public.evenements') IS NOT NULL AND to_regclass('public.budgets') IS NOT NULL AND to_regclass('public.depenses') IS NOT NULL")"

  if [[ "$schema_ready" == "t" ]]; then
    printf 'PostgreSQL schema is ready.\n'
    apply_migrations
    return 0
  fi

  # A bind-mounted directory can be replaced while a long-lived container is
  # stopped (for example after switching branches). Restart once so PostgreSQL
  # reopens the current persistent directory before treating it as empty.
  printf 'Refreshing the PostgreSQL data mount...\n'
  "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" restart postgres >/dev/null

  for _ in {1..60}; do
    if "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
      pg_isready -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  schema_ready="$("${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
    psql -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" -Atqc \
    "SELECT to_regclass('public.evenements') IS NOT NULL AND to_regclass('public.budgets') IS NOT NULL AND to_regclass('public.depenses') IS NOT NULL")"

  if [[ "$schema_ready" == "t" ]]; then
    printf 'PostgreSQL schema and persisted data are ready.\n'
    apply_migrations
    return 0
  fi

  printf 'Initializing the shared PostgreSQL schema and seed data...\n'
  "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" \
    < "$PROJECT_DIR/database/init/001_schema.sql"
  "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" \
    < "$PROJECT_DIR/database/init/002_seed.sql"
  apply_migrations
}

apply_migrations() {
  local migration
  for migration in "$PROJECT_DIR"/database/migrations/*.sql; do
    [[ -f "$migration" ]] || continue
    printf 'Applying database migration %s...\n' "$(basename "$migration")"
    "${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" exec -T postgres \
      psql -v ON_ERROR_STOP=1 -U "$DB_USER_VALUE" -d "$DB_NAME_VALUE" < "$migration"
  done
}

require_command curl
require_command mvn
require_command npx

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  printf 'Docker Compose was not found.\n' >&2
  exit 1
fi

if [[ ! -x "$JAVA_HOME_VALUE/bin/java" ]]; then
  printf 'Java 21 was not found at %s. Set EVENTFLOW_JAVA_HOME to your Java 21 directory.\n' "$JAVA_HOME_VALUE" >&2
  exit 1
fi

JAVA_MAJOR_VERSION="$($JAVA_HOME_VALUE/bin/java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
if [[ "$JAVA_MAJOR_VERSION" != "21" ]]; then
  printf 'EventFlow requires Java 21, but %s contains Java %s.\n' \
    "$JAVA_HOME_VALUE" "${JAVA_MAJOR_VERSION:-unknown}" >&2
  printf 'Set EVENTFLOW_JAVA_HOME to your Java 21 directory.\n' >&2
  exit 1
fi

if [[ ! -f "$PROJECT_DIR/frontend/node_modules/vite/bin/vite.js" ]]; then
  printf 'Frontend dependencies are missing. Install them before running this script.\n' >&2
  exit 1
fi

printf 'Starting PostgreSQL...\n'
"${COMPOSE[@]}" -f "$PROJECT_DIR/compose.yaml" up -d postgres
prepare_database

printf 'Using Java 21 from %s\n' "$JAVA_HOME_VALUE"

printf 'Starting Event service on http://localhost:5001...\n'
(
  cd "$PROJECT_DIR/services/evenement-service"
  env \
    JAVA_HOME="$JAVA_HOME_VALUE" \
    PATH="$JAVA_HOME_VALUE/bin:$PATH" \
    DATABASE_URL="$DATABASE_URL_VALUE" \
    DATABASE_USERNAME="$DATABASE_USERNAME_VALUE" \
    DATABASE_PASSWORD="$DATABASE_PASSWORD_VALUE" \
    PORT=5001 \
    mvn spring-boot:run
) >"$LOG_DIR/evenement-service.log" 2>&1 &
EVENT_PID=$!

printf 'Starting Budget service on http://localhost:5004...\n'
(
  cd "$PROJECT_DIR/services/budget-service"
  env \
    JAVA_HOME="$JAVA_HOME_VALUE" \
    PATH="$JAVA_HOME_VALUE/bin:$PATH" \
    DATABASE_URL="$DATABASE_URL_VALUE" \
    DATABASE_USERNAME="$DATABASE_USERNAME_VALUE" \
    DATABASE_PASSWORD="$DATABASE_PASSWORD_VALUE" \
    EVENT_SERVICE_URL="$EVENT_SERVICE_URL_VALUE" \
    FRONTEND_ORIGIN="$FRONTEND_ORIGIN_VALUE" \
    PORT=5004 \
    mvn spring-boot:run
) >"$LOG_DIR/budget-service.log" 2>&1 &
BUDGET_PID=$!

wait_for_service \
  "Event service" \
  "http://localhost:5001/actuator/health" \
  "$EVENT_PID" \
  "$LOG_DIR/evenement-service.log"

wait_for_service \
  "Budget service" \
  "http://localhost:5004/actuator/health" \
  "$BUDGET_PID" \
  "$LOG_DIR/budget-service.log"

printf '\nEventFlow is ready:\n'
printf '  Frontend:      http://localhost:5173\n'
printf '  Event service: http://localhost:5001\n'
printf '  Budget service:http://localhost:5004\n'
printf '  Logs:          %s\n' "$LOG_DIR"
printf '\nPress Ctrl+C to stop the applications.\n\n'

cd "$PROJECT_DIR/frontend"
npx -y node@20 node_modules/vite/bin/vite.js --host 0.0.0.0 --port 5173 --strictPort
