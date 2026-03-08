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

        Thread {
            val window = HebcalService.fetch(lat, lon, profile.candleMins, havMins)
            if (window != null) {
                val editor = prefs.edit()
                    .putLong("hebcal_candle_ms", window.candleMs)
                    .putLong("hebcal_havdalah_ms", window.havdalahMs)
                    .putLong("hebcal_cache_timestamp_ms", System.currentTimeMillis())
                if (!window.parasha.isNullOrEmpty()) {
                    editor.putString("hebcal_parasha", window.parasha)
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

    private fun setupRegionPicker() {
        btnSelectRegions.setOnClickListener {
            showRegionPickerDialog()
        }
    }

    private fun showRegionPickerDialog() {
        val allRegions = OrefRegions.all
        val savedJson = prefs.getString("alert_regions_selected", "[]") ?: "[]"
        val selectedSet = parseRegionsJson(savedJson).toMutableSet()

        var filteredRegions = allRegions
        var checkedItems = BooleanArray(filteredRegions.size) { i ->
            filteredRegions[i] in selectedSet
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(
            android.R.layout.simple_list_item_1, null
        )
        val searchEditText = android.widget.EditText(requireContext()).apply {
            hint = "Search regions..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
            isFocusable = true
            isFocusableInTouchMode = true
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.alert_regions_label))
            .setCustomTitle(
                android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    addView(
                        android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.alert_regions_label)
                            textSize = 20f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(24, 24, 24, 0)
                            }
                        }
                    )
                    addView(searchEditText)
                }
            )

        builder.setMultiChoiceItems(
            filteredRegions.toTypedArray(),
            checkedItems
        ) { _, which, isChecked ->
            if (isChecked) {
                selectedSet.add(filteredRegions[which])
            } else {
                selectedSet.remove(filteredRegions[which])
            }
        }
            .setNegativeButton(getString(R.string.accessibility_disclosure_button_cancel)) { _, _ ->
                // Dismiss
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newJson = selectedSet.toList().toJsonArray()
                prefs.edit().putString("alert_regions_selected", newJson).apply()
                updateRegionPickerLabel()
            }

        dialog = builder.show()

        // Add search functionality
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                filteredRegions = if (query.isEmpty()) {
                    allRegions
                } else {
                    allRegions.filter { it.contains(query, ignoreCase = true) }
                }

                checkedItems = BooleanArray(filteredRegions.size) { i ->
                    filteredRegions[i] in selectedSet
                }

                // Recreate the dialog with filtered items
                dialog?.dismiss()
                showRegionPickerDialogWithQuery(query)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun showRegionPickerDialogWithQuery(searchQuery: String) {
        val allRegions = OrefRegions.all
        val savedJson = prefs.getString("alert_regions_selected", "[]") ?: "[]"
        val selectedSet = parseRegionsJson(savedJson).toMutableSet()

        val filteredRegions = if (searchQuery.isEmpty()) {
            allRegions
        } else {
            allRegions.filter { it.contains(searchQuery, ignoreCase = true) }
        }

        val checkedItems = BooleanArray(filteredRegions.size) { i ->
            filteredRegions[i] in selectedSet
        }

        val searchEditText = android.widget.EditText(requireContext()).apply {
            hint = "Search regions..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(searchQuery)
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.alert_regions_label))
            .setCustomTitle(
                android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    addView(
                        android.widget.TextView(requireContext()).apply {
                            text = getString(R.string.alert_regions_label)
                            textSize = 20f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(24, 24, 24, 0)
                            }
                        }
                    )
                    addView(searchEditText)
                }
            )

        builder.setMultiChoiceItems(
            filteredRegions.toTypedArray(),
            checkedItems
        ) { _, which, isChecked ->
            if (isChecked) {
                selectedSet.add(filteredRegions[which])
            } else {
                selectedSet.remove(filteredRegions[which])
            }
        }
            .setNegativeButton(getString(R.string.accessibility_disclosure_button_cancel)) { _, _ ->
                // Dismiss
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newJson = selectedSet.toList().toJsonArray()
                prefs.edit().putString("alert_regions_selected", newJson).apply()
                updateRegionPickerLabel()
            }

        dialog = builder.show()

        // Add search functionality
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query != searchQuery) {
                    dialog?.dismiss()
                    showRegionPickerDialogWithQuery(query)
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
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
