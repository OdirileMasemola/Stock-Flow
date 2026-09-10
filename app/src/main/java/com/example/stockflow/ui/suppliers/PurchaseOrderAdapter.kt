package com.example.stockflow.ui.suppliers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockflow.R
import com.example.stockflow.data.remote.PurchaseOrderDto
import com.example.stockflow.databinding.ItemPurchaseOrderBinding

class PurchaseOrderAdapter(
    private val onOpen: (PurchaseOrderDto) -> Unit
) : ListAdapter<PurchaseOrderDto, PurchaseOrderAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPurchaseOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(
        private val binding: ItemPurchaseOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: PurchaseOrderDto) {
            val ctx = binding.root.context
            binding.tvOrderId.text = ctx.getString(R.string.po_id_format, order.id)
            binding.tvSupplier.text = order.supplierName ?: "Supplier #${order.supplierId}"
            binding.tvStatus.text = order.status
            binding.tvDate.text = order.createdAt.replace('T', ' ').take(16)
            binding.tvTotal.text = ctx.getString(R.string.price_format, order.totalAmount)
            binding.root.setOnClickListener { onOpen(order) }
            binding.btnView.setOnClickListener { onOpen(order) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<PurchaseOrderDto>() {
        override fun areItemsTheSame(a: PurchaseOrderDto, b: PurchaseOrderDto) = a.id == b.id
        override fun areContentsTheSame(a: PurchaseOrderDto, b: PurchaseOrderDto) = a == b
    }
}
