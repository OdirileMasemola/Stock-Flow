package com.odirilemasemola.stockflow.ui.employees

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.remote.EmployeeRoles
import com.odirilemasemola.stockflow.databinding.ActivityAddEmployeeBinding
import com.odirilemasemola.stockflow.ui.common.SystemBars

/** Adds an existing StockFlow account (registered as Staff or Supplier) to the owner's shop. */
class AddEmployeeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEmployeeBinding
    private val viewModel: AddEmployeeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEmployeeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.chipGroupRole.setOnCheckedStateChangeListener { _, _ -> updateRoleHint() }
        updateRoleHint()

        binding.etEmail.doAfterTextChanged {
            binding.etEmail.error = null
            binding.tvFormError.visibility = View.GONE
        }
        binding.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.btnSave.setOnClickListener { submit() }

        viewModel.formState.observe(this) { state ->
            setLoading(state is AddEmployeeViewModel.FormState.Loading)
            when (state) {
                is AddEmployeeViewModel.FormState.Idle,
                is AddEmployeeViewModel.FormState.Loading -> Unit
                is AddEmployeeViewModel.FormState.EmailError -> {
                    binding.etEmail.error = state.message
                    binding.etEmail.requestFocus()
                }
                is AddEmployeeViewModel.FormState.Error -> {
                    binding.tvFormError.visibility = View.VISIBLE
                    binding.tvFormError.text = state.message
                }
                is AddEmployeeViewModel.FormState.Success -> {
                    val name = state.employee.fullName.ifBlank { state.employee.username }
                    Toast.makeText(this, getString(R.string.employee_added, name), Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun selectedRole(): String =
        if (binding.chipSupplier.isChecked) EmployeeRoles.SUPPLIER else EmployeeRoles.STAFF

    private fun updateRoleHint() {
        binding.tvRoleHint.text = getString(
            if (selectedRole() == EmployeeRoles.SUPPLIER) R.string.role_supplier_description
            else R.string.role_staff_description
        )
    }

    private fun submit() {
        binding.tvFormError.visibility = View.GONE
        viewModel.save(binding.etEmail.text.toString(), selectedRole())
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSaving.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnSave.alpha = if (loading) 0.5f else 1f
        binding.etEmail.isEnabled = !loading
        binding.chipGroupRole.isEnabled = !loading
    }
}
