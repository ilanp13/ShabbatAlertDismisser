package com.ilanp13.shabbatalertdismisser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.tab_settings)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
