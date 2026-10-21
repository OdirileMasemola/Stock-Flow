package com.odirilemasemola.stockflow.data.local

import android.content.Context
import android.content.SharedPreferences
import com.odirilemasemola.stockflow.data.remote.ReportsDto
import com.google.gson.Gson

/**
 * Last successful report per user and period, so Reports still opens offline (shown with the
 * offline banner). Holds aggregate shop figures only — never tokens or personal data — and is
 * cleared per user on logout via [clearUser].
 */
class ReportsCache(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(userId: Int, periodKey: String, reports: ReportsDto, savedAt: Long) {
        prefs.edit().putString(key(userId, periodKey), gson.toJson(reports.copy(cachedAt = savedAt))).apply()
    }

    fun load(userId: Int, periodKey: String): ReportsDto? {
        val raw = prefs.getString(key(userId, periodKey), null) ?: return null
        return try {
            gson.fromJson(raw, ReportsDto::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun clearUser(userId: Int) {
        val prefix = "u${userId}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun key(userId: Int, periodKey: String) = "u${userId}_$periodKey"

    companion object {
        private const val PREFS = "stockflow_reports_cache"

        /** "7d", "30d", "today" or "custom:<from>_<to>". */
        fun periodKey(range: String?, from: String?, to: String?): String =
            if (!from.isNullOrBlank() && !to.isNullOrBlank()) "custom:${from}_$to" else (range ?: "7d")
    }
}
