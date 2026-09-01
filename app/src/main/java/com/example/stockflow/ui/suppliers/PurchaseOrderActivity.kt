package com.example.stockflow.ui.suppliers

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.databinding.ActivityPurchaseOrderBinding

class PurchaseOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseOrderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnPlaceOrder.setOnClickListener {
            finish()
        }
    }
}
