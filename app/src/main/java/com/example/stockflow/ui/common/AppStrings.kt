package com.example.stockflow.ui.common

import androidx.annotation.StringRes
import com.example.stockflow.StockFlowApp

/**
 * Resolves string resources using the application context so repositories
 * and shared objects can surface localized user-facing messages.
 */
object AppStrings {
    fun get(@StringRes id: Int): String = StockFlowApp.instance.getString(id)

    fun get(@StringRes id: Int, vararg formatArgs: Any): String =
        StockFlowApp.instance.getString(id, *formatArgs)
}
