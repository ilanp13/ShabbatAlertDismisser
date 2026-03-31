# Wear OS Shabbat Mode — Design Spec

**Date:** 2026-03-31
**Repo:** shabbat-alert-dismisser
**Approach:** Add Wear OS module to existing project (Approach B)

---

## 1. Overview

Add a Wear OS companion watch app to the existing Shabbat Alert Dismisser project. The watch app locks the watch into a dedicated Shabbat/holiday mode — showing only the time, Hebrew date, parasha, and havdalah time — while blocking all interaction. The phone app serves as the manager, calculating Shabbat schedules and syncing settings to the watch.

**Target devices:** All Wear OS watches (Galaxy Watch 4+, Pixel Watch, TicWatch, etc.)
**Languages:** Hebrew (default), English

---

## 2. Project Structure

The single-module project becomes a multi-module project:

```
shabbat-alert-dismisser/
├── settings.gradle.kts           # include(":app", ":shared", ":wear")
├── build.gradle.kts              # Root plugins
│
├── shared/                       # NEW: shared Kotlin library
│   ├── build.gradle.kts          # android-library plugin, no UI deps
│   └── src/main/java/com/ilanp13/shabbatalertdismisser/shared/
│       ├── ShabbatCalculator.kt  # Moved from :app
│       ├── HolidayCalculator.kt  # Moved from :app
│       ├── HebcalService.kt      # Moved from :app
│       └── MinhagProfiles.kt     # Moved from :app
│
├── app/                          # EXISTING: phone app
│   ├── build.gradle.kts          # adds dependency on :shared
│   └── src/main/java/.../
│       ├── (existing files)      # ShabbatCalculator etc. replaced by :shared imports
│       ├── WatchSettingsActivity.kt   # NEW: watch configuration screen
│       └── WatchSyncService.kt        # NEW: syncs schedule + settings to watch
│
├── wear/                         # NEW: Wear OS watch app
│   ├── build.gradle.kts          # wear app plugin, depends on :shared
│   └── src/main/java/com/ilanp13/shabbatalertdismisser/wear/
│       ├── ShabbatWatchFaceActivity.kt  # Main locked Shabbat screen
│       ├── ShabbatModeController.kt     # Lock Task Mode management
│       ├── WearDataReceiver.kt          # Receives synced data from phone
│       ├── AlertBannerManager.kt        # Passive emergency alert display
│       ├── NotificationFilterService.kt # Filters notifications to whitelist
│       ├── ButtonInterceptService.kt    # Disables physical buttons
│       ├── EmergencyDialogActivity.kt   # Long-press emergency popup
│       ├── BatteryOptimizer.kt          # Manages radio/sensor toggles
│       ├── IdleStatusActivity.kt        # Non-Shabbat status screen
│       └── ui/                          # Compose for Wear OS
│           ├── ShabbatFace.kt           # Watch face composable (round-first)
│           ├── AnalogClock.kt           # Analog clock composable
│           ├── DigitalClock.kt          # Digital clock composable
│           ├── AlertBanner.kt           # Emergency alert banner composable
│           └── theme/
│               └── Theme.kt            # Shabbat mode color scheme
```

---

## 3. Shared Module (`:shared`)

### Files moved from `:app`

| File | Purpose |
|------|---------|
| `ShabbatCalculator.kt` | NOAA sunset algorithm, `isShabbatNow()`, `getShabbatTimes()` |
| `HolidayCalculator.kt` | Reingold-Dershowitz Hebrew calendar, `isYomTovToday()`, `gregorianToHebrew()` |
| `HebcalService.kt` | Hebcal API client, `ShabbatWindow` data class, window merging, JSON serialization |
| `MinhagProfiles.kt` | 5 community profiles (Ashkenaz, Or HaChaim, Kise Rachamim, Ben Ish Chai, Jerusalem) |

### Build config

- `android-library` plugin (no application ID)
- `minSdk: 26` (matches both phone and Wear OS)
- No UI dependencies — pure Kotlin + Android stdlib
- The `:app` and `:wear` modules both depend on `:shared`

### Migration

- Move files to `:shared` with new package `com.ilanp13.shabbatalertdismisser.shared`
- Update all imports in `:app` to reference the shared package
- No logic changes — the code moves as-is

### Critical Constraint: No Regressions

All changes to the existing `:app` module must be purely additive. The existing alert dismisser behavior must remain completely unchanged:
- Moving files to `:shared` is a refactor with zero behavior change (same code, new package)
- The only new code in `:app` is `WatchSettingsActivity`, `WatchSyncService`, and a conditional entry point in `SettingsFragment`
- No existing UI, preferences, services, or logic are modified beyond updating import paths
- The watch feature is invisible to users who don't have a Wear OS watch with the app installed

---

## 4. Watch App (`:wear`) — Shabbat Mode

### 4.1 Activation & Deactivation

**Activation modes (user-configurable on phone):**
- **Auto:** Watch enters Shabbat mode at candle lighting time (minus user offset) and exits at havdalah time (plus user offset)
- **Manual:** Watch enters automatically, but requires manual deactivation from phone app (or emergency long-press on watch after havdalah)

**Time offsets:**
- Extra minutes before candle lighting: 0–30 min (slider)
- Extra minutes after havdalah: 0–60 min (slider)

**Schedule source:**
- Phone calculates the full `ShabbatWindow` list (from Hebcal + local fallback)
- Synced to watch via Wear Data Layer API `DataClient`
- Watch stores schedule in local `SharedPreferences`
- Schedule is deterministic — does not change during Shabbat

### 4.2 Lock Task Mode (Kiosk Mode)

When Shabbat mode activates:

1. Watch enters **Lock Task Mode** via Device Policy Manager
   - The watch app is set as the Device Owner (requires one-time setup via ADB or companion setup flow)
   - Only the Shabbat watch face activity is pinned
   - Navigation is blocked (no swipe to app drawer, no recent apps)
2. **Physical buttons disabled:**
   - Single press — blocked (no action)
   - Double press — blocked (no action)
   - Long press (3+ seconds) — opens emergency dialog
3. **Touch interaction disabled** except for long-press gesture
4. **DND mode activated** — suppresses all notifications except whitelisted apps
5. **Always-on display enabled** — ambient mode stays active

### 4.3 Emergency Long-Press Dialog

Triggered by long-pressing any physical button for 3+ seconds. Minimal dialog with:

1. **SOS emergency call** (if enabled in settings)
2. **Show last alert details** (if enabled in settings; shows most recent whitelisted alert info)
3. **End Shabbat mode** — always available:
   - After havdalah time: ends immediately
   - Before havdalah time: confirmation prompt "שבת/חג עדיין לא הסתיימו. לצאת ממצב שבת?" / "Shabbat/holiday hasn't ended yet. Exit Shabbat mode?"
4. **Cancel** — returns to Shabbat watch face

### 4.4 Battery Optimization

User-configurable toggles (set on phone, synced to watch):

| Setting | Default | Notes |
|---------|---------|-------|
| Disable Wi-Fi | ON | Saves significant battery |
| Disable GPS | ON | Not needed during Shabbat |
| Disable tilt-to-wake | ON | Prevents accidental wake |
| Disable touch-to-wake | OFF | User may want to check time |
| Disable LTE | OFF | **Warning:** disabling prevents direct emergency alerts on standalone watches |

Settings are applied when Shabbat mode activates and restored when it deactivates.

### 4.5 Whitelisted App Notifications

- Watch runs a `NotificationListenerService` that filters incoming notifications
- Only notifications from user-selected whitelisted packages are displayed
- Pre-populated suggestions: Pikud HaOref, Red Alert (צבע אדום), cell broadcast receiver
- Matching notifications appear as a **passive banner** on the watch face (no buttons, no interaction)
- Banner auto-dismisses after configurable timeout (15s / 30s / 60s / stay until next alert)
- For LTE watches: alerts arrive directly via cell broadcast or alert apps
- For Bluetooth-paired watches: notifications are mirrored from phone via standard Wear OS bridge

---

## 5. Watch Face UI

### 5.1 Layout (Round-first)

Designed for round displays (primary), with square adaptation via Compose for Wear OS `ScreenScaffold`.

```
        ╭──────────────────╮
      ╱                      ╲
    ╱       שבת שלום 🕯️        ╲
   │                            │
   │                            │
   │         12:34              │
   │      (or analog)           │
   │                            │
   │     כ״ה אדר תשפ״ו         │
   │     פרשת ויקרא             │
   │     מוצ״ש: 19:42           │
   │                            │
    ╲  ┌──────────────────┐   ╱
      ╲│ 🚨 צבע אדום - תא│ ╱
        ╰──────────────────╯
```

### 5.2 Display Elements

| Element | Position | Details |
|---------|----------|---------|
| Shabbat/holiday indicator | Top | Static text: "שבת שלום" or holiday name (e.g., "חג סוכות") with candle icon |
| Clock | Center | Analog or digital per user setting |
| Hebrew date | Below clock | Formatted Hebrew date (e.g., "כ״ה אדר תשפ״ו") |
| Parasha | Below date | Weekly Torah portion (e.g., "פרשת ויקרא") |
| Havdalah time | Below parasha | "מוצ״ש: 19:42" or "מוצאי חג: 20:15" |
| Alert banner | Bottom | Only visible when emergency alert active; passive, non-interactive |

### 5.3 Ambient Mode (Always-On)

- Reduced color palette (dark background, white/gray text)
- Updates once per minute (clock only)
- Alert banner IS shown in ambient mode when an emergency alert is active (safety-critical)
- Lower brightness for battery conservation

### 5.4 Non-Shabbat Idle Screen

When Shabbat mode is NOT active, the watch app shows:
- Next candle lighting time
- Next holiday (if within the week)
- Option to manually activate Shabbat mode early (for users who want to enter before the calculated time)

---

## 6. Phone App Changes (`:app`)

### 6.1 Watch Detection

Using Wear OS `CapabilityClient`:

```kotlin
Wearable.getCapabilityClient(context)
    .getCapability("shabbat_watch_app", CapabilityClient.FILTER_REACHABLE)
    .addOnSuccessListener { capabilityInfo ->
        val hasWatch = capabilityInfo.nodes.isNotEmpty()
        // Show/hide watch settings entry in SettingsFragment
    }
```

The watch app declares the `shabbat_watch_app` capability in its `wear.xml` resource.

### 6.2 WatchSettingsActivity

New activity launched from a button/entry in the existing `SettingsFragment`. Only visible when a watch with the app is detected.

**Sections:**

1. **Watch Status**
   - Connected watch name and model
   - Watch battery level
   - Current Shabbat mode status (active / inactive / scheduled)
   - Last sync timestamp

2. **Watch Face Style**
   - Radio: Analog / Digital

3. **Activation Mode**
   - Radio: Auto / Manual
   - Slider: Extra minutes before candle lighting (0–30)
   - Slider: Extra minutes after havdalah (0–60)

4. **Battery Optimization**
   - Toggle: Disable Wi-Fi
   - Toggle: Disable GPS
   - Toggle: Disable tilt-to-wake
   - Toggle: Disable touch-to-wake
   - Toggle: Disable LTE (with warning text about emergency alerts)

5. **Whitelisted Apps**
   - List of installed apps on the connected watch
   - Checkboxes to whitelist apps for alert banners during Shabbat mode
   - Pre-suggested: Pikud HaOref, Red Alert, cell broadcast

6. **Alert Banner**
   - Dropdown: Auto-dismiss timeout (15s / 30s / 60s / stay until next)

7. **Emergency Long-Press**
   - Toggle: Enable/disable SOS call option
   - Toggle: Enable/disable "show last alert" option
   - (End Shabbat mode is always available — not configurable)

### 6.3 WatchSyncService

Handles syncing data from phone to watch via Data Layer API:

**DataClient (persistent sync):**
- `/shabbat-schedule` — `ShabbatWindow` list as JSON (candle time, havdalah time, holiday name, parasha)
- `/watch-settings` — all watch configuration as JSON (face style, activation mode, offsets, battery toggles, whitelisted packages, banner timeout, emergency options)

**MessageClient (one-off commands):**
- `/unlock-shabbat-mode` — manual deactivation command from phone

**Sync triggers:**
- Settings changed by user → immediate sync
- Hebcal refresh completed → sync updated schedule
- App launch → sync if last sync > 1 hour ago

### 6.4 Settings Integration

The existing Shabbat timing settings (minhag profile, candle lighting minutes, havdalah method, location) are NOT duplicated in WatchSettingsActivity. The watch inherits these from the main app settings. One source of truth — changes to minhag or location in the main settings automatically trigger a re-sync to the watch.

---

## 7. Communication Protocol

### Phone → Watch

| Path | Type | Payload | Trigger |
|------|------|---------|---------|
| `/shabbat-schedule` | DataItem | JSON: `[{candleMs, havdalahMs, holidayName?, parasha?}]` | Hebcal refresh, settings change |
| `/watch-settings` | DataItem | JSON: all watch config | Any setting change |
| `/unlock-shabbat-mode` | Message | Empty | User taps "End Shabbat" on phone |

### Watch → Phone

| Path | Type | Payload | Trigger |
|------|------|---------|---------|
| `/watch-status` | DataItem | JSON: `{batteryPct, shabbatModeActive, lastActivation}` | Mode change, every 30 min |

### Offline Behavior

- Watch stores last-synced schedule and settings in local SharedPreferences
- If phone is disconnected (LTE watch scenario), watch operates on cached data
- Shabbat times are deterministic — cached data is always valid for the current week
- Watch attempts re-sync when connection is restored

---

## 8. Device Owner Setup

Lock Task Mode requires the watch app to be a **Device Owner** or have a **Device Admin** with lock task privileges.

### Setup flow (one-time):

**Option A: ADB setup (developer-friendly)**
```bash
adb -s <watch-serial> shell dpm set-device-owner \
    com.ilanp13.shabbatalertdismisser.wear/.AdminReceiver
```

**Option B: Guided setup from phone app**
- WatchSettingsActivity detects if device owner is not set
- Shows step-by-step instructions with ADB commands
- Verifies setup by querying watch via MessageClient

### AdminReceiver

Minimal `DeviceAdminReceiver` in the wear module that:
- Whitelists the Shabbat watch face activity for Lock Task Mode
- Manages lock task packages (adds whitelisted alert apps if they need foreground)

---

## 9. Build Configuration

### Root `settings.gradle.kts`
```kotlin
include(":app", ":shared", ":wear")
```

### `:shared/build.gradle.kts`
```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ilanp13.shabbatalertdismisser.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
```

### `:wear/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.ilanp13.shabbatalertdismisser.wear"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ilanp13.shabbatalertdismisser.wear"
        minSdk = 26
        targetSdk = 35
    }
}
dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha29")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
}
```

### `:app/build.gradle.kts` (additions)
```kotlin
dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // ... existing dependencies unchanged
}
```

---

## 10. Localization

- Hebrew is the default language for the watch app
- English fully supported
- Language follows the phone app's `app_language` setting (synced to watch)
- All user-facing strings in `values-iw/strings.xml` (Hebrew) and `values/strings.xml` (English)
- Hebrew date formatting uses the shared module's `HolidayCalculator.gregorianToHebrew()`
- Holiday names provided in Hebrew from Hebcal API (with English fallback)

---

## 11. Permissions

### Watch app (`wear/AndroidManifest.xml`)
```xml
<uses-feature android:name="android.hardware.type.watch" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

### Phone app additions
```xml
<!-- No new permissions needed — Wearable API uses existing INTERNET permission -->
```

---

## 12. Out of Scope (Future)

- Custom watch face complications (Wear OS complications API)
- Tile for quick glance outside the app
- Watch-side Hebcal fetching (currently phone-only)
- Integration with other Jewish calendar apps
- Extracting `:shared` into a standalone published library
- Merging GalaxyShabbatWatch repo into this project (may revisit)
