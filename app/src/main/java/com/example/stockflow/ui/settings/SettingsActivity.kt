package com.example.stockflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.BuildConfig
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.ThemePreferences
import com.example.stockflow.databinding.ActivitySettingsBinding
import com.example.stockflow.databinding.ItemSettingsRowBinding
import com.example.stockflow.ui.common.SystemBars
import com.example.stockflow.ui.login.LoginActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themePreferences: ThemePreferences
    private lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        themePreferences = ThemePreferences(this)
        sessionStore = SessionStore(this)

        SystemBars.applyThemeAware(this, binding.settingsRoot)

        binding.toolbar.setNavigationOnClickListener { finish() }

        bindRows()
        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        binding.rowTheme.tvSubtitle.text = themeLabel(themePreferences.getMode())
    }

    private fun bindRows() {
        bindRow(
            row = binding.rowProfile,
            iconRes = R.drawable.ic_user,
            iconColor = R.color.icon_profile,
            iconBg = R.color.icon_bg_profile,
            title = getString(R.string.settings_profile),
            subtitle = getString(R.string.settings_profile_subtitle)
        )
        bindRow(
            row = binding.rowBusiness,
            iconRes = R.drawable.ic_business,
            iconColor = R.color.icon_business,
            iconBg = R.color.icon_bg_business,
            title = getString(R.string.settings_business),
            subtitle = getString(R.string.settings_business_subtitle)
        )
        bindRow(
            row = binding.rowTheme,
            iconRes = R.drawable.ic_theme,
            iconColor = R.color.icon_theme,
            iconBg = R.color.icon_bg_theme,
            title = getString(R.string.settings_theme),
            subtitle = themeLabel(themePreferences.getMode())
        )
        bindRow(
            row = binding.rowLanguage,
            iconRes = R.drawable.ic_language,
            iconColor = R.color.icon_language,
            iconBg = R.color.icon_bg_language,
            title = getString(R.string.settings_app_language),
            subtitle = getString(R.string.settings_language_english)
        )
        bindRow(
            row = binding.rowNotifications,
            iconRes = R.drawable.ic_notifications,
            iconColor = R.color.icon_notifications,
            iconBg = R.color.icon_bg_notifications,
            title = getString(R.string.settings_notifications),
            subtitle = getString(R.string.settings_coming_soon)
        )
        bindRow(
            row = binding.rowSync,
            iconRes = R.drawable.ic_sync,
            iconColor = R.color.icon_sync,
            iconBg = R.color.icon_bg_sync,
            title = getString(R.string.settings_sync),
            subtitle = getString(R.string.settings_coming_soon)
        )
        bindRow(
            row = binding.rowAbout,
            iconRes = R.drawable.ic_info,
            iconColor = R.color.icon_about,
            iconBg = R.color.icon_bg_about,
            title = getString(R.string.settings_about),
            subtitle = getString(R.string.settings_about_subtitle)
        )
        bindRow(
            row = binding.rowVersion,
            iconRes = R.drawable.ic_info,
            iconColor = R.color.icon_about,
            iconBg = R.color.icon_bg_about,
            title = getString(R.string.settings_app_version),
            subtitle = BuildConfig.VERSION_NAME,
            showChevron = false
        )

        // Logout row chip
        val logoutChip = binding.rowLogout.getChildAt(0) as? View
        logoutChip?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(getColor(R.color.icon_bg_logout))
        }
    }

    private fun bindRow(
        row: ItemSettingsRowBinding,
        iconRes: Int,
        iconColor: Int,
        iconBg: Int,
        title: String,
        subtitle: String,
        showChevron: Boolean = true
    ) {
        row.ivIcon.setImageResource(iconRes)
        row.ivIcon.imageTintList =
            android.content.res.ColorStateList.valueOf(getColor(iconColor))
        (row.ivIcon.parent as? View)?.background =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(getColor(iconBg))
            }
        row.tvTitle.text = title
        row.tvSubtitle.text = subtitle
        row.ivChevron.visibility = if (showChevron) View.VISIBLE else View.GONE
    }

    private fun setupClicks() {
        binding.rowProfile.root.setOnClickListener {
            showPlaceholder(getString(R.string.settings_profile_placeholder))
        }
        binding.rowBusiness.root.setOnClickListener {
            showPlaceholder(getString(R.string.settings_business_placeholder))
        }
        binding.rowTheme.root.setOnClickListener { showThemeDialog() }
        binding.rowLanguage.root.setOnClickListener { showLanguageDialog() }
        binding.rowNotifications.root.setOnClickListener {
            showPlaceholder(getString(R.string.settings_notifications_placeholder))
        }
        binding.rowSync.root.setOnClickListener {
            showPlaceholder(getString(R.string.settings_sync_placeholder))
        }
        binding.rowAbout.root.setOnClickListener { showAboutDialog() }
        binding.rowVersion.root.setOnClickListener { showAboutDialog() }
        binding.rowLogout.setOnClickListener { confirmLogout() }
    }

    private fun showThemeDialog() {
        val modes = arrayOf(
            ThemePreferences.Mode.SYSTEM,
            ThemePreferences.Mode.LIGHT,
            ThemePreferences.Mode.DARK
        )
        val labels = arrayOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark)
        )
        val checked = modes.indexOf(themePreferences.getMode()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                themePreferences.setMode(modes[which])
                binding.rowTheme.tvSubtitle.text = themeLabel(modes[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.settings_language_english),
            getString(R.string.settings_language_isizulu),
            getString(R.string.settings_language_sesotho),
            getString(R.string.settings_language_setswana)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_app_language)
            .setSingleChoiceItems(languages, 0) { dialog, which ->
                if (which == 0) {
                    Toast.makeText(
                        this,
                        R.string.settings_language_english_active,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        R.string.settings_language_future,
                        Toast.LENGTH_LONG
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        val message = getString(
            R.string.settings_about_body,
            BuildConfig.VERSION_NAME
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showPlaceholder(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_logout)
            .setMessage(R.string.settings_logout_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_logout) { _, _ -> performLogout() }
            .show()
    }

    private fun performLogout() {
        sessionStore.clearSession()
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAuth.getInstance().signOut()
        }
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun themeLabel(mode: ThemePreferences.Mode): String = when (mode) {
        ThemePreferences.Mode.SYSTEM -> getString(R.string.settings_theme_system)
        ThemePreferences.Mode.LIGHT -> getString(R.string.settings_theme_light)
        ThemePreferences.Mode.DARK -> getString(R.string.settings_theme_dark)
    }
}
