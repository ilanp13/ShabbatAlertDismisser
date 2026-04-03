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
