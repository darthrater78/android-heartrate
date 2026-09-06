# R8 rules for the release build.
#
# Referenced from app/build.gradle.kts. The file must exist whenever
# isMinifyEnabled is true — R8 fails the build on a missing rules file.
#
# The app stores its data as hand-built JSON (see data/Storage.kt) rather than
# through a reflective serializer, so the model classes need no keep rules.
# Compose and Kotlin ship their own consumer rules via AGP defaults.

# Keep BLE callback subclasses intact. These are instantiated by us but their
# methods are invoked by the Android Bluetooth stack, so R8 cannot see the call
# sites and may otherwise strip or rename an override.
-keepclassmembers class * extends android.bluetooth.BluetoothGattCallback {
    public *;
}
-keepclassmembers class * extends android.bluetooth.le.ScanCallback {
    public *;
}

# DO NOT RESTORE THE RULE BELOW. It is what broke v1.4.0.
#
# The rule stripped every Log.d/Log.v call from the release APK. v1.4.0 shipped with
# it enabled and hung on connect; the app reached the connecting screen and never
# left it. Minification was assumed to be the cause and v1.4.1 shipped unminified at
# 18 MB. A minified build with only this rule removed then connected normally on a
# real device, at 2.2 MB -- but that was one successful connection, and the fault is
# intermittent, so it settles nothing. Treat this rule as the known cause of the
# v1.4.0 hang and minification itself as untested either way.
#
# Minification is now off for good (see app/build.gradle.kts), so the question is
# moot unless someone deliberately re-opens it.
#
# Mechanism, now confirmed. The log calls were acting as an accidental delay.
# BleHeartRateManager called gatt.discoverServices() directly inside
# onConnectionStateChange, and calling it too soon after the link comes up is a known
# Android BLE race that yields an empty or stale service list. Log.d writes to the log
# socket, which is not free; strip it and discovery fires microseconds earlier, into
# the race.
#
# What settled it was v1.5.0: an UNMINIFIED release build that still failed
# intermittently, while a debug build of the identical commit worked every time. Debug
# builds are slower (debuggable, JIT, unoptimised) and so lose the race reliably.
# Minification was never the cause -- it was only ever a proxy for execution speed.
#
# v1.5.1 makes the wait explicit: discovery is posted to the main handler after a
# settle delay, as is the CCCD descriptor write. Connection correctness no longer
# depends on how fast the build runs.
#
# That does NOT make this rule safe to restore. It removes the log output that is the
# only way to diagnose the BLE lifecycle on a shipped build, for no benefit while
# minification is off. Leave it commented out.
#
# -assumenosideeffects class android.util.Log {
#     public static int d(...);
#     public static int v(...);
# }
