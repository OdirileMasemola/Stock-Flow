package com.example.stockflow.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.stockflow.MainActivity
import com.example.stockflow.R
import com.example.stockflow.data.remote.DashboardLowStockItemDto
import com.example.stockflow.data.remote.DashboardPurchaseOrderItemDto
import com.example.stockflow.data.remote.DashboardSaleItemDto
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.remote.WeeklySalesDayDto
import com.example.stockflow.databinding.FragmentDashboardBinding
import com.example.stockflow.ui.inventory.AddProductActivity
import com.example.stockflow.ui.suppliers.PurchaseOrdersActivity
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.greetingText.text = greetingForNow()

        binding.btnRetry.setOnClickListener { viewModel.loadDashboard() }
        binding.actionAddProduct.setOnClickListener {
            startActivity(Intent(requireContext(), AddProductActivity::class.java))
        }
        binding.actionNewSale.setOnClickListener {
            (activity as? MainActivity)?.selectNavItem(R.id.nav_sales)
        }
        binding.actionSupplier.setOnClickListener {
            (activity as? MainActivity)?.selectNavItem(R.id.nav_suppliers)
        }
        binding.actionReports.setOnClickListener {
            startActivity(Intent(requireContext(), ReportsActivity::class.java))
        }
        binding.cardTotalProducts.setOnClickListener {
            (activity as? MainActivity)?.selectNavItem(R.id.nav_inventory)
        }
        binding.cardLowStock.setOnClickListener {
            startActivity(Intent(requireContext(), LowStockActivity::class.java))
        }
        binding.btnSeeAllSales.setOnClickListener {
            (activity as? MainActivity)?.selectNavItem(R.id.nav_sales)
        }
        binding.btnSeeLowStock.setOnClickListener {
            startActivity(Intent(requireContext(), LowStockActivity::class.java))
        }
        binding.btnSeePurchaseOrders.setOnClickListener {
            startActivity(Intent(requireContext(), PurchaseOrdersActivity::class.java))
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DashboardViewModel.DashboardUiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.contentScroll.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                }
                is DashboardViewModel.DashboardUiState.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    binding.contentScroll.visibility = View.VISIBLE
                    bindSummary(state.summary)
                }
                is DashboardViewModel.DashboardUiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.contentScroll.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = state.message
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
    }

    private fun bindSummary(summary: DashboardSummaryDto) {
        binding.tvTodaySales.text = getString(R.string.price_format, summary.todaySalesTotal)
        binding.tvTodaySalesCount.text =
            getString(R.string.today_sales_count, summary.todaySalesCount)
        binding.tvInventoryValue.text = getString(R.string.price_format, summary.inventoryValue)
        binding.tvTotalStock.text =
            getString(R.string.stock_units_format, summary.totalStockQuantity)
        binding.tvTotalProducts.text = summary.totalProducts.toString()
        binding.tvLowStockCount.text = summary.lowStockCount.toString()

        renderWeeklyChart(summary.weeklySales)
        renderSales(summary.recentSales)
        renderLowStock(summary.lowStockPreview)
        renderPurchaseOrders(summary.recentPurchaseOrders)
    }

    private fun renderWeeklyChart(days: List<WeeklySalesDayDto>) {
        binding.weeklyBars.removeAllViews()
        binding.weeklyLabels.removeAllViews()

        val weekTotal = days.sumOf { it.totalAmount }
        binding.tvWeeklyTotal.text = getString(R.string.weekly_total_format, weekTotal)

        val hasSales = days.any { it.totalAmount > 0 }
        binding.tvWeeklyEmpty.visibility = if (hasSales) View.GONE else View.VISIBLE
        binding.weeklyBars.visibility = View.VISIBLE
        binding.weeklyLabels.visibility = View.VISIBLE

        val maxAmount = max(days.maxOfOrNull { it.totalAmount } ?: 0.0, 0.01)
        val density = resources.displayMetrics.density
        val maxBarHeightPx = (100 * density).roundToInt()
        val minBarHeightPx = (4 * density).roundToInt()

        for (day in days) {
            val barColumn = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                orientation = LinearLayout.VERTICAL
            }

            val heightPx = if (day.totalAmount <= 0.0) {
                minBarHeightPx
            } else {
                max(minBarHeightPx, ((day.totalAmount / maxAmount) * maxBarHeightPx).roundToInt())
            }

            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((10 * density).roundToInt(), heightPx)
                setBackgroundColor(requireContext().getColor(R.color.brand_primary))
                alpha = if (day.totalAmount > 0) 1f else 0.25f
                contentDescription = "${day.label}: ${getString(R.string.price_format, day.totalAmount)}"
            }
            barColumn.addView(bar)
            binding.weeklyBars.addView(barColumn)

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.CENTER
                text = day.label.take(3)
                setTextColor(requireContext().getColor(R.color.brand_text_light))
                textSize = 10f
            }
            binding.weeklyLabels.addView(label)
        }
    }

    private fun renderSales(items: List<DashboardSaleItemDto>) {
        binding.salesList.removeAllViews()
        binding.tvSalesEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for (sale in items) {
            binding.salesList.addView(
                rowView(
                    title = getString(R.string.sale_row_title, sale.id, sale.paymentMethod),
                    subtitle = sale.createdAt.replace('T', ' ').take(16),
                    trailing = getString(R.string.price_format, sale.totalAmount)
                )
            )
        }
    }

    private fun renderLowStock(items: List<DashboardLowStockItemDto>) {
        binding.lowStockList.removeAllViews()
        binding.tvLowStockEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for (item in items) {
            binding.lowStockList.addView(
                rowView(
                    title = getString(
                        R.string.low_stock_row,
                        item.name,
                        item.stockLevel,
                        item.minStockLevel
                    ),
                    subtitle = null,
                    trailing = null
                )
            )
        }
    }

    private fun renderPurchaseOrders(items: List<DashboardPurchaseOrderItemDto>) {
        binding.purchaseOrderList.removeAllViews()
        binding.tvPurchaseOrdersEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for (po in items) {
            val supplier = po.supplierName ?: "Supplier #${po.supplierId}"
            binding.purchaseOrderList.addView(
                rowView(
                    title = getString(R.string.po_row_title, po.id, supplier),
                    subtitle = "${po.status} · ${po.createdAt.replace('T', ' ').take(16)}",
                    trailing = getString(R.string.price_format, po.totalAmount)
                )
            )
        }
    }

    private fun rowView(title: String, subtitle: String?, trailing: String?): View {
        val row = layoutInflater.inflate(R.layout.item_dashboard_row, binding.salesList, false)
        row.findViewById<TextView>(R.id.tvTitle).text = title
        val subtitleView = row.findViewById<TextView>(R.id.tvSubtitle)
        if (subtitle.isNullOrBlank()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle
        }
        val trailingView = row.findViewById<TextView>(R.id.tvTrailing)
        if (trailing.isNullOrBlank()) {
            trailingView.visibility = View.GONE
        } else {
            trailingView.visibility = View.VISIBLE
            trailingView.text = trailing
        }
        (row.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin =
            (8 * resources.displayMetrics.density).toInt()
        return row
    }

    private fun greetingForNow(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> getString(R.string.good_morning)
            hour < 17 -> getString(R.string.good_afternoon)
            else -> getString(R.string.good_evening)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
