package com.example.stockflow.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.databinding.ItemLowStockBinding

class LowStockAdapter : ListAdapter<ProductDto, LowStockAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLowStockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemLowStockBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductDto) {
            val context = binding.root.context
            binding.tvProductName.text = product.name

            val category = product.categoryName?.takeIf { it.isNotBlank() }
            if (category != null) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = category
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            if (product.sku.isNullOrBlank()) {
                binding.tvSku.visibility = View.GONE
            } else {
                binding.tvSku.visibility = View.VISIBLE
                binding.tvSku.text = context.getString(R.string.sku_label, product.sku)
            }

            binding.tvCurrentStock.text =
                context.getString(R.string.low_stock_current, product.stockLevel)
            binding.tvMinStock.text =
                context.getString(R.string.low_stock_minimum, product.minStockLevel)

            if (product.stockLevel <= 0) {
                binding.tvStatus.text = context.getString(R.string.out_of_stock)
                binding.tvStatus.setTextColor(context.getColor(R.color.icon_warning))
                binding.tvStatus.setBackgroundColor(context.getColor(R.color.stat_card_red_bg))
            } else {
                binding.tvStatus.text = context.getString(R.string.low_stock)
                binding.tvStatus.setTextColor(context.getColor(R.color.icon_notifications))
                binding.tvStatus.setBackgroundColor(context.getColor(R.color.icon_bg_notifications))
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProductDto>() {
        override fun areItemsTheSame(oldItem: ProductDto, newItem: ProductDto): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ProductDto, newItem: ProductDto): Boolean =
            oldItem == newItem
    }
}
