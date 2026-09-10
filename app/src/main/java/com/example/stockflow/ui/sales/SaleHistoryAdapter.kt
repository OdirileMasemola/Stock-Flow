package com.example.stockflow.ui.sales

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockflow.R
import com.example.stockflow.data.remote.SaleDto
import com.example.stockflow.databinding.ItemSaleHistoryBinding

class SaleHistoryAdapter(
    private val onClick: (SaleDto) -> Unit
) : ListAdapter<SaleDto, SaleHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSaleHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSaleHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(sale: SaleDto) {
            binding.tvSaleId.text = binding.root.context.getString(R.string.sale_id_format, sale.id)
            binding.tvSaleDate.text = sale.createdAt.replace('T', ' ')
            binding.tvPayment.text = sale.paymentMethod
            binding.tvTotal.text = binding.root.context.getString(R.string.price_format, sale.totalAmount)
            binding.root.setOnClickListener { onClick(sale) }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<SaleDto>() {
            override fun areItemsTheSame(oldItem: SaleDto, newItem: SaleDto): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SaleDto, newItem: SaleDto): Boolean =
                oldItem == newItem
        }
    }
}
