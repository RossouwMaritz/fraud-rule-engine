#!/usr/bin/env bash
# Runs once, when Postgres starts on an empty volume. Creates the login the app connects as; the V4 migration
# grants it what it may do.
set -euo pipefail
psql -v ON_ERROR_STOP=1 -v password="$POSTGRES_APP_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
create role fraud_app login password :'password';
SQL
