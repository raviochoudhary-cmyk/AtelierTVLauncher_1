# Atelier TV Launcher - rollback (Windows PowerShell).
# Restores the previously recorded default home app, then uninstalls Atelier.
# The Xiaomi launcher is never disabled or uninstalled - removing ours simply
# hands Home back to the system default.
param(
    [string]$Device = "192.168.1.8:5555",
    [string]$BackupFile = "$PSScriptRoot\home-backup.txt"
)
$ErrorActionPreference = "Stop"

Write-Host "== Atelier TV Launcher rollback ==" -ForegroundColor Cyan

adb connect $Device
if ($LASTEXITCODE -ne 0) { throw "Could not connect to $Device." }

if (Test-Path $BackupFile) {
    $previous = (Get-Content $BackupFile | Select-Object -First 1).Trim()
    if ($previous) {
        Write-Host "Restoring previous home: $previous"
        adb shell cmd package set-home-activity $previous
    }
}

adb uninstall com.atelier.tvlauncher
if ($LASTEXITCODE -ne 0) { Write-Host "Atelier was not installed (nothing to remove)." }

Write-Host "Rollback complete. Press HOME to return to the original launcher." -ForegroundColor Green
