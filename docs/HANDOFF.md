# Handoff: move to AGP 9 and current dependencies

**Written:** 2026-09-23 · **Branch:** `deps/agp9-and-current-libs` (from master `8fb9d55`)

**Goal:** bring the build up to date (AGP 9.4.1, Gradle 9.x, Kotlin 2.4.20, compileSdk and
targetSdk 37, current Compose, AndroidX and coroutines). Then decide separately whether to
turn R8 back on.

## Current state

- **CI now uses the dev-skills release model** (PR #9, merged). Releases start when a
  maintainer pushes a tag (`git tag vX.Y.Z && git push origin vX.Y.Z` on master). A gate job
  checks three things before publishing: the tag is on master, Build passed for that commit,
  and the tag equals `versionName`.
  - Running **Release** by hand only builds a signed test APK. It never tags or publishes.
  - Build also compiles the unsigned release APK on every push.
  - All actions are pinned to commit SHAs.
  - `actionlint` runs on workflow edits.
  - `dependabot.yml` covers GitHub Actions and Gradle.
- **Dependabot alerts are on** (the API returns 200, 0 alerts). osv-scanner found 0
  advisories in the 81 packages in the release build.
- **Dependabot has opened #10–#14.** This branch replaces them: AGP 9 needs Gradle 9 and
  build-script changes that single-bump PRs can't make. Close them once this branch merges.
- **This branch has no code changes yet.** No version bump either.

## Key files

- `app/build.gradle.kts`: Kotlin plugin, `kotlinOptions`, compileSdk and targetSdk, the
  `isMinifyEnabled = false` block.
- `build.gradle.kts`: plugin aliases.
- `gradle/libs.versions.toml`: all versions.
- `gradle/wrapper/gradle-wrapper.properties`: currently Gradle 8.13.
- `settings.gradle.kts`: foojay resolver (#13 bumps it to 1.0.0).
- `.github/workflows/release.yml`: the gate job reads `build.yml` runs by file name.

## Decisions made

- **Go fully current, not "newest on AGP 8".** The current core-ktx and Compose UI (1.12.1)
  both declare `minCompileSdk=37` and `minAndroidGradlePluginVersion=9.1.0`.
- **The dependency upgrade is its own branch and PR,** separate from the CI changes.
- **`gradle/actions/setup-gradle` is gone on purpose.** Its v6 puts caching under
  separate Gradle terms of use, so `setup-java` with `cache: gradle` replaces it. Don't
  add it back without the maintainer agreeing to those terms.
- **Manual mode.** Git commands are handed to the maintainer to run. Claude never runs
  `gh workflow run` itself after handing it over.

## Why R8 was blamed (review finding, 2026-09-23)

**Minification was never shown to break the app.**
- A minified build of `ba5bc6f` compiles at 2.25 MB, against 18 MB unminified.
- All four `BluetoothGattCallback` overrides keep their names.
- The app's only reflection is the framework's `BluetoothGatt.refresh()`.
- v1.5.0 (unminified release) still failed while debug worked, so the fault goes with
  release-vs-debug timing.
- v1.5.1 delays `discoverServices()` by 600 ms and the CCCD write by 200 ms.

**Corrections to the in-repo comments:**
- In v1.4.0, discovery was already posted to the main handler. It ran immediately after
  connecting, not synchronously in the callback.
- One `Log.d` call costs microseconds, which is too small to have been the "margin".

**Open risk:** paired watches connect with `TRANSPORT_AUTO`, and 600 ms may be short for
them. A wait of about 1–1.6 s is commonly advised.

**To turn R8 back on:**
1. Set `isMinifyEnabled = true`, `isShrinkResources = true`.
2. Keep the `Log` strip rule commented out.
3. Build a signed test APK with a manual Release run.
4. Do ~20 connect/disconnect cycles on a real phone, including a paired watch and a cold
   start.

## Gate status (this branch)

```
🔢⬜ 🔨⬜ 🔒⏳ 📄⬜ 📦⬜ 🚀⬜ · manual
🔒 open Medium: dependencies majors behind (this branch fixes it)
🔒 open Medium: R8 off in release (needs the device trials above)
```

Nothing releases while either is open, unless it's fixed or the maintainer waives it.

**Shell:** Linux bash.

## Next step

Migrate the build to AGP 9.4.1 and Gradle 9.x:

1. Remove the `kotlin-android` plugin (AGP 9 has Kotlin built in) and move `kotlinOptions`
   to `kotlin { compilerOptions { … } }`.
2. Bump compileSdk and targetSdk to 37 and update `libs.versions.toml`.
3. Build locally.
4. Push, and let CI verify it.
5. Test on the phone before the PR.
