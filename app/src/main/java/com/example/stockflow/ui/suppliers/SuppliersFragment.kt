package com.example.stockflow.ui.suppliers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.stockflow.databinding.FragmentSuppliersBinding
import com.example.stockflow.ui.suppliers.PurchaseOrderActivity

class SuppliersFragment : Fragment() {

    private var _binding: FragmentSuppliersBinding? = null
    private val binding get() = _binding!!

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
        
        binding.btnAddSupplier.setOnClickListener {
            // startActivity(Intent(requireContext(), AddSupplierActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
