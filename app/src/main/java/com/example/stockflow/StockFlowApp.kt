package com.example.stockflow

import android.app.Application
import com.example.stockflow.data.local.ThemePreferences

/**
 * Applies the persisted theme before any Activity is created.
 */
class StockFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferences(this).applySavedMode()
    }
}
