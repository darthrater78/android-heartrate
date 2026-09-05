# Heart Rate Mirror

Android app that displays live heart rate from a BLE heart rate monitor on your phone screen during workouts.

## Features

- Connects to any standard BLE Heart Rate Profile (0x180D) device
- Works with Pixel Watch (1/2/3), Polar, Garmin, and BLE chest straps
- Large BPM display with pulse animation synced to heart rate
- HR zone colors — BPM display and graph segments shift from blue (rest) through green, yellow, orange, to red (max) with smooth interpolation
- Recent devices list — quickly reconnect to previously used devices
- Session history — stores up to 30 sessions with avg/max/min BPM stats and line graphs
- Session naming — connect straight away, then name sessions afterwards from Session History (e.g. "Morning Run"); unnamed sessions show their date
- Live session graph — scrolling 5-minute HR graph during active sessions
- Automatic reconnection — a dropped link is retried in the background without ending the session or losing the graph
- Sessions survive rotation and screen-off — the connection runs behind a foreground service
- Dark OLED-friendly theme
- Screen stays on while connected

## Pixel Watch Setup

The Pixel Watch 3 does not broadcast heart rate over BLE by default. To enable it:

1. On the watch, open **Settings**
2. Find the **"Share heart rate"** option and turn it on
3. The watch will now appear in the app's BLE scan

Without this setting enabled, the watch will not be discoverable by the app.

If you turn "Share heart rate" on *after* the app has already connected, the app drops
Android's cached service list and rediscovers automatically. Earlier versions required
restarting the app in that situation.

## Device Compatibility

This app requires a device that broadcasts the **standard BLE Heart Rate Service (0x180D)**. Not all wearables support this.

| Device | Works? | Notes |
|---|---|---|
| Pixel Watch 1/2/3 | Yes | Enable "Share heart rate" in watch Settings |
| Polar (Vantage, Pacer, Grit X) | Yes | Enable "HR broadcast" mode in watch settings |
| Garmin (select models) | Yes | Enable "Broadcast Heart Rate" in watch settings |
| BLE chest straps (Polar H10, Garmin HRM, Wahoo TICKR) | Yes | Broadcast by default |
| BLE arm bands (Polar Verity Sense, Scosche Rhythm+) | Yes | Broadcast by default |
| Samsung Galaxy Watch 4/5/6/7 | With app | No native BLE HR broadcast — install a Wear OS app like "Heart for Bluetooth" or "HR2VP" to enable it |
| Samsung Galaxy Watch 3 and older | No | Ran Tizen OS — no standard BLE HR support and no third-party workaround |
| **Fitbit Inspire, Charge, Versa, Sense, Luxe** | **No** | Fitbit OS devices use proprietary BLE services and do not expose standard HR |

Fitbit OS devices (everything except Pixel Watch) lock heart rate data to the Fitbit ecosystem. They will pair and connect, but after retrying discovery the app will report "No Heart Rate service found." This is a Fitbit firmware limitation, not an app bug.

## Permissions

| Permission | Purpose | Location flag |
|---|---|---|
| `BLUETOOTH_SCAN` | Discover nearby BLE heart rate monitors | `neverForLocation` — no location access |
| `BLUETOOTH_CONNECT` | Connect to and read data from the selected device | — |
| `FOREGROUND_SERVICE` | Keep the app running for the length of a session | — |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required from Android 14 for the session service type | — |
| `POST_NOTIFICATIONS` | Show the "session running" notification (Android 13+) | — |

No internet, storage, camera, or location permissions are used. The app communicates only over local Bluetooth LE and stores session data in private app storage (SharedPreferences).

Denying the notification permission does not break anything — the session service still runs, it just has no visible notification.

## Data Storage and Privacy

Nothing leaves the device. There is no internet permission, no analytics, and no account.

Session history — heart rate readings, timestamps, session names, and the paired device's
name and address — is written to app-private `SharedPreferences` at
`/data/data/com.scrivtech.heartrate/shared_prefs/heartrate_data.xml`. Individual sessions
can be deleted from the Session History screen.

That file is **not encrypted at rest by the app**. This is a deliberate decision, recorded
here so it is not rediscovered as an oversight:

- `MODE_PRIVATE` limits the file to the app's own UID; no other installed app can read it
- `allowBackup="false"` keeps it out of Google Drive and `adb backup`
- Release builds are not debuggable, so `run-as` cannot reach it
- Android's file-based encryption keeps it unreadable until the device is first unlocked
  after boot

The remaining exposure is an attacker with root, physical forensic access, or malware that
already has root. Encrypting with `EncryptedSharedPreferences` was considered and rejected:
Jetpack Security Crypto (`androidx.security:security-crypto`) is deprecated, and a
Keystore-backed key that becomes invalid — after a restore to new hardware, or some
lock-screen changes — makes the store throw on open, trading readable data for a
crash on launch and a lost history.

Reconsider this if the app ever stores something that grants access to anything else, such
as an account token or a cloud sync credential. Heart rate history on its own does not
justify the trade.

## Requirements

- Android 12+ (API 31)
- Device with Bluetooth LE support

## Building

Open in Android Studio, then Build > Generate App Bundles or APKs > Generate APKs.

**Command line (requires JDK 17):**

```bash
# Set environment (adjust paths for your system)
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
export ANDROID_HOME="$LOCALAPPDATA\Android\Sdk"

# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

Install the debug APK:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release process

This project uses the [dev-skills](https://github.com/darthrater78/claude-vibe-skills) gate system for releases:

```
VERSION → BUILD → SECURITY → DOCS → RELEASE → SHIP
```

Each gate must pass before proceeding to the next. Security scan runs after every build. Commits require explicit approval.

Releases are built by the **Release** workflow in the Actions tab, which reads the version from
`app/build.gradle.kts`, builds a signed APK, tags it, and publishes a GitHub Release. It takes
one input:

- **publish** (default on) — tag and publish a GitHub Release. Turn it **off** to build a
  signed test APK and attach it to the run as an artifact instead, without tagging or
  consuming a version number. Use this for diagnostic builds.

## Version History

### [v1.4.1](https://github.com/darthrater78/android-heartrate/releases/tag/v1.4.1) — 2026-09-05

Hotfix for v1.4.0, which hung on connect.

- Disabled R8 minification for release builds. v1.4.0 was the first minified APK ever to
  reach a device — `isMinifyEnabled` had been on since v1.1.0, but every release build before
  it was unsigned and therefore uninstallable, so the setting had never actually been
  exercised. An unminified build of the identical commit connects normally, which isolates
  R8 as the cause
- Release builds now keep their `Log.d`/`Log.v` output instead of stripping it. The entire
  BLE connection lifecycle is traced through `Log.d`, so stripping it left the shipped APK
  with a single usable log line and no way to diagnose the hang from a logcat
- The release workflow can now build a signed test APK and attach it to the run as an
  artifact without tagging or publishing, so diagnostic builds no longer consume a version
  number

**Known limitation:** release builds are unminified until the specific R8 rule at fault is
identified. The APK is larger and is not obfuscated. This app has no secrets, authentication,
or server, so the practical exposure is that the code is easier to read.

### [v1.4.0](https://github.com/darthrater78/android-heartrate/releases/tag/v1.4.0) — 2026-09-05

- Removed the "Name This Session" dialog that appeared on connect. Tapping a device now
  connects immediately
- Sessions are named afterwards instead, from Session History. Unnamed sessions show
  their date and time, so nothing is unlabelled
- Added a visible "Rename" control to each session card — renaming previously worked only
  by tapping the session title, which nothing advertised

### [v1.3.0](https://github.com/darthrater78/android-heartrate/releases/tag/v1.3.0) — 2026-09-05

Session durability release. Follows on from v1.2.0's connection fixes by keeping a
session alive through the things that used to end it.

- Sessions survive screen rotation — the BLE connection moved into a ViewModel, so
  rotating the phone no longer tears down an active session
- Added a foreground service for the duration of a session, so Doze and background
  execution limits can no longer drop the connection once the screen goes off
- Bluetooth being switched off mid-session is now detected and reported, instead of
  leaving the app retrying against a radio that is not there
- Connecting while Bluetooth is off now fails immediately with a clear message
- Session names survive rotation along with the connection
- Fixed a crash on launch when stored session data was corrupt or truncated — the
  unreadable data is discarded and the app starts with an empty history

**Known limitation:** the session ends if you dismiss the app from Recents. The
foreground service covers screen-off and backgrounding, not full task removal.

### [v1.2.0](https://github.com/darthrater78/android-heartrate/releases/tag/v1.2.0) — 2026-09-05

Connection reliability release. Fixes the case where the app had to be restarted after
enabling heart rate sharing on the watch.

- Fixed a GATT client leak — every disconnect, failed connect, and reconnect previously
  held onto an Android Bluetooth client handle. Once the per-app pool was exhausted, all
  further connections failed until the app was force-stopped. This was the main cause of
  "restart the app and it works"
- Stale service cache is now dropped and rediscovered when the Heart Rate service is
  missing, so enabling HR sharing after connecting no longer requires a restart
- Connection attempts retry with backoff instead of failing on the first error — status
  133 is the Bluetooth stack's generic "try again", not a real fault
- Added a settling delay between stopping the scan and opening a connection, a common
  source of spurious connection failures
- "Connected" is now reported only after notifications are confirmed enabled, instead of
  showing a connected screen that never displays a reading
- Automatic reconnection after an unexpected drop, with a "Reconnecting..." state that
  keeps the session and its graph intact
- Bluetooth scan failures are now surfaced instead of failing silently
- Heart rate readings are bounds-checked, so a malformed packet can no longer skew a
  session's min/max or the graph scale
- Added the missing `proguard-rules.pro` that release builds referenced but that was
  never committed — `assembleRelease` could not have succeeded without it

### [v1.1.0](https://github.com/darthrater78/android-heartrate/releases/tag/v1.1.0) — 2026-09-04
- HR zone colors with smooth interpolation across 5 zones (rest → light → moderate → hard → max)
- Recent devices list for quick reconnection
- Session history — stores up to 30 sessions with avg/max/min BPM and line graphs
- Session naming on connect, with rename and delete support
- Live scrolling HR graph during active sessions

### [v1.0.0](https://github.com/darthrater78/android-heartrate/releases/tag/v1.0.0) — 2026-09-03
- Initial release
- BLE scan with paired device support
- Heart rate display with pulse animation
- Connection error reporting with timeout
