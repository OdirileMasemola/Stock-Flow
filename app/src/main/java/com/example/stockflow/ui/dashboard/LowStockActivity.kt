package com.example.stockflow.ui.dashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.databinding.ActivityLowStockBinding

class LowStockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLowStockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLowStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}
