package com.example.stockflow.ui.common

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.stockflow.R

object SystemBars {
    fun apply(
        activity: Activity,
        root: View,
        @ColorInt statusBarColor: Int = activity.getColor(R.color.brand_background),
        @ColorInt navigationBarColor: Int = activity.getColor(R.color.brand_background),
        lightStatusBars: Boolean = false,
        lightNavigationBars: Boolean = false
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = statusBarColor
        activity.window.navigationBarColor = navigationBarColor

        val controller = WindowCompat.getInsetsController(activity.window, root)
        controller.isAppearanceLightStatusBars = lightStatusBars
        controller.isAppearanceLightNavigationBars = lightNavigationBars

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun applyLight(activity: Activity, root: View) {
        applyThemeAware(activity, root)
    }

    /** Status/nav bars that follow the current light/dark theme. */
    fun applyThemeAware(activity: Activity, root: View) {
        val night = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        apply(
            activity = activity,
            root = root,
            statusBarColor = ContextCompat.getColor(activity, R.color.page_background),
            navigationBarColor = ContextCompat.getColor(activity, R.color.surface),
            lightStatusBars = !night,
            lightNavigationBars = !night
        )
    }
}
