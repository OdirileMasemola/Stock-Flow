package com.example.stockflow.data.local

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    companion object {
        private const val PREFS_NAME = "stockflow_session"
        private const val KEY_TOKEN = "jwt_token"
    }
}
