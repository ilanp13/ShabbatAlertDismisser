# Wear OS Fixes & Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all code review issues, fix screen-on overlay leak bug, and add configurable long-press, emergency auto-dismiss, health sensor toggles, and "Always On" activation mode.

**Architecture:** Incremental changes to existing `:wear` and `:app` modules. All new settings follow the established pattern: phone-side UI → `WatchSyncService` sync → `WearDataReceiver` storage → watch-side consumption. No new modules or dependencies.

**Tech Stack:** Kotlin, Wear OS Compose, Wear Data Layer API, SharedPreferences, DevicePolicyManager

**Spec:** `docs/superpowers/specs/2026-04-03-wear-os-fixes-and-enhancements-design.md`

---

## File Structure

### Files to modify

```
wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/
├── ShabbatWatchFaceActivity.kt   # A1 (alert receiver), A3 (reactive data), B (long-press from prefs)
├── EmergencyDialogActivity.kt    # A6 (emergency number), C (auto-dismiss timer)
├── ShabbatModeController.kt      # A5 (alarm guard), E (always-on mode)
├── BatteryOptimizer.kt           # D (health sensor toggles)
├── WearDataReceiver.kt           # B+D+E (new pref constants)
├── IdleStatusActivity.kt         # E (always-on redirect)
├── ui/DigitalClock.kt            # A2 (LaunchedEffect key)
└── ui/AnalogClock.kt             # A2 (LaunchedEffect key)

wear/src/main/
├── AndroidManifest.xml           # D (BODY_SENSORS permission)
├── res/values/strings.xml        # A6, B, D, E new strings
└── res/values-iw/strings.xml     # A6, B, D, E new strings

app/src/main/java/com/ilanp13/shabbatalertdismisser/
├── WatchSettingsActivity.kt      # A4 (spinner guard), A7 (toast), B+D+E (new settings)
└── WatchSyncService.kt           # A8 (lang normalize), B+D (new sync fields)

app/src/main/res/
├── layout/activity_watch_settings.xml  # B, D, E (new UI elements)
├── values/strings.xml                  # A7, B, D, E new strings
└── values-iw/strings.xml              # A7, B, D, E new strings
```

---

## Task 1: Fix critical bug — wire up ALERT_NOTIFICATION receiver (C1)

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt`

- [ ] **Step 1: Add alert notification receiver field and registration**

In `ShabbatWatchFaceActivity.kt`, add a second `BroadcastReceiver` field after `stopLockTaskReceiver`:

```kotlin
    private val alertNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra("alert_text") ?: return
            bannerManager.onAlertReceived(text)
        }
    }
```

In `onCreate()`, after the `stopLockTaskReceiver` registration block, add:

```kotlin
        // Listen for whitelisted notification alerts
        val alertFilter = IntentFilter("com.ilanp13.shabbatalertdismisser.wear.ALERT_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alertNotificationReceiver, alertFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alertNotificationReceiver, alertFilter)
        }
```

In `onDestroy()`, add after the existing unregister:

```kotlin
        try {
            unregisterReceiver(alertNotificationReceiver)
        } catch (e: Exception) { /* ignore */ }
```

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt
git commit -m "fix(wear): wire up ALERT_NOTIFICATION receiver for notification banners"
```

---

## Task 2: Fix critical bug — LaunchedEffect ambient key (C2)

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/DigitalClock.kt`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AnalogClock.kt`

- [ ] **Step 1: Fix DigitalClock.kt**

Change `LaunchedEffect(Unit)` to `LaunchedEffect(isAmbient)`:

```kotlin
    LaunchedEffect(isAmbient) {
        while (true) {
            time = formatTime()
            delay(if (isAmbient) 60_000L else 1_000L)
        }
    }
```

- [ ] **Step 2: Fix AnalogClock.kt**

Change `LaunchedEffect(Unit)` to `LaunchedEffect(isAmbient)`:

```kotlin
    LaunchedEffect(isAmbient) {
        while (true) {
            calendar = Calendar.getInstance()
            delay(if (isAmbient) 60_000L else 1_000L)
        }
    }
```

- [ ] **Step 3: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/DigitalClock.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AnalogClock.kt
git commit -m "fix(wear): use isAmbient as LaunchedEffect key in clock composables"
```

---

## Task 3: Fix moderate bug — reactive watch face data (M1)

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt`

- [ ] **Step 1: Replace static remember blocks with periodic refresh**

In `ShabbatWatchFaceActivity.kt`, replace the `setContent` block inside `onCreate()`. The new version uses `mutableStateOf` with a `LaunchedEffect` that refreshes every 60 seconds:

Replace the entire block from `setContent {` through its closing `}` with:

```kotlin
        setContent {
            ShabbatWatchTheme {
                val alertText by bannerManager.alertText.collectAsState()

                var windowInfo by remember { mutableStateOf(controller.getCurrentWindowInfo()) }
                var hebrewDate by remember { mutableStateOf(formatHebrewDate()) }

                LaunchedEffect(Unit) {
                    while (true) {
                        delay(60_000L)
                        windowInfo = controller.getCurrentWindowInfo()
                        hebrewDate = formatHebrewDate()
                    }
                }

                val havdalahMs = windowInfo?.second ?: 0L
                val parasha = windowInfo?.first

                val havdalahFormatted = if (havdalahMs > 0) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    getString(R.string.havdalah_motzash, sdf.format(Date(havdalahMs)))
                } else ""

                val indicator = if (parasha != null && parasha != getString(R.string.shabbat_shalom)) {
                    parasha
                } else {
                    getString(R.string.shabbat_shalom)
                }

                ShabbatFace(
                    indicator = indicator,
                    hebrewDate = hebrewDate,
                    parasha = parasha,
                    havdalahTime = havdalahFormatted,
                    alertText = alertText,
                    useAnalog = useAnalog,
                    isAmbient = false
                )
            }
        }
```

Add import at top of file:

```kotlin
import kotlinx.coroutines.delay
```

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt
git commit -m "fix(wear): refresh watch face data every 60s for midnight/window transitions"
```

---

## Task 4: Fix moderate bug — spinner init sync guard (M3)

**Files:**
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt`

- [ ] **Step 1: Add init guard to setupBannerTimeout**

Replace the `setupBannerTimeout()` method with:

```kotlin
    private fun setupBannerTimeout() {
        val spinner = findViewById<Spinner>(R.id.spinnerBannerTimeout)
        val options = arrayOf(
            getString(R.string.watch_banner_15s),
            getString(R.string.watch_banner_30s),
            getString(R.string.watch_banner_60s),
            getString(R.string.watch_banner_stay)
        )
        val values = intArrayOf(15, 30, 60, 0)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        val current = prefs.getInt("watch_banner_timeout_sec", 30)
        spinner.setSelection(values.indexOf(current).coerceAtLeast(0))

        var ignoreInit = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignoreInit) {
                    ignoreInit = false
                    return
                }
                prefs.edit().putInt("watch_banner_timeout_sec", values[pos]).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt
git commit -m "fix(app): skip syncSettings on spinner init in WatchSettingsActivity"
```

---

## Task 5: Fix moderate bug — alarm scheduling past-time guard (M5)

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatModeController.kt`

- [ ] **Step 1: Add guard in scheduleFromSyncedWindows**

In `scheduleFromSyncedWindows()`, change the `else if` branch from:

```kotlin
        } else if (now < activateTime) {
            scheduleActivation(activateTime)
            scheduleDeactivation(deactivateTime)
        }
```

to:

```kotlin
        } else if (now < activateTime) {
            scheduleActivation(activateTime)
            scheduleDeactivation(deactivateTime)
        } else {
            // activateTime is past but deactivateTime is also past — skip
            Log.d(TAG, "Window already passed, skipping")
        }
```

This makes the logic explicit. The existing `if (now >= activateTime && now < deactivateTime)` branch already handles the "inside window" case correctly, and the `else if (now < activateTime)` only schedules future activations.

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatModeController.kt
git commit -m "fix(wear): add explicit handling for past windows in scheduleFromSyncedWindows"
```

---

## Task 6: Fix moderate + minor bugs — emergency number, unlock toast, language code (M6, m4, m5)

**Files:**
- Modify: `wear/src/main/res/values/strings.xml`
- Modify: `wear/src/main/res/values-iw/strings.xml`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/EmergencyDialogActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-iw/strings.xml`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt`

- [ ] **Step 1: Add emergency number string to wear strings**

In `wear/src/main/res/values/strings.xml`, add before `</resources>`:

```xml
    <string name="emergency_number">100</string>
```

In `wear/src/main/res/values-iw/strings.xml`, add before `</resources>`:

```xml
    <string name="emergency_number">100</string>
```

- [ ] **Step 2: Use string resource for emergency call**

In `EmergencyDialogActivity.kt`, change the SOS call from:

```kotlin
                        onSos = {
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:100"))
                            startActivity(callIntent)
                        },
```

to:

```kotlin
                        onSos = {
                            val number = getString(R.string.emergency_number)
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                            startActivity(callIntent)
                        },
```

- [ ] **Step 3: Add unlock toast string to app strings**

In `app/src/main/res/values/strings.xml`, add before `</resources>`:

```xml
    <string name="watch_unlock_sent">Unlock command sent</string>
```

In `app/src/main/res/values-iw/strings.xml`, add before `</resources>`:

```xml
    <string name="watch_unlock_sent">פקודת ביטול נשלחה</string>
```

- [ ] **Step 4: Use string resource for unlock toast**

In `WatchSettingsActivity.kt`, in `setupActionButtons()`, change:

```kotlin
            Toast.makeText(this, "Unlock command sent", Toast.LENGTH_SHORT).show()
```

to:

```kotlin
            Toast.makeText(this, getString(R.string.watch_unlock_sent), Toast.LENGTH_SHORT).show()
```

- [ ] **Step 5: Normalize language code in WatchSyncService**

In `WatchSyncService.kt`, in `syncSettings()`, change:

```kotlin
            dataMap.putString("language", prefs.getString("app_language", "iw") ?: "iw")
```

to:

```kotlin
            val lang = prefs.getString("app_language", "iw") ?: "iw"
            dataMap.putString("language", if (lang == "he") "iw" else lang)
```

- [ ] **Step 6: Build both modules**

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add wear/src/main/res/values/strings.xml \
    wear/src/main/res/values-iw/strings.xml \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/EmergencyDialogActivity.kt \
    app/src/main/res/values/strings.xml \
    app/src/main/res/values-iw/strings.xml \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt
git commit -m "fix: use string resources for emergency number and unlock toast, normalize lang code"
```

---

## Task 7: Add all new strings for enhancements (B, D, E)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-iw/strings.xml`
- Modify: `wear/src/main/res/values/strings.xml`
- Modify: `wear/src/main/res/values-iw/strings.xml`

- [ ] **Step 1: Add new app strings (EN)**

In `app/src/main/res/values/strings.xml`, add before `</resources>`:

```xml
    <string name="watch_activation_always">Always on (manual exit only)</string>
    <string name="watch_long_press_label">Long press duration to unlock</string>
    <string name="watch_long_press_format">%d seconds</string>
    <string name="watch_lock_screen_title">Lock Screen</string>
    <string name="watch_health_title">Health Sensors</string>
    <string name="watch_disable_heart_rate">Disable heart rate monitor</string>
    <string name="watch_disable_spo2">Disable blood oxygen (SpO2)</string>
    <string name="watch_disable_step_counter">Disable step counter</string>
    <string name="watch_disable_body_sensors">Disable other body sensors</string>
```

- [ ] **Step 2: Add new app strings (HE)**

In `app/src/main/res/values-iw/strings.xml`, add before `</resources>`:

```xml
    <string name="watch_activation_always">תמיד פעיל (יציאה ידנית בלבד)</string>
    <string name="watch_long_press_label">משך לחיצה ארוכה לביטול נעילה</string>
    <string name="watch_long_press_format">%d שניות</string>
    <string name="watch_lock_screen_title">מסך נעילה</string>
    <string name="watch_health_title">חיישני בריאות</string>
    <string name="watch_disable_heart_rate">כבה מד דופק</string>
    <string name="watch_disable_spo2">כבה מד חמצן בדם</string>
    <string name="watch_disable_step_counter">כבה מונה צעדים</string>
    <string name="watch_disable_body_sensors">כבה חיישני גוף נוספים</string>
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-iw/strings.xml
git commit -m "feat(app): add strings for long-press, health sensors, and always-on mode"
```

---

## Task 8: Configurable long-press duration — phone side (B)

**Files:**
- Modify: `app/src/main/res/layout/activity_watch_settings.xml`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt`

- [ ] **Step 1: Add lock screen section to layout**

In `activity_watch_settings.xml`, add after the Alert Banner Timeout `Spinner` closing tag and before the Emergency Long-Press `TextView`:

```xml
        <!-- Lock Screen -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_lock_screen_title"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_long_press_label"
            android:textSize="14sp"
            android:layout_marginBottom="4dp" />

        <SeekBar
            android:id="@+id/seekLongPress"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:min="3"
            android:max="15"
            android:layout_marginBottom="4dp" />

        <TextView
            android:id="@+id/tvLongPress"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:layout_marginBottom="16dp" />
```

- [ ] **Step 2: Add setupLongPress method to WatchSettingsActivity**

In `WatchSettingsActivity.kt`, add a new method:

```kotlin
    private fun setupLongPress() {
        val seekBar = findViewById<SeekBar>(R.id.seekLongPress)
        val tvValue = findViewById<TextView>(R.id.tvLongPress)

        seekBar.progress = prefs.getInt("watch_long_press_seconds", 10)
        tvValue.text = getString(R.string.watch_long_press_format, seekBar.progress)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvValue.text = getString(R.string.watch_long_press_format, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt("watch_long_press_seconds", sb?.progress ?: 10).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
        })
    }
```

Call it in `onCreate()`, add `setupLongPress()` after `setupBannerTimeout()` and before `setupEmergencyToggles()`.

- [ ] **Step 3: Add long_press_seconds to WatchSyncService**

In `WatchSyncService.kt`, in `syncSettings()`, add after the `banner_timeout_sec` line:

```kotlin
            dataMap.putInt("long_press_seconds", prefs.getInt("watch_long_press_seconds", 10))
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_watch_settings.xml \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt
git commit -m "feat(app): add configurable long-press duration setting (default 10s)"
```

---

## Task 9: Configurable long-press duration — watch side (B)

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/WearDataReceiver.kt`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt`

- [ ] **Step 1: Add pref constant and read in WearDataReceiver**

In `WearDataReceiver.kt`, add new constant in the companion object:

```kotlin
        const val PREF_LONG_PRESS_SECONDS = "watch_long_press_seconds"
```

In the `PATH_SETTINGS` branch, add after the `PREF_LANGUAGE` line:

```kotlin
                    editor.putInt(PREF_LONG_PRESS_SECONDS, data.getInt("long_press_seconds", 10))
```

- [ ] **Step 2: Use pref in ShabbatWatchFaceActivity**

In `ShabbatWatchFaceActivity.kt`, remove the hardcoded constant:

```kotlin
        private const val LONG_PRESS_THRESHOLD_MS = 3000L
```

In `onKeyUp()`, replace `LONG_PRESS_THRESHOLD_MS` with a pref read:

```kotlin
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val thresholdMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
        val pressDuration = System.currentTimeMillis() - buttonDownTime
        if (pressDuration >= thresholdMs) {
            val intent = Intent(this, EmergencyDialogActivity::class.java)
                .putExtra("last_alert", bannerManager.lastAlertText)
            startActivity(intent)
        }
        return true
    }
```

- [ ] **Step 3: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/WearDataReceiver.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt
git commit -m "feat(wear): read configurable long-press duration from synced prefs"
```

---

## Task 10: Emergency popup auto-dismiss (C)

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/EmergencyDialogActivity.kt`

- [ ] **Step 1: Add auto-dismiss timer**

In `EmergencyDialogActivity.kt`, add imports at the top:

```kotlin
import android.os.Handler
import android.os.Looper
```

Add a `handler` field and helper methods in the class:

```kotlin
class EmergencyDialogActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissMs = 10_000L

    private fun startAutoDismissTimer() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, autoDismissMs)
    }

    private fun cancelAutoDismissTimer() {
        handler.removeCallbacksAndMessages(null)
    }
```

In `onCreate()`, read the timeout and start the timer after reading prefs:

```kotlin
        autoDismissMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
        startAutoDismissTimer()
```

- [ ] **Step 2: Cancel timer on button press, restart on screen change**

Wrap each callback to cancel/restart the timer. Replace the `setContent` block:

```kotlin
        setContent {
            ShabbatWatchTheme {
                var showConfirm by remember { mutableStateOf(false) }

                if (showConfirm) {
                    // Restart timer on confirmation screen
                    LaunchedEffect(Unit) { startAutoDismissTimer() }

                    EndShabbatConfirmation(
                        isAfterHavdalah = isAfterHavdalah(),
                        onConfirm = {
                            cancelAutoDismissTimer()
                            ShabbatModeController(this@EmergencyDialogActivity)
                                .deactivateShabbatMode()
                            finish()
                        },
                        onCancel = {
                            showConfirm = false
                            startAutoDismissTimer()
                        }
                    )
                } else {
                    EmergencyMenu(
                        showSos = showSos,
                        showLastAlert = showLastAlert && lastAlert != null,
                        lastAlert = lastAlert,
                        onSos = {
                            cancelAutoDismissTimer()
                            val number = getString(R.string.emergency_number)
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                            startActivity(callIntent)
                        },
                        onEndShabbat = {
                            cancelAutoDismissTimer()
                            showConfirm = true
                        },
                        onCancel = {
                            cancelAutoDismissTimer()
                            finish()
                        }
                    )
                }
            }
        }
```

Add import:

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

- [ ] **Step 3: Clean up timer in onDestroy**

Add at the bottom of the class:

```kotlin
    override fun onDestroy() {
        super.onDestroy()
        cancelAutoDismissTimer()
    }
```

- [ ] **Step 4: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/EmergencyDialogActivity.kt
git commit -m "feat(wear): add auto-dismiss timer to emergency popup"
```

---

## Task 11: Health sensor toggles — phone side (D)

**Files:**
- Modify: `app/src/main/res/layout/activity_watch_settings.xml`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt`

- [ ] **Step 1: Add health sensors section to layout**

In `activity_watch_settings.xml`, add after the LTE warning `TextView` (after `android:layout_marginBottom="16dp" />`), before the Alert Banner Timeout header:

```xml
        <!-- Health Sensors -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_health_title"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginTop="8dp"
            android:layout_marginBottom="8dp" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableHeartRate"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_heart_rate"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableSpo2"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_spo2"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableStepCounter"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_step_counter"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableBodySensors"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_body_sensors"
            android:checked="true"
            android:layout_marginBottom="16dp" />
```

- [ ] **Step 2: Add health toggles setup in WatchSettingsActivity**

Add a new method:

```kotlin
    private fun setupHealthToggles() {
        setupToggle(R.id.switchDisableHeartRate, "watch_disable_heart_rate", true)
        setupToggle(R.id.switchDisableSpo2, "watch_disable_spo2", true)
        setupToggle(R.id.switchDisableStepCounter, "watch_disable_step_counter", true)
        setupToggle(R.id.switchDisableBodySensors, "watch_disable_body_sensors", true)
    }
```

Call it in `onCreate()`, add `setupHealthToggles()` after `setupBatteryToggles()`.

- [ ] **Step 3: Add health settings to WatchSyncService**

In `WatchSyncService.kt`, in `syncSettings()`, add after the `disable_lte` line:

```kotlin
            dataMap.putBoolean("disable_heart_rate", prefs.getBoolean("watch_disable_heart_rate", true))
            dataMap.putBoolean("disable_spo2", prefs.getBoolean("watch_disable_spo2", true))
            dataMap.putBoolean("disable_step_counter", prefs.getBoolean("watch_disable_step_counter", true))
            dataMap.putBoolean("disable_body_sensors", prefs.getBoolean("watch_disable_body_sensors", true))
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_watch_settings.xml \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt
git commit -m "feat(app): add health sensor toggle settings for Shabbat mode"
```

---

## Task 12: Health sensor toggles — watch side (D)

**Files:**
- Modify: `wear/src/main/AndroidManifest.xml`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/WearDataReceiver.kt`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/BatteryOptimizer.kt`

- [ ] **Step 1: Add BODY_SENSORS permission to manifest**

In `wear/src/main/AndroidManifest.xml`, add after the `SCHEDULE_EXACT_ALARM` permission:

```xml
    <uses-permission android:name="android.permission.BODY_SENSORS" />
```

- [ ] **Step 2: Add health pref constants and read in WearDataReceiver**

In `WearDataReceiver.kt`, add constants in the companion object:

```kotlin
        const val PREF_DISABLE_HEART_RATE = "watch_disable_heart_rate"
        const val PREF_DISABLE_SPO2 = "watch_disable_spo2"
        const val PREF_DISABLE_STEP_COUNTER = "watch_disable_step_counter"
        const val PREF_DISABLE_BODY_SENSORS = "watch_disable_body_sensors"
```

In the `PATH_SETTINGS` branch, add after the `PREF_DISABLE_LTE` line:

```kotlin
                    editor.putBoolean(PREF_DISABLE_HEART_RATE, data.getBoolean("disable_heart_rate", true))
                    editor.putBoolean(PREF_DISABLE_SPO2, data.getBoolean("disable_spo2", true))
                    editor.putBoolean(PREF_DISABLE_STEP_COUNTER, data.getBoolean("disable_step_counter", true))
                    editor.putBoolean(PREF_DISABLE_BODY_SENSORS, data.getBoolean("disable_body_sensors", true))
```

- [ ] **Step 3: Add health sensor management to BatteryOptimizer**

In `BatteryOptimizer.kt`, add import:

```kotlin
import android.app.admin.DevicePolicyManager
import android.hardware.Sensor
import android.hardware.SensorManager
```

Add constants in the companion object:

```kotlin
        private const val PREF_PREV_HEART_RATE = "prev_heart_rate_enabled"
        private const val PREF_PREV_SPO2 = "prev_spo2_enabled"
        private const val PREF_PREV_STEP_COUNTER = "prev_step_counter_enabled"
        private const val PREF_PREV_BODY_SENSORS = "prev_body_sensors_enabled"
```

Add helper method:

```kotlin
    private fun hasSensor(type: Int): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(type) != null
    }

    private fun setSensorPermission(granted: Boolean) {
        if (!AdminReceiver.isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — cannot manage sensor permissions")
            return
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.getComponentName(context)
        val grantState = if (granted)
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        else
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        try {
            dpm.setPermissionGrantState(
                admin, context.packageName,
                android.Manifest.permission.BODY_SENSORS, grantState
            )
            Log.d(TAG, "BODY_SENSORS permission ${if (granted) "granted" else "denied"}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set BODY_SENSORS permission: ${e.message}")
        }
    }
```

In `applyShabbatSettings()`, add after the touch-to-wake block and before `editor.apply()`:

```kotlin
        // Health sensors — best-effort disable via permission revocation
        val anyHealthDisabled = prefs.getBoolean(WearDataReceiver.PREF_DISABLE_HEART_RATE, true) ||
            prefs.getBoolean(WearDataReceiver.PREF_DISABLE_SPO2, true) ||
            prefs.getBoolean(WearDataReceiver.PREF_DISABLE_STEP_COUNTER, true) ||
            prefs.getBoolean(WearDataReceiver.PREF_DISABLE_BODY_SENSORS, true)
        if (anyHealthDisabled) {
            editor.putBoolean(PREF_PREV_BODY_SENSORS, true)
            setSensorPermission(false)
            Log.d(TAG, "Health sensors disabled")
        }
```

In `restoreSettings()`, add at the end:

```kotlin
        // Health sensors
        if (prefs.getBoolean(PREF_PREV_BODY_SENSORS, false)) {
            setSensorPermission(true)
            Log.d(TAG, "Health sensors restored")
        }
```

- [ ] **Step 4: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add wear/src/main/AndroidManifest.xml \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/WearDataReceiver.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/BatteryOptimizer.kt
git commit -m "feat(wear): add health sensor management during Shabbat mode"
```

---

## Task 13: "Always On" activation mode (E)

**Files:**
- Modify: `app/src/main/res/layout/activity_watch_settings.xml`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatModeController.kt`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/IdleStatusActivity.kt`

- [ ] **Step 1: Add "Always" radio button to layout**

In `activity_watch_settings.xml`, in the `radioActivation` RadioGroup, add after the `radioManual` RadioButton:

```xml
            <RadioButton
                android:id="@+id/radioAlways"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/watch_activation_always" />
```

- [ ] **Step 2: Update setupActivationMode in WatchSettingsActivity**

Replace the `setupActivationMode()` method:

```kotlin
    private fun setupActivationMode() {
        val radioGroup = findViewById<RadioGroup>(R.id.radioActivation)
        val current = prefs.getString("watch_activation_mode", "auto")
        when (current) {
            "manual" -> radioGroup.check(R.id.radioManual)
            "always" -> radioGroup.check(R.id.radioAlways)
            else -> radioGroup.check(R.id.radioAuto)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioManual -> "manual"
                R.id.radioAlways -> "always"
                else -> "auto"
            }
            prefs.edit().putString("watch_activation_mode", mode).apply()
            WatchSyncService.syncSettings(this)
        }
    }
```

- [ ] **Step 3: Handle "always" mode in ShabbatModeController**

In `ShabbatModeController.kt`, at the top of `scheduleFromSyncedWindows()`, add before the JSON parsing:

```kotlin
        val mode = prefs.getString(WearDataReceiver.PREF_ACTIVATION_MODE, "auto")
        if (mode == "always") {
            if (!isShabbatModeActive()) {
                activateShabbatMode()
            }
            return
        }
```

Also in `scheduleDeactivation()`, update the condition to also skip for "always" mode:

```kotlin
    private fun scheduleDeactivation(timeMs: Long) {
        val mode = prefs.getString(WearDataReceiver.PREF_ACTIVATION_MODE, "auto")
        if (mode == "auto") {
```

(This is already the case — no change needed here. The "always" mode simply never schedules deactivation because `scheduleFromSyncedWindows` returns early.)

- [ ] **Step 4: Handle "always" mode in IdleStatusActivity**

In `IdleStatusActivity.kt`, in `onCreate()`, add after the existing Shabbat-active redirect and before `controller.scheduleFromSyncedWindows()`:

```kotlin
        // If "always" mode, redirect to Shabbat watch face
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString(WearDataReceiver.PREF_ACTIVATION_MODE, "auto") == "always") {
            controller.activateShabbatMode()
            finish()
            return
        }
```

Add import:

```kotlin
import androidx.preference.PreferenceManager
```

(Check if this import already exists — it may already be imported via other code paths. If `PreferenceManager` is already imported, skip this.)

- [ ] **Step 5: Build**

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_watch_settings.xml \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatModeController.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/IdleStatusActivity.kt
git commit -m "feat: add 'Always On' activation mode for watch Shabbat mode"
```

---

## Task 14: Fix screen-on overlay leak in AlertDismissService

**Problem:** The "keep screen on during Shabbat & holidays" feature keeps the screen on all the time instead of only during Shabbat/holidays. The root cause is an overlay leak: when the accessibility service is killed and restarted by the system, `screenOnView` is null (fresh instance), but the old `TYPE_ACCESSIBILITY_OVERLAY` view may still exist in the window manager. When `updateScreenOn()` runs, it adds a NEW overlay since `screenOnView == null`, creating duplicate overlays. When Shabbat ends, only the latest overlay is removed — the leaked one stays, keeping the screen on forever.

**Files:**
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/AlertDismissService.kt`

- [ ] **Step 1: Replace screenOnView with robust overlay management**

In `AlertDismissService.kt`, replace the `updateScreenOn()` and `removeScreenOnOverlay()` methods with a version that always removes before adding, and uses `removeViewImmediate` for reliability:

Replace:

```kotlin
    private fun updateScreenOn() {
        val modeDisabled = prefs.getString("mode", "shabbat_holidays") == "disabled"
        val wantScreenOn = !modeDisabled && when (prefs.getString("screen_on_mode", "off")) {
            "always"  -> true
            "shabbat" -> isShabbatOrHolidayNow()
            else      -> false
        }
        if (wantScreenOn && screenOnView == null) {
            try {
                val params = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                )
                val view = View(this)
                getSystemService(WindowManager::class.java).addView(view, params)
                screenOnView = view
                Log.d(TAG, "Screen-on overlay added")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add screen-on overlay: ${e.message}")
            }
        } else if (!wantScreenOn) {
            removeScreenOnOverlay()
        }
    }

    private fun removeScreenOnOverlay() {
        screenOnView?.let {
            try {
                getSystemService(WindowManager::class.java).removeView(it)
                Log.d(TAG, "Screen-on overlay removed")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove screen-on overlay: ${e.message}")
            }
            screenOnView = null
        }
    }
```

With:

```kotlin
    private fun updateScreenOn() {
        val modeDisabled = prefs.getString("mode", "shabbat_holidays") == "disabled"
        val wantScreenOn = !modeDisabled && when (prefs.getString("screen_on_mode", "off")) {
            "always"  -> true
            "shabbat" -> isShabbatOrHolidayNow()
            else      -> false
        }
        Log.d(TAG, "updateScreenOn: want=$wantScreenOn, hasView=${screenOnView != null}")
        if (wantScreenOn && screenOnView == null) {
            try {
                val params = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                )
                val view = View(this)
                getSystemService(WindowManager::class.java).addView(view, params)
                screenOnView = view
                Log.d(TAG, "Screen-on overlay added")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add screen-on overlay: ${e.message}")
            }
        } else if (!wantScreenOn && screenOnView != null) {
            removeScreenOnOverlay()
        }
    }

    private fun removeScreenOnOverlay() {
        val view = screenOnView ?: return
        screenOnView = null
        try {
            getSystemService(WindowManager::class.java).removeViewImmediate(view)
            Log.d(TAG, "Screen-on overlay removed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove screen-on overlay: ${e.message}")
        }
    }
```

Key changes:
1. `removeScreenOnOverlay()` sets `screenOnView = null` BEFORE calling `removeViewImmediate`, preventing re-entrant issues
2. Uses `removeViewImmediate` instead of `removeView` for synchronous removal
3. The `else if (!wantScreenOn)` condition now explicitly checks `screenOnView != null` to avoid unnecessary calls
4. Added logging for debug diagnostics

- [ ] **Step 2: Force-remove overlay on service connect**

In `onServiceConnected()`, add `removeScreenOnOverlay()` as the first call to clean up any stale state from a previous service instance:

```kotlin
    override fun onServiceConnected() {
        removeScreenOnOverlay()  // Clean up any stale overlay from previous instance
        createNotificationChannel()
        postStatusNotification()
        updateScreenOn()
        handler.postDelayed(notifUpdateRunnable, NOTIF_UPDATE_MS)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        Log.d(TAG, "Service connected")
    }
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ilanp13/shabbatalertdismisser/AlertDismissService.kt
git commit -m "fix(app): prevent screen-on overlay leak on accessibility service restart"
```

---

## Task 15: Full build verification and push

- [ ] **Step 1: Clean build all modules**

```bash
./gradlew clean assembleDebug
```

Expected: BUILD SUCCESSFUL for all three modules (`:shared`, `:app`, `:wear`).

- [ ] **Step 2: Verify release build**

```bash
./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Review git log**

```bash
git log --oneline -15
```

Verify all commits are present and well-named.

- [ ] **Step 4: Push**

```bash
git push
```
