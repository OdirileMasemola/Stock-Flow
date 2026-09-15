package com.example.stockflow

import android.app.Application
import com.example.stockflow.data.local.LanguagePreferences
import com.example.stockflow.data.local.ThemePreferences

/**
 * Applies the persisted theme and language before any Activity is created.
 */
class StockFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemePreferences(this).applySavedMode()
        LanguagePreferences(this).applySavedLanguage()
    }

    companion object {
        lateinit var instance: StockFlowApp
            private set
    }
}
