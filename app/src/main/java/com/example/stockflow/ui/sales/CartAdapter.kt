package com.example.stockflow.ui.sales

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockflow.R
import com.example.stockflow.databinding.ItemCartLineBinding

class CartAdapter(
    private val onIncrease: (Int) -> Unit,
    private val onDecrease: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : ListAdapter<CartSession.CartLine, CartAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCartLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: CartSession.CartLine) {
            binding.tvName.text = line.name
            binding.tvUnitPrice.text = binding.root.context.getString(R.string.price_format, line.unitPrice)
            binding.tvQuantity.text = line.quantity.toString()
            binding.tvLineTotal.text = binding.root.context.getString(R.string.price_format, line.lineTotal)
            binding.btnIncrease.setOnClickListener { onIncrease(line.productId) }
            binding.btnDecrease.setOnClickListener { onDecrease(line.productId) }
            binding.btnRemove.setOnClickListener { onRemove(line.productId) }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<CartSession.CartLine>() {
            override fun areItemsTheSame(
                oldItem: CartSession.CartLine,
                newItem: CartSession.CartLine
            ): Boolean = oldItem.productId == newItem.productId

            override fun areContentsTheSame(
                oldItem: CartSession.CartLine,
                newItem: CartSession.CartLine
            ): Boolean = oldItem == newItem
        }
    }
}
