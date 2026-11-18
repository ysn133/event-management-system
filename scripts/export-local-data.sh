#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
umask 077
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else COMPOSE=(docker-compose); fi
mkdir -p deploy/backups
output="deploy/backups/local-business-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
temp=$(mktemp deploy/backups/export.XXXXXX)
trap 'rm -f "$temp"' EXIT
# Snapshot of business tables only: no local login hashes or migration history.
"${COMPOSE[@]}" -f compose.yaml exec -T postgres sh -c '
  exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --data-only \
    --column-inserts --on-conflict-do-nothing --no-owner --no-privileges \
    -t public.organisateurs -t public.evenements -t public.budgets \
    -t public.depenses -t public.salles -t public.reservations_salle \
    -t public.prestataires -t public.services_prestataire \
    -t public.contrats_prestataire -t public.invites -t public.invitations
' | gzip > "$temp"
gzip -t "$temp"
mv "$temp" "$output"
printf 'Private export (do not commit): %s\n' "$output"
