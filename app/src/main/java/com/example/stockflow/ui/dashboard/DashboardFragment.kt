package com.example.stockflow.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.stockflow.databinding.FragmentDashboardBinding
import com.example.stockflow.ui.inventory.AddProductActivity
import com.example.stockflow.ui.dashboard.LowStockActivity
import com.example.stockflow.ui.dashboard.ReportsActivity

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.cardLowStock.setOnClickListener {
            startActivity(Intent(requireContext(), LowStockActivity::class.java))
        }

        binding.actionAddProduct.setOnClickListener {
            startActivity(Intent(requireContext(), AddProductActivity::class.java))
        }

        binding.actionNewSale.setOnClickListener {
            // Toast.makeText(requireContext(), "New Sale clicked", Toast.LENGTH_SHORT).show()
            // In a real app, this might switch the BottomNav to Sales tab
        }

        binding.actionSupplier.setOnClickListener {
             // startActivity(Intent(requireContext(), SuppliersActivity::class.java))
        }

        binding.actionReports.setOnClickListener {
            startActivity(Intent(requireContext(), ReportsActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
