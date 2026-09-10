package com.example.stockflow.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockflow.R
import com.example.stockflow.databinding.ActivityLowStockBinding
import com.example.stockflow.ui.common.SystemBars

class LowStockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLowStockBinding
    private val viewModel: LowStockViewModel by viewModels()
    private lateinit var adapter: LowStockAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLowStockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = LowStockAdapter()
        binding.rvLowStock.layoutManager = LinearLayoutManager(this)
        binding.rvLowStock.adapter = adapter

        binding.btnRetry.setOnClickListener { viewModel.loadLowStock() }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is LowStockViewModel.LowStockUiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.rvLowStock.visibility = View.GONE
                    binding.emptyState.visibility = View.GONE
                }
                is LowStockViewModel.LowStockUiState.Empty -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.rvLowStock.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = getString(R.string.low_stock_empty)
                    binding.btnRetry.visibility = View.GONE
                    adapter.submitList(emptyList())
                }
                is LowStockViewModel.LowStockUiState.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.emptyState.visibility = View.GONE
                    binding.rvLowStock.visibility = View.VISIBLE
                    adapter.submitList(state.products)
                }
                is LowStockViewModel.LowStockUiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.rvLowStock.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = state.message
                    binding.btnRetry.visibility = View.VISIBLE
                    adapter.submitList(emptyList())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLowStock()
    }
}
