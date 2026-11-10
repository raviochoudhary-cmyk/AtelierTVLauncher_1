# Atelier TV Launcher

A fast, lightweight, Apple TV-inspired home screen for Android TV, built for:

| | |
|---|---|
| Device | Xiaomi MiTV-MOOQ1 ("tarzan", reig16) |
| OS | Android 10 (API 29), MediaTek m7632 |
| ABI | armeabi-v7a (32-bit ARM) |
| Build | GitHub Actions only - Android Studio is not required |

## What it does

- Opens straight into your streaming apps as large cards (actual installed
  apps discovered via `PackageManager` - nothing is shown unless it exists on
  the device).
- Netflix / Prime Video / YouTube / JioHotstar / Apple TV and friends are
  pinned first when installed; long-press **OK** on any card to move it left
  or right (order is saved).
- A separate **Inputs** row discovers the TV's real HDMI / Live TV / AV inputs
  through `TvInputManager` and switches to them via the standard passthrough
  channel URI.
- Header shows brand, live clock and a settings shortcut; bottom bar has
  Settings, Network, All applications and Refresh.
- Full D-pad navigation (up/down between rows keeping the column, left/right
  within a row, OK to launch, Back stays home). No mouse or touch needed.
- Framework-only Java - zero third-party libraries, no ads, no telemetry,
  works offline.

## Repository layout

```
app/src/main/java/com/atelier/tvlauncher/
  MainActivity.java      home screen, rows, D-pad navigation, clock, ordering
  AllAppsActivity.java   list of every installed leanback app
  AppDiscovery.java      PackageManager discovery + streaming priority order
  HdmiInputHelper.java   TvInputManager discovery + passthrough switching
  AppInfo.java, LauncherPreferences.java
app/src/main/res/        layouts, drawables, TV banner (320x180), launcher icons
scripts/                 validate_apk.py, install.ps1, rollback.ps1
.github/workflows/       build.yml (compile -> validate -> artifact)
```

## Building (GitHub Actions)

1. Push this repository to GitHub (any private/public repo).
2. Open **Actions > Build Atelier TV Launcher APK > Run workflow**.
3. Download `atelier-tv-launcher-apk` from the workflow's artifacts.

Toolchain: JDK 17 (Temurin), Gradle 7.6.3, Android Gradle Plugin 7.4.2,
`compileSdk 29`, `targetSdk 29`, `minSdk 21`. No Android Studio involved.

The workflow runs `scripts/validate_apk.py`, which fails the build unless the
APK contains `classes.dex`, `resources.arsc`, the HOME /
LEANBACK_LAUNCHER manifest entries, the TV banner and our launcher classes -
so an "empty shell" APK can never ship again.

## Why the earlier APK was only 12.7 KB

A genuine debug launcher APK is 1.5-4 MB (`resources.arsc` + PNG assets +
real `classes.dex`). 12.7 KB is an almost-empty ZIP: manifest + empty
resources.arsc + stub/near-empty dex. Typical causes, in order of likelihood:

1. **No Java sources were compiled** - sources lived outside
   `app/src/main/java`, or the module's `sourceSets` pointed somewhere empty,
   so Gradle happily packaged a dex with (almost) no classes.
2. **Nothing referenced the resources** - no activity in the manifest, and
   with resource shrinking on, aapt2 strips every drawable/string, leaving a
   tiny `resources.arsc`.
3. **The manifest declared no real HOME activity** - the build "succeeded"
   because packaging an APK with just a stub activity is still a success.

The validator now checks size, dex contents, resource count and manifest
strings on every build, and the build fails otherwise. A green build means
these classes really are inside the APK.

## Installing on the TV

Prerequisite: on the TV, enable **Settings > About > build number** taps to
unlock developer options, then enable **Network debugging (ADB)**.

```powershell
# from the repo folder, after downloading the artifact next to this script
.\scripts\install.ps1 -Apk .\app-debug.apk
```

Or manually:

```
adb connect 192.168.1.8:5555
adb install -r app-debug.apk
adb shell cmd package set-home-activity com.atelier.tvlauncher/com.atelier.tvlauncher.MainActivity
```

If `set-home-activity` is rejected by the firmware, press **HOME** on the
remote and choose *Atelier TV* in the system chooser.

Nothing system-side is touched: the Xiaomi launcher, Play services, casting,
Assistant and all streaming apps remain installed and enabled.

## Rollback

```powershell
.\scripts\rollback.ps1
```

This restores the home app recorded at install time (`home-backup.txt`) and
uninstalls Atelier TV. Press HOME and you are back on the stock launcher.
Equivalent manual commands:

```
adb shell cmd package set-home-activity <original-from-home-backup.txt>
adb uninstall com.atelier.tvlauncher
```

## Verified vs unverified on the target device

Verified by construction / CI (must pass for any green build):

- APK contains the launcher classes, HOME + LEANBACK_LAUNCHER intent
  filters, TV banner, min/target SDK 21/29.
- App discovery only lists packages `PackageManager` reports installed.
- Launch intents are built from resolved activity names, never guessed.

Requires on-device confirmation on the MiTV (do not assume):

- Which streaming packages Xiaomi actually ships (discovery adapts
  automatically; the exact card set depends on your installed apps).
- Whether `TvInputManager` exposes the HDMI inputs on this firmware. The code
  tries, in order: passthrough URI switch -> the input's setup activity -> a
  documented dialog. If the row shows labelled "HDMI 1/2/3" placeholders
  instead of discovered inputs, the proprietary Xiaomi input stack is not
  exposed and the remote's source/input key is the supported mechanism.
- Default-home selection via `cmd package set-home-activity` (fallback: HOME
  button chooser).
- D-pad focus behaviour with the stock Xiaomi remote (all focus is
  code-driven and mouse-independent, but confirm on hardware).

## Troubleshooting

- **"APP not installed" over ADB** - an existing signing conflict: uninstall
  any previous build first (`adb uninstall com.atelier.tvlauncher`).
- **HOME still opens Xiaomi launcher** - rerun `install.ps1`, or press HOME
  and pick Atelier in the chooser, then choose "Always".
- **Empty streaming row** - the TV's apps don't expose leanback launcher
  intents; use *All applications* (bottom bar) which lists everything.
