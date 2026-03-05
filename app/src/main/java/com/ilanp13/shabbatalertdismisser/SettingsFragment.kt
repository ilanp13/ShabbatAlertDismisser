package com.ilanp13.shabbatalertdismisser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.text.SpannableString
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var radioMode: RadioGroup
    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvShabbatTimes: TextView
    private lateinit var seekDelay: SeekBar
    private lateinit var tvDelay: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnUpdateLoc: Button
    private lateinit var spinnerMinhag: Spinner
    private lateinit var radioEndShabbat: RadioGroup
    private lateinit var tvSyncStatus: TextView
    private lateinit var switchNotif: SwitchMaterial
    private lateinit var radioScreenOn: RadioGroup
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerTheme: Spinner

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioMode = view.findViewById(R.id.radioMode)
        tvLocation = view.findViewById(R.id.tvLocation)
        tvShabbatTimes = view.findViewById(R.id.tvShabbatTimes)
        seekDelay = view.findViewById(R.id.seekDelay)
        tvDelay = view.findViewById(R.id.tvDelay)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        btnUpdateLoc = view.findViewById(R.id.btnUpdateLocation)
        spinnerMinhag = view.findViewById(R.id.spinnerMinhag)
        radioEndShabbat = view.findViewById(R.id.radioEndShabbat)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        switchNotif = view.findViewById(R.id.switchNotification)
        radioScreenOn = view.findViewById(R.id.radioScreenOn)
        spinnerLanguage = view.findViewById(R.id.spinnerLanguage)
        spinnerTheme = view.findViewById(R.id.spinnerTheme)

        setupModeRadio()
        setupDelaySeekbar()
        setupMinhagSpinner()
        setupEndShabbatRadio()
        setupButtons()
        setupNotificationSwitch()
        setupScreenOnRadio()
        setupLanguageSpinner()
        setupThemeSpinner()

        updateLocationText()
        updateShabbatTimes()
        updateSyncStatusText()
    }

    override fun onResume() {
        super.onResume()
        updateModeRadioButtonsState()
    }

    private fun setupModeRadio() {
        when (prefs.getString("mode", "shabbat_only")) {
            "shabbat_only" -> radioMode.check(R.id.radioShabbatOnly)
            "shabbat_holidays" -> radioMode.check(R.id.radioShabbatAndHolidays)
            "always" -> radioMode.check(R.id.radioAlways)
            "disabled" -> radioMode.check(R.id.radioDisabled)
        }
        radioMode.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("mode", when (id) {
                R.id.radioShabbatOnly -> "shabbat_only"
                R.id.radioShabbatAndHolidays -> "shabbat_holidays"
                R.id.radioAlways -> "always"
                R.id.radioDisabled -> "disabled"
                else -> "shabbat_only"
            }).apply()
        }
        updateModeRadioButtonsState()
    }

    private fun setupDelaySeekbar() {
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
    }

    private fun setupMinhagSpinner() {
        val profiles = MinhagProfiles.all
        spinnerMinhag.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            profiles.map { it.display })
        val savedKey = prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz"
        spinnerMinhag.setSelection(profiles.indexOfFirst { it.key == savedKey }.coerceAtLeast(0))
        spinnerMinhag.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onItemSelected(pos: Int) {
                val profile = profiles[pos]
                prefs.edit().putString("minhag_key", profile.key).apply()
                applyMinhag(profile)
                syncHebcal()
            }
        }
    }

    private fun setupEndShabbatRadio() {
        radioEndShabbat.check(
            if (prefs.getBoolean("use_rabenu_tam", false)) R.id.radioRabenTam else R.id.radioGra
        )
        radioEndShabbat.setOnCheckedChangeListener { _, id ->
            prefs.edit().putBoolean("use_rabenu_tam", id == R.id.radioRabenTam).apply()
            applyMinhag(MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz"))
            syncHebcal()
        }
    }

    private fun setupButtons() {
        btnAccessibility.setOnClickListener {
            showAccessibilityDisclosure()
        }
        view?.findViewById<Button>(R.id.btnBatteryOpt)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        btnUpdateLoc.setOnClickListener {
            requestLocationUpdate()
        }
    }

    private fun setupNotificationSwitch() {
        switchNotif.isChecked = prefs.getBoolean("show_notification", true)
        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_notification", isChecked).apply()
        }
    }

    private fun setupScreenOnRadio() {
        radioScreenOn.check(when (prefs.getString("screen_on_mode", "off")) {
            "shabbat" -> R.id.radioScreenOnShabbat
            "always" -> R.id.radioScreenOnAlways
            else -> R.id.radioScreenOnOff
        })
        radioScreenOn.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("screen_on_mode", when (id) {
                R.id.radioScreenOnShabbat -> "shabbat"
                R.id.radioScreenOnAlways -> "always"
                else -> "off"
            }).apply()
        }
    }

    private fun setupLanguageSpinner() {
        val languages = listOf(
            getString(R.string.language_system),
            getString(R.string.language_english),
            getString(R.string.language_hebrew)
        )
        spinnerLanguage.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            languages)

        val savedLang = prefs.getString("app_language", "system") ?: "system"
        val selection = when (savedLang) {
            "en" -> 1
            "he" -> 2
            else -> 0
        }
        spinnerLanguage.setSelection(selection)
        spinnerLanguage.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onItemSelected(pos: Int) {
                val langValue = when (pos) {
                    1 -> "en"
                    2 -> "he"
                    else -> "system"
                }
                prefs.edit().putString("app_language", langValue).apply()
                applyLanguage(langValue)
            }
        }
    }

    private fun applyLanguage(langCode: String) {
        val locales = if (langCode == "system") {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun setupThemeSpinner() {
        val themes = listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        spinnerTheme.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            themes)

        val savedTheme = prefs.getString("app_theme", "system") ?: "system"
        val selection = when (savedTheme) {
            "light" -> 1
            "dark" -> 2
            else -> 0
        }
        spinnerTheme.setSelection(selection)
        spinnerTheme.onItemSelectedListener = object : SimpleSpinnerListener() {
            override fun onItemSelected(pos: Int) {
                val themeValue = when (pos) {
                    1 -> "light"
                    2 -> "dark"
                    else -> "system"
                }
                prefs.edit().putString("app_theme", themeValue).apply()
                applyTheme(themeValue)
            }
        }
    }

    private fun applyTheme(themeValue: String) {
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyMinhag(profile: MinhagProfiles.Profile) {
        val useRt = prefs.getBoolean("use_rabenu_tam", false)
        prefs.edit()
            .putInt("candle_lighting_minutes", profile.candleMins)
            .putInt("havdalah_minutes", if (useRt) profile.rtMins else profile.graMins)
            .apply()
    }

    private fun syncHebcal() {
        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val profile = MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz")
        val useRt = prefs.getBoolean("use_rabenu_tam", false)
        val havMins = if (useRt) profile.rtMins else profile.graMins

        tvSyncStatus.text = getString(R.string.sync_status_syncing)

        Thread {
            val window = HebcalService.fetch(lat, lon, profile.candleMins, havMins)
            view?.post {
                if (window != null) {
                    prefs.edit()
                        .putLong("hebcal_candle_ms", window.candleMs)
                        .putLong("hebcal_havdalah_ms", window.havdalahMs)
                        .apply()
                    tvSyncStatus.text = getString(R.string.sync_status_synced)
                } else {
                    tvSyncStatus.text = getString(R.string.sync_status_offline)
                }
                updateShabbatTimes()
            }
        }.start()
    }

    private fun updateLocationText() {
        tvLocation.text = getString(R.string.location_format,
            prefs.getFloat("latitude", 31.7683f),
            prefs.getFloat("longitude", 35.2137f))
    }

    private fun updateShabbatTimes() {
        val candleMs = prefs.getLong("hebcal_candle_ms", 0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        val now = System.currentTimeMillis()

        if (candleMs > 0 && havdalahMs > now - 7 * 86_400_000L) {
            val fmt = java.text.SimpleDateFormat("EEEE HH:mm", java.util.Locale.getDefault())
            val havdalahDisplayMs = ((havdalahMs + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(java.util.Date(candleMs)),
                fmt.format(java.util.Date(havdalahDisplayMs)))
            return
        }

        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val candle = prefs.getInt("candle_lighting_minutes", 18)
        val havdala = prefs.getInt("havdalah_minutes", 40)
        val times = ShabbatCalculator(lat, lon).getShabbatTimes(candle, havdala)
        if (times != null) {
            val fmt = java.text.SimpleDateFormat("EEEE HH:mm", java.util.Locale.getDefault())
            val havdalahMs = ((times.second.time.time + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(times.first.time),
                fmt.format(java.util.Date(havdalahMs)))
        } else {
            tvShabbatTimes.text = getString(R.string.shabbat_times_unavailable)
        }
    }

    private fun updateSyncStatusText() {
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        tvSyncStatus.text = if (havdalahMs > System.currentTimeMillis() - 7 * 86_400_000L) {
            getString(R.string.sync_status_synced)
        } else {
            getString(R.string.sync_status_offline)
        }
    }

    private fun requestLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }
        val lm = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (loc != null) {
            prefs.edit()
                .putFloat("latitude", loc.latitude.toFloat())
                .putFloat("longitude", loc.longitude.toFloat())
                .apply()
            updateLocationText()
            syncHebcal()
            Toast.makeText(requireContext(), getString(R.string.location_updated), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.location_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModeRadioButtonsState() {
        val enabled = isAccessibilityServiceEnabled()
        val buttons = listOf(
            view?.findViewById<RadioButton>(R.id.radioShabbatOnly),
            view?.findViewById<RadioButton>(R.id.radioShabbatAndHolidays),
            view?.findViewById<RadioButton>(R.id.radioAlways)
        )
        for (btn in buttons) {
            btn?.isEnabled = enabled
            btn?.alpha = if (enabled) 1.0f else 0.5f
        }
        view?.findViewById<RadioButton>(R.id.radioDisabled)?.isEnabled = true
        view?.findViewById<RadioButton>(R.id.radioDisabled)?.alpha = 1.0f
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val cn = android.content.ComponentName(requireContext(), AlertDismissService::class.java)
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(cn.flattenToString())
    }

    private fun showAccessibilityDisclosure() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.accessibility_disclosure_title))
            .setMessage(getString(R.string.accessibility_disclosure_text))
            .setNegativeButton(getString(R.string.accessibility_disclosure_button_cancel)) { _, _ ->
                // Just dismiss
            }
            .setPositiveButton(getString(R.string.accessibility_disclosure_button_settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()

        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        if (messageView != null) {
            messageView.movementMethod = LinkMovementMethod.getInstance()
            val text = messageView.text.toString()
            val spannableString = SpannableString(text)

            val isHebrew = android.os.LocaleList.getDefault()[0].language == "he"
            val privacyPolicyUrl = if (isHebrew) {
                "https://github.com/ilanp13/ShabbatAlertDismisser/blob/main/PRIVACY_POLICY_HE.md"
            } else {
                "https://github.com/ilanp13/ShabbatAlertDismisser/blob/main/PRIVACY_POLICY.md"
            }

            val linkText = if (isHebrew) "מדיניות הפרטיות" else "Privacy Policy"
            val privacyPolicyStart = text.indexOf(linkText)
            if (privacyPolicyStart >= 0) {
                val privacyLink = object : URLSpan(privacyPolicyUrl) {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(privacyPolicyUrl)
                        })
                    }
                }
                spannableString.setSpan(
                    privacyLink,
                    privacyPolicyStart,
                    privacyPolicyStart + linkText.length,
                    0
                )
            }
            messageView.text = spannableString
        }
    }

    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onItemSelected(pos: Int)
        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = onItemSelected(pos)
        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
    }
}
