# Shabbat Alert Dismisser

An Android accessibility service that automatically dismisses cell broadcast emergency alerts (e.g. Pikud HaOref / Home Front Command) during Shabbat and Jewish holidays — so the full-screen alert doesn't block your screen while the siren still plays.

## How it works

The app registers as an Android Accessibility Service and listens for windows opened by cell broadcast packages. When an alert is detected during the configured time window, it waits a configurable delay (so you can hear the siren), then taps the dismiss button automatically.

Shabbat times are calculated locally using the **NOAA solar algorithm** based on your GPS coordinates — no internet connection required.

## Features

- Auto-dismiss during **Shabbat only**, **Shabbat + Jewish holidays**, **always**, or **disabled**
- Configurable **candle lighting** offset (18, 20, 22, 30, or 40 minutes before sunset)
- Configurable **Havdalah** offset (25–72 minutes after sunset)
- Configurable **delay** before dismissing (5–60 seconds) — gives you time to hear the siren
- Shows upcoming Shabbat start/end times in-app
- Hebrew and English UI
- Works entirely offline
- Supports major Android OEM cell broadcast packages (AOSP, Google, Samsung)

## Requirements

- Android 8.0+ (API 26)
- Accessibility service permission
- Location permission (for sunset calculation)

## Setup

1. Build and install the app
2. Open the app → tap **Open Accessibility Settings**
3. Find **Shabbat Alert Dismisser** → enable it → confirm
4. Back in the app → tap **Update Location** → allow location permission
5. Verify the Shabbat times shown are correct for your area
6. Adjust candle lighting / Havdalah minutes to your custom (minhag)
7. Set the delay before the alert is dismissed
8. Done — the service runs silently in the background

## Building from source

```bash
git clone https://github.com/YOUR_USERNAME/shabbat-alert-dismisser.git
cd shabbat-alert-dismisser
./gradlew assembleDebug
```

Or open the project in **Android Studio** (Electric Eel or newer) and hit Run.

**Requirements:** Android SDK 34, Gradle 8.2, Kotlin 1.9

## Project structure

```
app/src/main/
├── java/com/ilanp13/shabbatalertdismisser/
│   ├── MainActivity.kt           # Settings UI
│   ├── AlertDismissService.kt    # Accessibility service — detects and dismisses alerts
│   ├── ShabbatCalculator.kt      # NOAA-based sunset / Shabbat time calculator
│   └── HolidayCalculator.kt      # Hebrew calendar converter + Yom Tov detection
└── res/
    ├── layout/activity_main.xml
    ├── values/strings.xml         # English
    ├── values-iw/strings.xml      # Hebrew
    └── xml/accessibility_config.xml
```

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Calculate local sunset time |
| `BIND_ACCESSIBILITY_SERVICE` | Detect and dismiss alert windows |
| `RECEIVE_BOOT_COMPLETED` | (Reserved for future auto-start) |

## Disclaimer

This app is designed to dismiss the **screen overlay** after the alert has been displayed — it does not suppress the siren or prevent the notification from arriving. Always follow official safety instructions during an emergency.
