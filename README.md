# Shabbat Alert Dismisser

An Android accessibility service that automatically dismisses cell broadcast emergency alerts (e.g. Pikud HaOref / Home Front Command) during Shabbat and Jewish holidays — so the full-screen alert doesn't block your screen while the siren still plays.

## How it works

The app registers as an Android Accessibility Service and listens for windows opened by cell broadcast packages. When an alert is detected during the configured time window, it waits a configurable delay (so you can hear the siren), then taps the dismiss button automatically.

Shabbat times are fetched from the **Hebcal API** based on your GPS coordinates and chosen minhag for maximum accuracy. If no network is available, the app falls back to a local **NOAA solar algorithm**.

## Features

- Auto-dismiss during **Shabbat only**, **Shabbat + Jewish holidays**, **always**, or **disabled**
- **Minhag profiles** — choose your community calendar (Ashkenaz, Or HaChaim, Kise Rachamim, Ben Ish Chai, Jerusalem) and get accurate candle lighting / Havdalah times automatically
- **Standard (Gra) or Rabenu Tam** end-of-Shabbat, per your custom
- Configurable **delay** before dismissing (5–60 seconds) — gives you time to hear the siren
- **Persistent status notification** showing current state and upcoming Shabbat times (optional, can be disabled in-app)
- Shows upcoming Shabbat start/end times in-app, synced via Hebcal
- Offline fallback — works without internet using local sunset calculation
- Hebrew and English UI
- Supports major Android OEM cell broadcast packages (AOSP, Google, Samsung)

## Screenshots

| Main screen | Minhag & times | Delay & settings |
|---|---|---|
| ![Main](screenshots/01-main-screen.jpeg) | ![Minhag](screenshots/02-minhag-and-times.jpeg) | ![Delay](screenshots/03-delay-and-settings.jpeg) |

| Screen-on options | Notification |
|---|---|
| ![Screen on](screenshots/04-screen-on-options.jpeg) | ![Notification](screenshots/05-notification.jpeg) |

## Setup

1. Build and install the app
2. Open the app → tap **Open Accessibility Settings**
3. Find **Shabbat Alert Dismisser** → enable it → confirm
4. Back in the app → tap **Update Location** → allow location permission
5. Select your **minhag** and preferred end-of-Shabbat (Gra / Rabenu Tam)
6. Verify the Shabbat times shown are correct for your area
7. Set the delay before the alert is dismissed
8. Done — the service runs silently in the background

## Battery impact

The app is designed to be extremely lightweight:

| What it does | Battery cost |
|---|---|
| Accessibility service (idle) | Near zero — event-driven, only wakes on cell broadcast windows |
| Notification refresh | Once per minute (negligible string update) |
| Hebcal sync | Once per week, single HTTP call |
| Location | On demand only (when you tap Update Location) |

In practice the app won't appear in your battery stats.

> ⚠️ **Keep screen on** (optional): if enabled, the display stays lit during Shabbat — this *does* drain the battery significantly. The battery optimization setting below is especially recommended if you use this feature.

## Keeping the service alive (Samsung / aggressive OEMs)

Android restarts the accessibility service automatically after a normal reboot or crash. However, some OEMs (especially Samsung) aggressively kill background services to save battery, which can cause the service to stop working mid-Shabbat.

**Recommended:** add the app to the "Never sleeping apps" list:

> **Settings → Battery → Background usage limits → Never sleeping apps → add *Shabbat Alert Dismisser***

> ⚠️ **Note:** If you use **Force Stop** (Settings → Apps → Force Stop), Android will disable the accessibility service and it must be re-enabled manually. This is an Android security feature and cannot be worked around.

## Building from source

```bash
git clone https://github.com/ilanp13/ShabbatAlertDismisser.git
cd ShabbatAlertDismisser
./gradlew assembleDebug
```

Or open the project in **Android Studio (Hedgehog or newer)** and hit Run.

**Requirements:** Android SDK 34, Gradle 8.7, Kotlin 2.0, JDK 21

## Project structure

```
app/src/main/
├── java/com/ilanp13/shabbatalertdismisser/
│   ├── MainActivity.kt           # Settings UI
│   ├── AlertDismissService.kt    # Accessibility service — detects and dismisses alerts
│   ├── HebcalService.kt          # Fetches accurate Shabbat times from Hebcal API
│   ├── MinhagProfiles.kt         # Named community calendar profiles
│   ├── ShabbatCalculator.kt      # NOAA-based sunset / Shabbat time calculator (offline fallback)
│   ├── HolidayCalculator.kt      # Hebrew calendar converter + Yom Tov detection
│   └── BootReceiver.kt           # Refreshes Hebcal cache after device reboot
└── res/
    ├── layout/activity_main.xml
    ├── values/strings.xml         # English
    ├── values-iw/strings.xml      # Hebrew
    └── xml/accessibility_config.xml
```

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Fetch accurate Shabbat times from Hebcal API |
| `ACCESS_FINE_LOCATION` | Calculate local sunset time |
| `POST_NOTIFICATIONS` | Show persistent status notification (Android 13+) |
| `BIND_ACCESSIBILITY_SERVICE` | Detect and dismiss alert windows |
| `RECEIVE_BOOT_COMPLETED` | Refresh Hebcal times after device reboot |

## Disclaimer

This app is designed to dismiss the **screen overlay** after the alert has been displayed — it does not suppress the siren or prevent the notification from arriving. Always follow official safety instructions during an emergency.

## Privacy & Legal

- **[Privacy Policy](PRIVACY_POLICY.md)** — How we handle your data
- **[Terms of Service](TERMS_OF_SERVICE.md)** — App usage terms
