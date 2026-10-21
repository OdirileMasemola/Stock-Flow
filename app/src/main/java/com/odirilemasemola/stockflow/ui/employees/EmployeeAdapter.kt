package com.odirilemasemola.stockflow.ui.employees

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.remote.EmployeeDto
import com.odirilemasemola.stockflow.databinding.ItemEmployeeBinding

class EmployeeAdapter(
    private val onChangeRole: (EmployeeDto) -> Unit,
    private val onRemove: (EmployeeDto) -> Unit
) : ListAdapter<EmployeeDto, EmployeeAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemEmployeeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemEmployeeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(employee: EmployeeDto) {
            val ctx = binding.root.context
            val name = employee.fullName.ifBlank { employee.username }
            val role = roleLabel(ctx, employee.role)
            binding.tvAvatarLetter.text = EmployeeFormValidator.initials(employee.fullName, employee.username)
            binding.tvEmployeeName.text = name
            binding.tvEmployeeEmail.text = employee.email
            binding.tvRole.text = role
            binding.employeeSummary.contentDescription =
                ctx.getString(R.string.employee_item_description, name, role, employee.email)
            binding.btnChangeRole.contentDescription = ctx.getString(R.string.change_role_for, name)
            binding.btnRemove.contentDescription = ctx.getString(R.string.remove_employee_for, name)
            binding.btnChangeRole.setOnClickListener { onChangeRole(employee) }
            binding.btnRemove.setOnClickListener { onRemove(employee) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<EmployeeDto>() {
        override fun areItemsTheSame(a: EmployeeDto, b: EmployeeDto) = a.id == b.id
        override fun areContentsTheSame(a: EmployeeDto, b: EmployeeDto) = a == b
    }
}

/** Localised label for a server role name. */
fun roleLabel(context: android.content.Context, role: String): String = when {
    role.equals("Staff", ignoreCase = true) -> context.getString(R.string.role_staff)
    role.equals("Supplier", ignoreCase = true) -> context.getString(R.string.role_supplier)
    role.equals("Owner", ignoreCase = true) -> context.getString(R.string.role_owner)
    else -> role
}
