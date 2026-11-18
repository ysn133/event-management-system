#!/bin/sh
set -eu
# Only one migration job must run at a time; deploy-production.sh stops writers.
psql -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE IF NOT EXISTS eventflow_schema_history (
    filename text PRIMARY KEY,
    checksum text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);
SQL

apply() {
    file=$1
    filename=$(basename "$file")
    checksum=$(sha256sum "$file" | cut -d ' ' -f 1)
    previous=$(psql -Atqc "SELECT checksum FROM eventflow_schema_history WHERE filename='$filename'")
    if [ -n "$previous" ]; then
        [ "$previous" = "$checksum" ] || {
            echo "Previously applied SQL changed: $filename. Add a new migration instead." >&2
            exit 1
        }
        return
    fi
    echo "Applying $filename"
    psql -v ON_ERROR_STOP=1 -f "$file"
    psql -v ON_ERROR_STOP=1 -c "INSERT INTO eventflow_schema_history(filename,checksum) VALUES ('$filename','$checksum')"
}

apply /schema/001_schema.sql
for file in /migrations/*.sql; do
    [ -f "$file" ] || continue
    apply "$file"
done
# The current event form uses organizer 1. Its login belongs to Auth/app_users,
# not the legacy organisers password column. No demonstration event is inserted.
psql -v ON_ERROR_STOP=1 -v email="$BOOTSTRAP_USER_EMAIL" <<'SQL'
INSERT INTO organisateurs(id,nom,email,mot_de_passe_hash)
VALUES (1,'Organisateur', :'email', '!login-managed-by-auth-service')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('organisateurs','id'),
    GREATEST((SELECT MAX(id) FROM organisateurs),1),true);
SQL
