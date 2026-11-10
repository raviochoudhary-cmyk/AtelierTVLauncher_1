# Atelier TV Launcher - install over ADB (Windows PowerShell).
# Records the current default home app first so rollback.ps1 can restore it.
param(
    [string]$Device = "192.168.1.8:5555",
    [string]$Apk = "$PSScriptRoot\..\app-debug.apk",
    [string]$BackupFile = "$PSScriptRoot\home-backup.txt"
)
$ErrorActionPreference = "Stop"

Write-Host "== Atelier TV Launcher installer ==" -ForegroundColor Cyan

adb connect $Device
if ($LASTEXITCODE -ne 0) { throw "Could not connect to $Device. Enable ADB in TV settings first." }

$Apk = (Resolve-Path $Apk).Path
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

$currentHome = (adb shell cmd package get-home-activity) -join " "
$currentHome | Out-File -Encoding ascii $BackupFile
Write-Host "Current home recorded -> $BackupFile  ($currentHome)"

adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed." }

# Make Atelier the default home. If this command is rejected by the firmware,
# pressing HOME after install shows the system home-app chooser instead.
adb shell cmd package set-home-activity com.atelier.tvlauncher/com.atelier.tvlauncher.MainActivity

# Launch it once immediately so you see it working.
adb shell monkey -p com.atelier.tvlauncher -c android.intent.category.LEANBACK_LAUNCHER 1 | Out-Null

Write-Host ""
Write-Host "Installed. If HOME still opens the Xiaomi launcher, press HOME and pick" -ForegroundColor Green
Write-Host "'Atelier TV' in the chooser, or rerun this script." -ForegroundColor Green
