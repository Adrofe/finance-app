# Database Infrastructure (infra/db)

This directory contains the PostgreSQL database configuration for the project.

## Environment Variables

Database credentials and parameters are managed using environment variables. Do not commit sensitive values to the repository. Use the `.env.example` file as a template:

1. Copy `infra/db/.env.example` to `infra/db/.env`.
2. Edit `infra/db/.env` with your real values.

Required variables:

- `POSTGRES_USER`: PostgreSQL superuser username
- `POSTGRES_PASSWORD`: PostgreSQL superuser password
- `POSTGRES_DB`: Name of the database to create when the container starts

The `.env` file is included in `.gitignore` and will not be committed to the repository.

## Using Docker Compose

To start the database:

```sh
docker-compose up -d
```

To stop it:

```sh
docker-compose down
```

## Full Backup and Restore

This repository includes PowerShell scripts to create and restore a full PostgreSQL backup
(roles + all databases in the cluster).

Scripts location:

- `infra/db/scripts/backup-full-db.ps1`
- `infra/db/scripts/restore-full-db.ps1`
- `infra/db/scripts/backup-gdrive.ps1` (wrapper: compressed backup + upload to Google Drive path by default)

### Create full backup

From project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\backup-full-db.ps1
```

Optional flags:

- `-Compress` creates a `.zip` and removes the plain `.sql`.
- `-OutputDir <path>` changes destination folder (default is `infra/db/backups`).
- `-CloudTarget <remote:path>` uploads to cloud using `rclone copy` (if `rclone` is installed and configured).

Example with compression and cloud upload:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\backup-full-db.ps1 -Compress -CloudTarget "onedrive:finance-app-backups"
```

Shortcut wrapper for Google Drive (uses `gdrive:finance-app-backups` and compression by default):

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\backup-gdrive.ps1
```

Override destination if needed:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\backup-gdrive.ps1 -CloudTarget "gdrive:backups/finance-app/prod"
```

### Restore full backup

Important:

- Keep database container running (`finance-db`).
- Stop application services before restoring to avoid writes during import.
- The restore script now checks active microservices (`banking`, `investments`, `wealth`, `budget`, `frontend`) and stops by default if any are running.

Restore from `.sql`:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\restore-full-db.ps1 -BackupFile .\infra\db\backups\finance-db-full-YYYYMMDD_HHMMSS.sql
```

Restore from `.zip`:

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\restore-full-db.ps1 -BackupFile .\infra\db\backups\finance-db-full-YYYYMMDD_HHMMSS.sql.zip
```

Force restore even if microservices are active (not recommended):

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\db\scripts\restore-full-db.ps1 -BackupFile .\infra\db\backups\finance-db-full-YYYYMMDD_HHMMSS.sql.zip -AllowActiveMicroservices
```

### Suggested backup strategy

- Daily automated backup with Windows Task Scheduler.
- Keep retention policy (e.g. last 30 days in cloud).
- Test restore at least once per month in a non-production environment.
