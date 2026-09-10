package com.example.stockflow.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.data.remote.ReportsDto
import com.example.stockflow.databinding.ActivityReportsBinding
import com.example.stockflow.ui.common.SystemBars

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val viewModel: ReportsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.loadReports() }

        binding.chipGroupRange.setOnCheckedChangeListener { _, checkedId ->
            val range = when (checkedId) {
                R.id.chipToday -> "today"
                R.id.chip30d -> "30d"
                R.id.chip7d -> "7d"
                else -> return@setOnCheckedChangeListener
            }
            if (range != viewModel.currentRange()) {
                viewModel.loadReports(range)
            }
        }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is ReportsViewModel.ReportsUiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.contentScroll.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                }
                is ReportsViewModel.ReportsUiState.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    binding.contentScroll.visibility = View.VISIBLE
                    bindReports(state.reports)
                }
                is ReportsViewModel.ReportsUiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.contentScroll.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = state.message
                }
            }
        }

        viewModel.loadReports("7d")
    }

    private fun bindReports(reports: ReportsDto) {
        val sales = reports.sales
        binding.tvSalesTotal.text = getString(R.string.report_sales_total, sales.totalSales)
        binding.tvSalesCount.text = getString(R.string.report_sales_count, sales.salesCount)
        binding.tvSalesAverage.text = getString(R.string.report_sales_average, sales.averageSaleValue)
        binding.tvPaymentBreakdown.text = if (sales.byPaymentMethod.isEmpty()) {
            getString(R.string.report_payment_none)
        } else {
            sales.byPaymentMethod.joinToString("\n") { method ->
                getString(
                    R.string.report_payment_line,
                    method.paymentMethod,
                    method.salesCount,
                    method.totalAmount
                )
            }
        }

        val inv = reports.inventory
        binding.tvInvProducts.text = getString(R.string.report_inv_products, inv.totalProducts)
        binding.tvInvStock.text = getString(R.string.report_inv_stock, inv.totalStockQuantity)
        binding.tvInvValue.text = getString(R.string.report_inv_value, inv.inventoryValue)
        binding.tvInvLowStock.text = getString(R.string.report_inv_low, inv.lowStockCount)

        val purchases = reports.purchases
        binding.tvPoCount.text = getString(R.string.report_po_count, purchases.purchaseOrderCount)
        binding.tvPoPending.text = getString(R.string.report_po_pending, purchases.pendingCount)
        binding.tvPoReceived.text = getString(R.string.report_po_received, purchases.receivedCount)
        binding.tvPoTotal.text = getString(R.string.report_po_total, purchases.purchasingTotal)
    }
}
