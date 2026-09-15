package com.example.stockflow.ui.inventory

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.ProductSkuCodes
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.ui.common.ProductImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.stockflow.R

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
                    FormState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_unable_load_product))
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
        val normalizedSku = ProductSkuCodes.toStockFlowSku(sku) ?: sku.trim().takeIf { it.isNotEmpty() }

        _formState.value = FormState.Loading
        viewModelScope.launch {
            val imageUrlResult = resolveImageUrlForSave()
            if (imageUrlResult.isFailure) {
                _formState.postValue(
                    FormState.Error(
                        imageUrlResult.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.image_upload_failed)
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
                    FormState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_failed_save_product))
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
                return@withContext Result.failure(Exception(getApplication<Application>().getString(R.string.error_choose_valid_image)))
            }
            val bytes = try {
                ProductImages.readCompressedImageBytes(getApplication(), uri, MAX_UPLOAD_BYTES)
            } catch (e: IllegalArgumentException) {
                return@withContext Result.failure(Exception(e.message ?: getApplication<Application>().getString(R.string.error_unable_read_image)))
            }
            val fileName = "product.jpg"
            repository.uploadProductImage(bytes, fileName, "image/jpeg")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: getApplication<Application>().getString(R.string.image_upload_failed)))
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
        if (name.isBlank()) return getApplication<Application>().getString(R.string.error_product_name_required)
        if (costPriceText.toDoubleOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_cost_price)
        if (sellingPriceText.toDoubleOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_selling_price)
        if (stockLevelText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_stock_level)
        if (minStockLevelText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_min_stock)
        if (categoryIdText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_category_id)
        if (supplierIdText.isNotBlank() && supplierIdText.toIntOrNull() == null) {
            return getApplication<Application>().getString(R.string.error_valid_supplier_id)
        }
        if (costPriceText.toDouble() < 0) return getApplication<Application>().getString(R.string.error_cost_price_negative)
        if (sellingPriceText.toDouble() < 0) return getApplication<Application>().getString(R.string.error_selling_price_negative)
        if (stockLevelText.toInt() < 0) return getApplication<Application>().getString(R.string.error_stock_level_negative)
        if (minStockLevelText.toInt() < 0) return getApplication<Application>().getString(R.string.error_min_stock_negative)
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
