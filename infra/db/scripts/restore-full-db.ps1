param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [switch]$AllowActiveMicroservices
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Resolve paths and runtime defaults
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dbDir = [System.IO.Path]::GetFullPath((Join-Path $scriptDir ".."))
$envFile = Join-Path $dbDir ".env"
$containerName = "finance-db"

if (-not (Test-Path $envFile)) {
    throw "Could not find $envFile. Create infra/db/.env first from .env.example."
}

function Get-EnvMap([string]$Path) {
    $map = @{}
    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $trimmed -split "=", 2
        if ($parts.Count -eq 2) {
            $key = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"')
            $map[$key] = $value
        }
    }
    return $map
}

function Assert-Command([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Command '$Name' was not found. $Hint"
    }
}

$backupPath = [System.IO.Path]::GetFullPath($BackupFile)
if (-not (Test-Path $backupPath)) {
    throw "Backup file does not exist: $backupPath"
}

# Validate required database credentials
$envVars = Get-EnvMap -Path $envFile
foreach ($required in @("POSTGRES_USER", "POSTGRES_PASSWORD")) {
    if (-not $envVars.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($envVars[$required])) {
        throw "Missing required variable '$required' in $envFile"
    }
}

$user = $envVars["POSTGRES_USER"]
$password = $envVars["POSTGRES_PASSWORD"]

Assert-Command -Name "docker" -Hint "Install Docker Desktop and try again."

$runningContainer = docker ps --filter "name=^/$containerName$" --format "{{.Names}}"
if (-not ($runningContainer -contains $containerName)) {
    throw "Container '$containerName' is not running. Start the database first (infra/db/docker-compose up -d)."
}

# By default, block restore when app services are still running.
$serviceNames = @("banking", "investments", "wealth", "budget", "frontend")
$activeMicroserviceContainers = @()
foreach ($service in $serviceNames) {
    $containersForService = @(docker ps --filter "label=com.docker.compose.service=$service" --format "{{.Names}}")
    foreach ($container in $containersForService) {
        if (-not [string]::IsNullOrWhiteSpace($container)) {
            $activeMicroserviceContainers += $container
        }
    }
}

if ($activeMicroserviceContainers.Count -gt 0 -and -not $AllowActiveMicroservices) {
    $activeList = $activeMicroserviceContainers -join ", "
    throw "Restore blocked because active microservice containers were detected: $activeList. Stop app services first or run again with -AllowActiveMicroservices."
}

$tempSqlFile = ""
$restoreSqlFile = $backupPath
$tempDir = ""

try {
    if ($backupPath.ToLowerInvariant().EndsWith(".zip")) {
        # Support restore from a compressed backup artifact
        $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("finance-db-restore-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Path $tempDir | Out-Null
        Expand-Archive -Path $backupPath -DestinationPath $tempDir -Force

        $sqlFiles = @(Get-ChildItem -Path $tempDir -Filter "*.sql" -Recurse)
        if ($sqlFiles.Count -eq 0) {
            throw "The .zip file does not contain any .sql file"
        }

        $restoreSqlFile = $sqlFiles[0].FullName
    }

    $containerRestoreFile = "/tmp/restore-full.sql"

    Write-Host "Copying backup file to container..."
    docker cp $restoreSqlFile "${containerName}:$containerRestoreFile" | Out-Null

    # Terminate active sessions to avoid restore locks
    Write-Host "Closing active connections to avoid restore locks..."
    $terminateSql = 'SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid <> pg_backend_pid() AND datname <> ''postgres'';'
    docker exec -e "PGPASSWORD=$password" $containerName psql -U $user -d postgres -c $terminateSql | Out-Null

    Write-Host "Restoring full backup..."
    docker exec -e "PGPASSWORD=$password" $containerName psql -U $user -d postgres -f $containerRestoreFile | Out-Null

    Write-Host "Restore completed successfully."
}
finally {
    # Always clean temporary files (host and container)
    if (-not [string]::IsNullOrWhiteSpace($tempSqlFile) -and (Test-Path $tempSqlFile)) {
        Remove-Item -Path $tempSqlFile -Force
    }

    if (-not [string]::IsNullOrWhiteSpace($tempDir) -and (Test-Path $tempDir)) {
        Remove-Item -Path $tempDir -Recurse -Force
    }

    docker exec $containerName rm -f /tmp/restore-full.sql 2>$null | Out-Null
}
