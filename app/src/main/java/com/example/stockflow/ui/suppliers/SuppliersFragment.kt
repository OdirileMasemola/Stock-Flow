package com.example.stockflow.ui.suppliers

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
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.databinding.FragmentSuppliersBinding

class SuppliersFragment : Fragment() {

    private var _binding: FragmentSuppliersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SupplierViewModel by viewModels()
    private lateinit var adapter: SupplierAdapter
    private var refreshOnResume = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuppliersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SupplierAdapter(
            onEdit = { supplier -> openEdit(supplier) },
            onDelete = { supplier -> confirmDelete(supplier) },
            onCreateOrder = { supplier -> openCreateOrder(supplier) }
        )

        binding.rvSuppliers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuppliers.adapter = adapter

        binding.btnAddSupplier.setOnClickListener {
            refreshOnResume = true
            startActivity(Intent(requireContext(), AddSupplierActivity::class.java))
        }

        binding.btnPurchaseOrders.setOnClickListener {
            refreshOnResume = true
            startActivity(Intent(requireContext(), PurchaseOrdersActivity::class.java))
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadSuppliers()
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
        viewModel.loadSuppliers(force = refreshOnResume)
        refreshOnResume = false
    }

    private fun renderState(state: SupplierViewModel.SuppliersUiState) {
        when (state) {
            is SupplierViewModel.SuppliersUiState.Loading -> {
                binding.progressLoading.visibility = View.VISIBLE
                binding.rvSuppliers.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            }
            is SupplierViewModel.SuppliersUiState.Empty -> {
                binding.progressLoading.visibility = View.GONE
                binding.rvSuppliers.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = getString(R.string.suppliers_empty)
                binding.btnRetry.visibility = View.GONE
                adapter.submitList(emptyList())
            }
            is SupplierViewModel.SuppliersUiState.EmptySearch -> {
                binding.progressLoading.visibility = View.GONE
                binding.rvSuppliers.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = getString(R.string.suppliers_empty_search, state.query)
                binding.btnRetry.visibility = View.GONE
                adapter.submitList(emptyList())
            }
            is SupplierViewModel.SuppliersUiState.Success -> {
                binding.progressLoading.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
                binding.rvSuppliers.visibility = View.VISIBLE
                adapter.submitList(state.suppliers)
            }
            is SupplierViewModel.SuppliersUiState.Error -> {
                binding.progressLoading.visibility = View.GONE
                binding.rvSuppliers.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = state.message
                binding.btnRetry.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            }
        }
    }

    private fun openEdit(supplier: SupplierDto) {
        refreshOnResume = true
        startActivity(
            Intent(requireContext(), AddSupplierActivity::class.java).apply {
                putExtra(AddSupplierActivity.EXTRA_SUPPLIER_ID, supplier.id)
            }
        )
    }

    private fun openCreateOrder(supplier: SupplierDto) {
        refreshOnResume = true
        startActivity(
            Intent(requireContext(), PurchaseOrderActivity::class.java).apply {
                putExtra(PurchaseOrderActivity.EXTRA_SUPPLIER_ID, supplier.id)
            }
        )
    }

    private fun confirmDelete(supplier: SupplierDto) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_supplier_title)
            .setMessage(getString(R.string.delete_supplier_message, supplier.name))
            .setPositiveButton(R.string.delete_supplier) { _, _ ->
                viewModel.deleteSupplier(supplier)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
