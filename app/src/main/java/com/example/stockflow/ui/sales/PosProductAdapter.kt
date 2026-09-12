package com.example.stockflow.ui.sales

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.databinding.ItemPosProductBinding
import com.example.stockflow.ui.common.ProductImages

class PosProductAdapter(
    private val onAdd: (ProductDto) -> Unit
) : ListAdapter<ProductDto, PosProductAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPosProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPosProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductDto) {
            binding.tvName.text = product.name
            binding.tvPrice.text = binding.root.context.getString(R.string.price_format, product.sellingPrice)
            binding.tvStock.text = binding.root.context.getString(
                R.string.pos_stock_format,
                product.stockLevel
            )
            bindImage(product.imageUrl)
            val inStock = product.stockLevel > 0
            binding.root.alpha = if (inStock) 1f else 0.5f
            binding.root.isEnabled = inStock
            binding.root.setOnClickListener {
                if (inStock) onAdd(product)
            }
        }

        private fun bindImage(imageUrl: String?) {
            val resolved = ProductImages.resolveUrl(imageUrl)
            if (resolved.isNullOrBlank()) {
                binding.ivProductImage.setImageResource(R.drawable.bg_product_image_placeholder)
                binding.ivProductPlaceholderIcon.visibility = View.VISIBLE
                return
            }
            binding.ivProductPlaceholderIcon.visibility = View.GONE
            val thumbPx = (120 * binding.root.resources.displayMetrics.density).toInt()
            binding.ivProductImage.load(resolved) {
                placeholder(R.drawable.bg_product_image_placeholder)
                error(R.drawable.bg_product_image_placeholder)
                size(thumbPx, thumbPx)
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
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ProductDto>() {
            override fun areItemsTheSame(oldItem: ProductDto, newItem: ProductDto): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ProductDto, newItem: ProductDto): Boolean =
                oldItem == newItem
        }
    }
}
