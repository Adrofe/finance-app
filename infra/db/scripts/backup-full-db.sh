#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$DB_DIR/.env"
CONTAINER_NAME="finance-db"

OUTPUT_DIR=""
COMPRESS=false
CLOUD_TARGET=""

usage() {
  cat <<'USAGE'
Usage: backup-full-db.sh [--output-dir <path>] [--compress] [--cloud-target <remote:path>]

Options:
  --output-dir <path>       Destination folder (default: infra/db/backups)
  --compress                Compress output into .sql.gz and remove .sql
  --cloud-target <target>   Upload artifact with rclone copy (example: gdrive:finance-app-backups)
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --compress)
      COMPRESS=true
      shift
      ;;
    --cloud-target)
      CLOUD_TARGET="${2:-}"
      shift 2
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

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$DB_DIR/backups"
fi
mkdir -p "$OUTPUT_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
FILE_NAME="finance-db-full-${TIMESTAMP}.sql"
HOST_BACKUP_FILE="$OUTPUT_DIR/$FILE_NAME"
CONTAINER_BACKUP_FILE="/tmp/$FILE_NAME"

echo "Generating full backup (roles + all databases)..."
DUMP_COMMAND="pg_dumpall -U '$POSTGRES_USER' --clean --if-exists > '$CONTAINER_BACKUP_FILE'"
docker exec -e "PGPASSWORD=$POSTGRES_PASSWORD" "$CONTAINER_NAME" sh -c "$DUMP_COMMAND" >/dev/null

docker cp "${CONTAINER_NAME}:${CONTAINER_BACKUP_FILE}" "$HOST_BACKUP_FILE" >/dev/null
docker exec "$CONTAINER_NAME" rm -f "$CONTAINER_BACKUP_FILE" >/dev/null

ARTIFACT_FOR_UPLOAD="$HOST_BACKUP_FILE"
if [[ "$COMPRESS" == "true" ]]; then
  gzip -f "$HOST_BACKUP_FILE"
  ARTIFACT_FOR_UPLOAD="${HOST_BACKUP_FILE}.gz"
fi

if [[ -n "$CLOUD_TARGET" ]]; then
  require_command rclone "Install rclone and configure a remote (Google Drive, OneDrive, S3, Azure Blob, etc.)."
  echo "Uploading backup to cloud target $CLOUD_TARGET ..."
  rclone copy "$ARTIFACT_FOR_UPLOAD" "$CLOUD_TARGET" --progress
fi

echo "Backup created successfully: $ARTIFACT_FOR_UPLOAD"
