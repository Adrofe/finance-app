param(
    [string]$CloudTarget = "gdrive:finance-app-backups",
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backupScript = Join-Path $scriptDir "backup-full-db.ps1"

if (-not (Test-Path $backupScript)) {
    throw "Base script was not found: $backupScript"
}

# Wrapper: enforce compression and default cloud target
Write-Host "Running compressed backup with upload target: $CloudTarget"

$invokeParams = @{
    Compress = $true
    CloudTarget = $CloudTarget
}

if (-not [string]::IsNullOrWhiteSpace($OutputDir)) {
    $invokeParams["OutputDir"] = $OutputDir
}

& $backupScript @invokeParams
