package com.odirilemasemola.stockflow.ui.common

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.odirilemasemola.stockflow.StockFlowApp
import com.odirilemasemola.stockflow.ui.login.LoginActivity
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class SessionExpiryNavigatorTest {

    private val navigator = SessionExpiryNavigator(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun foregroundScreenIsSentToLoginWithClearedBackStack() {
        val screen = newScreen()
        navigator.onActivityResumed(screen)

        navigator.requestLogin()
        idleMain()

        val started = shadowOf(screen).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertNull(shadowOf(screen).nextStartedActivity)
    }

    @Test
    fun expiryInBackgroundRedirectsOnceOnNextResume() {
        navigator.requestLogin()
        idleMain()
        val screen = newScreen()
        assertNull(shadowOf(screen).nextStartedActivity)

        navigator.onActivityResumed(screen)
        assertEquals(LoginActivity::class.java.name, shadowOf(screen).nextStartedActivity.component?.className)

        navigator.onActivityPaused(screen)
        navigator.onActivityResumed(screen)
        assertNull(shadowOf(screen).nextStartedActivity)
    }

    @Test
    fun authScreensAreNotRedirected() {
        val login = mockk<LoginActivity>(relaxed = true)
        navigator.onActivityResumed(login)

        navigator.requestLogin()
        idleMain()

        verify(exactly = 0) { login.startActivity(any()) }
    }

    private fun newScreen(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()
}
