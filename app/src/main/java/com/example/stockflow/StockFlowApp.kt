package com.example.stockflow

import android.app.Application
import com.example.stockflow.data.local.LanguagePreferences
import com.example.stockflow.data.local.ThemePreferences
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.sync.SyncScheduler

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
    }

    companion object {
        lateinit var instance: StockFlowApp
            private set
    }
}
