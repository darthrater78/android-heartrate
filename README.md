# Heart Rate Mirror

Android app that displays live heart rate from a BLE heart rate monitor on your phone screen during workouts.

## Features

- Connects to any standard BLE Heart Rate Profile (0x180D) device
- Works with Fitbit, chest straps, and other BLE HR monitors
- Works with Pixel Watch 3 — requires enabling **"Share heart rate"** in watch Settings to broadcast HR over BLE
- Large BPM display with pulse animation synced to heart rate
- Dark OLED-friendly theme
- Screen stays on while connected

## Pixel Watch Setup

The Pixel Watch 3 does not broadcast heart rate over BLE by default. To enable it:

1. On the watch, open **Settings**
2. Find the **"Share heart rate"** option and turn it on
3. The watch will now appear in the app's BLE scan

Without this setting enabled, the watch will not be discoverable by the app.

## Requirements

- Android 12+ (API 31)
- Device with Bluetooth LE support

## Building

Open in Android Studio, then Build > Generate App Bundles or APKs > Generate APKs.

Install the debug APK:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Version History

### v1.0.0 — 2026-09-03
- Initial release
- BLE scan with paired device support
- Heart rate display with pulse animation
- Connection error reporting with timeout
