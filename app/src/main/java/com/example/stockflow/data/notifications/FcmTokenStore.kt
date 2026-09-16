package com.example.stockflow.data.notifications

import android.content.Context

/**
 * Remembers the last FCM token registered with the backend so logout can unregister it.
 * Uses plain SharedPreferences (token is not a secret). Never stored in Room.
 */
class FcmTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveRegisteredToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getRegisteredToken(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val PREFS = "stockflow_fcm"
        private const val KEY_TOKEN = "registered_fcm_token"
    }
}
