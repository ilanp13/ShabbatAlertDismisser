# Wear OS Shabbat Mode — Bug Fixes & Enhancements

**Date:** 2026-04-03
**Branch:** `feat/wear-os-shabbat-mode`
**Builds on:** `docs/superpowers/plans/2026-03-31-wear-os-shabbat-mode.md` (all 15 tasks complete)

---

## Overview

Fix all issues found in code review (2 critical, 4 moderate, 2 minor) and add four enhancements: configurable long-press unlock duration, emergency popup auto-dismiss, health sensor toggles, and "Always On" activation mode.

---

## A. Bug Fixes

### A1. Critical — Wire up ALERT_NOTIFICATION receiver (C1)

**Problem:** `NotificationFilterService` sends a broadcast with action `ALERT_NOTIFICATION`, but nothing receives it. The alert banner feature is broken end-to-end.

**Fix:** In `ShabbatWatchFaceActivity.onCreate()`, register a second `BroadcastReceiver` for action `com.ilanp13.shabbatalertdismisser.wear.ALERT_NOTIFICATION`. On receive, extract `alert_text` extra and call `bannerManager.onAlertReceived(text)`. Unregister in `onDestroy()`.

**Files:** `wear/.../ShabbatWatchFaceActivity.kt`

### A2. Critical — Fix LaunchedEffect key for ambient mode (C2)

**Problem:** `LaunchedEffect(Unit)` in `DigitalClock.kt` and `AnalogClock.kt` captures `isAmbient` but never restarts when it changes. Update interval stays locked to initial value.

**Fix:** Change `LaunchedEffect(Unit)` to `LaunchedEffect(isAmbient)` in both files.

**Files:** `wear/.../ui/DigitalClock.kt`, `wear/.../ui/AnalogClock.kt`

### A3. Moderate — Reactive watch face data (M1)

**Problem:** `hebrewDate`, `havdalahFormatted`, and `indicator` are computed once via `remember {}` and never update across midnight or window transitions.

**Fix:** Replace static `remember` blocks with a `var` driven by a `LaunchedEffect` that re-evaluates every 60 seconds. Use `mutableStateOf` so recomposition triggers naturally.

**Files:** `wear/.../ShabbatWatchFaceActivity.kt`

### A4. Moderate — Spinner init sync (M3)

**Problem:** `setupBannerTimeout()` in `WatchSettingsActivity` fires `onItemSelected` during programmatic `setSelection()`, triggering an unnecessary `syncSettings` call on every activity open.

**Fix:** Add a boolean guard `ignoreSpinnerInit` that is set to `true` before `setSelection()` and cleared in the first `onItemSelected` callback. Only call `syncSettings` when the guard is false.

**Files:** `app/.../WatchSettingsActivity.kt`

### A5. Moderate — Alarm scheduling past-time guard (M5)

**Problem:** `scheduleActivation()` can be called with a past time. While `setExactAndAllowWhileIdle` fires immediately for past times (acceptable), re-syncing data can cancel previously scheduled alarms via `FLAG_UPDATE_CURRENT`.

**Fix:** In `scheduleFromSyncedWindows()`, skip calling `scheduleActivation(activateTime)` if `activateTime <= now`. The existing `if (now >= activateTime && now < deactivateTime)` branch already handles mid-window activation.

**Files:** `wear/.../ShabbatModeController.kt`

### A6. Moderate — Emergency number string resource (M6)

**Problem:** Hardcoded `tel:100` (Israel Police) in `EmergencyDialogActivity`.

**Fix:** Add string resource `watch_emergency_number` (default `100` in EN, `100` in HE). Read via `getString()` when constructing the `ACTION_CALL` intent.

**Files:** `wear/.../EmergencyDialogActivity.kt`, `wear/src/main/res/values/strings.xml`, `wear/src/main/res/values-iw/strings.xml`

### A7. Minor — Hardcoded unlock toast (m4)

**Problem:** `WatchSettingsActivity` uses hardcoded English string `"Unlock command sent"`.

**Fix:** Add string resources `watch_unlock_sent` (EN: "Unlock command sent", HE: "פקודת ביטול נשלחה"). Use `getString()` in the toast.

**Files:** `app/.../WatchSettingsActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-iw/strings.xml`

### A8. Minor — Language code normalization (m5)

**Problem:** Phone stores language as `"he"` but Android locale uses `"iw"` for Hebrew. Mismatch when watch language support is implemented.

**Fix:** In `WatchSyncService.syncSettings()`, normalize the language value: if `"he"`, send `"iw"` instead. This matches Android's internal locale code.

**Files:** `app/.../WatchSyncService.kt`

---

## B. Configurable Long-Press Duration

**Requirement:** The time required to hold a physical button to break out of lock task mode should be configurable. Default: 10 seconds.

### Phone Side

- New setting key: `watch_long_press_seconds` (Int, default 10)
- `WatchSettingsActivity`: Add a SeekBar (range 3–15 seconds) with label and value display, in a new "Lock Screen" section above the Emergency section
- `WatchSyncService.syncSettings()`: Sync `long_press_seconds` field
- New strings:
  - EN: `watch_long_press_label` = "Long press duration to unlock"
  - EN: `watch_long_press_format` = "%d seconds"
  - HE: `watch_long_press_label` = "משך לחיצה ארוכה לביטול נעילה"
  - HE: `watch_long_press_format` = "%d שניות"

### Watch Side

- `WearDataReceiver`: Read `long_press_seconds` from DataMap, store as `PREF_LONG_PRESS_SECONDS`
- `ShabbatWatchFaceActivity`: Replace hardcoded `LONG_PRESS_THRESHOLD_MS = 3000L` with `prefs.getInt(PREF_LONG_PRESS_SECONDS, 10) * 1000L`

### Layout

Add to `activity_watch_settings.xml`, in a new "Lock Screen" section before the Emergency section:

```
Lock Screen
├── "Long press duration to unlock"
├── [SeekBar: 3–15 seconds]
└── "10 seconds"
```

---

## C. Emergency Popup Auto-Dismiss

**Requirement:** When the emergency popup appears after a long press, if no button is pressed within the same long-press duration, the popup closes and returns to the locked watch face.

### Implementation

- `EmergencyDialogActivity.onCreate()`: Read `watch_long_press_seconds` from prefs (default 10)
- Start a `Handler.postDelayed(::finish, timeoutMs)` on creation
- Any button click (`onSos`, `onEndShabbat`, `onCancel`, `onConfirm`) cancels the timer via `handler.removeCallbacksAndMessages(null)`
- If the user enters the confirmation sub-screen ("End Shabbat Mode?" → "Yes/No"), restart the timer for another full duration
- On timeout expiry, call `finish()` — returns to `ShabbatWatchFaceActivity` which is still in lock task mode

### Files

`wear/.../EmergencyDialogActivity.kt`

---

## D. Health Sensor Toggles

**Requirement:** Add individual toggles for disabling health monitoring features during Shabbat mode. All default to disabled.

### New Settings

| Key | Label (EN) | Label (HE) | Default |
|---|---|---|---|
| `watch_disable_heart_rate` | Disable heart rate monitor | כבה מד דופק | `true` |
| `watch_disable_spo2` | Disable blood oxygen (SpO2) | כבה מד חמצן בדם | `true` |
| `watch_disable_step_counter` | Disable step counter | כבה מונה צעדים | `true` |
| `watch_disable_body_sensors` | Disable other body sensors | כבה חיישני גוף נוספים | `true` |

### Phone Side

- Add a "Health Sensors" section header in `WatchSettingsActivity` layout, after "Battery Optimization"
- Four `SwitchMaterial` toggles, same pattern as existing battery toggles
- `WatchSyncService.syncSettings()`: Sync all four boolean fields
- New strings for section header: `watch_health_title` (EN: "Health Sensors", HE: "חיישני בריאות")

### Watch Side

- `WearDataReceiver`: Read and store all four prefs
- `BatteryOptimizer.applyShabbatSettings()`: For each enabled toggle, use `SensorManager` to check sensor type availability, then use `DevicePolicyManager.setPermittedInputMethods` is not applicable — instead, we disable sensor access by revoking the `BODY_SENSORS` permission at the device owner level using `DevicePolicyManager.setPermissionGrantState()` for the watch package. For step counter specifically, use `Settings.Global` to disable fitness tracking if available.
- `BatteryOptimizer.restoreSettings()`: Re-grant sensor permissions and restore settings

### Sensor Type Mapping

| Setting | Android Sensor Type |
|---|---|
| Heart rate | `Sensor.TYPE_HEART_RATE` |
| SpO2 | `Sensor.TYPE_HEART_BEAT` (or vendor-specific) |
| Step counter | `Sensor.TYPE_STEP_COUNTER`, `Sensor.TYPE_STEP_DETECTOR` |
| Body sensors | `Sensor.TYPE_HEART_RATE` catchall for remaining |

### Permissions

Add to `wear/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.BODY_SENSORS" />
```

Note: Actual sensor hardware disable is limited by Wear OS — the implementation will use `DevicePolicyManager` methods available to device owners. If a specific sensor cannot be programmatically disabled, the toggle is still synced and stored (for future implementation or third-party watch firmware support). The approach is best-effort with logging.

---

## E. "Always On" Activation Mode

**Requirement:** Users should be able to keep the watch permanently in Shabbat mode, independent of the Shabbat schedule, similar to the phone app's "Always" alert dismiss mode.

### Phone Side

- Add third radio button to `radioActivation` group in `WatchSettingsActivity`:
  - ID: `radioAlways`
  - Text: `watch_activation_always` (EN: "Always on (manual exit only)", HE: "תמיד פעיל (יציאה ידנית בלבד)")
- `WatchSyncService.syncSettings()`: Already syncs `activation_mode` string, just needs to handle `"always"` value

### Watch Side

- `WearDataReceiver`: Already stores `activation_mode` — no changes needed
- `ShabbatModeController.scheduleFromSyncedWindows()`: Add check at top:
  ```
  if mode == "always" and not currently active → activateShabbatMode()
  if mode == "always" → return (no alarms to schedule)
  ```
- `IdleStatusActivity.onCreate()`: Add check after existing Shabbat-active redirect:
  ```
  if mode == "always" → redirect to ShabbatWatchFaceActivity, finish()
  ```
- Exit only via long-press → Emergency dialog → "End Shabbat Mode" → confirm

### Edge Case

When switching from "always" to "auto", if Shabbat mode is currently active but we're outside a Shabbat window, the mode stays active until the user manually ends it or until the next havdalah deactivation fires. This is the expected behavior — we don't force-deactivate on settings change.

---

## Files Changed Summary

### Wear module (modified)
- `ShabbatWatchFaceActivity.kt` — A1, A3, B
- `EmergencyDialogActivity.kt` — A6, C
- `ShabbatModeController.kt` — A5, E
- `BatteryOptimizer.kt` — D
- `WearDataReceiver.kt` — B, D
- `ui/DigitalClock.kt` — A2
- `ui/AnalogClock.kt` — A2
- `AndroidManifest.xml` — D (BODY_SENSORS permission)
- `res/values/strings.xml` — A6, B, D, E
- `res/values-iw/strings.xml` — A6, B, D, E

### App module (modified)
- `WatchSettingsActivity.kt` — A4, A7, B, D, E
- `WatchSyncService.kt` — A8, B, D
- `res/layout/activity_watch_settings.xml` — B, D, E
- `res/values/strings.xml` — A7, B, D, E
- `res/values-iw/strings.xml` — A7, B, D, E
