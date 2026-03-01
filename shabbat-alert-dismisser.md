# Shabbat Alert Auto-Dismiss — Android Project

## Setup Instructions

1. Open Android Studio → **New Project → Empty Views Activity**
2. Name: `ShabbatAlertDismisser`
3. Package: `com.example.shabbatalert`
4. Language: **Kotlin**
5. Minimum SDK: **API 26** (Android 8.0)
6. Replace/create the files below

---

## File 1: `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".AlertDismissService"
            android:exported="false"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_config" />
        </service>

    </application>
</manifest>
```

---

## File 2: `res/xml/accessibility_config.xml`

Create folder `res/xml/` if it doesn't exist.

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/service_description"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:settingsActivity="com.example.shabbatalert.MainActivity" />
```

---

## File 3: `AlertDismissService.kt`

```kotlin
package com.example.shabbatalert

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager

class AlertDismissService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatAlertDismiss"
        private val CELL_BROADCAST_PACKAGES = setOf(
            "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver",
            "com.samsung.android.cellbroadcastreceiver",
            "com.android.cellbroadcastservice"
        )
        private val DISMISS_BUTTON_TEXTS = listOf(
            "OK", "Ok", "ok",
            "אישור",
            "סגור",
            "Close",
            "Dismiss"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Check if this is a cell broadcast alert
        if (!isCellBroadcastPackage(packageName)) return

        Log.d(TAG, "Cell broadcast alert detected from: $packageName")

        // Check mode
        val mode = prefs.getString("mode", "shabbat_only")
        if (mode == "disabled") return

        if (mode == "always") {
            // Skip time checks, always dismiss
        } else {
            // Check if it's currently Shabbat (or holiday)
            val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
            val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
            val candleMinutes = prefs.getInt("candle_lighting_minutes", 18)
            val havdalahMinutes = prefs.getInt("havdalah_minutes", 40)

            val calculator = ShabbatCalculator(lat, lon)
            val isShabbat = calculator.isShabbatNow(candleMinutes, havdalahMinutes)
            val isHoliday = if (mode == "shabbat_holidays") {
                HolidayCalculator.isYomTovToday(candleMinutes, havdalahMinutes, lat, lon)
            } else false

            if (!isShabbat && !isHoliday) {
                Log.d(TAG, "Not Shabbat/holiday right now, ignoring")
                return
            }
        }

        // Wait before dismissing (so siren can be heard)
        val delaySeconds = prefs.getInt("delay_seconds", 10)
        Log.d(TAG, "Shabbat mode active. Will dismiss in $delaySeconds seconds")

        handler.postDelayed({
            dismissAlert()
        }, delaySeconds * 1000L)
    }

    private fun dismissAlert() {
        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "Could not get root window")
            return
        }

        try {
            for (buttonText in DISMISS_BUTTON_TEXTS) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(buttonText)
                for (node in nodes) {
                    if (tryClick(node)) {
                        Log.d(TAG, "Successfully dismissed alert via button: $buttonText")
                        return
                    }
                }
            }

            // Fallback: try to find any clickable button
            if (findAndClickButton(rootNode)) {
                Log.d(TAG, "Dismissed alert via fallback button search")
                return
            }

            // Last resort: press Back
            Log.d(TAG, "No button found, trying BACK action")
            performGlobalAction(GLOBAL_ACTION_BACK)

        } finally {
            rootNode.recycle()
        }
    }

    private fun tryClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        // Walk up to find clickable parent
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun findAndClickButton(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() == "android.widget.Button" && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickButton(child)) return true
        }
        return false
    }

    private fun isCellBroadcastPackage(pkg: String): Boolean {
        return CELL_BROADCAST_PACKAGES.any { pkg.contains(it) } ||
               pkg.contains("cellbroadcast") ||
               pkg.contains("emergencyalert")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
}
```

---

## File 4b: `HolidayCalculator.kt`

```kotlin
package com.example.shabbatalert

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Calculates Jewish holidays (Yom Tov days when melacha is forbidden).
 * Uses a simplified Hebrew calendar conversion.
 */
object HolidayCalculator {

    /**
     * Check if right now is during a Yom Tov (from candle lighting eve to havdalah).
     */
    fun isYomTovToday(
        candleLightingMinutes: Int,
        havdalahMinutes: Int,
        latitude: Double,
        longitude: Double
    ): Boolean {
        val now = Calendar.getInstance()
        val calculator = ShabbatCalculator(latitude, longitude)

        // Check if today is Yom Tov
        val todayHebrew = gregorianToHebrew(now)
        if (isYomTovDate(todayHebrew)) {
            val sunset = calculator.getSunsetTimePublic(now)
            if (sunset != null) {
                sunset.add(Calendar.MINUTE, havdalahMinutes)
                if (now.before(sunset)) return true
            } else return true
        }

        // Check if yesterday was erev Yom Tov (we're in the night portion)
        val yesterday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayHebrew = gregorianToHebrew(yesterday)
        // If yesterday is erev YT, check if now is after yesterday's sunset
        val tomorrow = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowHebrew = gregorianToHebrew(tomorrow)

        // Check if tonight starts Yom Tov
        val todayForErev = gregorianToHebrew(now)
        val nextDay = nextHebrewDay(todayForErev)
        if (isYomTovDate(nextDay)) {
            val sunset = calculator.getSunsetTimePublic(now)
            if (sunset != null) {
                sunset.add(Calendar.MINUTE, -candleLightingMinutes)
                if (now.after(sunset)) return true
            }
        }

        return false
    }

    private fun isYomTovDate(hd: HebrewDate): Boolean {
        val m = hd.month
        val d = hd.day
        return when {
            // Rosh Hashana: Tishrei 1-2
            m == 7 && d in 1..2 -> true
            // Yom Kippur: Tishrei 10
            m == 7 && d == 10 -> true
            // Sukkot: Tishrei 15-16 (first days)
            m == 7 && d in 15..16 -> true
            // Shmini Atzeret / Simchat Torah: Tishrei 22-23
            m == 7 && d in 22..23 -> true
            // Pesach: Nisan 15-16, 21-22
            m == 1 && d in 15..16 -> true
            m == 1 && d in 21..22 -> true
            // Shavuot: Sivan 6-7
            m == 3 && d in 6..7 -> true
            else -> false
        }
    }

    data class HebrewDate(val year: Int, val month: Int, val day: Int)

    private fun nextHebrewDay(hd: HebrewDate): HebrewDate {
        // Simplified: just increment day (sufficient for Yom Tov checking)
        return HebrewDate(hd.year, hd.month, hd.day + 1)
    }

    /**
     * Convert Gregorian date to Hebrew date.
     * Algorithm based on the method by Edward Reingold & Nachum Dershowitz.
     */
    fun gregorianToHebrew(cal: Calendar): HebrewDate {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // Calculate Julian Day Number
        val jdn = gregorianToJDN(year, month, day)

        // Hebrew epoch: JDN of 1 Tishrei 1 (proleptic)
        val hebrewEpoch = 347997L

        // Approximate Hebrew year
        var hYear = ((jdn - hebrewEpoch) * 98496 / 35975351).toInt() + 1

        // Adjust year
        while (jdn >= hebrewNewYear(hYear + 1)) hYear++

        // Find month
        var hMonth: Int
        val firstDayOfYear = hebrewNewYear(hYear)

        if (jdn < hebrewDateToJDN(hYear, 1, 1)) {
            // Before Nisan, we're in the second half of the year
            hMonth = 7 // Start from Tishrei
            while (jdn > hebrewDateToJDN(hYear, hMonth, hebrewMonthDays(hYear, hMonth))) {
                hMonth++
                if (hMonth == 13 || (hMonth == 14 && !isHebrewLeapYear(hYear))) break
            }
        } else {
            hMonth = 1 // Start from Nisan
            while (jdn > hebrewDateToJDN(hYear, hMonth, hebrewMonthDays(hYear, hMonth))) {
                hMonth++
                if (hMonth == 7) break
            }
        }

        val hDay = (jdn - hebrewDateToJDN(hYear, hMonth, 1) + 1).toInt()

        return HebrewDate(hYear, hMonth, hDay)
    }

    private fun gregorianToJDN(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
    }

    private fun hebrewNewYear(hYear: Int): Long {
        // Molad calculation for Tishrei
        val monthsElapsed = (235L * ((hYear - 1) / 19)) +
                (12L * ((hYear - 1) % 19)) +
                ((7L * ((hYear - 1) % 19) + 1) / 19)

        val partsElapsed = 204 + 793 * (monthsElapsed % 1080)
        val hoursElapsed = 5 + 12 * monthsElapsed +
                793 * (monthsElapsed / 1080) +
                partsElapsed / 1080
        val day = 1 + 29 * monthsElapsed + hoursElapsed / 24
        val parts = 1080 * (hoursElapsed % 24) + partsElapsed % 1080

        var altDay = day.toLong()
        val dayOfWeek = (altDay % 7)

        // Postponement rules (dehiyot)
        if (parts >= 19440 ||
            (dayOfWeek == 2L && parts >= 9924 && !isHebrewLeapYear(hYear)) ||
            (dayOfWeek == 1L && parts >= 16789 && isHebrewLeapYear(hYear - 1))) {
            altDay++
        }

        val finalDow = altDay % 7
        if (finalDow == 0L || finalDow == 3L || finalDow == 5L) {
            altDay++
        }

        return altDay + 347996L
    }

    private fun isHebrewLeapYear(hYear: Int): Boolean {
        return ((7 * hYear + 1) % 19) < 7
    }

    private fun hebrewYearDays(hYear: Int): Int {
        return (hebrewNewYear(hYear + 1) - hebrewNewYear(hYear)).toInt()
    }

    private fun hebrewMonthDays(hYear: Int, hMonth: Int): Int {
        return when (hMonth) {
            1 -> 30  // Nisan
            2 -> 29  // Iyar
            3 -> 30  // Sivan
            4 -> 29  // Tammuz
            5 -> 30  // Av
            6 -> 29  // Elul
            7 -> 30  // Tishrei
            8 -> if (hebrewYearDays(hYear) % 10 != 5) 30 else 29  // Cheshvan
            9 -> if (hebrewYearDays(hYear) % 10 == 3) 30 else 29  // Kislev
            10 -> 29  // Tevet
            11 -> 30  // Shvat
            12 -> if (isHebrewLeapYear(hYear)) 30 else 29  // Adar I
            13 -> 29  // Adar II (only in leap years)
            else -> 30
        }
    }

    private fun hebrewDateToJDN(hYear: Int, hMonth: Int, hDay: Int): Long {
        var jdn = hebrewNewYear(hYear) // 1 Tishrei

        // Add days for months from Tishrei to target month
        if (hMonth >= 7) {
            var m = 7
            while (m < hMonth) {
                jdn += hebrewMonthDays(hYear, m)
                m++
            }
        } else {
            // Add months Tishrei(7) through Adar
            var m = 7
            val lastMonth = if (isHebrewLeapYear(hYear)) 13 else 12
            while (m <= lastMonth) {
                jdn += hebrewMonthDays(hYear, m)
                m++
            }
            // Then Nisan(1) to target
            m = 1
            while (m < hMonth) {
                jdn += hebrewMonthDays(hYear, m)
                m++
            }
        }

        jdn += hDay - 1
        return jdn
    }
}
```

---

## File 5: `ShabbatCalculator.kt`

```kotlin
package com.example.shabbatalert

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

class ShabbatCalculator(
    private val latitude: Double,
    private val longitude: Double
) {

    /**
     * Check if right now is during Shabbat.
     * Shabbat starts: Friday sunset minus [candleLightingMinutes]
     * Shabbat ends: Saturday sunset plus [havdalahMinutes]
     */
    fun isShabbatNow(candleLightingMinutes: Int = 18, havdalahMinutes: Int = 40): Boolean {
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        return when (dayOfWeek) {
            Calendar.FRIDAY -> {
                val sunset = getSunsetTime(now)
                if (sunset != null) {
                    sunset.add(Calendar.MINUTE, -candleLightingMinutes)
                    now.after(sunset)
                } else false
            }
            Calendar.SATURDAY -> {
                val sunset = getSunsetTime(now)
                if (sunset != null) {
                    sunset.add(Calendar.MINUTE, havdalahMinutes)
                    now.before(sunset)
                } else false
            }
            else -> false
        }
    }

    /**
     * Get Shabbat start and end times for the upcoming (or current) Shabbat.
     */
    fun getShabbatTimes(
        candleLightingMinutes: Int = 18,
        havdalahMinutes: Int = 40
    ): Pair<Calendar, Calendar>? {
        val now = Calendar.getInstance()

        // Find next Friday
        val friday = (now.clone() as Calendar).apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val saturday = (friday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val fridaySunset = getSunsetTime(friday) ?: return null
        val saturdaySunset = getSunsetTime(saturday) ?: return null

        fridaySunset.add(Calendar.MINUTE, -candleLightingMinutes)
        saturdaySunset.add(Calendar.MINUTE, havdalahMinutes)

        return Pair(fridaySunset, saturdaySunset)
    }

    /**
     * Public accessor for sunset time (used by HolidayCalculator).
     */
    fun getSunsetTimePublic(date: Calendar): Calendar? = getSunsetTime(date)

    /**
     * Calculate sunset time for a given date using NOAA algorithm.
     */
    private fun getSunsetTime(date: Calendar): Calendar? {
        val tz = TimeZone.getDefault()
        val tzOffset = tz.getOffset(date.timeInMillis) / 3600000.0

        val dayOfYear = date.get(Calendar.DAY_OF_YEAR)
        val year = date.get(Calendar.YEAR)

        // Julian century
        val jd = getJulianDay(year, date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH))
        val jc = (jd - 2451545.0) / 36525.0

        // Sun's geometric mean longitude & anomaly
        val geomMeanLongSun = (280.46646 + jc * (36000.76983 + 0.0003032 * jc)) % 360
        val geomMeanAnomSun = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)

        // Eccentricity of Earth's orbit
        val eccentEarthOrbit = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)

        // Sun equation of center
        val sunEqOfCtr = sin(Math.toRadians(geomMeanAnomSun)) *
                (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
                sin(Math.toRadians(2 * geomMeanAnomSun)) * (0.019993 - 0.000101 * jc) +
                sin(Math.toRadians(3 * geomMeanAnomSun)) * 0.000289

        val sunTrueLong = geomMeanLongSun + sunEqOfCtr

        // Sun apparent longitude
        val omega = 125.04 - 1934.136 * jc
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        // Mean obliquity of ecliptic
        val meanObliqEcliptic = 23 + (26 + (21.448 - jc *
                (46.815 + jc * (0.00059 - jc * 0.001813))) / 60) / 60
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos(Math.toRadians(omega))

        // Sun declination
        val sunDeclin = Math.toDegrees(
            asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(sunAppLong)))
        )

        // Equation of time
        val varY = tan(Math.toRadians(obliqCorr / 2)).pow(2)
        val eqOfTime = 4 * Math.toDegrees(
            varY * sin(2 * Math.toRadians(geomMeanLongSun)) -
                    2 * eccentEarthOrbit * sin(Math.toRadians(geomMeanAnomSun)) +
                    4 * eccentEarthOrbit * varY * sin(Math.toRadians(geomMeanAnomSun)) *
                    cos(2 * Math.toRadians(geomMeanLongSun)) -
                    0.5 * varY * varY * sin(4 * Math.toRadians(geomMeanLongSun)) -
                    1.25 * eccentEarthOrbit * eccentEarthOrbit *
                    sin(2 * Math.toRadians(geomMeanAnomSun))
        )

        // Hour angle for sunset (90.833° = standard refraction)
        val zenith = 90.833
        val haArg = cos(Math.toRadians(zenith)) /
                (cos(Math.toRadians(latitude)) * cos(Math.toRadians(sunDeclin))) -
                tan(Math.toRadians(latitude)) * tan(Math.toRadians(sunDeclin))

        if (haArg > 1 || haArg < -1) return null  // No sunset (polar)

        val hourAngle = Math.toDegrees(acos(haArg))

        // Sunset in minutes from midnight (UTC)
        val solarNoon = (720 - 4 * longitude - eqOfTime + tzOffset * 60) / 1440
        val sunsetFraction = solarNoon + hourAngle * 4 / 1440

        val sunsetMinutes = sunsetFraction * 1440
        val hours = (sunsetMinutes / 60).toInt()
        val minutes = (sunsetMinutes % 60).toInt()

        return (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
        }
    }

    private fun getJulianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y--; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day + b - 1524.5
    }
}
```

---

## File 5: `MainActivity.kt`

```kotlin
package com.example.shabbatalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val radioMode = findViewById<RadioGroup>(R.id.radioMode)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvLocation = findViewById<TextView>(R.id.tvLocation)
        val tvShabbatTimes = findViewById<TextView>(R.id.tvShabbatTimes)
        val seekDelay = findViewById<SeekBar>(R.id.seekDelay)
        val tvDelay = findViewById<TextView>(R.id.tvDelay)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnUpdateLocation = findViewById<Button>(R.id.btnUpdateLocation)
        val spinnerCandle = findViewById<Spinner>(R.id.spinnerCandle)
        val spinnerHavdalah = findViewById<Spinner>(R.id.spinnerHavdalah)

        // Mode radio group
        val savedMode = prefs.getString("mode", "shabbat_only")
        when (savedMode) {
            "shabbat_only" -> radioMode.check(R.id.radioShabbatOnly)
            "shabbat_holidays" -> radioMode.check(R.id.radioShabbatAndHolidays)
            "always" -> radioMode.check(R.id.radioAlways)
            "disabled" -> radioMode.check(R.id.radioDisabled)
        }
        radioMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioShabbatOnly -> "shabbat_only"
                R.id.radioShabbatAndHolidays -> "shabbat_holidays"
                R.id.radioAlways -> "always"
                R.id.radioDisabled -> "disabled"
                else -> "shabbat_only"
            }
            prefs.edit().putString("mode", mode).apply()
            updateStatusText(tvStatus)
        }

        // Delay seekbar (5-60 seconds)
        val currentDelay = prefs.getInt("delay_seconds", 10)
        seekDelay.progress = currentDelay - 5
        tvDelay.text = getString(R.string.delay_format, currentDelay)
        seekDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress + 5
                tvDelay.text = getString(R.string.delay_format, seconds)
                prefs.edit().putInt("delay_seconds", seconds).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Candle lighting minutes spinner
        val candleOptions = arrayOf("18", "20", "22", "30", "40")
        spinnerCandle.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, candleOptions)
        val savedCandle = prefs.getInt("candle_lighting_minutes", 18).toString()
        spinnerCandle.setSelection(candleOptions.indexOf(savedCandle).coerceAtLeast(0))
        spinnerCandle.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onItemSelected(pos: Int) {
                prefs.edit().putInt("candle_lighting_minutes", candleOptions[pos].toInt()).apply()
                updateShabbatTimes(tvShabbatTimes)
            }
        }

        // Havdalah minutes spinner
        val havdalahOptions = arrayOf("25", "30", "40", "50", "60", "72")
        spinnerHavdalah.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, havdalahOptions)
        val savedHavdalah = prefs.getInt("havdalah_minutes", 40).toString()
        spinnerHavdalah.setSelection(havdalahOptions.indexOf(savedHavdalah).coerceAtLeast(0))
        spinnerHavdalah.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onItemSelected(pos: Int) {
                prefs.edit().putInt("havdalah_minutes", havdalahOptions[pos].toInt()).apply()
                updateShabbatTimes(tvShabbatTimes)
            }
        }

        // Accessibility settings button
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Update location button
        btnUpdateLocation.setOnClickListener {
            requestLocationUpdate(tvLocation, tvShabbatTimes)
        }

        // Load saved location
        updateLocationText(tvLocation)
        updateShabbatTimes(tvShabbatTimes)
        updateStatusText(tvStatus)
    }

    private fun updateStatusText(tv: TextView) {
        val mode = prefs.getString("mode", "shabbat_only")
        val accessible = isAccessibilityServiceEnabled()
        tv.text = when {
            !accessible -> getString(R.string.status_no_accessibility)
            mode == "disabled" -> getString(R.string.status_disabled)
            mode == "always" -> getString(R.string.status_always)
            mode == "shabbat_holidays" -> getString(R.string.status_shabbat_holidays)
            else -> getString(R.string.status_shabbat_only)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/.AlertDismissService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    private fun updateLocationText(tv: TextView) {
        val lat = prefs.getFloat("latitude", 31.7683f)
        val lon = prefs.getFloat("longitude", 35.2137f)
        tv.text = getString(R.string.location_format, lat, lon)
    }

    private fun updateShabbatTimes(tv: TextView) {
        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val candle = prefs.getInt("candle_lighting_minutes", 18)
        val havdalah = prefs.getInt("havdalah_minutes", 40)

        val calc = ShabbatCalculator(lat, lon)
        val times = calc.getShabbatTimes(candle, havdalah)
        if (times != null) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            tv.text = getString(R.string.shabbat_times_format,
                fmt.format(times.first.time),
                fmt.format(times.second.time))
        } else {
            tv.text = getString(R.string.shabbat_times_unavailable)
        }
    }

    private fun requestLocationUpdate(tvLoc: TextView, tvTimes: TextView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (location != null) {
            prefs.edit()
                .putFloat("latitude", location.latitude.toFloat())
                .putFloat("longitude", location.longitude.toFloat())
                .apply()
            updateLocationText(tvLoc)
            updateShabbatTimes(tvTimes)
            Toast.makeText(this, getString(R.string.location_updated), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.location_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 100 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            val tvLoc = findViewById<TextView>(R.id.tvLocation)
            val tvTimes = findViewById<TextView>(R.id.tvShabbatTimes)
            requestLocationUpdate(tvLoc, tvTimes)
        }
    }

    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onItemSelected(pos: Int)
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = onItemSelected(pos)
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
```

---

## File 6: `res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="24dp"
    android:background="?android:colorBackground">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="start">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/app_name"
            android:textSize="24sp"
            android:textStyle="bold"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:layout_marginBottom="24dp" />

        <!-- Mode Selection -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/mode_label"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <RadioGroup
            android:id="@+id/radioMode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp">

            <RadioButton
                android:id="@+id/radioShabbatOnly"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/mode_shabbat_only"
                android:minHeight="48dp" />

            <RadioButton
                android:id="@+id/radioShabbatAndHolidays"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/mode_shabbat_holidays"
                android:minHeight="48dp" />

            <RadioButton
                android:id="@+id/radioAlways"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/mode_always"
                android:minHeight="48dp" />

            <RadioButton
                android:id="@+id/radioDisabled"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/mode_disabled"
                android:minHeight="48dp" />

        </RadioGroup>

        <!-- Accessibility -->
        <Button
            android:id="@+id/btnAccessibility"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/open_accessibility"
            android:layout_marginBottom="24dp" />

        <!-- Location -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/location_title"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <TextView
            android:id="@+id/tvLocation"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:layout_marginBottom="8dp" />

        <Button
            android:id="@+id/btnUpdateLocation"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/update_location"
            android:layout_marginBottom="24dp" />

        <!-- Shabbat Times -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/shabbat_times_title"
            android:textSize="16sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />

        <TextView
            android:id="@+id/tvShabbatTimes"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:layout_marginBottom="24dp" />

        <!-- Candle Lighting -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/candle_lighting_label"
            android:textSize="14sp"
            android:layout_marginBottom="4dp" />

        <Spinner
            android:id="@+id/spinnerCandle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp" />

        <!-- Havdalah -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/havdalah_label"
            android:textSize="14sp"
            android:layout_marginBottom="4dp" />

        <Spinner
            android:id="@+id/spinnerHavdalah"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="24dp" />

        <!-- Delay -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/delay_label"
            android:textSize="14sp"
            android:layout_marginBottom="4dp" />

        <SeekBar
            android:id="@+id/seekDelay"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="55"
            android:layout_marginBottom="4dp" />

        <TextView
            android:id="@+id/tvDelay"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp" />

    </LinearLayout>
</ScrollView>
```

---

## File 7: `res/values/strings.xml` (English — default)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Shabbat Alert Dismisser</string>
    <string name="service_description">Automatically dismisses emergency alerts during Shabbat so the full-screen alert does not block your screen.</string>
    <string name="mode_label">Auto-dismiss mode</string>
    <string name="mode_shabbat_only">Shabbat only</string>
    <string name="mode_shabbat_holidays">Shabbat + Jewish holidays</string>
    <string name="mode_always">Always (including weekdays)</string>
    <string name="mode_disabled">Disabled</string>
    <string name="open_accessibility">Open Accessibility Settings</string>
    <string name="location_title">Location</string>
    <string name="update_location">Update Location</string>
    <string name="location_format">Lat: %.4f, Lon: %.4f</string>
    <string name="location_updated">Location updated</string>
    <string name="location_failed">Could not get location</string>
    <string name="shabbat_times_title">Next Shabbat</string>
    <string name="shabbat_times_format">Starts: %1$s\nEnds: %2$s</string>
    <string name="shabbat_times_unavailable">Could not calculate times</string>
    <string name="candle_lighting_label">Candle lighting (minutes before sunset)</string>
    <string name="havdalah_label">Havdalah (minutes after sunset)</string>
    <string name="delay_label">Delay before dismissing alert</string>
    <string name="delay_format">%d seconds</string>
    <string name="status_shabbat_only">✅ Active — auto-dismiss during Shabbat</string>
    <string name="status_shabbat_holidays">✅ Active — auto-dismiss during Shabbat &amp; holidays</string>
    <string name="status_always">✅ Active — auto-dismiss always</string>
    <string name="status_disabled">⏸ Disabled</string>
    <string name="status_no_accessibility">⚠️ Accessibility service not enabled — tap the button below</string>
</resources>
```

---

## File 8: `res/values-iw/strings.xml` (Hebrew)

Create folder `res/values-iw/`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">סגירת התראות בשבת</string>
    <string name="service_description">סוגר אוטומטית התראות חירום בשבת כדי שלא יחסמו את המסך.</string>
    <string name="mode_label">מצב סגירה אוטומטית</string>
    <string name="mode_shabbat_only">שבת בלבד</string>
    <string name="mode_shabbat_holidays">שבת + חגים</string>
    <string name="mode_always">תמיד (כולל ימי חול)</string>
    <string name="mode_disabled">מושבת</string>
    <string name="open_accessibility">פתח הגדרות נגישות</string>
    <string name="location_title">מיקום</string>
    <string name="update_location">עדכן מיקום</string>
    <string name="location_format">קו רוחב: %.4f, קו אורך: %.4f</string>
    <string name="location_updated">המיקום עודכן</string>
    <string name="location_failed">לא ניתן לקבל מיקום</string>
    <string name="shabbat_times_title">שבת הקרובה</string>
    <string name="shabbat_times_format">כניסה: %1$s\nיציאה: %2$s</string>
    <string name="shabbat_times_unavailable">לא ניתן לחשב זמנים</string>
    <string name="candle_lighting_label">הדלקת נרות (דקות לפני שקיעה)</string>
    <string name="havdalah_label">הבדלה (דקות אחרי שקיעה)</string>
    <string name="delay_label">השהייה לפני סגירת ההתראה</string>
    <string name="delay_format">%d שניות</string>
    <string name="status_shabbat_only">✅ פעיל — סגירה אוטומטית בשבת</string>
    <string name="status_shabbat_holidays">✅ פעיל — סגירה אוטומטית בשבת ובחגים</string>
    <string name="status_always">✅ פעיל — סגירה אוטומטית תמיד</string>
    <string name="status_disabled">⏸ מושבת</string>
    <string name="status_no_accessibility">⚠️ שירות הנגישות לא מופעל — לחץ על הכפתור למטה</string>
</resources>
```

---

## File 9: `build.gradle.kts` (Module: app) — Dependencies

Add to the `dependencies` block:

```kotlin
implementation("androidx.preference:preference-ktx:1.2.1")
```

---

## After Installation — Setup Checklist

1. **Build & install** the app on your phone
2. Open the app → tap **"Open Accessibility Settings"**
3. Find **"Shabbat Alert Dismisser"** → enable it → confirm
4. Back in the app → tap **"Update Location"** → allow location permission
5. Verify the Shabbat times shown are correct
6. Adjust candle lighting / havdalah minutes to your minhag
7. Set the delay (how many seconds to wait before closing the alert)
8. Done! The app works silently in the background
