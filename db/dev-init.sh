#!/bin/sh
# Used only by docker-compose.dev.yml: runs db/init.sql in the throwaway Postgres.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v auth_user="$AUTH_DB_USERNAME" -v auth_password="$AUTH_DB_PASSWORD" \
  -v app_user="$APP_DB_USERNAME" -v app_password="$APP_DB_PASSWORD" \
  -f /db/init.sql
