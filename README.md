# ReOrient

ReOrient is a lightweight Android app that globally corrects broken orientation behavior by inverting/mapping rotation values.

I built this for my Essential PH-1 after the accelerometer went off the rails: up/down and left/right were effectively reversed, and auto-rotate became unusable.

## What It Does

- Runs a foreground service that listens to device orientation changes.
- Applies a 180-degree corrected mapping for screen orientation.
- Uses a tiny invisible overlay window to enforce orientation globally.
- Can restart automatically after boot if it was enabled.

## Why It Exists

This app is specifically for devices where hardware sensor output is wrong but still changing, so software remapping can make the phone usable again.

## Requirements

- Android 7.0+ (`minSdk 24`)
- Overlay permission (`Display over other apps`)
- Foreground service + persistent notification
- Recommended: disable battery optimization for ReOrient so Android does not kill the service

## Build

```bash
./gradlew assembleDebug
```

Debug APK path:

`app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. Install and open ReOrient.
2. Tap **Start Orientation Fix**.
3. Grant overlay permission when prompted.
4. In battery settings, set ReOrient to **Don’t optimize**.
5. Rotate the phone and confirm orientation behaves normally.

To disable the fix, open the app and tap **Stop Orientation Fix**.

## Technical Notes

- Orientation mapping is handled in `RotationService`.
- A small hysteresis threshold is used to reduce flicker near rotation boundaries.
- Boot restore behavior is implemented in `BootReceiver` using `SharedPreferences`.

## Known Limitations

- This is a workaround, not a hardware repair.
- Some OEM ROMs may aggressively kill background services even with optimizations disabled.
- If overlay permission is revoked, the fix cannot be applied.
