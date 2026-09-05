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

# Debug logging is deliberately RETAINED in release builds for now.
#
# The rule below stripped every Log.d/Log.v call from the release APK. Because
# BleHeartRateManager traces its entire connection lifecycle through Log.d --
# onConnectionStateChange, service discovery, cache refresh, notification enable --
# stripping it left the shipped v1.4.0 APK with exactly one surviving log line
# (the Log.w on scan failure) and no way to diagnose the connection hang from a
# logcat. A release build nobody can debug is a worse trade than logs on a device
# the user already owns.
#
# Note this file is only consulted when isMinifyEnabled is true, which it is not
# as of v1.4.1 (see app/build.gradle.kts). Keeping the rule commented out matters
# for the build that turns minification back on -- that build needs a readable
# logcat to identify which R8 optimization breaks the BLE connection.
#
# Before restoring it, confirm no lifecycle diagnostics depend on Log.d, or move
# those to Log.i so the release build keeps a usable trace.
#
# -assumenosideeffects class android.util.Log {
#     public static int d(...);
#     public static int v(...);
# }
