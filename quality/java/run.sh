#!/usr/bin/env bash
set -euo pipefail
quality_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$quality_dir/../.." && pwd)"
export JAVA_HOME="${QUALITY_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
[[ -x "$JAVA_HOME/bin/java" ]] || { echo 'JDK 21 required: set QUALITY_JAVA_HOME.' >&2; exit 1; }
compose_bin="$(command -v docker-compose || true)"
compose() {
 if [[ -n "$compose_bin" ]]; then "$compose_bin" --env-file "$quality_dir/.runtime/compose.env" -f "$quality_dir/compose.yaml" "$@";
 else docker compose --env-file "$quality_dir/.runtime/compose.env" -f "$quality_dir/compose.yaml" "$@"; fi
}
mkdir -p "$quality_dir/results"
case "${1:-help}" in
 unit)
  mvn -B -f "$quality_dir/pom.xml" -Pquality verify > "$quality_dir/results/junit-jacoco.log" 2>&1
  mvn -B -f "$quality_dir/pom.xml" -Pquality -DskipTests checkstyle:checkstyle > "$quality_dir/results/checkstyle.log" 2>&1
  ;;
 start)
  [[ ! -f "$quality_dir/.runtime/pids.json" ]] || { echo 'Recorded environment exists: run stop before start.' >&2; exit 1; }
  node "$quality_dir/prepare-runtime.cjs"
  compose up -d --wait postgres
  for sql in "$repo_dir/database/init/001_schema.sql" "$repo_dir"/database/migrations/*.sql; do
   compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ef_quality -d eventflow_quality < "$sql"
  done > "$quality_dir/results/schema-initialization.log" 2>&1
  compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ef_quality -d eventflow_quality -c "INSERT INTO organisateurs(id,nom,email,mot_de_passe_hash) VALUES (1,'Qualité Java','organisateur@eventflow.test','!not-an-auth-account');"
  (cd "$repo_dir/frontend" && VITE_GATEWAY_URL='' ./node_modules/.bin/vite build --outDir "$quality_dir/.runtime/frontend")
  node "$quality_dir/start-java.cjs"
  compose up -d gateway
  for port in 46355 46351 46354; do
   ready=false
   for attempt in {1..90}; do
    if curl -fsS "http://127.0.0.1:$port/actuator/health" >/dev/null 2>&1; then ready=true; break; fi
    sleep 1
   done
   "$ready" || { echo "Service on $port is not ready; inspect .runtime/*.log" >&2; exit 1; }
  done
  node "$quality_dir/seed-live.cjs"
  ;;
 api) node "$quality_dir/postman.cjs" > "$quality_dir/results/postman.log" 2>&1 ;;
 browser) mvn -B -f "$quality_dir/selenium/pom.xml" -Dtest=JavaJourneysTest test > "$quality_dir/results/selenium.log" 2>&1 ;;
 performance) node "$quality_dir/performance.cjs" > "$quality_dir/results/performance-run.log" 2>&1 ;;
 collect)
  node "$quality_dir/collect-results.cjs"
  mvn -B -f "$quality_dir/selenium/pom.xml" -Dtest=ReportScreenshots test > "$quality_dir/results/report-screenshots.log" 2>&1
  ;;
 stop)
  node "$quality_dir/stop-java.cjs"
  compose down
  ;;
 *) echo 'Usage: bash quality/java/run.sh {unit|start|api|browser|performance|collect|stop}' ;;
esac
