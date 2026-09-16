package com.example.stockflow

import android.app.Application
import com.example.stockflow.data.local.LanguagePreferences
import com.example.stockflow.data.local.ThemePreferences
import com.example.stockflow.data.local.cache.CacheDatabaseProvider

/**
 * Applies the persisted theme and language before any Activity is created.
 * Also initializes the offline READ cache database.
 */
class StockFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemePreferences(this).applySavedMode()
        LanguagePreferences(this).applySavedLanguage()
        CacheDatabaseProvider.init(this)
    }

    companion object {
        lateinit var instance: StockFlowApp
            private set
    }
}
