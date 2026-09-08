package com.example.stockflow.ui.dashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.databinding.ActivityReportsBinding
import com.example.stockflow.ui.common.SystemBars

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}
