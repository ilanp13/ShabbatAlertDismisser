package com.ilanp13.shabbatalertdismisser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.text.SpannableString
import android.text.style.URLSpan
import android.widget.Button
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show setup or main layout based on accessibility status
        if (!isAccessibilityServiceEnabled()) {
            showSetupLayout()
        } else {
            showMainLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if accessibility status changed and swap layouts if needed
        if (isAccessibilityServiceEnabled()) {
            // Check if we're currently showing setup layout
            if (findViewById<ViewPager2>(R.id.viewPager) == null) {
                // Currently showing setup layout, switch to main
                showMainLayout()
            }
        } else {
            // Check if we're currently showing main layout
            if (findViewById<ViewPager2>(R.id.viewPager) != null) {
                // Currently showing main layout, switch to setup
                showSetupLayout()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val cn = android.content.ComponentName(this, AlertDismissService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(cn.flattenToString())
    }

    // ── Setup Layout ───────────────────────────────────────────────────────────

    private fun showSetupLayout() {
        setContentView(R.layout.activity_setup)

        val btnAccessibility = findViewById<Button>(R.id.btnAccessibilitySetup)
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Make Privacy Policy clickable (use Hebrew or English based on device language)
        val disclosureText = findViewById<TextView>(R.id.tvDisclosureText)
        disclosureText.movementMethod = LinkMovementMethod.getInstance()
        val text = disclosureText.text.toString()
        val spannableString = SpannableString(text)

        // Detect device language
        val isHebrew = android.os.LocaleList.getDefault()[0].language == "he"
        val privacyPolicyUrl = if (isHebrew) {
            "https://github.com/ilanp13/ShabbatAlertDismisser/blob/main/PRIVACY_POLICY_HE.md"
        } else {
            "https://github.com/ilanp13/ShabbatAlertDismisser/blob/main/PRIVACY_POLICY.md"
        }

        // Find and link the privacy policy text
        val linkText = if (isHebrew) "מדיניות הפרטיות" else "Privacy Policy"
        val privacyPolicyStart = text.indexOf(linkText)
        if (privacyPolicyStart >= 0) {
            val privacyLink = object : URLSpan(privacyPolicyUrl) {
                override fun onClick(widget: android.view.View) {
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
        disclosureText.text = spannableString
    }

    private fun showMainLayout() {
        setContentView(R.layout.activity_main)

        val pager = findViewById<ViewPager2>(R.id.viewPager)
        val tabs = findViewById<TabLayout>(R.id.tabLayout)

        pager.adapter = MainPagerAdapter(this)

        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.tab_status)
                1 -> getString(R.string.tab_settings)
                2 -> getString(R.string.tab_history)
                else -> ""
            }
        }.attach()

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Apply saved language preference
        val savedLang = prefs.getString("app_language", "system") ?: "system"
        applyLanguagePreference(savedLang)
    }

    private fun applyLanguagePreference(langCode: String) {
        val locales = if (langCode == "system") {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

}
