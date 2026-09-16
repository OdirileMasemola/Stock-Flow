package com.example.stockflow.ui.inventory

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.ProductSkuCodes
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.repository.CategoryRepository
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.ui.common.ProductImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles create and edit product form submissions, including optional image upload
 * and in-place category pick/create.
 */
class AddProductViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application.applicationContext)
    private val repository = ProductRepository(sessionStore = sessionStore)
    private val categoryRepository = CategoryRepository(sessionStore = sessionStore)

    private val _formState = MutableLiveData<FormState>()
    val formState: LiveData<FormState> = _formState

    private val _loadedProduct = MutableLiveData<ProductDto?>()
    val loadedProduct: LiveData<ProductDto?> = _loadedProduct

    private val _categories = MutableLiveData<List<CategoryDto>>(emptyList())
    val categories: LiveData<List<CategoryDto>> = _categories

    /** Existing remote image URL for the product being edited (if any). */
    private var existingImageUrl: String? = null

    /** Newly picked local image waiting to be uploaded on save. */
    private var pendingImageUri: Uri? = null

    /** True when the user explicitly cleared the product image. */
    private var imageRemoved: Boolean = false

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            val result = categoryRepository.getCategories()
            if (result.isSuccess) {
                _categories.postValue(result.getOrNull().orEmpty())
            }
            // Soft-fail: user can still type a new category name without the list.
        }
    }

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
                    FormState.Error(
                        result.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.error_unable_load_product)
                    )
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
        categoryNameText: String,
        supplierIdText: String
    ) {
        if (_formState.value is FormState.Loading) {
            return
        }

        val validationError = validateLocal(
            name,
            costPriceText,
            sellingPriceText,
            stockLevelText,
            minStockLevelText,
            categoryNameText,
            supplierIdText
        )
        if (validationError != null) {
            _formState.value = FormState.Error(validationError)
            return
        }

        val costPrice = costPriceText.toDouble()
        val sellingPrice = sellingPriceText.toDouble()
        val stockLevel = stockLevelText.toInt()
        val minStockLevel = minStockLevelText.toInt()
        val supplierId = supplierIdText.trim().takeIf { it.isNotEmpty() }?.toInt()
        val normalizedSku = ProductSkuCodes.toStockFlowSku(sku) ?: sku.trim().takeIf { it.isNotEmpty() }
        val categoryName = categoryNameText.trim()

        _formState.value = FormState.Loading
        viewModelScope.launch {
            val categoryResult = resolveCategoryId(categoryName)
            if (categoryResult.isFailure) {
                _formState.postValue(
                    FormState.Error(
                        categoryResult.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.error_failed_resolve_category)
                    )
                )
                return@launch
            }
            val categoryId = categoryResult.getOrNull()!!

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
                    FormState.Error(
                        result.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.error_failed_save_product)
                    )
                )
            }
        }
    }

    /**
     * Prefer a cached match (case-insensitive), otherwise ask the backend to find-or-create.
     */
    private suspend fun resolveCategoryId(categoryName: String): Result<Int> {
        val cached = _categories.value.orEmpty().firstOrNull {
            it.name.equals(categoryName, ignoreCase = true)
        }
        if (cached != null) {
            return Result.success(cached.id)
        }

        val created = categoryRepository.findOrCreateCategory(categoryName)
        if (created.isSuccess) {
            val category = created.getOrNull()!!
            val updated = _categories.value.orEmpty().toMutableList()
            if (updated.none { it.id == category.id }) {
                updated.add(category)
                updated.sortBy { it.name.lowercase() }
                _categories.postValue(updated)
            }
            return Result.success(category.id)
        }
        return Result.failure(
            created.exceptionOrNull()
                ?: Exception(getApplication<Application>().getString(R.string.error_failed_resolve_category))
        )
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
                return@withContext Result.failure(
                    Exception(getApplication<Application>().getString(R.string.error_choose_valid_image))
                )
            }
            val bytes = try {
                ProductImages.readCompressedImageBytes(getApplication(), uri, MAX_UPLOAD_BYTES)
            } catch (e: IllegalArgumentException) {
                return@withContext Result.failure(
                    Exception(
                        e.message
                            ?: getApplication<Application>().getString(R.string.error_unable_read_image)
                    )
                )
            }
            val fileName = "product.jpg"
            repository.uploadProductImage(bytes, fileName, "image/jpeg")
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    e.message ?: getApplication<Application>().getString(R.string.image_upload_failed)
                )
            )
        }
    }

    private fun validateLocal(
        name: String,
        costPriceText: String,
        sellingPriceText: String,
        stockLevelText: String,
        minStockLevelText: String,
        categoryNameText: String,
        supplierIdText: String
    ): String? {
        if (name.isBlank()) return getApplication<Application>().getString(R.string.error_product_name_required)
        if (costPriceText.toDoubleOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_cost_price)
        if (sellingPriceText.toDoubleOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_selling_price)
        if (stockLevelText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_stock_level)
        if (minStockLevelText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_min_stock)
        if (categoryNameText.isBlank()) return getApplication<Application>().getString(R.string.error_category_required)
        if (categoryNameText.trim().length > 50) {
            return getApplication<Application>().getString(R.string.error_category_name_too_long)
        }
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
