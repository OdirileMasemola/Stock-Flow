package com.example.stockflow.ui.suppliers

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.data.remote.PurchaseOrderDto
import com.example.stockflow.databinding.ActivityPurchaseOrderDetailBinding
import com.example.stockflow.ui.common.SystemBars

class PurchaseOrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseOrderDetailBinding
    private val viewModel: PurchaseOrderViewModel by viewModels()
    private var orderId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        orderId = intent.getIntExtra(EXTRA_ORDER_ID, -1)
        if (orderId <= 0) {
            Toast.makeText(this, R.string.po_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnReceive.setOnClickListener { confirmReceive() }

        viewModel.detailState.observe(this) { state ->
            when (state) {
                is PurchaseOrderViewModel.DetailUiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.content.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                }
                is PurchaseOrderViewModel.DetailUiState.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.content.visibility = View.VISIBLE
                    renderOrder(state.order)
                }
                is PurchaseOrderViewModel.DetailUiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.content.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = state.message
                }
            }
        }

        viewModel.receiveMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.clearReceiveMessage()
            }
        }

        viewModel.loadPurchaseOrder(orderId)
    }

    private fun renderOrder(order: PurchaseOrderDto) {
        binding.tvOrderId.text = getString(R.string.po_id_format, order.id)
        binding.tvSupplier.text = order.supplierName ?: getString(R.string.supplier_id_label, order.supplierId)
        binding.tvStatus.text = getString(R.string.po_status_format, order.status)
        binding.tvDate.text = order.createdAt.replace('T', ' ').take(19)
        binding.tvTotal.text = getString(R.string.price_format, order.totalAmount)

        binding.itemsContainer.removeAllViews()
        for (item in order.items) {
            val row = TextView(this).apply {
                text = getString(
                    R.string.po_item_line_format,
                    item.productName ?: "Product #${item.productId}",
                    item.quantity,
                    item.unitCost,
                    item.subtotal
                )
                setTextColor(getColor(R.color.brand_text_dark))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            binding.itemsContainer.addView(row)
        }

        val canReceive = order.status.equals("Pending", ignoreCase = true)
        binding.btnReceive.visibility = if (canReceive) View.VISIBLE else View.GONE
    }

    private fun confirmReceive() {
        AlertDialog.Builder(this)
            .setTitle(R.string.receive_order_title)
            .setMessage(R.string.receive_order_message)
            .setPositiveButton(R.string.receive_order) { _, _ ->
                viewModel.receivePurchaseOrder(orderId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }
}
