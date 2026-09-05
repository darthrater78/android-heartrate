# Heart Rate Mirror

Android app that displays live heart rate from a BLE heart rate monitor on your phone screen during workouts.

## Features

- Connects to any standard BLE Heart Rate Profile (0x180D) device
- Works with Pixel Watch (1/2/3), Polar, Garmin, and BLE chest straps
- Large BPM display with pulse animation synced to heart rate
- Device history with quick reconnect
- Heart rate session recording with stats and chart
- Dark OLED-friendly theme
- Screen stays on while connected

## Pixel Watch Setup

The Pixel Watch 3 does not broadcast heart rate over BLE by default. To enable it:

1. On the watch, open **Settings**
2. Find the **"Share heart rate"** option and turn it on
3. The watch will now appear in the app's BLE scan

Without this setting enabled, the watch will not be discoverable by the app.

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

Fitbit OS devices (everything except Pixel Watch) lock heart rate data to the Fitbit ecosystem. They will pair and connect, but the app will report "No Heart Rate service found." This is a Fitbit firmware limitation, not an app bug.

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

### [v1.0.0](https://github.com/darthrater78/android-heartrate/releases/tag/v1.0.0) — 2026-09-03
- Initial release
- BLE scan with paired device support
- Heart rate display with pulse animation
- Connection error reporting with timeout
