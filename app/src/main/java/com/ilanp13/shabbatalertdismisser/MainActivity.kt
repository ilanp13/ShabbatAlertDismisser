package com.ilanp13.shabbatalertdismisser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import com.google.android.material.switchmaterial.SwitchMaterial
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

        val radioMode        = findViewById<RadioGroup>(R.id.radioMode)
        val tvStatus         = findViewById<TextView>(R.id.tvStatus)
        val tvLocation       = findViewById<TextView>(R.id.tvLocation)
        val tvShabbatTimes   = findViewById<TextView>(R.id.tvShabbatTimes)
        val seekDelay        = findViewById<SeekBar>(R.id.seekDelay)
        val tvDelay          = findViewById<TextView>(R.id.tvDelay)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnUpdateLoc     = findViewById<Button>(R.id.btnUpdateLocation)
        val spinnerMinhag    = findViewById<Spinner>(R.id.spinnerMinhag)
        val radioEndShabbat  = findViewById<RadioGroup>(R.id.radioEndShabbat)
        val tvSyncStatus     = findViewById<TextView>(R.id.tvSyncStatus)

        // ── Mode radio ────────────────────────────────────────────────────────
        when (prefs.getString("mode", "shabbat_only")) {
            "shabbat_only"      -> radioMode.check(R.id.radioShabbatOnly)
            "shabbat_holidays"  -> radioMode.check(R.id.radioShabbatAndHolidays)
            "always"            -> radioMode.check(R.id.radioAlways)
            "disabled"          -> radioMode.check(R.id.radioDisabled)
        }
        radioMode.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("mode", when (id) {
                R.id.radioShabbatOnly         -> "shabbat_only"
                R.id.radioShabbatAndHolidays  -> "shabbat_holidays"
                R.id.radioAlways              -> "always"
                R.id.radioDisabled            -> "disabled"
                else                          -> "shabbat_only"
            }).apply()
            updateStatusText(tvStatus)
        }

        // ── Delay seekbar (5–60 s) ────────────────────────────────────────────
        val currentDelay = prefs.getInt("delay_seconds", 10)
        seekDelay.progress = currentDelay - 5
        tvDelay.text = getString(R.string.delay_format, currentDelay)
        seekDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val s = progress + 5
                tvDelay.text = getString(R.string.delay_format, s)
                prefs.edit().putInt("delay_seconds", s).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ── Minhag spinner ────────────────────────────────────────────────────
        val profiles = MinhagProfiles.all
        spinnerMinhag.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            profiles.map { it.display })
        val savedKey = prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz"
        spinnerMinhag.setSelection(profiles.indexOfFirst { it.key == savedKey }.coerceAtLeast(0))
        spinnerMinhag.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onItemSelected(pos: Int) {
                val profile = profiles[pos]
                prefs.edit().putString("minhag_key", profile.key).apply()
                applyMinhag(profile)
                syncHebcal(tvShabbatTimes, tvSyncStatus)
            }
        }

        // ── End-of-Shabbat: Gra or Rabenu Tam ────────────────────────────────
        radioEndShabbat.check(
            if (prefs.getBoolean("use_rabenu_tam", false)) R.id.radioRabenTam else R.id.radioGra
        )
        radioEndShabbat.setOnCheckedChangeListener { _, id ->
            prefs.edit().putBoolean("use_rabenu_tam", id == R.id.radioRabenTam).apply()
            applyMinhag(MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz"))
            syncHebcal(tvShabbatTimes, tvSyncStatus)
        }

        // ── Buttons ───────────────────────────────────────────────────────────
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnBatteryOpt).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        btnUpdateLoc.setOnClickListener {
            requestLocationUpdate(tvLocation, tvShabbatTimes, tvSyncStatus)
        }

        // ── Persistent notification toggle ────────────────────────────────────
        val switchNotif = findViewById<SwitchMaterial>(R.id.switchNotification)
        switchNotif.isChecked = prefs.getBoolean("show_notification", true)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_notification", isChecked).apply()
        }

        // ── Keep screen on radio ──────────────────────────────────────────────
        val radioScreenOn = findViewById<RadioGroup>(R.id.radioScreenOn)
        radioScreenOn.check(when (prefs.getString("screen_on_mode", "off")) {
            "shabbat" -> R.id.radioScreenOnShabbat
            "always"  -> R.id.radioScreenOnAlways
            else      -> R.id.radioScreenOnOff
        })
        radioScreenOn.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("screen_on_mode", when (id) {
                R.id.radioScreenOnShabbat -> "shabbat"
                R.id.radioScreenOnAlways  -> "always"
                else                      -> "off"
            }).apply()
            updateStatusText(tvStatus)
        }

        // ── Request notification permission (Android 13+) ────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // ── Initial state ─────────────────────────────────────────────────────
        updateLocationText(tvLocation)
        updateShabbatTimes(tvShabbatTimes)
        updateStatusText(tvStatus)
        updateSyncStatusText(tvSyncStatus)
    }

    override fun onResume() {
        super.onResume()
        // Refresh accessibility status every time we return (e.g. from accessibility settings)
        updateStatusText(findViewById(R.id.tvStatus))
        // Auto-refresh if Hebcal cache has expired (havdalah is in the past)
        if (prefs.getLong("hebcal_havdalah_ms", 0) < System.currentTimeMillis()) {
            syncHebcal(
                findViewById(R.id.tvShabbatTimes),
                findViewById(R.id.tvSyncStatus)
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyMinhag(profile: MinhagProfiles.Profile) {
        val useRt = prefs.getBoolean("use_rabenu_tam", false)
        prefs.edit()
            .putInt("candle_lighting_minutes", profile.candleMins)
            .putInt("havdalah_minutes", if (useRt) profile.rtMins else profile.graMins)
            .apply()
    }

    private fun syncHebcal(tvTimes: TextView, tvStatus: TextView) {
        val lat  = prefs.getFloat("latitude",  31.7683f).toDouble()
        val lon  = prefs.getFloat("longitude", 35.2137f).toDouble()
        val profile = MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz")
        val useRt   = prefs.getBoolean("use_rabenu_tam", false)
        val havMins = if (useRt) profile.rtMins else profile.graMins

        tvStatus.text = getString(R.string.sync_status_syncing)

        Thread {
            val window = HebcalService.fetch(lat, lon, profile.candleMins, havMins)
            runOnUiThread {
                if (window != null) {
                    prefs.edit()
                        .putLong("hebcal_candle_ms",   window.candleMs)
                        .putLong("hebcal_havdalah_ms", window.havdalahMs)
                        .apply()
                    tvStatus.text = getString(R.string.sync_status_synced)
                } else {
                    tvStatus.text = getString(R.string.sync_status_offline)
                }
                updateShabbatTimes(tvTimes)
            }
        }.start()
    }

    private fun updateSyncStatusText(tv: TextView) {
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        tv.text = if (havdalahMs > System.currentTimeMillis() - 7 * 86_400_000L) {
            getString(R.string.sync_status_synced)
        } else {
            getString(R.string.sync_status_offline)
        }
    }

    private fun updateStatusText(tv: TextView) {
        val mode = prefs.getString("mode", "shabbat_only")
        val base = when {
            !isAccessibilityServiceEnabled()  -> getString(R.string.status_no_accessibility)
            mode == "disabled"                -> getString(R.string.status_disabled)
            mode == "always"                  -> getString(R.string.status_always)
            mode == "shabbat_holidays"        -> getString(R.string.status_shabbat_holidays)
            else                              -> getString(R.string.status_shabbat_only)
        }
        val screenOnMode = prefs.getString("screen_on_mode", "off")
        val screenOnLine = when {
            !isAccessibilityServiceEnabled() || mode == "disabled" -> null
            screenOnMode == "shabbat" -> getString(R.string.status_screen_on_shabbat)
            screenOnMode == "always"  -> getString(R.string.status_screen_on_always)
            else -> null
        }
        tv.text = if (screenOnLine != null) "$base\n$screenOnLine" else base
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val cn = android.content.ComponentName(this, AlertDismissService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(cn.flattenToString())
    }

    private fun updateLocationText(tv: TextView) {
        tv.text = getString(R.string.location_format,
            prefs.getFloat("latitude",  31.7683f),
            prefs.getFloat("longitude", 35.2137f))
    }

    private fun updateShabbatTimes(tv: TextView) {
        val candleMs   = prefs.getLong("hebcal_candle_ms",   0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        val now = System.currentTimeMillis()

        if (candleMs > 0 && havdalahMs > now - 7 * 86_400_000L) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            // Round havdalah to nearest minute (≥30 s rounds up — matches published calendars)
            val havdalahDisplayMs = ((havdalahMs + 30_000L) / 60_000L) * 60_000L
            tv.text = getString(R.string.shabbat_times_format,
                fmt.format(Date(candleMs)),
                fmt.format(Date(havdalahDisplayMs)))
            return
        }

        // Offline fallback
        val lat     = prefs.getFloat("latitude",  31.7683f).toDouble()
        val lon     = prefs.getFloat("longitude", 35.2137f).toDouble()
        val candle  = prefs.getInt("candle_lighting_minutes", 18)
        val havdala = prefs.getInt("havdalah_minutes", 40)
        val times   = ShabbatCalculator(lat, lon).getShabbatTimes(candle, havdala)
        if (times != null) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            val havdalahMs = ((times.second.time.time + 30_000L) / 60_000L) * 60_000L
            tv.text = getString(R.string.shabbat_times_format,
                fmt.format(times.first.time),
                fmt.format(Date(havdalahMs)))
        } else {
            tv.text = getString(R.string.shabbat_times_unavailable)
        }
    }

    private fun requestLocationUpdate(tvLoc: TextView, tvTimes: TextView, tvStatus: TextView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (loc != null) {
            prefs.edit()
                .putFloat("latitude",  loc.latitude.toFloat())
                .putFloat("longitude", loc.longitude.toFloat())
                .apply()
            updateLocationText(tvLoc)
            syncHebcal(tvTimes, tvStatus)
            Toast.makeText(this, getString(R.string.location_updated), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.location_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 100 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationUpdate(
                findViewById(R.id.tvLocation),
                findViewById(R.id.tvShabbatTimes),
                findViewById(R.id.tvSyncStatus)
            )
        }
    }

    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onItemSelected(pos: Int)
        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = onItemSelected(pos)
        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
    }
}
