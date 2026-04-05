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
        setupToggle(R.id.switchShowSeconds, "watch_show_seconds", true)
        setupAppearance()
        setupActivationMode()
        setupOffsets()
        setupBatteryToggles()
        setupHealthToggles()
        setupBannerTimeout()
        setupLongPress()
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

    private fun setupAppearance() {
        // Accent color spinner
        val spinnerColor = findViewById<Spinner>(R.id.spinnerAccentColor)
        val colorNames = arrayOf(
            getString(R.string.watch_accent_gold),
            getString(R.string.watch_accent_blue),
            getString(R.string.watch_accent_green),
            getString(R.string.watch_accent_white),
            getString(R.string.watch_accent_red),
            getString(R.string.watch_accent_purple)
        )
        val colorValues = arrayOf("gold", "blue", "green", "white", "red", "purple")
        spinnerColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, colorNames)
        val savedColor = prefs.getString("watch_accent_color", "gold") ?: "gold"
        spinnerColor.setSelection(colorValues.indexOf(savedColor).coerceAtLeast(0))
        var ignoreColorInit = true
        spinnerColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignoreColorInit) { ignoreColorInit = false; return }
                prefs.edit().putString("watch_accent_color", colorValues[pos]).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Clock size spinner
        val spinnerSize = findViewById<Spinner>(R.id.spinnerClockSize)
        val sizeNames = arrayOf(
            getString(R.string.watch_clock_small),
            getString(R.string.watch_clock_medium),
            getString(R.string.watch_clock_large)
        )
        val sizeValues = arrayOf("small", "medium", "large")
        spinnerSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizeNames)
        val savedSize = prefs.getString("watch_clock_size", "medium") ?: "medium"
        spinnerSize.setSelection(sizeValues.indexOf(savedSize).coerceAtLeast(0))
        var ignoreSizeInit = true
        spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignoreSizeInit) { ignoreSizeInit = false; return }
                prefs.edit().putString("watch_clock_size", sizeValues[pos]).apply()
                WatchSyncService.syncSettings(this@WatchSettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Show/hide toggles
        setupToggle(R.id.switchShowBattery, "watch_show_battery", true)
        setupToggle(R.id.switchShowHebrewDate, "watch_show_hebrew_date", true)
        setupToggle(R.id.switchShowParasha, "watch_show_parasha", true)
        setupToggle(R.id.switchShowHavdalah, "watch_show_havdalah", true)
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

    private fun setupHealthToggles() {
        setupToggle(R.id.switchDisableHeartRate, "watch_disable_heart_rate", true)
        setupToggle(R.id.switchDisableSpo2, "watch_disable_spo2", true)
        setupToggle(R.id.switchDisableStepCounter, "watch_disable_step_counter", true)
        setupToggle(R.id.switchDisableBodySensors, "watch_disable_body_sensors", true)
    }

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
            Toast.makeText(this, getString(R.string.watch_unlock_sent), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
