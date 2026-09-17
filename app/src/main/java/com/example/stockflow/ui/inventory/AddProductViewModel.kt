package com.example.stockflow.ui.inventory

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.ProductSkuCodes
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.repository.CategoryRepository
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.data.sync.WriteResult
import com.example.stockflow.ui.common.AppStrings
import com.example.stockflow.ui.common.ProductImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Create/edit product with category dropdown + inline "Add new category".
 * Offline: new categories are cached + queued (CATEGORY before PRODUCT).
 */
class AddProductViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application.applicationContext)
    private val repository = ProductRepository(
        sessionStore = sessionStore,
        appContext = application.applicationContext
    )
    private val categoryRepository = CategoryRepository(
        sessionStore = sessionStore,
        appContext = application.applicationContext
    )

    private val _formState = MutableLiveData<FormState>()
    val formState: LiveData<FormState> = _formState

    private val _loadedProduct = MutableLiveData<ProductDto?>()
    val loadedProduct: LiveData<ProductDto?> = _loadedProduct

    /** Room-backed category list for the selector. */
    val categories: LiveData<List<CategoryDto>> =
        categoryRepository.observeCategories().asLiveData(viewModelScope.coroutineContext)

    private val _addingNewCategory = MutableLiveData(false)
    val addingNewCategory: LiveData<Boolean> = _addingNewCategory

    private var existingImageUrl: String? = null
    private var pendingImageUri: Uri? = null
    private var imageRemoved: Boolean = false

    init {
        refreshCategories()
    }

    fun refreshCategories() {
        viewModelScope.launch {
            categoryRepository.getCategories()
        }
    }

    fun setAddingNewCategory(enabled: Boolean) {
        _addingNewCategory.value = enabled
    }

    fun loadProduct(id: Int) {
        _formState.value = FormState.Loading
        viewModelScope.launch {
            when (val result = repository.getProduct(id)) {
                is CacheResult.Fresh, is CacheResult.Cached -> {
                    val product = result.getOrNull()
                    existingImageUrl = product?.imageUrl
                    pendingImageUri = null
                    imageRemoved = false
                    _loadedProduct.postValue(product)
                    _formState.postValue(FormState.Idle)
                }
                CacheResult.Empty -> {
                    _formState.postValue(FormState.Error(AppStrings.get(R.string.offline_no_cached_data)))
                }
                is CacheResult.Error -> {
                    _formState.postValue(FormState.Error(result.message))
                }
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

    fun saveProduct(
        productId: Int?,
        name: String,
        sku: String,
        costPriceText: String,
        sellingPriceText: String,
        stockLevelText: String,
        minStockLevelText: String,
        selectedCategory: CategoryDto?,
        newCategoryName: String?,
        isAddingNewCategory: Boolean,
        supplierIdText: String
    ) {
        if (_formState.value is FormState.Loading) return

        val categoryNameForSave: String
        val validationError = validateLocal(
            name,
            costPriceText,
            sellingPriceText,
            stockLevelText,
            minStockLevelText,
            selectedCategory,
            newCategoryName,
            isAddingNewCategory,
            supplierIdText
        )
        if (validationError != null) {
            _formState.value = FormState.Error(validationError)
            return
        }

        categoryNameForSave = if (isAddingNewCategory) {
            newCategoryName!!.trim()
        } else {
            selectedCategory!!.name
        }

        val costPrice = costPriceText.toDouble()
        val sellingPrice = sellingPriceText.toDouble()
        val stockLevel = stockLevelText.toInt()
        val minStockLevel = minStockLevelText.toInt()
        val supplierId = supplierIdText.trim().takeIf { it.isNotEmpty() }?.toInt()
        val normalizedSku = ProductSkuCodes.toStockFlowSku(sku) ?: sku.trim().takeIf { it.isNotEmpty() }

        _formState.value = FormState.Loading
        viewModelScope.launch {
            if (pendingImageUri != null) {
                val imageUrlResult = uploadLocalImage(pendingImageUri!!)
                if (imageUrlResult.isFailure) {
                    _formState.postValue(
                        FormState.Error(
                            imageUrlResult.exceptionOrNull()?.message
                                ?: getApplication<Application>().getString(R.string.image_upload_failed)
                        )
                    )
                    return@launch
                }
                existingImageUrl = imageUrlResult.getOrNull()
                pendingImageUri = null
                imageRemoved = false
            }

            val categoryResult = resolveCategory(categoryNameForSave, selectedCategory, isAddingNewCategory)
            if (categoryResult.isFailure) {
                _formState.postValue(
                    FormState.Error(
                        categoryResult.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.error_failed_resolve_category)
                    )
                )
                return@launch
            }
            val category = categoryResult.getOrNull()!!
            val categoryId = category.id

            val imageUrl = when {
                imageRemoved -> null
                else -> existingImageUrl
            }

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
                    ),
                    categoryName = category.name
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
                    ),
                    categoryName = category.name
                )
            }

            when (result) {
                is WriteResult.Synced -> {
                    _formState.postValue(
                        FormState.Success(result.data, isUpdate = productId != null, savedOffline = false)
                    )
                }
                is WriteResult.Queued -> {
                    _formState.postValue(
                        FormState.Success(result.data, isUpdate = productId != null, savedOffline = true)
                    )
                }
                is WriteResult.Failed -> {
                    _formState.postValue(
                        FormState.Error(
                            result.message.ifBlank {
                                getApplication<Application>().getString(R.string.error_failed_save_product)
                            }
                        )
                    )
                }
            }
        }
    }

    private suspend fun resolveCategory(
        categoryName: String,
        selected: CategoryDto?,
        isAddingNew: Boolean
    ): Result<CategoryDto> {
        if (!isAddingNew && selected != null) {
            return Result.success(selected)
        }
        // Match existing cache first (case-insensitive) even when "add new" was chosen.
        val cached = categories.value.orEmpty().firstOrNull {
            it.name.equals(categoryName, ignoreCase = true)
        }
        if (cached != null && !isAddingNew) {
            return Result.success(cached)
        }
        if (isAddingNew && cached != null) {
            return Result.failure(
                Exception(getApplication<Application>().getString(R.string.error_category_duplicate))
            )
        }

        return when (val created = categoryRepository.findOrCreateCategory(categoryName)) {
            is WriteResult.Synced, is WriteResult.Queued -> Result.success(created.getOrNull()!!)
            is WriteResult.Failed -> Result.failure(Exception(created.message))
        }
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
            repository.uploadProductImage(bytes, "product.jpg", "image/jpeg")
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
        selectedCategory: CategoryDto?,
        newCategoryName: String?,
        isAddingNewCategory: Boolean,
        supplierIdText: String
    ): String? {
        if (name.isBlank()) return getApplication<Application>().getString(R.string.error_product_name_required)
        if (costPriceText.toDoubleOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_cost_price)
        if (sellingPriceText.toDoubleOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_selling_price)
        if (stockLevelText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_stock_level)
        if (minStockLevelText.toIntOrNull() == null) return getApplication<Application>().getString(R.string.error_valid_min_stock)

        if (isAddingNewCategory) {
            val trimmed = newCategoryName?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                return getApplication<Application>().getString(R.string.error_category_required)
            }
            if (trimmed.length > 50) {
                return getApplication<Application>().getString(R.string.error_category_name_too_long)
            }
            val duplicate = categories.value.orEmpty().any {
                it.name.equals(trimmed, ignoreCase = true)
            }
            if (duplicate) {
                return getApplication<Application>().getString(R.string.error_category_duplicate)
            }
        } else if (selectedCategory == null) {
            return getApplication<Application>().getString(R.string.error_category_required)
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
        data class Success(
            val product: ProductDto,
            val isUpdate: Boolean,
            val savedOffline: Boolean = false
        ) : FormState()
        data class Error(val message: String) : FormState()
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
        const val ADD_NEW_CATEGORY_SENTINEL = "__ADD_NEW_CATEGORY__"
    }
}
