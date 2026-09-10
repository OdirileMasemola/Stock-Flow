package com.example.stockflow.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists the user's appearance preference locally (not on the server).
 */
class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Mode {
        SYSTEM,
        LIGHT,
        DARK
    }

    fun getMode(): Mode {
        val raw = prefs.getString(KEY_THEME_MODE, Mode.SYSTEM.name) ?: Mode.SYSTEM.name
        return runCatching { Mode.valueOf(raw) }.getOrDefault(Mode.SYSTEM)
    }

    fun setMode(mode: Mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        applyMode(mode)
    }

    /** Apply the saved preference to the whole process. Call once at app start. */
    fun applySavedMode() {
        applyMode(getMode())
    }

    private fun applyMode(mode: Mode) {
        val nightMode = when (mode) {
            Mode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Mode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Mode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    companion object {
        private const val PREFS_NAME = "stockflow_theme"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
