#!/usr/bin/env bash
# Backup of home-inspect: both PostgreSQL databases + photos volume + JWT signing keys volume.
#
#   scripts/backup.sh [destination-dir]        (default: ./backups/<date>)
#
# Reads the connection data from .env. pg_dump runs in a postgres container, so no client
# is needed on the host. Set PG_CLIENT_IMAGE if your server is newer than PostgreSQL 17.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[ -f .env ] || { echo "Missing .env in $ROOT" >&2; exit 1; }
set -a; . ./.env; set +a

PROJECT="${COMPOSE_PROJECT_NAME:-home-inspect}"
PG_CLIENT_IMAGE="${PG_CLIENT_IMAGE:-postgres:17-alpine}"
DEST="${1:-$ROOT/backups/$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"

# Same network options as the services: host Postgres or the network of a Postgres container
NET_ARGS=(--add-host=host.docker.internal:host-gateway)
[ -n "${DB_NETWORK:-}" ] && NET_ARGS+=(--network "$DB_NETWORK")

dump() { # name user password
  echo "· pg_dump $1"
  docker run --rm "${NET_ARGS[@]}" -e PGPASSWORD="$3" -v "$DEST":/backup "$PG_CLIENT_IMAGE" \
    pg_dump -h "$DB_HOST" -p "${DB_PORT:-5432}" -U "$2" -d "$1" -Fc -f "/backup/$1.dump"
}
dump "${AUTH_DB_NAME:-homeinspect_auth}" "${AUTH_DB_USER:-homeinspect_auth}" "$AUTH_DB_PASSWORD"
dump "${APP_DB_NAME:-homeinspect_app}" "${APP_DB_USER:-homeinspect_app}" "$APP_DB_PASSWORD"

archive() { # volume file
  echo "· volume $1"
  docker run --rm -v "$1":/data:ro -v "$DEST":/backup alpine:3 tar czf "/backup/$2" -C /data .
}
archive "${PROJECT}_uploads" uploads.tar.gz
archive "${PROJECT}_auth-keys" auth-keys.tar.gz

echo "Backup written to $DEST"
ls -lh "$DEST"
