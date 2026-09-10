package com.example.stockflow

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.databinding.ActivityMainBinding
import com.example.stockflow.ui.dashboard.DashboardFragment
import com.example.stockflow.ui.dashboard.LowStockActivity
import com.example.stockflow.ui.dashboard.ReportsActivity
import com.example.stockflow.ui.inventory.InventoryFragment
import com.example.stockflow.ui.login.LoginActivity
import com.example.stockflow.ui.sales.SalesFragment
import com.example.stockflow.ui.settings.SettingsActivity
import com.example.stockflow.ui.suppliers.SuppliersFragment
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionStore = SessionStore(this)

        setupSystemBars()
        setupToolbar()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
            loadFragment(DashboardFragment(), getString(R.string.nav_dashboard))
        }
    }

    /**
     * Pad only the toolbar (status bar) and bottom nav (gesture/nav bar)
     * so the bottom navigation sits flush instead of floating upward.
     */
    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = ContextCompat.getColor(this, R.color.page_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface)

        val controller = WindowCompat.getInsetsController(window, binding.root)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Grow the spacer to status-bar height — do NOT pad the toolbar (that clips the title).
            val spacerParams = binding.statusBarSpacer.layoutParams
            spacerParams.height = bars.top
            binding.statusBarSpacer.layoutParams = spacerParams
            binding.bottomNavigation.updatePadding(bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_low_stock -> {
                    startActivity(Intent(this, LowStockActivity::class.java))
                    true
                }
                R.id.action_reports -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    true
                }
                R.id.action_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment(), getString(R.string.nav_dashboard))
                    true
                }
                R.id.nav_inventory -> {
                    loadFragment(InventoryFragment(), getString(R.string.nav_inventory))
                    true
                }
                R.id.nav_sales -> {
                    loadFragment(SalesFragment(), getString(R.string.nav_sales))
                    true
                }
                R.id.nav_suppliers -> {
                    loadFragment(SuppliersFragment(), getString(R.string.nav_suppliers))
                    true
                }
                else -> false
            }
        }
    }

    fun selectNavItem(itemId: Int) {
        binding.bottomNavigation.selectedItemId = itemId
    }

    private fun loadFragment(fragment: Fragment, title: String) {
        binding.topAppBar.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    private fun logout() {
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
}
