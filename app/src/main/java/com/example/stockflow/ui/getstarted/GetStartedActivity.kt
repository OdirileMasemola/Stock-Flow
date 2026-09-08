package com.example.stockflow.ui.getstarted

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.databinding.ActivityGetStartedBinding
import com.example.stockflow.ui.common.SystemBars
import com.example.stockflow.ui.login.LoginActivity

/**
 * First-launch welcome screen.
 * Shown only while [SessionStore.hasSeenGetStarted] is false.
 */
class GetStartedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGetStartedBinding
    private lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore(this)

        // Safety: if this activity is opened after the flag is already set, skip it.
        if (sessionStore.hasSeenGetStarted()) {
            goToLogin(clearSelf = true)
            return
        }

        binding = ActivityGetStartedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this, binding.root)

        binding.btnGetStarted.setOnClickListener {
            // Persist so Get Started never shows again until app data is cleared.
            sessionStore.markGetStartedSeen()
            goToLogin(clearSelf = true)
        }
    }

    private fun goToLogin(clearSelf: Boolean) {
        startActivity(Intent(this, LoginActivity::class.java))
        if (clearSelf) {
            finish()
        }
    }
}
