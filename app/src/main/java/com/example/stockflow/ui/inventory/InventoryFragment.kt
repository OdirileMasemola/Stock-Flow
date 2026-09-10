package com.example.stockflow.ui.inventory

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.databinding.FragmentInventoryBinding

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductViewModel by viewModels()
    private lateinit var adapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductAdapter(
            onEdit = { product -> openEdit(product) },
            onDelete = { product -> confirmDelete(product) }
        )

        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter

        binding.btnAddProduct.setOnClickListener {
            startActivity(Intent(requireContext(), AddProductActivity::class.java))
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadProducts()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderState(state)
        }

        viewModel.deleteMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.clearDeleteMessage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload whenever the screen is shown (e.g. after adding/editing a product)
        viewModel.loadProducts()
    }

    private fun renderState(state: ProductViewModel.ProductsUiState) {
        when (state) {
            is ProductViewModel.ProductsUiState.Loading -> {
                binding.progressLoading.visibility = View.VISIBLE
                binding.rvProducts.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            }
            is ProductViewModel.ProductsUiState.Empty -> {
                binding.progressLoading.visibility = View.GONE
                binding.rvProducts.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = getString(R.string.inventory_empty)
                binding.btnRetry.visibility = View.GONE
                adapter.submitList(emptyList())
            }
            is ProductViewModel.ProductsUiState.EmptySearch -> {
                binding.progressLoading.visibility = View.GONE
                binding.rvProducts.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = getString(R.string.inventory_empty_search, state.query)
                binding.btnRetry.visibility = View.GONE
                adapter.submitList(emptyList())
            }
            is ProductViewModel.ProductsUiState.Success -> {
                binding.progressLoading.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
                binding.rvProducts.visibility = View.VISIBLE
                adapter.submitList(state.products)
            }
            is ProductViewModel.ProductsUiState.Error -> {
                binding.progressLoading.visibility = View.GONE
                binding.rvProducts.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = state.message
                binding.btnRetry.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            }
        }
    }

    private fun openEdit(product: ProductDto) {
        val intent = Intent(requireContext(), AddProductActivity::class.java).apply {
            putExtra(AddProductActivity.EXTRA_PRODUCT_ID, product.id)
        }
        startActivity(intent)
    }

    private fun confirmDelete(product: ProductDto) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_message, product.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_product) { _, _ ->
                viewModel.deleteProduct(product)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
