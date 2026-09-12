package com.example.stockflow.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.databinding.ItemProductBinding
import com.example.stockflow.ui.common.ProductImages

class ProductAdapter(
    private val onEdit: (ProductDto) -> Unit,
    private val onDelete: (ProductDto) -> Unit
) : ListAdapter<ProductDto, ProductAdapter.ProductViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductDto) {
            val context = binding.root.context
            binding.tvProductName.text = product.name
            binding.tvSellingPrice.text = context.getString(R.string.price_format, product.sellingPrice)
            binding.tvStockStats.text = context.getString(R.string.stock_count, product.stockLevel)

            val category = product.categoryName?.takeIf { it.isNotBlank() }
                ?: "Category #${product.categoryId}"
            binding.tvMeta.text = if (product.sku.isNullOrBlank()) {
                category
            } else {
                "$category · ${context.getString(R.string.sku_label, product.sku)}"
            }

            bindImage(product.imageUrl)

            when {
                product.stockLevel <= 0 -> {
                    binding.tvStockStatus.text = context.getString(R.string.out_of_stock)
                    binding.tvStockStatus.setTextColor(context.getColor(R.color.icon_warning))
                    binding.tvStockStatus.setBackgroundColor(context.getColor(R.color.stat_card_red_bg))
                    binding.stockProgress.setIndicatorColor(context.getColor(R.color.icon_warning))
                    binding.stockProgress.progress = 0
                }
                product.stockLevel <= product.minStockLevel -> {
                    binding.tvStockStatus.text = context.getString(R.string.low_stock)
                    binding.tvStockStatus.setTextColor(context.getColor(R.color.icon_notifications))
                    binding.tvStockStatus.setBackgroundColor(context.getColor(R.color.icon_bg_notifications))
                    binding.stockProgress.setIndicatorColor(context.getColor(R.color.icon_notifications))
                    binding.stockProgress.progress = stockPercent(product)
                }
                else -> {
                    binding.tvStockStatus.text = context.getString(R.string.in_stock)
                    binding.tvStockStatus.setTextColor(context.getColor(R.color.brand_primary))
                    binding.tvStockStatus.setBackgroundResource(R.drawable.bg_status_chip)
                    binding.stockProgress.setIndicatorColor(context.getColor(R.color.brand_primary))
                    binding.stockProgress.progress = stockPercent(product)
                }
            }

            binding.btnEdit.setOnClickListener { onEdit(product) }
            binding.btnDelete.setOnClickListener { onDelete(product) }
            binding.root.setOnClickListener { onEdit(product) }
        }

        private fun bindImage(imageUrl: String?) {
            val resolved = ProductImages.resolveUrl(imageUrl)
            if (resolved.isNullOrBlank()) {
                binding.ivProductImage.setImageResource(R.drawable.bg_product_image_placeholder)
                binding.ivProductPlaceholderIcon.visibility = View.VISIBLE
                return
            }
            binding.ivProductPlaceholderIcon.visibility = View.GONE
            binding.ivProductImage.load(resolved) {
                placeholder(R.drawable.bg_product_image_placeholder)
                error(R.drawable.bg_product_image_placeholder)
                listener(
                    onError = { _, _ ->
                        binding.ivProductPlaceholderIcon.visibility = View.VISIBLE
                    },
                    onSuccess = { _, _ ->
                        binding.ivProductPlaceholderIcon.visibility = View.GONE
                    }
                )
            }
        }

        /** Progress bar relative to a comfortable buffer above min stock. */
        private fun stockPercent(product: ProductDto): Int {
            val target = (product.minStockLevel * 2).coerceAtLeast(1)
            return ((product.stockLevel.toFloat() / target) * 100).toInt().coerceIn(0, 100)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ProductDto>() {
            override fun areItemsTheSame(oldItem: ProductDto, newItem: ProductDto): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ProductDto, newItem: ProductDto): Boolean =
                oldItem == newItem
        }
    }
}
