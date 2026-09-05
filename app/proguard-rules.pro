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
# 18 MB. That was wrong: a minified build with only this rule removed connects
# normally on a real device, at 2.2 MB. Minification is fine. This rule is not.
#
# Likely mechanism, inferred rather than proven: the log calls were acting as an
# accidental delay. BleHeartRateManager calls gatt.discoverServices() directly inside
# onConnectionStateChange, and calling it too soon after the link comes up is a
# known Android BLE race that yields an empty or stale service list. Log.d writes to
# the log socket, which is not free; strip it and discovery fires microseconds
# earlier, into the race. That matches the symptom -- the hang is silent, and the
# missing-HR-service path is exactly where it would stall.
#
# If that mechanism is right, the connection currently works partly by accident and
# the durable fix is an explicit delay before discoverServices() rather than relying
# on log-call timing. Until that is implemented and verified on a real device,
# leave this commented out. Restoring it without re-testing the connection on
# hardware will reintroduce the v1.4.0 hang.
#
# -assumenosideeffects class android.util.Log {
#     public static int d(...);
#     public static int v(...);
# }
