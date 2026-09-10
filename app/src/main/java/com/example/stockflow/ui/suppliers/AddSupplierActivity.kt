package com.example.stockflow.ui.suppliers

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.databinding.ActivityAddSupplierBinding
import com.example.stockflow.ui.common.SystemBars

/**
 * Create a new supplier or edit an existing one.
 * Pass [EXTRA_SUPPLIER_ID] to open in edit mode.
 */
class AddSupplierActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSupplierBinding
    private val viewModel: AddSupplierViewModel by viewModels()

    private var supplierId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSupplierBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        supplierId = intent.getIntExtra(EXTRA_SUPPLIER_ID, -1).takeIf { it > 0 }

        if (supplierId != null) {
            binding.toolbar.title = getString(R.string.edit_supplier_title)
            viewModel.loadSupplier(supplierId!!)
        } else {
            binding.toolbar.title = getString(R.string.add_supplier_title)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            viewModel.saveSupplier(
                supplierId = supplierId,
                name = binding.etName.text.toString(),
                contactName = binding.etContactName.text.toString(),
                phone = binding.etPhone.text.toString(),
                email = binding.etEmail.text.toString(),
                address = binding.etAddress.text.toString()
            )
        }

        viewModel.loadedSupplier.observe(this) { supplier ->
            supplier?.let { fillForm(it) }
        }

        viewModel.formState.observe(this) { state ->
            when (state) {
                is AddSupplierViewModel.FormState.Idle -> setLoading(false)
                is AddSupplierViewModel.FormState.Loading -> setLoading(true)
                is AddSupplierViewModel.FormState.Success -> {
                    setLoading(false)
                    val message = if (state.isUpdate) {
                        getString(R.string.supplier_updated)
                    } else {
                        getString(R.string.supplier_created)
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is AddSupplierViewModel.FormState.Error -> {
                    setLoading(false)
                    binding.tvFormError.visibility = View.VISIBLE
                    binding.tvFormError.text = state.message
                }
            }
        }
    }

    private fun fillForm(supplier: SupplierDto) {
        binding.etName.setText(supplier.name)
        binding.etContactName.setText(supplier.contactName.orEmpty())
        binding.etPhone.setText(supplier.phone.orEmpty())
        binding.etEmail.setText(supplier.email.orEmpty())
        binding.etAddress.setText(supplier.address.orEmpty())
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSaving.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        if (loading) {
            binding.tvFormError.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_SUPPLIER_ID = "extra_supplier_id"
    }
}
