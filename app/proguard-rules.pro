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

# Strip debug logging from release builds — Log.d/Log.v output is readable over
# ADB and by any app on a rooted device.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
