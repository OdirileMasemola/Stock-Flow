package com.example.stockflow.data.local

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Clears the JWT only (keeps first-launch / Get Started flag). */
    fun clearSession() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /** True when a StockFlow JWT is stored locally. */
    fun hasValidSession(): Boolean = !getToken().isNullOrBlank()

    /**
     * First-launch welcome flag.
     * Defaults to false so Get Started shows on a fresh install / cleared app data.
     */
    fun hasSeenGetStarted(): Boolean =
        prefs.getBoolean(KEY_HAS_SEEN_GET_STARTED, false)

    fun markGetStartedSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_GET_STARTED, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "stockflow_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_HAS_SEEN_GET_STARTED = "has_seen_get_started"
    }
}
