package com.example.stockflow.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.stockflow.R
import com.example.stockflow.data.remote.ReportsDto
import com.example.stockflow.databinding.ActivityReportsBinding
import com.example.stockflow.ui.common.SystemBars
import com.google.android.material.datepicker.MaterialDatePicker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val viewModel: ReportsViewModel by viewModels()

    private var exportFromIso: String? = null
    private var exportToIso: String? = null
    private var exportRangeLabel: String? = null
    private var suppressChipCallback = false
    private var lastPresetChipId: Int = R.id.chip7d

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { reloadCurrent() }
        binding.btnPickExportDates.setOnClickListener {
            openDateRangePicker(forExport = true, alsoLoadScreen = false)
        }
        binding.btnExportPdf.setOnClickListener { onExportPdfClicked() }

        binding.chipGroupRange.setOnCheckedChangeListener { _, checkedId ->
            if (suppressChipCallback) return@setOnCheckedChangeListener
            when (checkedId) {
                R.id.chipToday -> {
                    lastPresetChipId = checkedId
                    viewModel.loadReports("today")
                }
                R.id.chip7d -> {
                    lastPresetChipId = checkedId
                    viewModel.loadReports("7d")
                }
                R.id.chip30d -> {
                    lastPresetChipId = checkedId
                    viewModel.loadReports("30d")
                }
                R.id.chipCustom -> {
                    openDateRangePicker(forExport = false, alsoLoadScreen = true)
                }
                else -> return@setOnCheckedChangeListener
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
                    binding.tvSelectedRange.text = getString(R.string.showing_range, state.rangeLabel)
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

        viewModel.exportState.observe(this) { state ->
            when (state) {
                is ReportsViewModel.ExportState.Idle -> {
                    binding.btnExportPdf.isEnabled = true
                    binding.btnPickExportDates.isEnabled = true
                }
                is ReportsViewModel.ExportState.Loading -> {
                    binding.btnExportPdf.isEnabled = false
                    binding.btnPickExportDates.isEnabled = false
                    Toast.makeText(this, "Creating PDF…", Toast.LENGTH_SHORT).show()
                }
                is ReportsViewModel.ExportState.Success -> {
                    binding.btnExportPdf.isEnabled = true
                    binding.btnPickExportDates.isEnabled = true
                    sharePdf(state.file)
                    viewModel.clearExportState()
                }
                is ReportsViewModel.ExportState.Error -> {
                    binding.btnExportPdf.isEnabled = true
                    binding.btnPickExportDates.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.clearExportState()
                }
            }
        }

        viewModel.loadReports("7d")
    }

    private fun reloadCurrent() {
        val from = viewModel.customFrom()
        val to = viewModel.customTo()
        if (viewModel.currentRange() == "custom" && from != null && to != null) {
            viewModel.loadCustomReports(from, to, formatDisplayRange(from, to))
        } else {
            viewModel.loadReports(viewModel.currentRange())
        }
    }

    private fun onExportPdfClicked() {
        val from = exportFromIso
        val to = exportToIso
        val label = exportRangeLabel
        if (from.isNullOrBlank() || to.isNullOrBlank() || label.isNullOrBlank()) {
            openDateRangePicker(forExport = true, alsoLoadScreen = false, exportAfterPick = true)
            return
        }
        viewModel.exportPdf(from, to, label)
    }

    private fun openDateRangePicker(
        forExport: Boolean,
        alsoLoadScreen: Boolean,
        exportAfterPick: Boolean = false
    ) {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.date_range_picker_title)
            .setTheme(com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialCalendar)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first ?: return@addOnPositiveButtonClickListener
            val endMillis = selection.second ?: return@addOnPositiveButtonClickListener
            val fromIso = millisToIsoDate(startMillis)
            val toIso = millisToIsoDate(endMillis)
            val label = formatDisplayRange(fromIso, toIso)

            if (forExport || exportAfterPick) {
                exportFromIso = fromIso
                exportToIso = toIso
                exportRangeLabel = label
                binding.tvExportDateRange.text = getString(R.string.export_dates_selected, label)
                binding.btnPickExportDates.text = getString(R.string.pick_export_dates)
            }

            if (alsoLoadScreen) {
                suppressChipCallback = true
                binding.chipCustom.isChecked = true
                suppressChipCallback = false
                viewModel.loadCustomReports(fromIso, toIso, label)
            } else if (binding.chipCustom.isChecked && !alsoLoadScreen) {
                // Keep custom chip only when that path selected it.
            }

            if (exportAfterPick) {
                viewModel.exportPdf(fromIso, toIso, label)
            }
        }

        picker.addOnNegativeButtonClickListener {
            if (alsoLoadScreen && viewModel.currentRange() != "custom") {
                restorePresetChip()
            }
        }
        picker.addOnCancelListener {
            if (alsoLoadScreen && viewModel.currentRange() != "custom") {
                restorePresetChip()
            }
        }

        picker.show(supportFragmentManager, "report_date_range")
    }

    private fun restorePresetChip() {
        suppressChipCallback = true
        binding.chipGroupRange.check(lastPresetChipId)
        suppressChipCallback = false
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_pdf_ready))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.export_pdf_share))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.export_as_pdf)))
    }

    private fun millisToIsoDate(utcMillis: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun formatDisplayRange(fromIso: String, toIso: String): String {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return try {
            val from = parser.parse(fromIso) ?: Date()
            val to = parser.parse(toIso) ?: Date()
            "${formatter.format(from)} – ${formatter.format(to)}"
        } catch (_: Exception) {
            "$fromIso – $toIso"
        }
    }

    private fun bindReports(reports: ReportsDto) {
        val sales = reports.sales
        binding.tvSalesTotal.text = getString(
            R.string.report_sales_total,
            sales?.totalSales ?: 0.0
        )
        binding.tvSalesCount.text = getString(
            R.string.report_sales_count,
            sales?.salesCount ?: 0
        )
        binding.tvSalesAverage.text = getString(
            R.string.report_sales_average,
            sales?.averageSaleValue ?: 0.0
        )
        val paymentMethods = sales?.byPaymentMethod.orEmpty()
        binding.tvPaymentBreakdown.text = if (paymentMethods.isEmpty()) {
            getString(R.string.report_payment_none)
        } else {
            paymentMethods.joinToString("\n") { method ->
                getString(
                    R.string.report_payment_line,
                    method.paymentMethod.orEmpty(),
                    method.salesCount,
                    method.totalAmount
                )
            }
        }

        val inv = reports.inventory
        binding.tvInvProducts.text = getString(
            R.string.report_inv_products,
            inv?.totalProducts ?: 0
        )
        binding.tvInvStock.text = getString(
            R.string.report_inv_stock,
            inv?.totalStockQuantity ?: 0
        )
        binding.tvInvValue.text = getString(
            R.string.report_inv_value,
            inv?.inventoryValue ?: 0.0
        )
        binding.tvInvLowStock.text = getString(
            R.string.report_inv_low,
            inv?.lowStockCount ?: 0
        )

        val purchases = reports.purchases
        binding.tvPoCount.text = getString(
            R.string.report_po_count,
            purchases?.purchaseOrderCount ?: 0
        )
        binding.tvPoPending.text = getString(
            R.string.report_po_pending,
            purchases?.pendingCount ?: 0
        )
        binding.tvPoReceived.text = getString(
            R.string.report_po_received,
            purchases?.receivedCount ?: 0
        )
        binding.tvPoTotal.text = getString(
            R.string.report_po_total,
            purchases?.purchasingTotal ?: 0.0
        )
    }
}
