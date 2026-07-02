#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$DB_DIR/.env"
CONTAINER_NAME="finance-db"

BACKUP_FILE=""
ALLOW_ACTIVE_MICROSERVICES=false

TEMP_DIR=""
RESTORE_SQL_FILE=""
CONTAINER_RESTORE_FILE="/tmp/restore-full.sql"

usage() {
  cat <<'USAGE'
Usage: restore-full-db.sh --backup-file <path> [--allow-active-microservices]

Options:
  --backup-file <path>              .sql, .sql.gz, or .zip backup file
  --allow-active-microservices      Stop active microservices automatically and continue restore
USAGE
}

cleanup() {
  if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then
    rm -rf "$TEMP_DIR"
  fi
  if docker ps -a --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    docker exec "$CONTAINER_NAME" rm -f "$CONTAINER_RESTORE_FILE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-file)
      BACKUP_FILE="${2:-}"
      shift 2
      ;;
    --allow-active-microservices)
      ALLOW_ACTIVE_MICROSERVICES=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$BACKUP_FILE" ]]; then
  echo "Missing required argument: --backup-file" >&2
  usage
  exit 1
fi

require_command() {
  local name="$1"
  local hint="$2"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Command '$name' was not found. $hint" >&2
    exit 1
  fi
}

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Could not find $ENV_FILE. Create infra/db/.env first from .env.example." >&2
  exit 1
fi

BACKUP_PATH="$(realpath "$BACKUP_FILE")"
if [[ ! -f "$BACKUP_PATH" ]]; then
  echo "Backup file does not exist: $BACKUP_PATH" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

: "${POSTGRES_USER:?Missing required variable 'POSTGRES_USER' in $ENV_FILE}"
: "${POSTGRES_PASSWORD:?Missing required variable 'POSTGRES_PASSWORD' in $ENV_FILE}"

require_command docker "Install Docker and try again."

if [[ "$(docker ps --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}')" != "$CONTAINER_NAME" ]]; then
  echo "Container '$CONTAINER_NAME' is not running. Start the database first (infra/db/docker-compose up -d)." >&2
  exit 1
fi

service_names=(banking investments wealth budget frontend gateway)
active_microservices=()
for service in "${service_names[@]}"; do
  while IFS= read -r container; do
    if [[ -n "$container" ]]; then
      active_microservices+=("$container")
    fi
  done < <(docker ps --filter "label=com.docker.compose.service=${service}" --format '{{.Names}}')
done

if [[ ${#active_microservices[@]} -gt 0 ]]; then
  if [[ "$ALLOW_ACTIVE_MICROSERVICES" != "true" ]]; then
    echo "Restore blocked because active microservice containers were detected: ${active_microservices[*]}." >&2
    echo "Stop app services first or run again with --allow-active-microservices to stop them automatically." >&2
    exit 1
  fi

  echo "Forced restore requested. Stopping active microservice containers: ${active_microservices[*]}"
  for container in "${active_microservices[@]}"; do
    docker stop "$container" >/dev/null || true
  done
fi

RESTORE_SQL_FILE="$BACKUP_PATH"

case "$BACKUP_PATH" in
  *.zip)
    require_command unzip "Install unzip to restore .zip backups."
    TEMP_DIR="$(mktemp -d -t finance-db-restore-XXXXXX)"
    unzip -q "$BACKUP_PATH" -d "$TEMP_DIR"
    mapfile -t sql_files < <(find "$TEMP_DIR" -type f -name '*.sql')
    if [[ ${#sql_files[@]} -eq 0 ]]; then
      echo "The .zip file does not contain any .sql file" >&2
      exit 1
    fi
    RESTORE_SQL_FILE="${sql_files[0]}"
    ;;
  *.gz)
    require_command gzip "Install gzip to restore .gz backups."
    TEMP_DIR="$(mktemp -d -t finance-db-restore-XXXXXX)"
    RESTORE_SQL_FILE="$TEMP_DIR/restore-full.sql"
    gzip -dc "$BACKUP_PATH" > "$RESTORE_SQL_FILE"
    ;;
  *.sql)
    ;;
  *)
    echo "Unsupported backup format. Use .sql, .sql.gz, or .zip" >&2
    exit 1
    ;;
esac

echo "Copying backup file to container..."
docker cp "$RESTORE_SQL_FILE" "${CONTAINER_NAME}:${CONTAINER_RESTORE_FILE}" >/dev/null

echo "Closing active connections to avoid restore locks..."
terminate_sql="SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid <> pg_backend_pid() AND datname <> 'postgres';"
docker exec -e "PGPASSWORD=$POSTGRES_PASSWORD" "$CONTAINER_NAME" \
  psql -U "$POSTGRES_USER" -d postgres -c "$terminate_sql" >/dev/null

echo "Restoring full backup..."
docker exec -e "PGPASSWORD=$POSTGRES_PASSWORD" "$CONTAINER_NAME" \
  psql -U "$POSTGRES_USER" -d postgres -f "$CONTAINER_RESTORE_FILE" >/dev/null

echo "Restore completed successfully."
