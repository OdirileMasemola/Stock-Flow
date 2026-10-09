package com.odirilemasemola.stockflow.ui.common

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.ui.getstarted.GetStartedActivity
import com.odirilemasemola.stockflow.ui.launch.LaunchActivity
import com.odirilemasemola.stockflow.ui.login.LoginActivity
import com.odirilemasemola.stockflow.ui.signup.SignUpActivity

/**
 * Returns the user to [LoginActivity] after the server rejected their token. If no screen is in
 * the foreground (e.g. background sync got the 401) the redirect waits for the next resumed screen.
 * Auth screens are left alone so there is no redirect loop.
 */
class SessionExpiryNavigator(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumed: Activity? = null
    private var redirectPending = false

    fun requestLogin() {
        mainHandler.post {
            redirectPending = true
            resumed?.let(::redirect)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = activity
        if (redirectPending) redirect(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed === activity) resumed = null
    }

    private fun redirect(from: Activity) {
        redirectPending = false
        if (from.isAuthScreen()) return
        Toast.makeText(app, R.string.error_not_signed_in, Toast.LENGTH_LONG).show()
        from.startActivity(
            Intent(from, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun Activity.isAuthScreen() =
        this is LoginActivity || this is SignUpActivity || this is GetStartedActivity || this is LaunchActivity

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
