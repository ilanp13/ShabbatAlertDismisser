package com.ilanp13.shabbatalertdismisser

import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
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
import org.json.JSONArray

class SettingsFragment : Fragment() {

    private lateinit var radioMode: RadioGroup
    private lateinit var tvLocation: TextView
    private lateinit var seekDelay: SeekBar
    private lateinit var tvDelay: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnUpdateLoc: Button
    private lateinit var spinnerMinhag: Spinner
    private lateinit var radioEndShabbat: RadioGroup
    private lateinit var switchNotif: SwitchMaterial
    private lateinit var radioScreenOn: RadioGroup
    private lateinit var radioCyclerMode: RadioGroup
    private lateinit var seekCyclerDuration: SeekBar
    private lateinit var tvCyclerDuration: TextView
    private lateinit var radioHistoryGrouping: RadioGroup
    private lateinit var seekPollFrequency: SeekBar
    private lateinit var tvPollFrequency: TextView
    private lateinit var radioMapZoom: RadioGroup
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var tvSelectedRegions: TextView
    private lateinit var btnSelectRegions: Button
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        radioMode = view.findViewById(R.id.radioMode)
        tvLocation = view.findViewById(R.id.tvLocation)
        seekDelay = view.findViewById(R.id.seekDelay)
        tvDelay = view.findViewById(R.id.tvDelay)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        btnUpdateLoc = view.findViewById(R.id.btnUpdateLocation)
        spinnerMinhag = view.findViewById(R.id.spinnerMinhag)
        radioEndShabbat = view.findViewById(R.id.radioEndShabbat)
        switchNotif = view.findViewById(R.id.switchNotification)
        radioScreenOn = view.findViewById(R.id.radioScreenOn)
        radioCyclerMode = view.findViewById(R.id.radioCyclerMode)
        seekCyclerDuration = view.findViewById(R.id.seekCyclerDuration)
        tvCyclerDuration = view.findViewById(R.id.tvCyclerDuration)
        radioHistoryGrouping = view.findViewById(R.id.radioHistoryGrouping)
        seekPollFrequency = view.findViewById(R.id.seekPollFrequency)
        tvPollFrequency = view.findViewById(R.id.tvPollFrequency)
        radioMapZoom = view.findViewById(R.id.radioMapZoom)
        spinnerLanguage = view.findViewById(R.id.spinnerLanguage)
        spinnerTheme = view.findViewById(R.id.spinnerTheme)
        tvSelectedRegions = view.findViewById(R.id.tvSelectedRegions)
        btnSelectRegions = view.findViewById(R.id.btnSelectRegions)
        setupModeRadio()
        setupDelaySeekbar()
        setupMinhagSpinner()
        setupEndShabbatRadio()
        setupButtons()
        setupNotificationSwitch()
        setupScreenOnRadio()
        setupCyclerSettings()
        setupHistoryGrouping()
        setupPollFrequency()
        setupMapZoom()
        setupLanguageSpinner()
        setupThemeSpinner()
        setupRegionPicker()

        updateLocationText()
        updateRegionPickerLabel()
    }

    override fun onResume() {
        super.onResume()
        updateModeRadioButtonsState()
    }

    private fun setupModeRadio() {
        // Migrate old "shabbat_only" to "shabbat_holidays"
        if (prefs.getString("mode", "shabbat_holidays") == "shabbat_only") {
            prefs.edit().putString("mode", "shabbat_holidays").apply()
        }
        when (prefs.getString("mode", "shabbat_holidays")) {
            "shabbat_holidays" -> radioMode.check(R.id.radioShabbatAndHolidays)
            "always" -> radioMode.check(R.id.radioAlways)
            "disabled" -> radioMode.check(R.id.radioDisabled)
        }
        radioMode.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("mode", when (id) {
                R.id.radioShabbatAndHolidays -> "shabbat_holidays"
                R.id.radioAlways -> "always"
                R.id.radioDisabled -> "disabled"
                else -> "shabbat_holidays"
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

    private fun setupCyclerSettings() {
        // Setup cycler mode radio group
        radioCyclerMode.check(when (prefs.getString("cycler_mode", "off")) {
            "shabbat" -> R.id.radioCyclerShabbat
            "always" -> R.id.radioCyclerAlways
            "live" -> R.id.radioCyclerLive
            else -> R.id.radioCyclerOff
        })
        radioCyclerMode.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("cycler_mode", when (id) {
                R.id.radioCyclerShabbat -> "shabbat"
                R.id.radioCyclerAlways -> "always"
                R.id.radioCyclerLive -> "live"
                else -> "off"
            }).apply()
        }

        // Setup cycler duration seekbar (3-10 seconds, default 5)
        val currentDuration = prefs.getInt("cycler_duration_seconds", 5)
        seekCyclerDuration.progress = currentDuration - 3
        tvCyclerDuration.text = getString(R.string.cycler_duration_format, currentDuration)
        seekCyclerDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val s = progress + 3
                tvCyclerDuration.text = getString(R.string.cycler_duration_format, s)
                prefs.edit().putInt("cycler_duration_seconds", s).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupHistoryGrouping() {
        radioHistoryGrouping.check(
            if (prefs.getString("history_grouping", "tiered") == "all") R.id.radioGroupingAll
            else R.id.radioGroupingTiered
        )
        radioHistoryGrouping.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("history_grouping", when (id) {
                R.id.radioGroupingAll -> "all"
                else -> "tiered"
            }).apply()
        }
    }

    private fun setupPollFrequency() {
        // Slider: 0=off, 1=5s, 2=10s, ... 12=60s
        val currentSeconds = prefs.getInt("poll_frequency_seconds", 30)
        val progress = if (currentSeconds == 0) 0 else (currentSeconds / 5).coerceIn(1, 12)
        seekPollFrequency.progress = progress
        tvPollFrequency.text = if (currentSeconds == 0) getString(R.string.poll_frequency_off)
            else getString(R.string.poll_frequency_format, currentSeconds)

        seekPollFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val seconds = if (p == 0) 0 else p * 5
                tvPollFrequency.text = if (seconds == 0) getString(R.string.poll_frequency_off)
                    else getString(R.string.poll_frequency_format, seconds)
                prefs.edit().putInt("poll_frequency_seconds", seconds).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupMapZoom() {
        radioMapZoom.check(when (prefs.getString("map_zoom_mode", "off")) {
            "click" -> R.id.radioZoomClick
            "auto" -> R.id.radioZoomAuto
            else -> R.id.radioZoomOff
        })
        radioMapZoom.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("map_zoom_mode", when (id) {
                R.id.radioZoomClick -> "click"
                R.id.radioZoomAuto -> "auto"
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

        Thread {
            val result = HebcalService.fetch(lat, lon, profile.candleMins, havMins)
            if (result != null) {
                val now = System.currentTimeMillis()
                val next = result.nextWindow(now) ?: result.windows.lastOrNull()
                val editor = prefs.edit()
                    .putString("hebcal_windows_json", HebcalService.windowsToJson(result.windows))
                if (next != null) {
                    editor.putLong("hebcal_candle_ms", next.candleMs)
                    editor.putLong("hebcal_havdalah_ms", next.havdalahMs)
                }
                editor
                    .putLong("hebcal_cache_timestamp_ms", now)
                if (!result.parasha.isNullOrEmpty()) {
                    editor.putString("hebcal_parasha", result.parasha)
                }
                editor.apply()
            }
        }.start()
    }

    private fun updateLocationText() {
        tvLocation.text = getString(R.string.location_format,
            prefs.getFloat("latitude", 31.7683f),
            prefs.getFloat("longitude", 35.2137f))
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

    private fun setupRegionPicker() {
        btnSelectRegions.setOnClickListener {
            showRegionPickerDialog()
        }
    }

    /** Shared state for the region picker across dialog recreations */
    private var regionPickerSelectedSet = mutableSetOf<String>()

    private fun showRegionPickerDialog() {
        regionPickerSelectedSet = parseRegionsJson(
            prefs.getString("alert_regions_selected", "[]") ?: "[]"
        ).toMutableSet()

        val allRegions = OrefRegions.all
        // Sort: selected items first, then alphabetical
        val sortedRegions = allRegions.sortedByDescending { it in regionPickerSelectedSet }

        // Build custom layout: search EditText + ListView
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val searchEditText = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.search_regions_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(48, 16, 48, 8) }
        }
        container.addView(searchEditText)

        val listView = android.widget.ListView(requireContext()).apply {
            choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        container.addView(listView)

        // Current displayed list (changes with search filter)
        val displayedRegions = mutableListOf<String>().apply { addAll(sortedRegions) }
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_multiple_choice,
            displayedRegions
        )
        listView.adapter = adapter

        // Set initial checked state
        fun syncCheckedState() {
            for (i in displayedRegions.indices) {
                listView.setItemChecked(i, displayedRegions[i] in regionPickerSelectedSet)
            }
        }
        syncCheckedState()

        // Handle check/uncheck
        listView.setOnItemClickListener { _, _, position, _ ->
            val region = displayedRegions[position]
            if (listView.isItemChecked(position)) {
                regionPickerSelectedSet.add(region)
            } else {
                regionPickerSelectedSet.remove(region)
            }
        }

        // Filter in-place on text change (no dialog recreation)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                displayedRegions.clear()
                if (query.isEmpty()) {
                    displayedRegions.addAll(sortedRegions)
                } else {
                    displayedRegions.addAll(
                        sortedRegions.filter { it.contains(query, ignoreCase = true) }
                    )
                }
                adapter.notifyDataSetChanged()
                listView.post { syncCheckedState() }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.alert_regions_label))
            .setView(container)
            .setNegativeButton(getString(R.string.accessibility_disclosure_button_cancel), null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString("alert_regions_selected",
                    regionPickerSelectedSet.toList().toJsonArray()).apply()
                updateRegionPickerLabel()
            }
            .create()

        // Let the dialog resize when keyboard appears
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        dialog.show()

        // Show keyboard after dialog is visible
        searchEditText.requestFocus()
        searchEditText.postDelayed({
            if (!isAdded) return@postDelayed
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun updateRegionPickerLabel() {
        val json = prefs.getString("alert_regions_selected", "[]") ?: "[]"
        val selected = parseRegionsJson(json)

        tvSelectedRegions.text = if (selected.isEmpty()) {
            getString(R.string.alert_regions_all)
        } else {
            getString(R.string.alert_regions_selected_fmt, selected.size)
        }
    }

    private fun parseRegionsJson(json: String): List<String> {
        return try {
            val array = org.json.JSONArray(json)
            val regions = mutableListOf<String>()
            for (i in 0 until array.length()) {
                regions.add(array.getString(i))
            }
            regions
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun List<String>.toJsonArray(): String {
        val array = org.json.JSONArray()
        for (region in this) {
            array.put(region)
        }
        return array.toString()
    }

    abstract class SimpleSpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        abstract fun onItemSelected(pos: Int)
        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = onItemSelected(pos)
        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
    }
}
