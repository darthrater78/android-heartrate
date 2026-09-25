# Dev Skills gate state
Track: release sequence — v1.6.0 from fix/ble-reconnect-hang
Mode: semi-autonomous (approved 2026-09-24) — commits and the tag still require the user's approval
Origin: darthrater78/android-heartrate (not a fork)
Standards: at-rest ➖ decided — plaintext app storage, recorded in README (6783703) · no auth · no Docker
Version: 1.6.0
Updated: 2026-09-24

🔢 VERSION    ✅ all refs at 1.6.0 (versionCode 9)
              only version ref: app/build.gradle.kts; app screen links derive from versionName (ScanScreen.kt:326)
              README top: GitHub + v1.6.0 release notes links; v1.5.1 tag on remote; minor per feat 379e38f, user-chosen
🔨 BUILD      ✅ local build + phone trial pass; pre-merge signed artifact owed for the PR head
              phone trial: user reports "we're good for release" on signed R8 APK, Release run 36074557797 (3e533f1)
              bumped tree (7eeba7a): assembleRelease (R8 on, 2.17 MB) + lintDebug pass, HEAD/porcelain stable across run
              owed before merge: signed Release dispatch on the PR head (user runs; classifier blocks Claude)
🔒 SECURITY   ✅ 0 open — 0 Critical, 0 High (fixed: 1 Medium + 3 earlier; 1 Low waived by user)
              ✅ fixed: Medium — R8 off in release → isMinifyEnabled/isShrinkResources true (3e533f1)
              ✅ fixed: deps behind — AGP 9.4.1 / Gradle 9.7.1 / Kotlin 2.4.20 (PR #15, accc1ae); OSV 98 pkgs, 0 advisories
              ✅ fixed: Low — phone-to-phone transfer ignored allowBackup=false → data_extraction_rules.xml (5830e81)
              ✅ fixed: Low — session name unbounded → capped at 60 (5830e81)
              ➖ waived by user 2026-09-24: Low — Gradle wrapper 9.7.1, 9.8.0 available (released same day, no advisory); upgrade after v1.6.0
              full audit 2026-09-24: manifest, exported components, intents, PendingIntent, logs, storage, CI, key history; Dependabot 0 / secret scanning 0
              Quality: no findings (nesting ≤3, functions <40 lines, BLE calls async on main looper)
📄 DOCS       ✅ v1.6.0 Version History entry; stale docs/HANDOFF.md removed (user approved)
              release.yml notes extraction dry-run: 26 lines; unsigned-build README note fixed
              checked: features, Data Storage, JDK 17 vs CI, release process vs release.yml
📦 RELEASE    ⏳ release notes approved with 7eeba7a; PR to master next
🚀 SHIP       ⏳ plan: signed artifact on PR head → merge (no --delete-branch) → user pushes tag v1.6.0 → Release workflow publishes → verify
