package com.example.stockflow.ui.suppliers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockflow.R
import com.example.stockflow.databinding.ItemPoLineBinding

class PoDraftLineAdapter(
    private val onIncrease: (PoDraftLine) -> Unit,
    private val onDecrease: (PoDraftLine) -> Unit,
    private val onRemove: (PoDraftLine) -> Unit
) : ListAdapter<PoDraftLine, PoDraftLineAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemPoLineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: PoDraftLine) {
            val ctx = binding.root.context
            binding.tvName.text = line.productName
            binding.tvUnitCost.text = ctx.getString(R.string.po_unit_cost_format, line.unitCost)
            binding.tvQuantity.text = line.quantity.toString()
            binding.tvLineTotal.text = ctx.getString(R.string.price_format, line.subtotal)
            binding.btnIncrease.setOnClickListener { onIncrease(line) }
            binding.btnDecrease.setOnClickListener { onDecrease(line) }
            binding.btnRemove.setOnClickListener { onRemove(line) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<PoDraftLine>() {
        override fun areItemsTheSame(a: PoDraftLine, b: PoDraftLine) = a.productId == b.productId
        override fun areContentsTheSame(a: PoDraftLine, b: PoDraftLine) = a == b
    }
}
