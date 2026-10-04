# Handoff: Gradle 9.8.0 upgrade

**Updated:** 2026-09-24 · **Branch:** `deps/gradle-9.8.0` (from master `6b45adc`, v1.6.0)

**Goal:** move the Gradle wrapper from 9.7.1 to 9.8.0 as its own change, and close the
Low finding that was waived until after v1.6.0.

## Current state

- **v1.6.0 shipped** on 2026-09-24:
  - PR #16 merged as `6b45adc`, tagged `v1.6.0`.
  - Release run 36077585625 published `heart-rate-mirror-v1.6.0.apk` (2.18 MB, signed, R8 on).
- **This branch has no code changes yet**, only this file.
- **The remote branch `fix/ble-reconnect-hang` has not been deleted yet.** Deleting a ref is
  the maintainer's to run: `git push origin --delete fix/ble-reconnect-hang`.

## Gate status

```
🔢⬜ 🔨⬜ 🔒✅ 📄⬜ 📦⬜ 🚀⬜ · work commit
🔒 waived by the maintainer (extended 2026-09-24): Low — Gradle wrapper 9.7.1, 9.8.0
   available (released 2026-09-24, no advisory on 9.7.1). The waiver lasts until the
   upgrade lands on this branch. It reopens if the upgrade is dropped.
```

v1.6.0's `SHIP ✅` line is in the local `.claude/dev-skills-gates.md`. It goes into this
branch's PR, not a separate one.

## Next steps

1. **Update the wrapper:**
   - `./gradlew wrapper --gradle-version 9.8.0`.
   - Confirm `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` is
     `bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`. That's the value
     published at `services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256`.
   - Keep `validateDistributionUrl=true`.
2. **Check that AGP 9.4.1 supports Gradle 9.8.0.** Look at the AGP and Gradle
   compatibility table, and check for a newer AGP 9.4.x patch.
3. **Build locally:** `ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleRelease lintDebug`.
   The lint warning `AndroidGradlePluginVersion` should be gone.
4. **Decide the version.** Is a build-tool-only change a 1.6.1 release, or does it wait
   for the next feature? Ask the maintainer.
5. **If it's released:**
   - The maintainer dispatches a signed Release build of the PR head.
   - Do a quick connect/disconnect check on the phone. R8 output can change with build
     tooling.

## Decisions made

- **One change per PR.** The Gradle upgrade stays separate from any BLE work.
- **Workflow dispatches are the maintainer's to run.** Claude Code's permission check
  refuses `gh workflow run release.yml` as a production deploy.
- **Don't change BLE timing without repeated trials on the phone.** The delays of 500,
  600 and 200 ms are unchanged since v1.5.1.
- **R8 stays on.** If a release build shows a connection problem and a debug build
  doesn't, suspect timing first (see the comment in `app/build.gradle.kts`).
- **Session history stays on the phone:** no cloud backup, and no phone-to-phone
  transfer.

## Other open work

- `connectGatt(..., transport)` is deprecated at API 37 but still works. Leave it until
  there's a reason to touch the BLE code.
- Pre-existing lint warnings: 3 × `UseKtx` in `Storage.kt`, 2 × `MonochromeLauncherIcon`,
  1 × `ObsoleteSdkInt`, and 1 × `EmptySuperCall` in `HeartRateViewModel.kt:48`.

## Environment

- **Shell:** Linux bash, local.
- **Local builds need `ANDROID_HOME=$HOME/Android/Sdk`.** It isn't set in `~/.bashrc`, so
  it disappears after a reboot.
- **JDK:** the system JDK is 21, and the build targets 17 to match CI.
- **Mode:** the last session ran semi-autonomous. The next session asks again.

## Key files

- `gradle/wrapper/gradle-wrapper.properties`: the wrapper version and checksum
- `gradle/libs.versions.toml`: AGP 9.4.1, Kotlin 2.4.20
- `.github/workflows/release.yml`: a dispatch builds a signed test APK, and a tag push
  publishes
