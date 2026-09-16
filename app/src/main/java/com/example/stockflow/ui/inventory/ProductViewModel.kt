package com.example.stockflow.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.ui.common.AppStrings
import kotlinx.coroutines.launch

/**
 * Loads products for the Inventory screen and supports local search + delete.
 */
class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private var allProducts: List<ProductDto> = emptyList()
    private var fromCache: Boolean = false
    private var cachedAt: Long? = null

    private val _uiState = MutableLiveData<ProductsUiState>(ProductsUiState.Loading)
    val uiState: LiveData<ProductsUiState> = _uiState

    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    private val _lookupMessage = MutableLiveData<String?>()
    val lookupMessage: LiveData<String?> = _lookupMessage

    private val _lookupProduct = MutableLiveData<ProductDto?>()
    val lookupProduct: LiveData<ProductDto?> = _lookupProduct

    private val _lookupLoading = MutableLiveData(false)
    val lookupLoading: LiveData<Boolean> = _lookupLoading

    private var lastQuery: String = ""
    private var loadInFlight = false

    fun loadProducts(force: Boolean = true) {
        if (loadInFlight) return
        if (!force) {
            val current = _uiState.value
            if (current is ProductsUiState.Success || current is ProductsUiState.Empty) {
                return
            }
        }
        loadInFlight = true
        _uiState.value = ProductsUiState.Loading
        viewModelScope.launch {
            try {
                when (val result = repository.getProducts()) {
                    is CacheResult.Fresh -> {
                        fromCache = false
                        cachedAt = null
                        allProducts = result.data
                        publishFiltered(lastQuery)
                    }
                    is CacheResult.Cached -> {
                        fromCache = true
                        cachedAt = result.cachedAt
                        allProducts = result.data
                        publishFiltered(lastQuery)
                    }
                    CacheResult.Empty -> {
                        fromCache = false
                        cachedAt = null
                        _uiState.postValue(
                            ProductsUiState.Error(AppStrings.get(R.string.offline_no_cached_data))
                        )
                    }
                    is CacheResult.Error -> {
                        fromCache = false
                        cachedAt = null
                        _uiState.postValue(ProductsUiState.Error(result.message))
                    }
                }
            } finally {
                loadInFlight = false
            }
        }
    }

    /** Filters the already-loaded list by name or SKU (no extra API call). */
    fun search(query: String) {
        if (_uiState.value is ProductsUiState.Error) {
            return
        }
        publishFiltered(query)
    }

    fun deleteProduct(product: ProductDto) {
        viewModelScope.launch {
            val result = repository.deleteProduct(product.id)
            if (result.isSuccess) {
                allProducts = allProducts.filterNot { it.id == product.id }
                _deleteMessage.postValue(getApplication<Application>().getString(R.string.item_deleted, product.name))
                publishFiltered(lastQuery)
            } else {
                _deleteMessage.postValue(
                    result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_failed_delete_product)
                )
            }
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

    fun clearLookupMessage() {
        _lookupMessage.value = null
    }

    fun clearLookupProduct() {
        _lookupProduct.value = null
    }

    fun findBySku(sku: String) {
        val normalized = sku.trim()
        if (normalized.isEmpty() || normalized.length > 50) {
            _lookupMessage.value = getApplication<Application>().getString(R.string.product_not_found)
            return
        }
        _lookupLoading.value = true
        viewModelScope.launch {
            val result = repository.getProductBySku(normalized)
            _lookupLoading.postValue(false)
            if (result.isSuccess) {
                _lookupProduct.postValue(result.getOrNull())
            } else {
                _lookupMessage.postValue(
                    result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.product_not_found)
                )
            }
        }
    }

    private fun publishFiltered(query: String) {
        lastQuery = query
        val filtered = if (query.isBlank()) {
            allProducts
        } else {
            val q = query.trim().lowercase()
            allProducts.filter { product ->
                product.name.lowercase().contains(q) ||
                    product.sku.orEmpty().lowercase().contains(q) ||
                    product.categoryName.orEmpty().lowercase().contains(q)
            }
        }

        _uiState.postValue(
            when {
                allProducts.isEmpty() -> ProductsUiState.Empty(fromCache, cachedAt)
                filtered.isEmpty() -> ProductsUiState.EmptySearch(query, fromCache, cachedAt)
                else -> ProductsUiState.Success(filtered, fromCache, cachedAt)
            }
        )
    }

    sealed class ProductsUiState {
        object Loading : ProductsUiState()
        data class Empty(val fromCache: Boolean = false, val cachedAt: Long? = null) : ProductsUiState()
        data class EmptySearch(
            val query: String,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : ProductsUiState()
        data class Success(
            val products: List<ProductDto>,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : ProductsUiState()
        data class Error(val message: String) : ProductsUiState()
    }
}
