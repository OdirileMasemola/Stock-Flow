package com.example.stockflow.ui.inventory

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles create and edit product form submissions, including optional image upload.
 */
class AddProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _formState = MutableLiveData<FormState>()
    val formState: LiveData<FormState> = _formState

    private val _loadedProduct = MutableLiveData<ProductDto?>()
    val loadedProduct: LiveData<ProductDto?> = _loadedProduct

    /** Existing remote image URL for the product being edited (if any). */
    private var existingImageUrl: String? = null

    /** Newly picked local image waiting to be uploaded on save. */
    private var pendingImageUri: Uri? = null

    /** True when the user explicitly cleared the product image. */
    private var imageRemoved: Boolean = false

    fun loadProduct(id: Int) {
        _formState.value = FormState.Loading
        viewModelScope.launch {
            val result = repository.getProduct(id)
            if (result.isSuccess) {
                val product = result.getOrNull()
                existingImageUrl = product?.imageUrl
                pendingImageUri = null
                imageRemoved = false
                _loadedProduct.postValue(product)
                _formState.postValue(FormState.Idle)
            } else {
                _formState.postValue(
                    FormState.Error(result.exceptionOrNull()?.message ?: "Unable to load product")
                )
            }
        }
    }

    fun setPendingImage(uri: Uri?) {
        pendingImageUri = uri
        imageRemoved = false
    }

    fun clearImage() {
        pendingImageUri = null
        imageRemoved = true
        existingImageUrl = null
    }

    fun currentRemoteImageUrl(): String? =
        if (imageRemoved) null else existingImageUrl

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
            val imageUrlResult = resolveImageUrlForSave()
            if (imageUrlResult.isFailure) {
                _formState.postValue(
                    FormState.Error(
                        imageUrlResult.exceptionOrNull()?.message
                            ?: "Could not upload the product image. Product was not saved."
                    )
                )
                return@launch
            }
            val imageUrl = imageUrlResult.getOrNull()

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
                        supplierId = supplierId,
                        imageUrl = imageUrl
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
                        supplierId = supplierId,
                        imageUrl = imageUrl
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

    private suspend fun resolveImageUrlForSave(): Result<String?> {
        val localUri = pendingImageUri
        if (localUri != null) {
            return uploadLocalImage(localUri)
        }
        if (imageRemoved) {
            return Result.success(null)
        }
        return Result.success(existingImageUrl)
    }

    private suspend fun uploadLocalImage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            if (!mimeType.startsWith("image/")) {
                return@withContext Result.failure(Exception("Please choose a valid image file"))
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Unable to read the selected image"))
            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("Selected image is empty"))
            }
            if (bytes.size > MAX_UPLOAD_BYTES) {
                return@withContext Result.failure(Exception("Image must be 5 MB or smaller"))
            }
            val extension = extensionForMime(mimeType)
            val fileName = "product.$extension"
            repository.uploadProductImage(bytes, fileName, mimeType)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Could not upload the product image. Product was not saved."))
        }
    }

    private fun extensionForMime(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
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

    companion object {
        private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
    }
}
