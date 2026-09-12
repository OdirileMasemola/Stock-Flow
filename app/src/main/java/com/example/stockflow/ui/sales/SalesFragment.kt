package com.example.stockflow.ui.sales

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockflow.R
import com.example.stockflow.data.remote.SaleDto
import com.example.stockflow.databinding.FragmentSalesBinding
import com.example.stockflow.ui.scanner.BarcodeScannerActivity

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalesViewModel by viewModels()
    private lateinit var productAdapter: PosProductAdapter
    private lateinit var historyAdapter: SaleHistoryAdapter

    private var showingHistory = false
    /** Refresh POS catalog after cart/scan; skip redundant reloads on mere tab re-show. */
    private var refreshProductsOnResume = true

    private val scanProduct = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val value = result.data
            ?.getStringExtra(BarcodeScannerActivity.EXTRA_SCAN_VALUE)
            ?.trim()
            .orEmpty()
        if (value.isNotEmpty()) {
            viewModel.addToCartBySku(value)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        productAdapter = PosProductAdapter { product ->
            viewModel.addToCart(product)
        }
        historyAdapter = SaleHistoryAdapter { sale ->
            openSaleDetails(sale.id)
        }

        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = productAdapter

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnRetryProducts.setOnClickListener { viewModel.loadProducts() }
        binding.btnRetryHistory.setOnClickListener { viewModel.loadSalesHistory() }

        binding.btnOpenCart.setOnClickListener {
            refreshProductsOnResume = true
            startActivity(Intent(requireContext(), CartActivity::class.java))
        }

        binding.btnScanProduct.setOnClickListener {
            scanProduct.launch(Intent(requireContext(), BarcodeScannerActivity::class.java))
        }

        binding.btnToggleHistory.setOnClickListener {
            showingHistory = !showingHistory
            renderMode()
            if (showingHistory) {
                viewModel.loadSalesHistory()
            }
        }

        viewModel.productsState.observe(viewLifecycleOwner) { renderProducts(it) }
        viewModel.cartState.observe(viewLifecycleOwner) { renderCartBadge(it) }
        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
        viewModel.historyState.observe(viewLifecycleOwner) { renderHistory(it) }

        renderMode()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProducts(force = refreshProductsOnResume)
        refreshProductsOnResume = false
        if (showingHistory) {
            viewModel.loadSalesHistory()
        }
    }

    private fun renderMode() {
        binding.posPane.isVisible = !showingHistory
        binding.historyPane.isVisible = showingHistory
        binding.btnToggleHistory.text = getString(
            if (showingHistory) R.string.pos_back_to_pos else R.string.pos_history
        )
        binding.tvTitle.text = getString(
            if (showingHistory) R.string.pos_history else R.string.pos_title
        )
        binding.btnOpenCart.isVisible = !showingHistory
        binding.btnScanProduct.isVisible = !showingHistory
    }

    private fun renderCartBadge(state: CartSession.CartUiState) {
        val count = state.itemCount
        binding.tvCartBadge.isVisible = count > 0
        binding.tvCartBadge.text = if (count > 99) "99+" else count.toString()
    }

    private fun renderProducts(state: SalesViewModel.ProductsUiState) {
        binding.progressProducts.isVisible = state is SalesViewModel.ProductsUiState.Loading
        binding.rvProducts.isVisible = state is SalesViewModel.ProductsUiState.Success
        binding.emptyProducts.isVisible =
            state is SalesViewModel.ProductsUiState.Empty ||
                state is SalesViewModel.ProductsUiState.EmptySearch ||
                state is SalesViewModel.ProductsUiState.Error

        when (state) {
            is SalesViewModel.ProductsUiState.Loading -> Unit
            is SalesViewModel.ProductsUiState.Empty -> {
                binding.tvEmptyProducts.text = getString(R.string.pos_products_empty)
                binding.btnRetryProducts.isVisible = false
            }
            is SalesViewModel.ProductsUiState.EmptySearch -> {
                binding.tvEmptyProducts.text = getString(R.string.pos_products_empty_search, state.query)
                binding.btnRetryProducts.isVisible = false
            }
            is SalesViewModel.ProductsUiState.Success -> {
                productAdapter.submitList(state.products)
            }
            is SalesViewModel.ProductsUiState.Error -> {
                binding.tvEmptyProducts.text = state.message
                binding.btnRetryProducts.isVisible = true
            }
        }
    }

    private fun renderHistory(state: SalesViewModel.HistoryUiState) {
        binding.progressHistory.isVisible = state is SalesViewModel.HistoryUiState.Loading
        binding.rvHistory.isVisible = state is SalesViewModel.HistoryUiState.Success
        binding.emptyHistory.isVisible =
            state is SalesViewModel.HistoryUiState.Empty ||
                state is SalesViewModel.HistoryUiState.Error

        when (state) {
            is SalesViewModel.HistoryUiState.Loading -> Unit
            is SalesViewModel.HistoryUiState.Empty -> {
                binding.tvEmptyHistory.text = getString(R.string.pos_history_empty)
                binding.btnRetryHistory.isVisible = false
            }
            is SalesViewModel.HistoryUiState.Success -> {
                historyAdapter.submitList(state.sales)
            }
            is SalesViewModel.HistoryUiState.Error -> {
                binding.tvEmptyHistory.text = state.message
                binding.btnRetryHistory.isVisible = true
            }
        }
    }

    private fun openSaleDetails(saleId: Int) {
        viewModel.loadSaleDetails(saleId) { result ->
            if (!isAdded) return@loadSaleDetails
            if (result.isFailure) {
                Toast.makeText(
                    requireContext(),
                    result.exceptionOrNull()?.message ?: getString(R.string.pos_history_empty),
                    Toast.LENGTH_LONG
                ).show()
                return@loadSaleDetails
            }
            val sale = result.getOrNull() ?: return@loadSaleDetails
            showSaleDetailsDialog(sale)
        }
    }

    private fun showSaleDetailsDialog(sale: SaleDto) {
        val lines = if (sale.items.isEmpty()) {
            getString(R.string.pos_history_empty)
        } else {
            sale.items.joinToString("\n") { item ->
                val name = item.productName ?: "Product #${item.productId}"
                val unit = getString(R.string.price_format, item.unitPrice)
                val sub = getString(R.string.price_format, item.subtotal)
                "$name × ${item.quantity} @ $unit = $sub"
            }
        }

        val message = buildString {
            append(sale.createdAt.replace('T', ' '))
            append("\n")
            append(sale.paymentMethod)
            append("\n")
            append(getString(R.string.price_format, sale.totalAmount))
            append("\n\n")
            append(lines)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sale_details_title, sale.id))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
