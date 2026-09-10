package com.example.stockflow.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.repository.ProductRepository
import kotlinx.coroutines.launch

/**
 * Handles create and edit product form submissions.
 */
class AddProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _formState = MutableLiveData<FormState>()
    val formState: LiveData<FormState> = _formState

    private val _loadedProduct = MutableLiveData<ProductDto?>()
    val loadedProduct: LiveData<ProductDto?> = _loadedProduct

    fun loadProduct(id: Int) {
        _formState.value = FormState.Loading
        viewModelScope.launch {
            val result = repository.getProduct(id)
            if (result.isSuccess) {
                _loadedProduct.postValue(result.getOrNull())
                _formState.postValue(FormState.Idle)
            } else {
                _formState.postValue(
                    FormState.Error(result.exceptionOrNull()?.message ?: "Unable to load product")
                )
            }
        }
    }

    fun saveProduct(
        productId: Int?,
        name: String,
        sku: String,
        costPriceText: String,
        sellingPriceText: String,
        stockLevelText: String,
        minStockLevelText: String,
        categoryIdText: String,
        supplierIdText: String
    ) {
        if (_formState.value is FormState.Loading) {
            return
        }

        val validationError = validateLocal(
            name, costPriceText, sellingPriceText, stockLevelText, minStockLevelText, categoryIdText, supplierIdText
        )
        if (validationError != null) {
            _formState.value = FormState.Error(validationError)
            return
        }

        val costPrice = costPriceText.toDouble()
        val sellingPrice = sellingPriceText.toDouble()
        val stockLevel = stockLevelText.toInt()
        val minStockLevel = minStockLevelText.toInt()
        val categoryId = categoryIdText.toInt()
        val supplierId = supplierIdText.trim().takeIf { it.isNotEmpty() }?.toInt()
        val normalizedSku = sku.trim().takeIf { it.isNotEmpty() }

        _formState.value = FormState.Loading
        viewModelScope.launch {
            val result = if (productId == null) {
                repository.createProduct(
                    CreateProductRequest(
                        name = name.trim(),
                        sku = normalizedSku,
                        costPrice = costPrice,
                        sellingPrice = sellingPrice,
                        stockLevel = stockLevel,
                        minStockLevel = minStockLevel,
                        categoryId = categoryId,
                        supplierId = supplierId
                    )
                )
            } else {
                repository.updateProduct(
                    productId,
                    UpdateProductRequest(
                        name = name.trim(),
                        sku = normalizedSku,
                        costPrice = costPrice,
                        sellingPrice = sellingPrice,
                        stockLevel = stockLevel,
                        minStockLevel = minStockLevel,
                        categoryId = categoryId,
                        supplierId = supplierId
                    )
                )
            }

            if (result.isSuccess) {
                _formState.postValue(FormState.Success(result.getOrNull()!!, isUpdate = productId != null))
            } else {
                _formState.postValue(
                    FormState.Error(result.exceptionOrNull()?.message ?: "Failed to save product")
                )
            }
        }
    }

    private fun validateLocal(
        name: String,
        costPriceText: String,
        sellingPriceText: String,
        stockLevelText: String,
        minStockLevelText: String,
        categoryIdText: String,
        supplierIdText: String
    ): String? {
        if (name.isBlank()) return "Product name is required"
        if (costPriceText.toDoubleOrNull() == null) return "Enter a valid cost price"
        if (sellingPriceText.toDoubleOrNull() == null) return "Enter a valid selling price"
        if (stockLevelText.toIntOrNull() == null) return "Enter a valid stock level"
        if (minStockLevelText.toIntOrNull() == null) return "Enter a valid minimum stock level"
        if (categoryIdText.toIntOrNull() == null) return "Enter a valid category ID"
        if (supplierIdText.isNotBlank() && supplierIdText.toIntOrNull() == null) {
            return "Enter a valid supplier ID (or leave blank)"
        }
        if (costPriceText.toDouble() < 0) return "Cost price cannot be negative"
        if (sellingPriceText.toDouble() < 0) return "Selling price cannot be negative"
        if (stockLevelText.toInt() < 0) return "Stock level cannot be negative"
        if (minStockLevelText.toInt() < 0) return "Minimum stock cannot be negative"
        return null
    }

    sealed class FormState {
        object Idle : FormState()
        object Loading : FormState()
        data class Success(val product: ProductDto, val isUpdate: Boolean) : FormState()
        data class Error(val message: String) : FormState()
    }
}
