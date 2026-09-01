package com.example.stockflow.ui.inventory

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.databinding.ActivityAddProductBinding

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            finish()
        }
    }
}
