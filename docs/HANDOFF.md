# Handoff: BLE reconnect hang and hard-drop reconnects

**Updated:** 2026-09-24 · **Branch:** `fix/ble-reconnect-hang` (from master `8fb9d55`)

**Goal:** fix manual reconnects that hang on "Waiting for heart rate...", and handle a
watch drop with three reconnect attempts on the live screen, then a hard drop back to the
device list. Then run the release gates and ship it as v1.5.2.

## Current state

- **Two commits are pushed, and CI is green on both:**
  - `d562c8c` fixes the reconnect hang.
  - `379e38f` added 3 reconnect attempts, run from the device list.
- **Waiting to be committed:** the reconnect attempts move back onto the live screen,
  plus two security fixes from the full audit (see Gate status). This needs a new test
  APK.
- **Signed test APK:** Release run 36030686212 (`379e38f`). The APK is in the
  `heart-rate-mirror-apk` artifact, which expires 2026-12-23. Install that artifact, not
  `heart-rate-mirror-debug`. The debug build is signed with a different key, so it
  would need an uninstall, and an uninstall wipes the saved sessions.
- **Verified:**
  - Local `assembleDebug`, `assembleRelease` and `lintDebug` pass, with no warnings in
    the changed files.
  - An OSV check of master's release runtime classpath found 0 advisories in 81 packages.
- **Tested on the phone (`379e38f`):** the user reports it works well. The one exception
  is that the retries ran from the device list instead of the live screen, which the
  uncommitted change fixes.
- No version bump or changelog entry yet.

## Root cause (from logcat on a Pixel 10, Android 17, 2026-09-24)

- **The Bluetooth stack keeps an idle link up for about 4 s** after the last app client
  closes (`start link idle timer for 4 seconds`). A reconnect inside that window reuses
  the link.
- **`disconnect()` closed the client without switching notifications off.** On a reused
  link, the watch still had notifications on from the old client. The new client's
  "notifications on" write succeeded but changed nothing, so the watch never streamed.
- **Anything that outlasted 4 s recovered it,** which explains two things the user saw:
  - force-closing the app
  - scanning first, then picking the stored device
- **A drop kept the live screen up** while 3 rounds of 3 connects ran, which could take
  minutes. Nothing watched for readings going silent.

## What the branch changes

`BleHeartRateManager.kt`:
- **Clean shutdown:**
  - turns notifications off (a CCCD write of 0), then disconnects, then closes
  - capped at 1 s (`CLOSE_TIMEOUT_MS`)
  - a client being torn down is held separately in `closingGatt`
- **Every connect writes notifications off, then on** (`CccdStep`), so the watch always
  sees the change to "on".
- **Silence watchdog:**
  - no first reading within 8 s → retry
  - 10 s with no readings mid-session → treated as a drop
- **Callbacks from any client other than the live one are ignored.**
- **Retry counters reset on the first reading,** not when notifications are enabled.
- **Reconnect, then hard drop:**
  - The app makes 3 single attempts (`MAX_RECONNECT_ATTEMPTS`) with 2, 4 and 6 s
    backoff. The live screen stays up showing "Reconnecting... (n of 3)", and the
    session and graph are kept.
  - A reconnect that succeeds carries on in the same session.
  - After the third failure it's a hard drop: `DISCONNECTED` goes back to the device
    list, with a message, and the session is saved.
- **New log lines (tag `HeartRateMirror`):** `First reading N ms after connectGatt`,
  `No reading ...`, `Graceful close timed out`, `Giving up after 3 reconnect attempts`.

Other files:
- `HeartRateScreen.kt`: shows "Reconnecting... (n of 3)". The Disconnect button cancels
  the attempts.
- `MainActivity.kt`: unchanged from master in the end. `RECONNECTING` still counts as
  in-session.
- Security fixes:
  - `AndroidManifest.xml` and `res/xml/data_extraction_rules.xml` keep the session
    history out of Android 12+ phone-to-phone transfer.
  - `SessionHistoryScreen.kt` caps session names at 60 characters.
- `README.md`: the reconnect feature line and the at-rest section are updated.

## Phone test (next step)

Capture logcat with no filter, and save it from Android Studio's Logcat window.

1. **The user's repro:** connect, turn the watch off, tap disconnect in the app, turn the
   watch back on, connect. Expect readings, with no force close needed.
2. **Quick reconnects:** disconnect and reconnect in the app 5 or more times, each within
   4 s. Every time should show `First reading`.
3. **Watch drop mid-session:** within about 10 s the live screen should show
   "Reconnecting... (1 of 3)" with the graph still there.
   - Watch back on: it should carry on in the same session.
   - Watch left off: after 3 attempts it should go back to the device list with a
     message, and the session should be in history.
4. **Disconnect during the attempts:** it stops them and saves the session.

## Gate status

```
🔢⬜ 🔨⏳ 🔒⏳ 📄⬜ 📦⬜ 🚀⬜ · work commit · manual
🔨 local builds pass; phone test pending
🔒 full audit 2026-09-24: 0 Critical, 0 High — 2 open Medium (block the release track):
   📝 dependencies behind on master (AGP 8.13.2, Kotlin 2.0.21): fixed on deps/agp9, closes when it merges
   📝 R8 off in release: do it after this fix passes on the phone
   ✅ fixed (uncommitted): Low — phone-to-phone transfer ignored allowBackup=false
   ✅ fixed (uncommitted): Low — session names had no length limit
```

What the audit checked and found clean:
- **Manifest:** there's no INTERNET permission, so the app has no network access at all.
  The service isn't exported. The only exported component is the launcher activity,
  which has to be.
- **Runtime:**
  - The PendingIntent is immutable.
  - The Bluetooth-state receiver only listens to a protected system broadcast.
  - Heart rate packets are range-checked.
- **Logs:** nothing personal. Only timings, counts, status codes and service UUIDs.
- **Keys and CI:**
  - No signing key has ever been committed.
  - GitHub reports 0 Dependabot alerts and 0 secret-scanning alerts.
  - The workflows use actions pinned to exact commit SHAs, with least-privilege
    permissions. The CI keystore is deleted with `if: always()`.
- **Dependencies:** OSV found 0 advisories in 81 packages.

## Other open work

- **`deps/agp9-and-current-libs`** (`b0ba52e`): AGP 9.4.1, Gradle 9.7.1, Kotlin 2.4.20.
  - Its APK hasn't been checked on the phone.
  - It has an uncommitted edit to its own `docs/HANDOFF.md`, stashed as
    `stash@{0}`. Run `git stash pop` on that branch to get it back.
  - Both branches add `docs/HANDOFF.md`, so whichever merges second will conflict on it.
    Keep this file's content and fold in the AGP 9 section.
  - Dependabot PRs #10–#14 close once it merges.
- **R8:** turn it back on (`isMinifyEnabled`, `isShrinkResources`), then do about 20
  connect and disconnect cycles on the phone. This comes after this fix is confirmed.
- **`connectGatt(..., transport)` is deprecated at API 37** but still works. Leave it
  until the BLE changes have settled.

## Decisions made

- **Manual mode.** The maintainer runs git and `gh workflow run`. Claude never dispatches
  workflows itself.
- **One change per PR:** the reconnect fix, the AGP 9 upgrade and R8 are separate
  branches.
- **The reconnect attempts run on the live screen and keep the session.** Only after
  the third failure is it a hard drop to the device list. The user changed this on
  2026-09-24, replacing "save and start a new session".
- **Three reconnect attempts, each a single connect.** Initial connects keep their own
  3 retries, because status 133 is routine.
- **Session history stays on the phone:** no cloud backup, and no phone-to-phone
  transfer.
- **Don't change BLE timing without repeated trials on the phone.** The existing delays
  (500, 600 and 200 ms) are unchanged.
- Local builds need `ANDROID_HOME=$HOME/Android/Sdk`. **Shell:** Linux bash.

## Key files

- `app/src/main/java/com/scrivtech/heartrate/BleHeartRateManager.kt`:
  - `closeGatt` and `finishClosing`: the clean shutdown
  - `handleDescriptorWrite`: the notifications off-then-on sequence
  - `readingWatchdog`: the silence watchdog
  - `handleUnexpectedDisconnect`: the 3 attempts, then the hard drop
- `app/src/main/java/com/scrivtech/heartrate/MainActivity.kt`: which screen shows, and
  when a session is saved (`inSession`).
- `app/src/main/java/com/scrivtech/heartrate/ui/HeartRateScreen.kt`: the reconnect
  progress.
