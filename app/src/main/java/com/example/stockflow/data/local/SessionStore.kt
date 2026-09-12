package com.example.stockflow.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createSecurePrefs(appContext)

    init {
        migrateLegacyTokenIfNeeded(appContext)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveUserFullName(fullName: String) {
        prefs.edit().putString(KEY_USER_FULL_NAME, fullName.trim()).apply()
    }

    fun getUserFullName(): String? =
        prefs.getString(KEY_USER_FULL_NAME, null)?.takeIf { it.isNotBlank() }

    /** Clears the JWT and profile display fields (keeps first-launch / Get Started flag). */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_FULL_NAME)
            .apply()
    }

    /**
     * True when a StockFlow JWT is stored locally and not past its `exp` claim.
     * Expiry is checked client-side for UX only; the server remains authoritative.
     */
    fun hasValidSession(): Boolean {
        val token = getToken()?.takeIf { it.isNotBlank() } ?: return false
        return !isJwtExpired(token)
    }

    /**
     * First-launch welcome flag.
     * Defaults to false so Get Started shows on a fresh install / cleared app data.
     */
    fun hasSeenGetStarted(): Boolean =
        prefs.getBoolean(KEY_HAS_SEEN_GET_STARTED, false)

    fun markGetStartedSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_GET_STARTED, true).apply()
    }

    private fun migrateLegacyTokenIfNeeded(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyToken = legacy.getString(KEY_TOKEN, null)
        val legacyName = legacy.getString(KEY_USER_FULL_NAME, null)
        val legacySeen = if (legacy.contains(KEY_HAS_SEEN_GET_STARTED)) {
            legacy.getBoolean(KEY_HAS_SEEN_GET_STARTED, false)
        } else {
            null
        }
        if (legacyToken.isNullOrBlank() && legacyName.isNullOrBlank() && legacySeen == null) {
            return
        }
        val editor = prefs.edit()
        if (!legacyToken.isNullOrBlank() && prefs.getString(KEY_TOKEN, null).isNullOrBlank()) {
            editor.putString(KEY_TOKEN, legacyToken)
        }
        if (!legacyName.isNullOrBlank() && prefs.getString(KEY_USER_FULL_NAME, null).isNullOrBlank()) {
            editor.putString(KEY_USER_FULL_NAME, legacyName)
        }
        if (legacySeen != null && !prefs.contains(KEY_HAS_SEEN_GET_STARTED)) {
            editor.putBoolean(KEY_HAS_SEEN_GET_STARTED, legacySeen)
        }
        editor.apply()
        legacy.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "stockflow_session_encrypted"
        private const val LEGACY_PREFS_NAME = "stockflow_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_FULL_NAME = "user_full_name"
        private const val KEY_HAS_SEEN_GET_STARTED = "has_seen_get_started"

        private fun createSecurePrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /** Returns true when the JWT payload `exp` is in the past. Unparseable tokens are treated as not expired. */
        fun isJwtExpired(token: String): Boolean {
            return try {
                val parts = token.split('.')
                if (parts.size < 2) return false
                val payloadJson = String(
                    Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                )
                val expSeconds = JSONObject(payloadJson).optLong("exp", 0L)
                if (expSeconds <= 0L) return false
                expSeconds * 1000L <= System.currentTimeMillis()
            } catch (_: Exception) {
                false
            }
        }
    }
}
