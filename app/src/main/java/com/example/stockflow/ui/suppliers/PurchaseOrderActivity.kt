package com.example.stockflow.ui.suppliers

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.databinding.ActivityPurchaseOrderBinding
import com.example.stockflow.ui.common.SystemBars

class PurchaseOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseOrderBinding
    private val viewModel: PurchaseOrderViewModel by viewModels()
    private lateinit var lineAdapter: PoDraftLineAdapter

    private var suppliers: List<SupplierDto> = emptyList()
    private var products: List<ProductDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        val preselectedSupplierId = intent.getIntExtra(EXTRA_SUPPLIER_ID, -1).takeIf { it > 0 }

        binding.toolbar.setNavigationOnClickListener { finish() }

        lineAdapter = PoDraftLineAdapter(
            onIncrease = { line -> viewModel.setLineQuantity(line.productId, line.quantity + 1) },
            onDecrease = { line -> viewModel.setLineQuantity(line.productId, line.quantity - 1) },
            onRemove = { line -> viewModel.removeLine(line.productId) }
        )
        binding.rvLines.layoutManager = LinearLayoutManager(this)
        binding.rvLines.adapter = lineAdapter

        binding.btnAddLine.setOnClickListener { addSelectedProduct() }
        binding.btnPlaceOrder.setOnClickListener { viewModel.submitPurchaseOrder() }

        binding.spinnerProduct.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val product = products.getOrNull(position) ?: return
                binding.etUnitCost.setText(product.costPrice.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.spinnerSupplier.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val supplier = suppliers.getOrNull(position) ?: return
                viewModel.setSelectedSupplier(supplier.id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        viewModel.suppliers.observe(this) { list ->
            suppliers = list
            val names = list.map { it.name }
            binding.spinnerSupplier.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
            val index = list.indexOfFirst { it.id == preselectedSupplierId }
            if (index >= 0) {
                binding.spinnerSupplier.setSelection(index)
                viewModel.setSelectedSupplier(list[index].id)
            } else if (list.isNotEmpty()) {
                viewModel.setSelectedSupplier(list[0].id)
            }
        }

        viewModel.products.observe(this) { list ->
            products = list
            val names = list.map { "${it.name} (stock ${it.stockLevel})" }
            binding.spinnerProduct.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
            list.firstOrNull()?.let {
                binding.etUnitCost.setText(it.costPrice.toString())
            }
        }

        viewModel.draftLines.observe(this) { lines ->
            lineAdapter.submitList(lines)
            val total = lines.sumOf { it.subtotal }
            binding.tvDraftTotal.text = getString(R.string.po_draft_total, total)
        }

        viewModel.createState.observe(this) { state ->
            when (state) {
                is PurchaseOrderViewModel.CreateUiState.Idle,
                is PurchaseOrderViewModel.CreateUiState.Ready -> {
                    setSubmitting(false)
                    binding.tvFormError.visibility = View.GONE
                }
                is PurchaseOrderViewModel.CreateUiState.Submitting -> setSubmitting(true)
                is PurchaseOrderViewModel.CreateUiState.Success -> {
                    setSubmitting(false)
                    Toast.makeText(
                        this,
                        getString(R.string.po_created, state.order.id),
                        Toast.LENGTH_SHORT
                    ).show()
                    startActivity(
                        Intent(this, PurchaseOrderDetailActivity::class.java).apply {
                            putExtra(PurchaseOrderDetailActivity.EXTRA_ORDER_ID, state.order.id)
                        }
                    )
                    finish()
                }
                is PurchaseOrderViewModel.CreateUiState.Error -> {
                    setSubmitting(false)
                    binding.tvFormError.visibility = View.VISIBLE
                    binding.tvFormError.text = state.message
                }
            }
        }

        viewModel.prepareCreateForm(preselectedSupplierId)
    }

    private fun addSelectedProduct() {
        val product = products.getOrNull(binding.spinnerProduct.selectedItemPosition)
        if (product == null) {
            binding.tvFormError.visibility = View.VISIBLE
            binding.tvFormError.text = getString(R.string.po_no_products)
            return
        }
        val qty = binding.etQuantity.text.toString().toIntOrNull()
        val cost = binding.etUnitCost.text.toString().toDoubleOrNull()
        if (qty == null || qty <= 0) {
            binding.tvFormError.visibility = View.VISIBLE
            binding.tvFormError.text = getString(R.string.po_invalid_quantity)
            return
        }
        if (cost == null || cost < 0) {
            binding.tvFormError.visibility = View.VISIBLE
            binding.tvFormError.text = getString(R.string.po_invalid_cost)
            return
        }
        viewModel.addOrUpdateLine(product, qty, cost)
        binding.etQuantity.setText("1")
    }

    private fun setSubmitting(loading: Boolean) {
        binding.progressSaving.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnPlaceOrder.isEnabled = !loading
        binding.btnAddLine.isEnabled = !loading
    }

    companion object {
        const val EXTRA_SUPPLIER_ID = "extra_supplier_id"
    }
}
