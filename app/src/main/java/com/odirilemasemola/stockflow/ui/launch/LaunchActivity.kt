package com.odirilemasemola.stockflow.ui.launch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.odirilemasemola.stockflow.MainActivity
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.ui.getstarted.GetStartedActivity
import com.odirilemasemola.stockflow.ui.login.LoginActivity

/**
 * App entry router (no UI).
 *
 * Routes:
 * - First install / cleared data -> GetStartedActivity
 * - Returning user without session -> LoginActivity
 * - Returning user with stored JWT -> MainActivity (Dashboard)
 */
class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionStore = SessionStore(this)
        val destination = when {
            !sessionStore.hasSeenGetStarted() -> GetStartedActivity::class.java
            sessionStore.hasValidSession() -> MainActivity::class.java
            else -> LoginActivity::class.java
        }

        startActivity(Intent(this, destination))
        finish()
    }
}
