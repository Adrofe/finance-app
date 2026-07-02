#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
BACKUP_SCRIPT="$SCRIPT_DIR/backup-full-db.sh"

CLOUD_TARGET="gdrive:finance-app-backups"
OUTPUT_DIR=""

usage() {
  cat <<'USAGE'
Usage: backup-gdrive.sh [--cloud-target <remote:path>] [--output-dir <path>]

Defaults:
  --cloud-target gdrive:finance-app-backups
  --compress enabled (always)
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --cloud-target)
      CLOUD_TARGET="${2:-}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="${2:-}"
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

if [ ! -f "$BACKUP_SCRIPT" ]; then
  echo "Base script was not found: $BACKUP_SCRIPT" >&2
  exit 1
fi

echo "Running compressed backup with upload target: $CLOUD_TARGET"

if [ -n "$OUTPUT_DIR" ]; then
  sh "$BACKUP_SCRIPT" --compress --cloud-target "$CLOUD_TARGET" --output-dir "$OUTPUT_DIR"
else
  sh "$BACKUP_SCRIPT" --compress --cloud-target "$CLOUD_TARGET"
fi
