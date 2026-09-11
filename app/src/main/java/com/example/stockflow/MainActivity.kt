package com.example.stockflow

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.databinding.ActivityMainBinding
import com.example.stockflow.databinding.ItemBottomNavBinding
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
    private var selectedNavId: Int = R.id.nav_dashboard

    private data class NavTab(
        val id: Int,
        val root: LinearLayout,
        val iconView: ImageView,
        val labelView: TextView,
        val iconRes: Int,
        val titleRes: Int,
        val fragmentFactory: () -> Fragment
    )

    private lateinit var navTabs: List<NavTab>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionStore = SessionStore(this)

        setupSystemBars()
        setupToolbar()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            selectNavItem(R.id.nav_dashboard, animate = false)
        } else {
            selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV, R.id.nav_dashboard)
            applyNavSelection(selectedNavId, animate = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV, selectedNavId)
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = ContextCompat.getColor(this, R.color.page_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.page_background)

        val controller = WindowCompat.getInsetsController(window, binding.root)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val spacerParams = binding.statusBarSpacer.layoutParams
            spacerParams.height = bars.top
            binding.statusBarSpacer.layoutParams = spacerParams
            binding.bottomNavContainer.updatePadding(
                bottom = bars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_outer_gap)
            )
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
        navTabs = listOf(
            navTab(
                id = R.id.nav_dashboard,
                item = binding.navDashboard,
                iconRes = R.drawable.ic_dashboard,
                titleRes = R.string.nav_dashboard,
                fragmentFactory = { DashboardFragment() }
            ),
            navTab(
                id = R.id.nav_inventory,
                item = binding.navInventory,
                iconRes = R.drawable.ic_inventory,
                titleRes = R.string.nav_inventory,
                fragmentFactory = { InventoryFragment() }
            ),
            navTab(
                id = R.id.nav_sales,
                item = binding.navSales,
                iconRes = R.drawable.ic_sales,
                titleRes = R.string.nav_sales,
                fragmentFactory = { SalesFragment() }
            ),
            navTab(
                id = R.id.nav_suppliers,
                item = binding.navSuppliers,
                iconRes = R.drawable.ic_suppliers,
                titleRes = R.string.nav_suppliers,
                fragmentFactory = { SuppliersFragment() }
            )
        )

        navTabs.forEach { tab ->
            tab.iconView.setImageResource(tab.iconRes)
            tab.labelView.setText(tab.titleRes)
            tab.root.contentDescription = getString(tab.titleRes)
            tab.root.setOnClickListener { selectNavItem(tab.id, animate = true) }
        }
    }

    private fun navTab(
        id: Int,
        item: ItemBottomNavBinding,
        iconRes: Int,
        titleRes: Int,
        fragmentFactory: () -> Fragment
    ): NavTab {
        return NavTab(
            id = id,
            root = item.root,
            iconView = item.navIcon,
            labelView = item.navLabel,
            iconRes = iconRes,
            titleRes = titleRes,
            fragmentFactory = fragmentFactory
        )
    }

    fun selectNavItem(itemId: Int) {
        selectNavItem(itemId, animate = true)
    }

    private fun selectNavItem(itemId: Int, animate: Boolean) {
        val tab = navTabs.firstOrNull { it.id == itemId } ?: return
        val alreadySelected = selectedNavId == itemId &&
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) != null
        selectedNavId = itemId
        applyNavSelection(itemId, animate)
        if (!alreadySelected) {
            loadFragment(tab.fragmentFactory(), getString(tab.titleRes))
        }
    }

    private fun applyNavSelection(itemId: Int, animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(
                binding.bottomNavigation,
                AutoTransition().apply { duration = 220 }
            )
        }

        val activeColor = ContextCompat.getColor(this, R.color.bottom_nav_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.bottom_nav_inactive)
        val activePad = resources.getDimensionPixelSize(R.dimen.bottom_nav_item_padding_active)
        val inactivePad = resources.getDimensionPixelSize(R.dimen.bottom_nav_item_padding_inactive)

        navTabs.forEach { tab ->
            val selected = tab.id == itemId
            tab.root.isSelected = selected
            // Show full page name only on the active tab (to the right of the icon).
            tab.labelView.visibility = if (selected) View.VISIBLE else View.GONE
            tab.labelView.setTextColor(activeColor)
            tab.iconView.setColorFilter(if (selected) activeColor else inactiveColor)
            val padH = if (selected) activePad else inactivePad
            tab.root.setPadding(padH, tab.root.paddingTop, padH, tab.root.paddingBottom)
            // Keep active pill measuring to its full label width.
            tab.root.layoutParams = tab.root.layoutParams.apply {
                width = LinearLayout.LayoutParams.WRAP_CONTENT
                height = tab.root.layoutParams.height
            }
        }
        binding.bottomNavigation.requestLayout()
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

    companion object {
        private const val KEY_SELECTED_NAV = "selected_nav_id"
    }
}
