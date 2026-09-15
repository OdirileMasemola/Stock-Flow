package com.example.stockflow.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Persists the user's app language preference locally (not on the server).
 * Uses AppCompat per-app locales so the choice applies across the whole process.
 */
class LanguagePreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLanguageTag(): String {
        val raw = prefs.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
            ?: DEFAULT_LANGUAGE_TAG
        return normalizeTag(raw)
    }

    fun setLanguageTag(tag: String) {
        val normalized = normalizeTag(tag)
        prefs.edit().putString(KEY_LANGUAGE_TAG, normalized).apply()
        applyLanguageTag(normalized)
    }

    /** Apply the saved preference to the whole process. Call once at app start. */
    fun applySavedLanguage() {
        applyLanguageTag(getLanguageTag())
    }

    companion object {
        private const val PREFS_NAME = "stockflow_language"
        private const val KEY_LANGUAGE_TAG = "language_tag"

        const val TAG_ENGLISH = "en"
        const val TAG_ISIZULU = "zu"
        const val TAG_SESOTHO = "st"

        /** Default app language when nothing has been saved yet. */
        const val DEFAULT_LANGUAGE_TAG = TAG_ENGLISH

        /** Languages that can be selected and switched to in Stage 1. */
        val SUPPORTED_TAGS: Set<String> = setOf(TAG_ENGLISH, TAG_ISIZULU, TAG_SESOTHO)

        fun isSupported(tag: String): Boolean {
            val language = languageSubtag(tag) ?: return false
            return language in SUPPORTED_TAGS
        }

        fun normalizeTag(tag: String): String {
            val language = languageSubtag(tag)
            return if (language != null && language in SUPPORTED_TAGS) language else DEFAULT_LANGUAGE_TAG
        }

        /** Language subtag only (e.g. "zu" from "zu-ZA"), or null if empty. */
        private fun languageSubtag(tag: String): String? {
            val trimmed = tag.trim().lowercase()
            if (trimmed.isEmpty()) return null
            return trimmed.substringBefore('-').substringBefore('_')
        }

        fun applyLanguageTag(tag: String) {
            val normalized = normalizeTag(tag)
            val locales = LocaleListCompat.forLanguageTags(normalized)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
