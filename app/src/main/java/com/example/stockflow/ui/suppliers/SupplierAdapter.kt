package com.example.stockflow.ui.suppliers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.databinding.ItemSupplierBinding

class SupplierAdapter(
    private val onEdit: (SupplierDto) -> Unit,
    private val onDelete: (SupplierDto) -> Unit,
    private val onCreateOrder: (SupplierDto) -> Unit
) : ListAdapter<SupplierDto, SupplierAdapter.SupplierViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SupplierViewHolder {
        val binding = ItemSupplierBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SupplierViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SupplierViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SupplierViewHolder(
        private val binding: ItemSupplierBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(supplier: SupplierDto) {
            binding.tvSupplierName.text = supplier.name
            binding.tvContactName.text = supplier.contactName?.takeIf { it.isNotBlank() }
                ?: "No contact person"
            binding.tvPhone.text = supplier.phone?.takeIf { it.isNotBlank() } ?: "—"
            binding.tvEmail.text = supplier.email?.takeIf { it.isNotBlank() } ?: "—"
            binding.tvAvatarLetter.text = supplier.name
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"

            binding.btnEdit.setOnClickListener { onEdit(supplier) }
            binding.btnDelete.setOnClickListener { onDelete(supplier) }
            binding.btnCreateOrder.setOnClickListener { onCreateOrder(supplier) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<SupplierDto>() {
        override fun areItemsTheSame(oldItem: SupplierDto, newItem: SupplierDto): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SupplierDto, newItem: SupplierDto): Boolean =
            oldItem == newItem
    }
}
