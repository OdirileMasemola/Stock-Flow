package com.odirilemasemola.stockflow.ui.employees

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.remote.EmployeeDto
import com.odirilemasemola.stockflow.data.remote.EmployeeRoles
import com.odirilemasemola.stockflow.databinding.ActivityEmployeesBinding
import com.odirilemasemola.stockflow.ui.common.SystemBars

/**
 * Owner-only staff list (design doc screen 10): add, change role, remove.
 * Settings only shows the entry for owners; the server also rejects everyone else (403).
 */
class EmployeesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmployeesBinding
    private val viewModel: EmployeesViewModel by viewModels()
    private lateinit var adapter: EmployeeAdapter

    private val addEmployee = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) viewModel.load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmployeesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = EmployeeAdapter(onChangeRole = ::showRoleDialog, onRemove = ::confirmRemove)
        binding.rvEmployees.layoutManager = LinearLayoutManager(this)
        binding.rvEmployees.adapter = adapter

        binding.btnAddEmployee.setOnClickListener {
            addEmployee.launch(Intent(this, AddEmployeeActivity::class.java))
        }
        binding.btnRetry.setOnClickListener { viewModel.load() }

        viewModel.state.observe(this, ::render)
        viewModel.busy.observe(this) { busy ->
            binding.btnAddEmployee.isEnabled = !busy
            binding.rvEmployees.alpha = if (busy) 0.6f else 1f
        }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
        }
        viewModel.load()
    }

    private fun render(state: EmployeesViewModel.UiState) {
        binding.progressLoading.visibility = if (state is EmployeesViewModel.UiState.Loading) View.VISIBLE else View.GONE
        binding.rvEmployees.visibility = if (state is EmployeesViewModel.UiState.Success) View.VISIBLE else View.GONE
        binding.emptyState.visibility =
            if (state is EmployeesViewModel.UiState.Empty || state is EmployeesViewModel.UiState.Error) View.VISIBLE else View.GONE
        binding.tvCount.visibility = if (state is EmployeesViewModel.UiState.Success) View.VISIBLE else View.GONE
        when (state) {
            is EmployeesViewModel.UiState.Loading -> Unit
            is EmployeesViewModel.UiState.Empty -> {
                binding.ivEmptyIcon.setImageResource(R.drawable.ic_group)
                binding.tvEmptyTitle.text = getString(R.string.employees_empty_title)
                binding.tvEmptyMessage.text = getString(R.string.employees_empty_message)
                binding.btnRetry.visibility = View.GONE
                binding.btnAddEmployee.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            }
            is EmployeesViewModel.UiState.Success -> {
                binding.tvCount.text = resources.getQuantityString(
                    R.plurals.employees_count, state.employees.size, state.employees.size
                )
                binding.btnAddEmployee.visibility = View.VISIBLE
                adapter.submitList(state.employees)
            }
            is EmployeesViewModel.UiState.Error -> {
                binding.ivEmptyIcon.setImageResource(if (state.notOwner) R.drawable.ic_lock else R.drawable.ic_warning)
                binding.tvEmptyTitle.text = getString(
                    if (state.notOwner) R.string.employees_owner_only_title else R.string.employees_error_title
                )
                binding.tvEmptyMessage.text = state.message
                binding.btnRetry.visibility = if (state.notOwner) View.GONE else View.VISIBLE
                binding.btnAddEmployee.visibility = if (state.notOwner) View.GONE else View.VISIBLE
                adapter.submitList(emptyList())
            }
        }
    }

    private fun showRoleDialog(employee: EmployeeDto) {
        val roles = EmployeeRoles.ASSIGNABLE
        val labels = roles.map { roleLabel(this, it) }.toTypedArray()
        var selected = roles.indexOfFirst { it.equals(employee.role, ignoreCase = true) }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_role_title, employee.fullName.ifBlank { employee.username }))
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setPositiveButton(R.string.save) { _, _ -> viewModel.changeRole(employee, roles[selected]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(employee: EmployeeDto) {
        val name = employee.fullName.ifBlank { employee.username }
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_employee_title)
            .setMessage(getString(R.string.remove_employee_message, name))
            .setPositiveButton(R.string.remove_employee) { _, _ -> viewModel.remove(employee) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
