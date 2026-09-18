package com.example.stockflow.ui.suppliers

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockflow.R
import com.example.stockflow.databinding.ActivityPurchaseOrdersBinding
import com.example.stockflow.ui.common.OfflineBanner
import com.example.stockflow.ui.common.SystemBars

class PurchaseOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseOrdersBinding
    private val viewModel: PurchaseOrderViewModel by viewModels()
    private var refreshOnResume = true
    private lateinit var adapter: PurchaseOrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = PurchaseOrderAdapter { order ->
            startActivity(
                Intent(this, PurchaseOrderDetailActivity::class.java).apply {
                    putExtra(PurchaseOrderDetailActivity.EXTRA_ORDER_ID, order.id)
                }
            )
        }
        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        binding.rvOrders.adapter = adapter

        binding.btnNewOrder.setOnClickListener {
            refreshOnResume = true
            startActivity(Intent(this, PurchaseOrderActivity::class.java))
        }
        binding.btnRetry.setOnClickListener { viewModel.loadPurchaseOrders() }

        viewModel.listState.observe(this) { state ->
            when (state) {
                is PurchaseOrderViewModel.ListUiState.Loading -> {
                    OfflineBanner.hide(binding.tvOfflineBanner)
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.rvOrders.visibility = View.GONE
                    binding.emptyState.visibility = View.GONE
                }
                is PurchaseOrderViewModel.ListUiState.Empty -> {
                    OfflineBanner.show(binding.tvOfflineBanner, state.fromCache, state.cachedAt)
                    binding.progressLoading.visibility = View.GONE
                    binding.rvOrders.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = getString(R.string.po_empty)
                    binding.btnRetry.visibility = View.GONE
                    adapter.submitList(emptyList())
                }
                is PurchaseOrderViewModel.ListUiState.Success -> {
                    OfflineBanner.show(binding.tvOfflineBanner, state.fromCache, state.cachedAt)
                    binding.progressLoading.visibility = View.GONE
                    binding.emptyState.visibility = View.GONE
                    binding.rvOrders.visibility = View.VISIBLE
                    adapter.submitList(state.orders)
                }
                is PurchaseOrderViewModel.ListUiState.Error -> {
                    OfflineBanner.hide(binding.tvOfflineBanner)
                    binding.progressLoading.visibility = View.GONE
                    binding.rvOrders.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = state.message
                    binding.btnRetry.visibility = View.VISIBLE
                    adapter.submitList(emptyList())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPurchaseOrders(force = refreshOnResume)
        refreshOnResume = false
    }
}
