package com.odirilemasemola.stockflow

import android.app.Application
import com.odirilemasemola.stockflow.data.local.LanguagePreferences
import com.odirilemasemola.stockflow.data.local.ThemePreferences
import com.odirilemasemola.stockflow.data.local.cache.CacheDatabaseProvider
import com.odirilemasemola.stockflow.data.notifications.FcmRegistrationHelper
import com.odirilemasemola.stockflow.data.sync.SyncScheduler

/**
 * Applies the persisted theme and language before any Activity is created.
 * Also initializes the offline READ cache + WRITE queue database and kicks
 * a best-effort pending sync when the process starts.
 */
class StockFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemePreferences(this).applySavedMode()
        LanguagePreferences(this).applySavedLanguage()
        CacheDatabaseProvider.init(this)
        try {
            // Attempt to flush any leftover PENDING writes when the app launches.
            SyncScheduler.enqueueSync(this)
        } catch (_: Exception) {
            // WorkManager may be unavailable in unit-test environments.
        }
        try {
            // Re-register FCM token after process start when a session exists.
            FcmRegistrationHelper.registerIfLoggedIn(this)
        } catch (_: Exception) {
            // Firebase / Play Services may be unavailable in unit tests.
        }
    }

    companion object {
        lateinit var instance: StockFlowApp
            private set
    }
}
