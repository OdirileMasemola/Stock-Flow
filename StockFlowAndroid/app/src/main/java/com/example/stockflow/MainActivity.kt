package com.example.stockflow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stockflow.databinding.ActivityMainBinding
import com.example.stockflow.ui.dashboard.DashboardFragment
import com.example.stockflow.ui.inventory.InventoryFragment
import com.example.stockflow.ui.sales.SalesFragment
import com.example.stockflow.ui.suppliers.SuppliersFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initial fragment
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_inventory -> InventoryFragment()
                R.id.nav_sales -> SalesFragment()
                R.id.nav_suppliers -> SuppliersFragment()
                else -> null
            }
            fragment?.let {
                loadFragment(it)
                true
            } ?: false
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
