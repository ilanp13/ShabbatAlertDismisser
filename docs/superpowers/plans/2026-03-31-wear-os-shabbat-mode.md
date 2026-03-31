# Wear OS Shabbat Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Wear OS watch module to the existing Shabbat Alert Dismisser app that locks the watch into a dedicated Shabbat/holiday display mode.

**Architecture:** Multi-module Gradle project with `:shared` (extracted Shabbat logic), `:app` (existing phone app + new watch settings), and `:wear` (new Wear OS app). Phone syncs schedule to watch via Wear Data Layer API. Watch locks into kiosk mode using Lock Task Mode.

**Tech Stack:** Kotlin, Wear OS 4+, Compose for Wear OS, Wear Data Layer API, Lock Task Mode (Device Policy Manager)

**Spec:** `docs/superpowers/specs/2026-03-31-wear-os-shabbat-mode-design.md`

**Critical constraint:** No regressions to existing phone app behavior. All changes to `:app` are purely additive.

**Known deferred items (to be addressed in follow-up tasks):**
- Ambient mode integration with `AmbientLifecycleObserver` (currently `isAmbient` is always false)
- Watch → Phone status reporting (`/watch-status` path with battery level, mode status)
- GPS and LTE radio disable/restore in `BatteryOptimizer` (toggles exist but hardware control requires further research)
- `ScreenScaffold` for proper round/square display adaptation
- Whitelisted apps UI in `WatchSettingsActivity` (picking from installed apps on watch)
- Conditional sync on app launch (sync if last sync > 1 hour ago)

---

## File Structure

### New files to create

```
shared/
├── build.gradle.kts
└── src/main/java/com/ilanp13/shabbatalertdismisser/shared/
    ├── ShabbatCalculator.kt      # moved from :app (package updated)
    ├── HolidayCalculator.kt      # moved from :app (package updated)
    ├── HebcalService.kt          # moved from :app (package updated)
    └── MinhagProfiles.kt         # moved from :app (package updated)

wear/
├── build.gradle.kts
├── src/main/AndroidManifest.xml
├── src/main/res/
│   ├── values/strings.xml
│   ├── values-iw/strings.xml
│   └── xml/wear.xml              # capability declaration
└── src/main/java/com/ilanp13/shabbatalertdismisser/wear/
    ├── WearDataReceiver.kt       # receives synced data from phone
    ├── ShabbatModeController.kt  # manages Lock Task Mode lifecycle
    ├── AdminReceiver.kt          # DeviceAdminReceiver for device owner
    ├── BatteryOptimizer.kt       # toggles radios/sensors
    ├── NotificationFilterService.kt  # filters notifications to whitelist
    ├── AlertBannerManager.kt     # manages alert banner display state
    ├── ShabbatWatchFaceActivity.kt   # locked Shabbat mode screen
    ├── EmergencyDialogActivity.kt    # long-press emergency popup
    ├── IdleStatusActivity.kt         # non-Shabbat status screen
    └── ui/
        ├── theme/Theme.kt        # Shabbat mode colors
        ├── ShabbatFace.kt        # main watch face composable
        ├── AnalogClock.kt        # analog clock composable
        ├── DigitalClock.kt       # digital clock composable
        └── AlertBanner.kt        # emergency alert banner composable

app/src/main/java/com/ilanp13/shabbatalertdismisser/
├── WatchSettingsActivity.kt      # new: watch configuration screen
├── WatchSyncService.kt           # new: syncs data to watch
└── res/
    ├── layout/activity_watch_settings.xml
    └── xml/wear.xml              # capability for phone side
```

### Existing files to modify

```
settings.gradle.kts               # add :shared, :wear
build.gradle.kts                   # add compose compiler plugin
app/build.gradle.kts               # add :shared dep + wearable API
app/src/main/res/layout/fragment_settings.xml  # add watch settings button
app/src/main/res/values/strings.xml            # add watch-related strings
app/src/main/res/values-iw/strings.xml         # add Hebrew watch strings

# Import updates only (add shared.* imports):
app/src/main/java/.../SettingsFragment.kt
app/src/main/java/.../StatusFragment.kt
app/src/main/java/.../AlertDismissService.kt
app/src/main/java/.../CalendarFragment.kt
app/src/main/java/.../BootReceiver.kt
```

---

## Task 1: Create `:shared` module and move Shabbat logic

**Files:**
- Create: `shared/build.gradle.kts`
- Create: `shared/src/main/java/com/ilanp13/shabbatalertdismisser/shared/ShabbatCalculator.kt`
- Create: `shared/src/main/java/com/ilanp13/shabbatalertdismisser/shared/HolidayCalculator.kt`
- Create: `shared/src/main/java/com/ilanp13/shabbatalertdismisser/shared/HebcalService.kt`
- Create: `shared/src/main/java/com/ilanp13/shabbatalertdismisser/shared/MinhagProfiles.kt`
- Modify: `settings.gradle.kts`
- Delete: `app/src/main/java/com/ilanp13/shabbatalertdismisser/ShabbatCalculator.kt`
- Delete: `app/src/main/java/com/ilanp13/shabbatalertdismisser/HolidayCalculator.kt`
- Delete: `app/src/main/java/com/ilanp13/shabbatalertdismisser/HebcalService.kt`
- Delete: `app/src/main/java/com/ilanp13/shabbatalertdismisser/MinhagProfiles.kt`

- [ ] **Step 1: Create shared module directory structure**

```bash
mkdir -p shared/src/main/java/com/ilanp13/shabbatalertdismisser/shared
```

- [ ] **Step 2: Create `shared/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ilanp13.shabbatalertdismisser.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
```

- [ ] **Step 3: Update `settings.gradle.kts` to include `:shared`**

Change line 17 from:
```kotlin
include(":app")
```
to:
```kotlin
include(":app", ":shared")
```

- [ ] **Step 4: Copy and update package for each shared file**

For each of the 4 files (`ShabbatCalculator.kt`, `HolidayCalculator.kt`, `HebcalService.kt`, `MinhagProfiles.kt`):
- Copy from `app/src/main/java/com/ilanp13/shabbatalertdismisser/` to `shared/src/main/java/com/ilanp13/shabbatalertdismisser/shared/`
- Change the package declaration from `package com.ilanp13.shabbatalertdismisser` to `package com.ilanp13.shabbatalertdismisser.shared`

In `HolidayCalculator.kt`, no import changes needed because `ShabbatCalculator` will be in the same `shared` package.

- [ ] **Step 5: Delete originals from `:app`**

```bash
rm app/src/main/java/com/ilanp13/shabbatalertdismisser/ShabbatCalculator.kt
rm app/src/main/java/com/ilanp13/shabbatalertdismisser/HolidayCalculator.kt
rm app/src/main/java/com/ilanp13/shabbatalertdismisser/HebcalService.kt
rm app/src/main/java/com/ilanp13/shabbatalertdismisser/MinhagProfiles.kt
```

- [ ] **Step 6: Add `:shared` dependency to `app/build.gradle.kts`**

Add to the `dependencies` block in `app/build.gradle.kts`:
```kotlin
implementation(project(":shared"))
```

- [ ] **Step 7: Add shared imports to all consumer files**

Add these imports to each file that uses the shared classes. Since all files are in `com.ilanp13.shabbatalertdismisser`, they previously needed no imports for same-package classes. Now they need explicit imports.

**`SettingsFragment.kt`** — add after existing imports:
```kotlin
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
```

**`StatusFragment.kt`** — add after existing imports:
```kotlin
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.ShabbatCalculator
```

**`AlertDismissService.kt`** — add after existing imports:
```kotlin
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.HolidayCalculator
import com.ilanp13.shabbatalertdismisser.shared.ShabbatCalculator
```

**`CalendarFragment.kt`** — add after existing imports:
```kotlin
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.HolidayCalculator
import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
import com.ilanp13.shabbatalertdismisser.shared.ShabbatCalculator
```

**`BootReceiver.kt`** — add after existing imports:
```kotlin
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
```

Note: Check each file for actual usage — only add the imports that the file actually references. Use the build output to catch any missed imports.

- [ ] **Step 8: Build and verify no regressions**

```bash
cd /Users/ilan.peretz/dev/personal/shabbat-alert-dismisser
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The phone app compiles with all shared classes resolved via `:shared` module.

- [ ] **Step 9: Commit**

```bash
git add shared/ settings.gradle.kts app/build.gradle.kts \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/SettingsFragment.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/StatusFragment.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/AlertDismissService.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/CalendarFragment.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/BootReceiver.kt
git rm app/src/main/java/com/ilanp13/shabbatalertdismisser/ShabbatCalculator.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/HolidayCalculator.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/HebcalService.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/MinhagProfiles.kt
git commit -m "refactor: extract shared Shabbat logic into :shared module"
```

---

## Task 2: Set up `:wear` module scaffold

**Files:**
- Create: `wear/build.gradle.kts`
- Create: `wear/src/main/AndroidManifest.xml`
- Create: `wear/src/main/res/values/strings.xml`
- Create: `wear/src/main/res/values-iw/strings.xml`
- Create: `wear/src/main/res/xml/wear.xml`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts` (root)

- [ ] **Step 1: Create wear module directory structure**

```bash
mkdir -p wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/theme
mkdir -p wear/src/main/res/values-iw
mkdir -p wear/src/main/res/xml
```

- [ ] **Step 2: Add compose compiler plugin to root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.github.triplet.play") version "3.11.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

- [ ] **Step 3: Update `settings.gradle.kts`**

Change:
```kotlin
include(":app", ":shared")
```
to:
```kotlin
include(":app", ":shared", ":wear")
```

- [ ] **Step 4: Create `wear/build.gradle.kts`**

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
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))

    // Wear OS
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Compose for Wear OS
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha29")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
```

- [ ] **Step 5: Create `wear/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CALL_PHONE" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">

        <activity
            android:name=".IdleStatusActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ShabbatWatchFaceActivity"
            android:exported="false"
            android:launchMode="singleTask"
            android:lockTaskMode="if_whitelisted" />

        <activity
            android:name=".EmergencyDialogActivity"
            android:exported="false"
            android:theme="@android:style/Theme.DeviceDefault.Dialog" />

        <receiver
            android:name=".AdminReceiver"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin" />
        </receiver>

        <service
            android:name=".WearDataReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/shabbat-schedule" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/watch-settings" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:pathPrefix="/unlock-shabbat-mode" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

- [ ] **Step 6: Create `wear/src/main/res/xml/wear.xml`** (capability declaration)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="android_wear_capabilities">
        <item>shabbat_watch_app</item>
    </string-array>
</resources>
```

- [ ] **Step 7: Create `wear/src/main/res/xml/device_admin.xml`**

```bash
mkdir -p wear/src/main/res/xml
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin>
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
```

- [ ] **Step 8: Create initial wear strings**

**`wear/src/main/res/values/strings.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Shabbat Watch</string>
    <string name="shabbat_shalom">Shabbat Shalom</string>
    <string name="chag_sameach">Chag %s</string>
    <string name="havdalah_label">Ends: %s</string>
    <string name="havdalah_motzash">Motzei Shabbat: %s</string>
    <string name="havdalah_motzei_chag">Motzei Chag: %s</string>
    <string name="parasha_label">Parashat %s</string>
    <string name="next_candle_lighting">Next: %s</string>
    <string name="shabbat_mode_active">Shabbat Mode Active</string>
    <string name="activate_early">Enter Shabbat Mode</string>
    <string name="emergency_title">Emergency</string>
    <string name="emergency_sos">SOS Call</string>
    <string name="emergency_last_alert">Last Alert</string>
    <string name="emergency_end_shabbat">End Shabbat Mode</string>
    <string name="emergency_cancel">Cancel</string>
    <string name="emergency_confirm_title">End Shabbat Mode?</string>
    <string name="emergency_confirm_early">Shabbat/holiday hasn\'t ended yet. Exit Shabbat mode?</string>
    <string name="emergency_confirm_yes">Yes, exit</string>
    <string name="emergency_confirm_no">No, stay</string>
    <string name="no_schedule">No schedule synced</string>
    <string name="sync_from_phone">Open phone app to sync</string>
</resources>
```

**`wear/src/main/res/values-iw/strings.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">שעון שבת</string>
    <string name="shabbat_shalom">שבת שלום</string>
    <string name="chag_sameach">חג %s</string>
    <string name="havdalah_label">יציאה: %s</string>
    <string name="havdalah_motzash">מוצ״ש: %s</string>
    <string name="havdalah_motzei_chag">מוצאי חג: %s</string>
    <string name="parasha_label">פרשת %s</string>
    <string name="next_candle_lighting">הבא: %s</string>
    <string name="shabbat_mode_active">מצב שבת פעיל</string>
    <string name="activate_early">כניסה למצב שבת</string>
    <string name="emergency_title">חירום</string>
    <string name="emergency_sos">שיחת חירום</string>
    <string name="emergency_last_alert">התראה אחרונה</string>
    <string name="emergency_end_shabbat">יציאה ממצב שבת</string>
    <string name="emergency_cancel">ביטול</string>
    <string name="emergency_confirm_title">לצאת ממצב שבת?</string>
    <string name="emergency_confirm_early">שבת/חג עדיין לא הסתיימו. לצאת ממצב שבת?</string>
    <string name="emergency_confirm_yes">כן, לצאת</string>
    <string name="emergency_confirm_no">לא, להישאר</string>
    <string name="no_schedule">לא סונכרנו זמנים</string>
    <string name="sync_from_phone">פתח את האפליקציה בטלפון לסנכרון</string>
</resources>
```

- [ ] **Step 9: Build wear module**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL (empty app with manifest, no activities yet).

- [ ] **Step 10: Commit**

```bash
git add wear/ settings.gradle.kts build.gradle.kts
git commit -m "feat: scaffold :wear module for Wear OS Shabbat app"
```

---

## Task 3: Wear OS theme and Compose UI components

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/theme/Theme.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/DigitalClock.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AnalogClock.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AlertBanner.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/ShabbatFace.kt`

- [ ] **Step 1: Create Theme.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ColorScheme

val ShabbatGold = Color(0xFFD4AF37)
val ShabbatWhite = Color(0xFFEEEEEE)
val ShabbatDarkBg = Color(0xFF111111)
val ShabbatAmbientGray = Color(0xFF888888)
val AlertRed = Color(0xFFFF4444)

@Composable
fun ShabbatWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = ShabbatGold,
            onPrimary = ShabbatDarkBg,
            background = ShabbatDarkBg,
            onBackground = ShabbatWhite,
            surface = ShabbatDarkBg,
            onSurface = ShabbatWhite,
        ),
        content = content
    )
}
```

- [ ] **Step 2: Create DigitalClock.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DigitalClock(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    isAmbient: Boolean = false
) {
    var time by remember { mutableStateOf(formatTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            time = formatTime()
            // Update every second in active, every minute in ambient
            delay(if (isAmbient) 60_000L else 1_000L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = time,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            color = color
        )
    }
}

private fun formatTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
```

- [ ] **Step 3: Create AnalogClock.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatGold
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogClock(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    accentColor: Color = ShabbatGold,
    isAmbient: Boolean = false
) {
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            calendar = Calendar.getInstance()
            delay(if (isAmbient) 60_000L else 1_000L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // Hour markers
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30 - 90).toDouble())
                val innerR = radius * 0.85f
                val outerR = radius * 0.95f
                drawLine(
                    color = color.copy(alpha = 0.5f),
                    start = Offset(
                        center.x + (innerR * cos(angle)).toFloat(),
                        center.y + (innerR * sin(angle)).toFloat()
                    ),
                    end = Offset(
                        center.x + (outerR * cos(angle)).toFloat(),
                        center.y + (outerR * sin(angle)).toFloat()
                    ),
                    strokeWidth = if (i % 3 == 0) 3f else 1.5f
                )
            }

            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)

            // Hour hand
            val hourAngle = Math.toRadians(((hour + minute / 60.0) * 30 - 90))
            drawHand(center, hourAngle, radius * 0.5f, color, 4f)

            // Minute hand
            val minuteAngle = Math.toRadians((minute * 6 - 90).toDouble())
            drawHand(center, minuteAngle, radius * 0.7f, color, 2.5f)

            // Second hand (only in active mode)
            if (!isAmbient) {
                val secondAngle = Math.toRadians((second * 6 - 90).toDouble())
                drawHand(center, secondAngle, radius * 0.75f, accentColor, 1f)
            }

            // Center dot
            drawCircle(color = accentColor, radius = 4f, center = center)
        }
    }
}

private fun DrawScope.drawHand(
    center: Offset,
    angle: Double,
    length: Float,
    color: Color,
    width: Float
) {
    drawLine(
        color = color,
        start = center,
        end = Offset(
            center.x + (length * cos(angle)).toFloat(),
            center.y + (length * sin(angle)).toFloat()
        ),
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}
```

- [ ] **Step 4: Create AlertBanner.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.AlertRed

@Composable
fun AlertBanner(
    alertText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertRed.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = alertText,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}
```

- [ ] **Step 5: Create ShabbatFace.kt** (main watch face composable)

```kotlin
package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatGold
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWhite
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatAmbientGray

@Composable
fun ShabbatFace(
    indicator: String,       // "שבת שלום" or holiday name
    hebrewDate: String,      // "כ״ה אדר תשפ״ו"
    parasha: String?,        // "פרשת ויקרא"
    havdalahTime: String,    // "מוצ״ש: 19:42"
    alertText: String?,      // active emergency alert text, or null
    useAnalog: Boolean,
    isAmbient: Boolean
) {
    val textColor = if (isAmbient) ShabbatAmbientGray else ShabbatWhite
    val accentColor = if (isAmbient) ShabbatAmbientGray else ShabbatGold

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Shabbat/holiday indicator
            Text(
                text = indicator,
                fontSize = 14.sp,
                color = accentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Center: Clock
            if (useAnalog) {
                AnalogClock(
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    accentColor = accentColor,
                    isAmbient = isAmbient
                )
            } else {
                DigitalClock(
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    isAmbient = isAmbient
                )
            }

            // Bottom: Info block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = hebrewDate,
                    fontSize = 12.sp,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                if (parasha != null) {
                    Text(
                        text = parasha,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = havdalahTime,
                    fontSize = 12.sp,
                    color = accentColor,
                    textAlign = TextAlign.Center
                )
            }

            // Alert banner (only when active)
            if (alertText != null) {
                AlertBanner(
                    alertText = alertText,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 6: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/
git commit -m "feat(wear): add Compose UI components for Shabbat watch face"
```

---

## Task 4: Wear data receiver and local storage

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/WearDataReceiver.kt`

- [ ] **Step 1: Create WearDataReceiver.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Receives data synced from the phone app via Wear Data Layer API.
 * Stores schedule and settings in local SharedPreferences.
 */
class WearDataReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataReceiver"
        const val PATH_SCHEDULE = "/shabbat-schedule"
        const val PATH_SETTINGS = "/watch-settings"
        const val PATH_UNLOCK = "/unlock-shabbat-mode"

        // SharedPreferences keys
        const val PREF_SCHEDULE_JSON = "watch_schedule_json"
        const val PREF_FACE_STYLE = "watch_face_style"         // "analog" or "digital"
        const val PREF_ACTIVATION_MODE = "watch_activation_mode" // "auto" or "manual"
        const val PREF_OFFSET_BEFORE = "watch_offset_before_min"
        const val PREF_OFFSET_AFTER = "watch_offset_after_min"
        const val PREF_DISABLE_WIFI = "watch_disable_wifi"
        const val PREF_DISABLE_GPS = "watch_disable_gps"
        const val PREF_DISABLE_TILT_WAKE = "watch_disable_tilt_wake"
        const val PREF_DISABLE_TOUCH_WAKE = "watch_disable_touch_wake"
        const val PREF_DISABLE_LTE = "watch_disable_lte"
        const val PREF_WHITELISTED_PACKAGES = "watch_whitelisted_packages"
        const val PREF_BANNER_TIMEOUT_SEC = "watch_banner_timeout_sec"
        const val PREF_EMERGENCY_SOS = "watch_emergency_sos"
        const val PREF_EMERGENCY_LAST_ALERT = "watch_emergency_last_alert"
        const val PREF_LANGUAGE = "watch_language"
        const val PREF_LAST_SYNC_MS = "watch_last_sync_ms"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            val data = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                PATH_SCHEDULE -> {
                    val json = data.getString("schedule_json", "[]")
                    prefs.edit()
                        .putString(PREF_SCHEDULE_JSON, json)
                        .putLong(PREF_LAST_SYNC_MS, System.currentTimeMillis())
                        .apply()
                    Log.d(TAG, "Schedule synced: ${JSONArray(json).length()} windows")

                    // Notify ShabbatModeController to re-evaluate schedule
                    sendBroadcast(Intent("com.ilanp13.shabbatalertdismisser.wear.SCHEDULE_UPDATED").setPackage(packageName))
                }

                PATH_SETTINGS -> {
                    val editor = prefs.edit()
                    editor.putString(PREF_FACE_STYLE, data.getString("face_style", "digital"))
                    editor.putString(PREF_ACTIVATION_MODE, data.getString("activation_mode", "auto"))
                    editor.putInt(PREF_OFFSET_BEFORE, data.getInt("offset_before_min", 0))
                    editor.putInt(PREF_OFFSET_AFTER, data.getInt("offset_after_min", 0))
                    editor.putBoolean(PREF_DISABLE_WIFI, data.getBoolean("disable_wifi", true))
                    editor.putBoolean(PREF_DISABLE_GPS, data.getBoolean("disable_gps", true))
                    editor.putBoolean(PREF_DISABLE_TILT_WAKE, data.getBoolean("disable_tilt_wake", true))
                    editor.putBoolean(PREF_DISABLE_TOUCH_WAKE, data.getBoolean("disable_touch_wake", false))
                    editor.putBoolean(PREF_DISABLE_LTE, data.getBoolean("disable_lte", false))
                    editor.putString(PREF_WHITELISTED_PACKAGES, data.getString("whitelisted_packages", "[]"))
                    editor.putInt(PREF_BANNER_TIMEOUT_SEC, data.getInt("banner_timeout_sec", 30))
                    editor.putBoolean(PREF_EMERGENCY_SOS, data.getBoolean("emergency_sos", true))
                    editor.putBoolean(PREF_EMERGENCY_LAST_ALERT, data.getBoolean("emergency_last_alert", true))
                    editor.putString(PREF_LANGUAGE, data.getString("language", "iw"))
                    editor.apply()
                    Log.d(TAG, "Settings synced")
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_UNLOCK -> {
                Log.d(TAG, "Unlock command received from phone")
                sendBroadcast(Intent("com.ilanp13.shabbatalertdismisser.wear.UNLOCK_SHABBAT").setPackage(packageName))
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/WearDataReceiver.kt
git commit -m "feat(wear): add WearDataReceiver for phone-to-watch sync"
```

---

## Task 5: AdminReceiver, BatteryOptimizer, and ShabbatModeController

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/AdminReceiver.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/BatteryOptimizer.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatModeController.kt`

- [ ] **Step 1: Create AdminReceiver.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device admin receiver for Lock Task Mode.
 * Must be set as device owner via ADB:
 *   adb shell dpm set-device-owner com.ilanp13.shabbatalertdismisser.wear/.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, AdminReceiver::class.java)
        }

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as android.app.admin.DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.d(TAG, "Device admin enabled")
    }
}
```

- [ ] **Step 2: Create BatteryOptimizer.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Applies and restores battery-saving settings during Shabbat mode.
 * Stores previous state so it can be restored on deactivation.
 */
class BatteryOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "BatteryOptimizer"
        private const val PREF_PREV_WIFI = "prev_wifi_enabled"
        private const val PREF_PREV_TILT_WAKE = "prev_tilt_to_wake"
        private const val PREF_PREV_TOUCH_WAKE = "prev_touch_to_wake"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun applyShabbatSettings() {
        val editor = prefs.edit()

        // Wi-Fi
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_WIFI, true)) {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            editor.putBoolean(PREF_PREV_WIFI, wifiManager.isWifiEnabled)
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = false
            Log.d(TAG, "Wi-Fi disabled")
        }

        // Tilt-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TILT_WAKE, true)) {
            try {
                val prev = Settings.Global.getInt(
                    context.contentResolver, "tilt_to_wake", 1
                )
                editor.putInt(PREF_PREV_TILT_WAKE, prev)
                Settings.Global.putInt(context.contentResolver, "tilt_to_wake", 0)
                Log.d(TAG, "Tilt-to-wake disabled")
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable tilt-to-wake: ${e.message}")
            }
        }

        // Touch-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TOUCH_WAKE, false)) {
            try {
                val prev = Settings.Global.getInt(
                    context.contentResolver, "touch_to_wake", 1
                )
                editor.putInt(PREF_PREV_TOUCH_WAKE, prev)
                Settings.Global.putInt(context.contentResolver, "touch_to_wake", 0)
                Log.d(TAG, "Touch-to-wake disabled")
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable touch-to-wake: ${e.message}")
            }
        }

        editor.apply()
    }

    fun restoreSettings() {
        // Wi-Fi
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_WIFI, true)) {
            val prev = prefs.getBoolean(PREF_PREV_WIFI, true)
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = prev
            Log.d(TAG, "Wi-Fi restored to $prev")
        }

        // Tilt-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TILT_WAKE, true)) {
            try {
                val prev = prefs.getInt(PREF_PREV_TILT_WAKE, 1)
                Settings.Global.putInt(context.contentResolver, "tilt_to_wake", prev)
                Log.d(TAG, "Tilt-to-wake restored to $prev")
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore tilt-to-wake: ${e.message}")
            }
        }

        // Touch-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TOUCH_WAKE, false)) {
            try {
                val prev = prefs.getInt(PREF_PREV_TOUCH_WAKE, 1)
                Settings.Global.putInt(context.contentResolver, "touch_to_wake", prev)
                Log.d(TAG, "Touch-to-wake restored to $prev")
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore touch-to-wake: ${e.message}")
            }
        }
    }
}
```

- [ ] **Step 3: Create ShabbatModeController.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import org.json.JSONArray

/**
 * Manages the Shabbat mode lifecycle:
 * - Schedules activation/deactivation based on synced windows
 * - Enters/exits Lock Task Mode
 * - Activates DND and battery optimizations
 */
class ShabbatModeController(private val context: Context) {

    companion object {
        private const val TAG = "ShabbatModeCtrl"
        const val ACTION_ACTIVATE = "com.ilanp13.shabbatalertdismisser.wear.ACTIVATE_SHABBAT"
        const val ACTION_DEACTIVATE = "com.ilanp13.shabbatalertdismisser.wear.DEACTIVATE_SHABBAT"
        const val PREF_SHABBAT_MODE_ACTIVE = "shabbat_mode_active"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val batteryOptimizer = BatteryOptimizer(context)

    /**
     * Call after schedule is synced to schedule activation/deactivation alarms.
     */
    fun scheduleFromSyncedWindows() {
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        if (windows.isEmpty()) {
            Log.d(TAG, "No windows to schedule")
            return
        }

        val now = System.currentTimeMillis()
        val offsetBefore = prefs.getInt(WearDataReceiver.PREF_OFFSET_BEFORE, 0) * 60_000L
        val offsetAfter = prefs.getInt(WearDataReceiver.PREF_OFFSET_AFTER, 0) * 60_000L

        // Find the next relevant window
        val nextWindow = windows.find { it.havdalahMs + offsetAfter > now }
        if (nextWindow == null) {
            Log.d(TAG, "All windows are in the past")
            return
        }

        val activateTime = nextWindow.candleMs - offsetBefore
        val deactivateTime = nextWindow.havdalahMs + offsetAfter

        // Are we already inside a window?
        if (now >= activateTime && now < deactivateTime) {
            if (!isShabbatModeActive()) {
                activateShabbatMode()
            }
            scheduleDeactivation(deactivateTime)
        } else if (now < activateTime) {
            scheduleActivation(activateTime)
            scheduleDeactivation(deactivateTime)
        }

        Log.d(TAG, "Scheduled: activate=${activateTime}, deactivate=${deactivateTime}")
    }

    private fun scheduleActivation(timeMs: Long) {
        val intent = Intent(ACTION_ACTIVATE).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
    }

    private fun scheduleDeactivation(timeMs: Long) {
        val mode = prefs.getString(WearDataReceiver.PREF_ACTIVATION_MODE, "auto")
        if (mode == "auto") {
            val intent = Intent(ACTION_DEACTIVATE).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
        }
        // Manual mode: no deactivation alarm — user must unlock via phone or emergency dialog
    }

    fun activateShabbatMode() {
        Log.d(TAG, "Activating Shabbat mode")
        prefs.edit().putBoolean(PREF_SHABBAT_MODE_ACTIVE, true).apply()

        // Apply battery optimizations
        batteryOptimizer.applyShabbatSettings()

        // Activate DND
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not activate DND: ${e.message}")
        }

        // Launch locked Shabbat watch face
        val intent = Intent(context, ShabbatWatchFaceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    fun deactivateShabbatMode() {
        Log.d(TAG, "Deactivating Shabbat mode")
        prefs.edit().putBoolean(PREF_SHABBAT_MODE_ACTIVE, false).apply()

        // Restore battery settings
        batteryOptimizer.restoreSettings()

        // Restore DND
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore DND: ${e.message}")
        }

        // Stop Lock Task Mode (activity handles this)
        context.sendBroadcast(Intent("com.ilanp13.shabbatalertdismisser.wear.STOP_LOCK_TASK").setPackage(context.packageName))

        // Schedule next window
        scheduleFromSyncedWindows()
    }

    fun isShabbatModeActive(): Boolean {
        return prefs.getBoolean(PREF_SHABBAT_MODE_ACTIVE, false)
    }

    /**
     * Get current window info for display.
     * Returns (holidayName?, havdalahMs) or null if no active window.
     */
    fun getCurrentWindowInfo(): Pair<String?, Long>? {
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        val now = System.currentTimeMillis()
        val offsetBefore = prefs.getInt(WearDataReceiver.PREF_OFFSET_BEFORE, 0) * 60_000L
        val offsetAfter = prefs.getInt(WearDataReceiver.PREF_OFFSET_AFTER, 0) * 60_000L

        return windows.find { now in (it.candleMs - offsetBefore)..(it.havdalahMs + offsetAfter) }
            ?.let { Pair(it.parasha, it.havdalahMs) }
    }

    /**
     * Get the next upcoming window for idle screen display.
     */
    fun getNextWindowInfo(): Triple<Long, Long, String?>? {
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        val now = System.currentTimeMillis()
        val next = windows.find { it.havdalahMs > now } ?: return null
        return Triple(next.candleMs, next.havdalahMs, next.parasha)
    }
}

/**
 * BroadcastReceiver that handles activation/deactivation alarms.
 * Must be registered in the manifest.
 */
class ShabbatModeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = ShabbatModeController(context)
        when (intent.action) {
            ShabbatModeController.ACTION_ACTIVATE -> controller.activateShabbatMode()
            ShabbatModeController.ACTION_DEACTIVATE -> controller.deactivateShabbatMode()
            "com.ilanp13.shabbatalertdismisser.wear.SCHEDULE_UPDATED" -> controller.scheduleFromSyncedWindows()
            "com.ilanp13.shabbatalertdismisser.wear.UNLOCK_SHABBAT" -> controller.deactivateShabbatMode()
        }
    }
}
```

- [ ] **Step 4: Register the alarm receiver in `wear/src/main/AndroidManifest.xml`**

Add inside the `<application>` tag, before the closing `</application>`:

```xml
        <receiver
            android:name=".ShabbatModeAlarmReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.ilanp13.shabbatalertdismisser.wear.ACTIVATE_SHABBAT" />
                <action android:name="com.ilanp13.shabbatalertdismisser.wear.DEACTIVATE_SHABBAT" />
                <action android:name="com.ilanp13.shabbatalertdismisser.wear.SCHEDULE_UPDATED" />
                <action android:name="com.ilanp13.shabbatalertdismisser.wear.UNLOCK_SHABBAT" />
            </intent-filter>
        </receiver>
```

Also add the `SCHEDULE_EXACT_ALARM` permission at the top of the manifest:
```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

- [ ] **Step 5: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/AdminReceiver.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/BatteryOptimizer.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatModeController.kt \
    wear/src/main/AndroidManifest.xml
git commit -m "feat(wear): add ShabbatModeController, AdminReceiver, and BatteryOptimizer"
```

---

## Task 6: ShabbatWatchFaceActivity (locked Shabbat screen)

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt`
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/AlertBannerManager.kt`

- [ ] **Step 1: Create AlertBannerManager.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the current alert banner text and auto-dismiss timeout.
 */
class AlertBannerManager(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())

    private val _alertText = MutableStateFlow<String?>(null)
    val alertText: StateFlow<String?> = _alertText

    fun showAlert(text: String) {
        _alertText.value = text
        val timeoutSec = prefs.getInt(WearDataReceiver.PREF_BANNER_TIMEOUT_SEC, 30)
        if (timeoutSec > 0) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ _alertText.value = null }, timeoutSec * 1000L)
        }
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        _alertText.value = null
    }

    /** Store last alert text for emergency dialog "Show last alert" */
    var lastAlertText: String? = null
        private set

    fun onAlertReceived(text: String) {
        lastAlertText = text
        showAlert(text)
    }
}
```

- [ ] **Step 2: Create ShabbatWatchFaceActivity.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.ilanp13.shabbatalertdismisser.shared.HolidayCalculator
import com.ilanp13.shabbatalertdismisser.wear.ui.ShabbatFace
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ShabbatWatchFaceActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShabbatWatchFace"
        private const val LONG_PRESS_THRESHOLD_MS = 3000L
    }

    private lateinit var controller: ShabbatModeController
    private lateinit var bannerManager: AlertBannerManager
    private var buttonDownTime = 0L

    private val stopLockTaskReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            exitLockTaskAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ShabbatModeController(this)
        bannerManager = AlertBannerManager(this)

        // Keep screen on (always-on display)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enter Lock Task Mode if device owner
        enterLockTask()

        // Listen for stop lock task broadcast
        val filter = IntentFilter("com.ilanp13.shabbatalertdismisser.wear.STOP_LOCK_TASK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopLockTaskReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopLockTaskReceiver, filter)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useAnalog = prefs.getString(WearDataReceiver.PREF_FACE_STYLE, "digital") == "analog"

        setContent {
            ShabbatWatchTheme {
                val alertText by bannerManager.alertText.collectAsState()

                val windowInfo = controller.getCurrentWindowInfo()
                val havdalahMs = windowInfo?.second ?: 0L
                val parasha = windowInfo?.first

                val hebrewDate = remember { formatHebrewDate() }
                val havdalahFormatted = remember(havdalahMs) {
                    if (havdalahMs > 0) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        getString(R.string.havdalah_motzash, sdf.format(Date(havdalahMs)))
                    } else ""
                }

                val indicator = remember {
                    val hd = HolidayCalculator.gregorianToHebrew(Calendar.getInstance())
                    // Check if it's a holiday (not just Shabbat)
                    if (parasha != null && parasha != getString(R.string.shabbat_shalom)) {
                        parasha
                    } else {
                        getString(R.string.shabbat_shalom)
                    }
                }

                ShabbatFace(
                    indicator = indicator,
                    hebrewDate = hebrewDate,
                    parasha = parasha,
                    havdalahTime = havdalahFormatted,
                    alertText = alertText,
                    useAnalog = useAnalog,
                    isAmbient = false // Ambient mode integration deferred — see known deferred items
                )
            }
        }
    }

    private fun enterLockTask() {
        if (AdminReceiver.isDeviceOwner(this)) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = AdminReceiver.getComponentName(this)
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            startLockTask()
            Log.d(TAG, "Lock Task Mode entered")
        } else {
            Log.w(TAG, "Not device owner — Lock Task Mode unavailable")
        }
    }

    private fun exitLockTaskAndFinish() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop lock task: ${e.message}")
        }
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        buttonDownTime = System.currentTimeMillis()
        return true // Consume all button presses
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val pressDuration = System.currentTimeMillis() - buttonDownTime
        if (pressDuration >= LONG_PRESS_THRESHOLD_MS) {
            // Long press — open emergency dialog
            val intent = Intent(this, EmergencyDialogActivity::class.java)
                .putExtra("last_alert", bannerManager.lastAlertText)
            startActivity(intent)
        }
        // Short press — consumed, do nothing
        return true
    }

    override fun onBackPressed() {
        // Block back button
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopLockTaskReceiver)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun formatHebrewDate(): String {
        val hd = HolidayCalculator.gregorianToHebrew(Calendar.getInstance())
        val dayStr = hebrewNumeral(hd.day)
        val monthStr = hebrewMonthName(hd.month, HolidayCalculator.gregorianToHebrew(Calendar.getInstance()).year)
        val yearStr = hebrewNumeral(hd.year % 1000) // Last 3 digits of year
        return "$dayStr $monthStr $yearStr"
    }

    private fun hebrewMonthName(month: Int, year: Int): String {
        return when (month) {
            1 -> "ניסן"
            2 -> "אייר"
            3 -> "סיוון"
            4 -> "תמוז"
            5 -> "אב"
            6 -> "אלול"
            7 -> "תשרי"
            8 -> "חשוון"
            9 -> "כסלו"
            10 -> "טבת"
            11 -> "שבט"
            12 -> "אדר"
            13 -> "אדר ב׳"
            else -> ""
        }
    }

    private fun hebrewNumeral(n: Int): String {
        if (n <= 0) return ""
        val ones = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
        val tens = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
        val hundreds = arrayOf("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק")

        val h = (n / 100).coerceAtMost(9)
        val t = (n % 100) / 10
        val o = n % 10

        var result = hundreds[h] + tens[t] + ones[o]
        // Handle 15 and 16 special cases (ט״ו, ט״ז instead of י״ה, י״ו)
        result = result
            .replace("יה", "טו")
            .replace("יו", "טז")

        // Add geresh (׳) or gershayim (״)
        return when {
            result.length == 1 -> "$result׳"
            result.length > 1 -> result.dropLast(1) + "״" + result.last()
            else -> result
        }
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
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/AlertBannerManager.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt
git commit -m "feat(wear): add ShabbatWatchFaceActivity with locked Shabbat display"
```

---

## Task 7: EmergencyDialogActivity

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/EmergencyDialogActivity.kt`

- [ ] **Step 1: Create EmergencyDialogActivity.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.AlertRed
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme

class EmergencyDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val showSos = prefs.getBoolean(WearDataReceiver.PREF_EMERGENCY_SOS, true)
        val showLastAlert = prefs.getBoolean(WearDataReceiver.PREF_EMERGENCY_LAST_ALERT, true)
        val lastAlert = intent.getStringExtra("last_alert")

        setContent {
            ShabbatWatchTheme {
                var showConfirm by remember { mutableStateOf(false) }

                if (showConfirm) {
                    EndShabbatConfirmation(
                        isAfterHavdalah = isAfterHavdalah(),
                        onConfirm = {
                            ShabbatModeController(this@EmergencyDialogActivity)
                                .deactivateShabbatMode()
                            finish()
                        },
                        onCancel = { showConfirm = false }
                    )
                } else {
                    EmergencyMenu(
                        showSos = showSos,
                        showLastAlert = showLastAlert && lastAlert != null,
                        lastAlert = lastAlert,
                        onSos = {
                            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:100"))
                            startActivity(intent)
                        },
                        onLastAlert = { /* Already showing in lastAlert text */ },
                        onEndShabbat = { showConfirm = true },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun isAfterHavdalah(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        val now = System.currentTimeMillis()
        val current = windows.find { now in it.candleMs..it.havdalahMs }
        return current == null // If not inside any raw window, havdalah has passed
    }
}

@Composable
private fun EmergencyMenu(
    showSos: Boolean,
    showLastAlert: Boolean,
    lastAlert: String?,
    onSos: () -> Unit,
    onLastAlert: () -> Unit,
    onEndShabbat: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.emergency_title),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (showSos) {
            Button(
                onClick = onSos,
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(stringResource(R.string.emergency_sos), fontSize = 14.sp)
            }
        }

        if (showLastAlert && lastAlert != null) {
            Text(
                text = lastAlert,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Color.Yellow,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Button(
            onClick = onEndShabbat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_end_shabbat), fontSize = 13.sp)
        }

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_cancel), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EndShabbatConfirmation(
    isAfterHavdalah: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAfterHavdalah) {
                stringResource(R.string.emergency_confirm_title)
            } else {
                stringResource(R.string.emergency_confirm_early)
            },
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAfterHavdalah) Color.DarkGray else AlertRed
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_confirm_yes), fontSize = 13.sp)
        }

        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_confirm_no), fontSize = 13.sp)
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/EmergencyDialogActivity.kt
git commit -m "feat(wear): add EmergencyDialogActivity with SOS and end-Shabbat options"
```

---

## Task 8: NotificationFilterService

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/NotificationFilterService.kt`

- [ ] **Step 1: Create NotificationFilterService.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray

/**
 * Filters incoming notifications during Shabbat mode.
 * Only whitelisted app notifications are shown as alert banners.
 */
class NotificationFilterService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifFilter"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isShabbatMode = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!isShabbatMode) return

        val packageName = sbn.packageName
        val whitelistJson = prefs.getString(WearDataReceiver.PREF_WHITELISTED_PACKAGES, "[]") ?: "[]"
        val whitelist = try {
            val arr = JSONArray(whitelistJson)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        if (packageName in whitelist) {
            val text = sbn.notification.extras.getString("android.text")
                ?: sbn.notification.extras.getString("android.title")
                ?: "Alert"
            Log.d(TAG, "Whitelisted notification from $packageName: $text")

            // Send broadcast to show alert banner on watch face
            val intent = android.content.Intent(
                "com.ilanp13.shabbatalertdismisser.wear.ALERT_NOTIFICATION"
            ).putExtra("alert_text", text)
            sendBroadcast(intent)
        } else {
            // Cancel non-whitelisted notifications during Shabbat mode
            cancelNotification(sbn.key)
        }
    }
}
```

- [ ] **Step 2: Add NotificationListenerService to manifest**

Add in `wear/src/main/AndroidManifest.xml` inside `<application>`:

```xml
        <service
            android:name=".NotificationFilterService"
            android:exported="false"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/NotificationFilterService.kt \
    wear/src/main/AndroidManifest.xml
git commit -m "feat(wear): add NotificationFilterService for whitelisted alert banners"
```

---

## Task 9: IdleStatusActivity (non-Shabbat screen)

**Files:**
- Create: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/IdleStatusActivity.kt`

- [ ] **Step 1: Create IdleStatusActivity.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatGold
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shown when the watch app is opened outside of Shabbat mode.
 * Displays next candle lighting time and option to activate early.
 */
class IdleStatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = ShabbatModeController(this)

        // If Shabbat mode is active, redirect to watch face
        if (controller.isShabbatModeActive()) {
            startActivity(
                Intent(this, ShabbatWatchFaceActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        // Re-schedule alarms from synced data
        controller.scheduleFromSyncedWindows()

        val nextWindow = controller.getNextWindowInfo()
        val sdf = SimpleDateFormat("EEE dd/MM HH:mm", Locale.getDefault())

        setContent {
            ShabbatWatchTheme {
                IdleScreen(
                    nextCandleLighting = nextWindow?.let { sdf.format(Date(it.first)) },
                    nextHavdalah = nextWindow?.let { sdf.format(Date(it.second)) },
                    parasha = nextWindow?.third,
                    onActivateEarly = {
                        controller.activateShabbatMode()
                        startActivity(
                            Intent(
                                this@IdleStatusActivity,
                                ShabbatWatchFaceActivity::class.java
                            ).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleScreen(
    nextCandleLighting: String?,
    nextHavdalah: String?,
    parasha: String?,
    onActivateEarly: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 16.sp,
            color = ShabbatGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (nextCandleLighting != null) {
            Text(
                text = stringResource(R.string.next_candle_lighting, nextCandleLighting),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (nextHavdalah != null) {
                Text(
                    text = stringResource(R.string.havdalah_label, nextHavdalah),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (parasha != null) {
                Text(
                    text = parasha,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = onActivateEarly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.activate_early), fontSize = 13.sp)
            }
        } else {
            Text(
                text = stringResource(R.string.no_schedule),
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.sync_from_phone),
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/IdleStatusActivity.kt
git commit -m "feat(wear): add IdleStatusActivity for non-Shabbat status display"
```

---

## Task 10: Phone app — WatchSyncService

**Files:**
- Create: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt`
- Create: `app/src/main/res/xml/wear.xml`

- [ ] **Step 1: Add Wearable dependency to `app/build.gradle.kts`**

Add to the `dependencies` block:
```kotlin
implementation("com.google.android.gms:play-services-wearable:18.2.0")
```

- [ ] **Step 2: Create `app/src/main/res/xml/wear.xml`** (phone-side capability query)

```bash
mkdir -p app/src/main/res/xml
```

Note: This directory may already exist (it has `accessibility_config.xml`). The `wear.xml` declares what capabilities the phone app looks for.

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="android_wear_capabilities">
        <item>shabbat_watch_manager</item>
    </string-array>
</resources>
```

- [ ] **Step 3: Create WatchSyncService.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.*
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
import org.json.JSONArray

/**
 * Syncs Shabbat schedule and watch settings to the connected Wear OS watch.
 */
object WatchSyncService {

    private const val TAG = "WatchSyncService"
    private const val PATH_SCHEDULE = "/shabbat-schedule"
    private const val PATH_SETTINGS = "/watch-settings"
    private const val PATH_UNLOCK = "/unlock-shabbat-mode"
    private const val CAPABILITY_WATCH_APP = "shabbat_watch_app"

    /**
     * Check if a watch with the Shabbat app is connected.
     */
    fun checkWatchConnected(context: Context, callback: (Boolean) -> Unit) {
        Wearable.getCapabilityClient(context)
            .getCapability(CAPABILITY_WATCH_APP, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                callback(capabilityInfo.nodes.isNotEmpty())
            }
            .addOnFailureListener {
                Log.w(TAG, "Failed to check watch capability: ${it.message}")
                callback(false)
            }
    }

    /**
     * Sync the Shabbat schedule to the watch.
     * Call after Hebcal refresh or settings change.
     */
    fun syncSchedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val windowsJson = prefs.getString("hebcal_windows_json", "[]") ?: "[]"

        val dataMap = PutDataMapRequest.create(PATH_SCHEDULE).apply {
            dataMap.putString("schedule_json", windowsJson)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(dataMap)
            .addOnSuccessListener { Log.d(TAG, "Schedule synced to watch") }
            .addOnFailureListener { Log.w(TAG, "Schedule sync failed: ${it.message}") }
    }

    /**
     * Sync all watch-specific settings to the watch.
     * Call when any watch setting changes.
     */
    fun syncSettings(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val dataMap = PutDataMapRequest.create(PATH_SETTINGS).apply {
            dataMap.putString("face_style", prefs.getString("watch_face_style", "digital") ?: "digital")
            dataMap.putString("activation_mode", prefs.getString("watch_activation_mode", "auto") ?: "auto")
            dataMap.putInt("offset_before_min", prefs.getInt("watch_offset_before_min", 0))
            dataMap.putInt("offset_after_min", prefs.getInt("watch_offset_after_min", 0))
            dataMap.putBoolean("disable_wifi", prefs.getBoolean("watch_disable_wifi", true))
            dataMap.putBoolean("disable_gps", prefs.getBoolean("watch_disable_gps", true))
            dataMap.putBoolean("disable_tilt_wake", prefs.getBoolean("watch_disable_tilt_wake", true))
            dataMap.putBoolean("disable_touch_wake", prefs.getBoolean("watch_disable_touch_wake", false))
            dataMap.putBoolean("disable_lte", prefs.getBoolean("watch_disable_lte", false))
            dataMap.putString("whitelisted_packages", prefs.getString("watch_whitelisted_packages", "[]") ?: "[]")
            dataMap.putInt("banner_timeout_sec", prefs.getInt("watch_banner_timeout_sec", 30))
            dataMap.putBoolean("emergency_sos", prefs.getBoolean("watch_emergency_sos", true))
            dataMap.putBoolean("emergency_last_alert", prefs.getBoolean("watch_emergency_last_alert", true))
            dataMap.putString("language", prefs.getString("app_language", "iw") ?: "iw")
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(dataMap)
            .addOnSuccessListener { Log.d(TAG, "Settings synced to watch") }
            .addOnFailureListener { Log.w(TAG, "Settings sync failed: ${it.message}") }
    }

    /**
     * Send manual unlock command to the watch.
     */
    fun sendUnlockCommand(context: Context) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                for (node in nodes) {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, PATH_UNLOCK, byteArrayOf())
                        .addOnSuccessListener { Log.d(TAG, "Unlock sent to ${node.displayName}") }
                }
            }
    }

    /**
     * Sync both schedule and settings. Call on app launch or after Hebcal refresh.
     */
    fun syncAll(context: Context) {
        syncSchedule(context)
        syncSettings(context)
    }
}
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts \
    app/src/main/res/xml/wear.xml \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSyncService.kt
git commit -m "feat(app): add WatchSyncService for phone-to-watch data sync"
```

---

## Task 11: Phone app — WatchSettingsActivity

**Files:**
- Create: `app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_watch_settings.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-iw/strings.xml`

- [ ] **Step 1: Add watch-related strings**

**Add to `app/src/main/res/values/strings.xml`** (before closing `</resources>`):
```xml
    <!-- Watch settings -->
    <string name="watch_settings_title">Watch Settings</string>
    <string name="watch_settings_button">Shabbat Watch Settings</string>
    <string name="watch_status_title">Watch Status</string>
    <string name="watch_connected">Connected: %s</string>
    <string name="watch_not_connected">No watch connected</string>
    <string name="watch_last_sync">Last sync: %s</string>
    <string name="watch_face_style_label">Watch face style</string>
    <string name="watch_face_analog">Analog</string>
    <string name="watch_face_digital">Digital</string>
    <string name="watch_activation_label">Activation mode</string>
    <string name="watch_activation_auto">Automatic</string>
    <string name="watch_activation_manual">Manual (unlock from phone)</string>
    <string name="watch_offset_before_label">Extra minutes before candle lighting</string>
    <string name="watch_offset_after_label">Extra minutes after havdalah</string>
    <string name="watch_offset_format">%d minutes</string>
    <string name="watch_battery_title">Battery Optimization</string>
    <string name="watch_disable_wifi">Disable Wi-Fi</string>
    <string name="watch_disable_gps">Disable GPS</string>
    <string name="watch_disable_tilt_wake">Disable tilt-to-wake</string>
    <string name="watch_disable_touch_wake">Disable touch-to-wake</string>
    <string name="watch_disable_lte">Disable LTE</string>
    <string name="watch_lte_warning">Warning: disabling LTE prevents direct emergency alerts on standalone watches</string>
    <string name="watch_whitelist_title">Whitelisted Apps</string>
    <string name="watch_banner_timeout_label">Alert banner timeout</string>
    <string name="watch_banner_15s">15 seconds</string>
    <string name="watch_banner_30s">30 seconds</string>
    <string name="watch_banner_60s">60 seconds</string>
    <string name="watch_banner_stay">Stay until next</string>
    <string name="watch_emergency_title">Emergency Long-Press</string>
    <string name="watch_emergency_sos">Enable SOS call</string>
    <string name="watch_emergency_last_alert">Show last alert details</string>
    <string name="watch_sync_now">Sync Now</string>
    <string name="watch_synced">Synced!</string>
    <string name="watch_unlock_shabbat">End Watch Shabbat Mode</string>
    <string name="watch_setup_title">Device Owner Setup Required</string>
    <string name="watch_setup_instructions">Lock Task Mode requires device owner setup via ADB:\n\nadb -s WATCH_SERIAL shell dpm set-device-owner com.ilanp13.shabbatalertdismisser.wear/.AdminReceiver</string>
```

**Add to `app/src/main/res/values-iw/strings.xml`** (before closing `</resources>`):
```xml
    <!-- Watch settings -->
    <string name="watch_settings_title">הגדרות שעון</string>
    <string name="watch_settings_button">הגדרות שעון שבת</string>
    <string name="watch_status_title">סטטוס שעון</string>
    <string name="watch_connected">מחובר: %s</string>
    <string name="watch_not_connected">אין שעון מחובר</string>
    <string name="watch_last_sync">סנכרון אחרון: %s</string>
    <string name="watch_face_style_label">סגנון השעון</string>
    <string name="watch_face_analog">אנלוגי</string>
    <string name="watch_face_digital">דיגיטלי</string>
    <string name="watch_activation_label">מצב הפעלה</string>
    <string name="watch_activation_auto">אוטומטי</string>
    <string name="watch_activation_manual">ידני (ביטול מהטלפון)</string>
    <string name="watch_offset_before_label">דקות נוספות לפני הדלקת נרות</string>
    <string name="watch_offset_after_label">דקות נוספות אחרי הבדלה</string>
    <string name="watch_offset_format">%d דקות</string>
    <string name="watch_battery_title">חיסכון בסוללה</string>
    <string name="watch_disable_wifi">כבה Wi-Fi</string>
    <string name="watch_disable_gps">כבה GPS</string>
    <string name="watch_disable_tilt_wake">כבה הטיה להפעלה</string>
    <string name="watch_disable_touch_wake">כבה נגיעה להפעלה</string>
    <string name="watch_disable_lte">כבה LTE</string>
    <string name="watch_lte_warning">אזהרה: כיבוי LTE מונע קבלת התראות חירום ישירות בשעון עצמאי</string>
    <string name="watch_whitelist_title">אפליקציות מורשות</string>
    <string name="watch_banner_timeout_label">זמן הצגת באנר התראה</string>
    <string name="watch_banner_15s">15 שניות</string>
    <string name="watch_banner_30s">30 שניות</string>
    <string name="watch_banner_60s">60 שניות</string>
    <string name="watch_banner_stay">עד ההתראה הבאה</string>
    <string name="watch_emergency_title">לחיצה ארוכה לחירום</string>
    <string name="watch_emergency_sos">הפעל שיחת חירום</string>
    <string name="watch_emergency_last_alert">הצג פרטי התראה אחרונה</string>
    <string name="watch_sync_now">סנכרן עכשיו</string>
    <string name="watch_synced">סונכרן!</string>
    <string name="watch_unlock_shabbat">סיים מצב שבת בשעון</string>
    <string name="watch_setup_title">נדרשת הגדרת Device Owner</string>
    <string name="watch_setup_instructions">מצב נעילת משימה דורש הגדרת device owner דרך ADB:\n\nadb -s WATCH_SERIAL shell dpm set-device-owner com.ilanp13.shabbatalertdismisser.wear/.AdminReceiver</string>
```

- [ ] **Step 2: Create `app/src/main/res/layout/activity_watch_settings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Watch Status -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_status_title"
            android:textSize="18sp"
            android:textStyle="bold"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/tvWatchStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:layout_marginBottom="16dp" />

        <!-- Watch Face Style -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_face_style_label"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <RadioGroup
            android:id="@+id/radioFaceStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp">

            <RadioButton
                android:id="@+id/radioDigital"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/watch_face_digital" />

            <RadioButton
                android:id="@+id/radioAnalog"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/watch_face_analog" />
        </RadioGroup>

        <!-- Activation Mode -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_activation_label"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <RadioGroup
            android:id="@+id/radioActivation"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp">

            <RadioButton
                android:id="@+id/radioAuto"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/watch_activation_auto" />

            <RadioButton
                android:id="@+id/radioManual"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/watch_activation_manual" />
        </RadioGroup>

        <!-- Time Offsets -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_offset_before_label"
            android:textSize="14sp"
            android:layout_marginBottom="4dp" />

        <SeekBar
            android:id="@+id/seekOffsetBefore"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="30"
            android:layout_marginBottom="4dp" />

        <TextView
            android:id="@+id/tvOffsetBefore"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:layout_marginBottom="12dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_offset_after_label"
            android:textSize="14sp"
            android:layout_marginBottom="4dp" />

        <SeekBar
            android:id="@+id/seekOffsetAfter"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="60"
            android:layout_marginBottom="4dp" />

        <TextView
            android:id="@+id/tvOffsetAfter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:layout_marginBottom="16dp" />

        <!-- Battery Optimization -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_battery_title"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="8dp" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableWifi"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_wifi"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableGps"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_gps"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableTiltWake"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_tilt_wake"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableTouchWake"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_touch_wake"
            android:checked="false" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchDisableLte"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_disable_lte"
            android:checked="false" />

        <TextView
            android:id="@+id/tvLteWarning"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_lte_warning"
            android:textSize="12sp"
            android:textColor="#FF8800"
            android:visibility="gone"
            android:layout_marginBottom="16dp" />

        <!-- Alert Banner Timeout -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_banner_timeout_label"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <Spinner
            android:id="@+id/spinnerBannerTimeout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp" />

        <!-- Emergency Long-Press -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/watch_emergency_title"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="8dp" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchEmergencySos"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_emergency_sos"
            android:checked="true" />

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchEmergencyLastAlert"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_emergency_last_alert"
            android:checked="true"
            android:layout_marginBottom="16dp" />

        <!-- Actions -->
        <Button
            android:id="@+id/btnSyncNow"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_sync_now"
            android:layout_marginBottom="8dp" />

        <Button
            android:id="@+id/btnUnlockShabbat"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_unlock_shabbat"
            android:layout_marginBottom="16dp" />

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: Create WatchSettingsActivity.kt**

```kotlin
package com.ilanp13.shabbatalertdismisser

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.switchmaterial.SwitchMaterial

class WatchSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_settings)
        title = getString(R.string.watch_settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupWatchStatus()
        setupFaceStyle()
        setupActivationMode()
        setupOffsets()
        setupBatteryToggles()
        setupBannerTimeout()
        setupEmergencyToggles()
        setupActionButtons()
    }

    private fun setupWatchStatus() {
        val tvStatus = findViewById<TextView>(R.id.tvWatchStatus)
        WatchSyncService.checkWatchConnected(this) { connected ->
            runOnUiThread {
                tvStatus.text = if (connected) {
                    getString(R.string.watch_connected, "Wear OS Watch")
                } else {
                    getString(R.string.watch_not_connected)
                }
            }
        }
    }

    private fun setupFaceStyle() {
        val radioGroup = findViewById<RadioGroup>(R.id.radioFaceStyle)
        val current = prefs.getString("watch_face_style", "digital")
        if (current == "analog") {
            radioGroup.check(R.id.radioAnalog)
        } else {
            radioGroup.check(R.id.radioDigital)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val style = if (checkedId == R.id.radioAnalog) "analog" else "digital"
            prefs.edit().putString("watch_face_style", style).apply()
            WatchSyncService.syncSettings(this)
        }
    }

    private fun setupActivationMode() {
        val radioGroup = findViewById<RadioGroup>(R.id.radioActivation)
        val current = prefs.getString("watch_activation_mode", "auto")
        if (current == "manual") {
            radioGroup.check(R.id.radioManual)
        } else {
            radioGroup.check(R.id.radioAuto)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radioManual) "manual" else "auto"
            prefs.edit().putString("watch_activation_mode", mode).apply()
            WatchSyncService.syncSettings(this)
        }
    }

    private fun setupOffsets() {
        val seekBefore = findViewById<SeekBar>(R.id.seekOffsetBefore)
        val tvBefore = findViewById<TextView>(R.id.tvOffsetBefore)
        val seekAfter = findViewById<SeekBar>(R.id.seekOffsetAfter)
        val tvAfter = findViewById<TextView>(R.id.tvOffsetAfter)

        seekBefore.progress = prefs.getInt("watch_offset_before_min", 0)
        tvBefore.text = getString(R.string.watch_offset_format, seekBefore.progress)

        seekAfter.progress = prefs.getInt("watch_offset_after_min", 0)
        tvAfter.text = getString(R.string.watch_offset_format, seekAfter.progress)

        seekBefore.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBefore.text = getString(R.string.watch_offset_format, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt("watch_offset_before_min", sb?.progress ?: 0).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
        })

        seekAfter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvAfter.text = getString(R.string.watch_offset_format, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt("watch_offset_after_min", sb?.progress ?: 0).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
        })
    }

    private fun setupBatteryToggles() {
        setupToggle(R.id.switchDisableWifi, "watch_disable_wifi", true)
        setupToggle(R.id.switchDisableGps, "watch_disable_gps", true)
        setupToggle(R.id.switchDisableTiltWake, "watch_disable_tilt_wake", true)
        setupToggle(R.id.switchDisableTouchWake, "watch_disable_touch_wake", false)

        val switchLte = findViewById<SwitchMaterial>(R.id.switchDisableLte)
        val tvWarning = findViewById<TextView>(R.id.tvLteWarning)
        switchLte.isChecked = prefs.getBoolean("watch_disable_lte", false)
        tvWarning.visibility = if (switchLte.isChecked) View.VISIBLE else View.GONE

        switchLte.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("watch_disable_lte", isChecked).apply()
            tvWarning.visibility = if (isChecked) View.VISIBLE else View.GONE
            WatchSyncService.syncSettings(this)
        }
    }

    private fun setupToggle(viewId: Int, prefKey: String, defaultValue: Boolean) {
        val switch = findViewById<SwitchMaterial>(viewId)
        switch.isChecked = prefs.getBoolean(prefKey, defaultValue)
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefKey, isChecked).apply()
            WatchSyncService.syncSettings(this)
        }
    }

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

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt("watch_banner_timeout_sec", values[pos]).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupEmergencyToggles() {
        setupToggle(R.id.switchEmergencySos, "watch_emergency_sos", true)
        setupToggle(R.id.switchEmergencyLastAlert, "watch_emergency_last_alert", true)
    }

    private fun setupActionButtons() {
        findViewById<Button>(R.id.btnSyncNow).setOnClickListener {
            WatchSyncService.syncAll(this)
            Toast.makeText(this, getString(R.string.watch_synced), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnUnlockShabbat).setOnClickListener {
            WatchSyncService.sendUnlockCommand(this)
            Toast.makeText(this, "Unlock command sent", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
```

- [ ] **Step 4: Register WatchSettingsActivity in AndroidManifest.xml**

Add inside `<application>` in `app/src/main/AndroidManifest.xml`, after the existing `SettingsActivity`:

```xml
        <activity
            android:name=".WatchSettingsActivity"
            android:theme="@style/Theme.Material3.DayNight"
            android:parentActivityName=".MainActivity" />
```

- [ ] **Step 5: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ilanp13/shabbatalertdismisser/WatchSettingsActivity.kt \
    app/src/main/res/layout/activity_watch_settings.xml \
    app/src/main/AndroidManifest.xml \
    app/src/main/res/values/strings.xml \
    app/src/main/res/values-iw/strings.xml
git commit -m "feat(app): add WatchSettingsActivity for watch configuration"
```

---

## Task 12: Integrate watch settings into SettingsFragment

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/SettingsFragment.kt`

- [ ] **Step 1: Add watch settings button to `fragment_settings.xml`**

Add before the closing `</LinearLayout>` tag (before line 432), after the theme spinner:

```xml
        <!-- Watch Settings (conditionally visible) -->
        <Button
            android:id="@+id/btnWatchSettings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/watch_settings_button"
            android:visibility="gone"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="24dp" />
```

- [ ] **Step 2: Add watch detection and button handler to SettingsFragment.kt**

Add a `lateinit var` for the button in the class fields area, and wire it up in `onViewCreated`.

Add after existing `lateinit var` declarations (around line 30):
```kotlin
    private lateinit var btnWatchSettings: Button
```

In the `onViewCreated` method, after the existing UI setup code, add:
```kotlin
        // Watch settings button — only visible when watch with app is detected
        btnWatchSettings = view.findViewById(R.id.btnWatchSettings)
        btnWatchSettings.setOnClickListener {
            startActivity(Intent(requireContext(), WatchSettingsActivity::class.java))
        }
        WatchSyncService.checkWatchConnected(requireContext()) { connected ->
            activity?.runOnUiThread {
                btnWatchSettings.visibility = if (connected) View.VISIBLE else View.GONE
            }
        }
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The watch settings button appears only when a watch is detected.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/SettingsFragment.kt
git commit -m "feat(app): add conditional watch settings button in SettingsFragment"
```

---

## Task 13: Wire up auto-sync triggers

**Files:**
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/SettingsFragment.kt`
- Modify: `app/src/main/java/com/ilanp13/shabbatalertdismisser/BootReceiver.kt`

- [ ] **Step 1: Trigger watch sync after Hebcal refresh and settings changes in SettingsFragment**

In `SettingsFragment.kt`, find the background Hebcal refresh code (the function that calls `HebcalService.fetch` and saves the result). After the `editor.apply()` call that saves Hebcal data, add:

```kotlin
WatchSyncService.syncSchedule(requireContext())
```

Also find the minhag spinner change listener and location update handler. After each one saves its new value, add:

```kotlin
WatchSyncService.syncAll(requireContext())
```

This ensures the watch receives updated schedule data whenever the phone refreshes from Hebcal or when the user changes minhag/location settings (which affect Shabbat times).

- [ ] **Step 2: Trigger watch sync after boot in BootReceiver**

In `BootReceiver.kt`, after the existing `editor.apply()` (line 42), add:

```kotlin
WatchSyncService.syncSchedule(context)
```

Add the import at the top if not already present:
```kotlin
import com.ilanp13.shabbatalertdismisser.WatchSyncService
```

Wait — `WatchSyncService` is in the same package, so no import needed.

- [ ] **Step 3: Build and verify**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ilanp13/shabbatalertdismisser/SettingsFragment.kt \
    app/src/main/java/com/ilanp13/shabbatalertdismisser/BootReceiver.kt
git commit -m "feat(app): trigger watch sync on Hebcal refresh and boot"
```

---

## Task 14: Full build verification

- [ ] **Step 1: Build all modules**

```bash
cd /Users/ilan.peretz/dev/personal/shabbat-alert-dismisser
./gradlew clean assembleDebug
```

Expected: BUILD SUCCESSFUL for all three modules (`:shared`, `:app`, `:wear`).

- [ ] **Step 2: Verify no regressions to phone app**

```bash
./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL with same APK structure as before.

- [ ] **Step 3: List all new/modified files for review**

```bash
git diff --stat HEAD~13
```

Review that:
- Only expected files were added/modified
- No existing phone app behavior was changed
- Shared module contains exactly the 4 moved files
- Wear module contains all expected files

- [ ] **Step 4: Commit clean build verification**

If any fixes were needed during verification, commit them:
```bash
git add -A
git commit -m "fix: resolve build issues from full verification"
```

If build was clean, no commit needed — proceed to next task.

---

## Task 15: Final integration commit and push

- [ ] **Step 1: Review git log**

```bash
git log --oneline -15
```

Verify all commits are present and well-named.

- [ ] **Step 2: Push to remote**

```bash
git push
```

- [ ] **Step 3: Verify**

Confirm the push succeeded and all changes are on the remote.
