package com.ilanp13.shabbatalertdismisser

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
