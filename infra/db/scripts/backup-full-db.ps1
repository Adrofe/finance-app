param(
    [string]$OutputDir = "",
    [switch]$Compress,
    [string]$CloudTarget = ""
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

# Validate required database credentials
$envVars = Get-EnvMap -Path $envFile
foreach ($required in @("POSTGRES_USER", "POSTGRES_PASSWORD")) {
    if (-not $envVars.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($envVars[$required])) {
        throw "Missing required variable '$required' in $envFile"
    }
}

$user = $envVars["POSTGRES_USER"]
$password = $envVars["POSTGRES_PASSWORD"]

Assert-Command -Name "docker" -Hint "Instala Docker Desktop y vuelve a intentarlo."

$runningContainer = docker ps --filter "name=^/$containerName$" --format "{{.Names}}"
if (-not ($runningContainer -contains $containerName)) {
    throw "Container '$containerName' is not running. Start the database first (infra/db/docker-compose up -d)."
}

# Create output folder when needed
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $dbDir "backups"
}
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$fileName = "finance-db-full-$timestamp.sql"
$hostBackupFile = Join-Path $OutputDir $fileName
$containerBackupFile = "/tmp/$fileName"

# Dump full PostgreSQL cluster (roles + all databases)
Write-Host "Generating full backup (roles + all databases)..."
$dumpCommand = "pg_dumpall -U '$user' --clean --if-exists > '$containerBackupFile'"
docker exec -e "PGPASSWORD=$password" $containerName sh -c $dumpCommand | Out-Null

docker cp "${containerName}:$containerBackupFile" $hostBackupFile | Out-Null
docker exec $containerName rm -f $containerBackupFile | Out-Null

$artifactForUpload = $hostBackupFile
if ($Compress) {
    # Compress output and remove uncompressed SQL file
    $zipPath = "$hostBackupFile.zip"
    Compress-Archive -Path $hostBackupFile -DestinationPath $zipPath -Force
    Remove-Item -Path $hostBackupFile -Force
    $artifactForUpload = $zipPath
}

if (-not [string]::IsNullOrWhiteSpace($CloudTarget)) {
    # Upload to cloud storage when rclone target is provided
    Assert-Command -Name "rclone" -Hint "Install rclone and configure a remote (Google Drive, OneDrive, S3, Azure Blob, etc.)."
    Write-Host "Uploading backup to cloud target $CloudTarget ..."
    rclone copy $artifactForUpload $CloudTarget --progress
}

Write-Host "Backup created successfully: $artifactForUpload"