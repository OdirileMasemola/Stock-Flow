package com.example.stockflow.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.ui.common.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Inventory screen: Room Flow is the observable source of truth.
 * Network refresh writes into Room; observers update without manual refresh.
 */
class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        sessionStore = SessionStore(application.applicationContext),
        appContext = application.applicationContext
    )

    private val queryFlow = MutableStateFlow("")
    private val freshnessFlow = MutableStateFlow(Freshness(fromCache = false, cachedAt = null))
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: LiveData<ProductsUiState> = combine(
        repository.observeProducts(),
        queryFlow,
        freshnessFlow,
        errorFlow
    ) { products, query, freshness, error ->
        if (error != null && products.isEmpty()) {
            ProductsUiState.Error(error)
        } else {
            val filtered = if (query.isBlank()) {
                products
            } else {
                val q = query.trim().lowercase()
                products.filter { product ->
                    product.name.lowercase().contains(q) ||
                        product.sku.orEmpty().lowercase().contains(q) ||
                        product.categoryName.orEmpty().lowercase().contains(q)
                }
            }
            when {
                products.isEmpty() && error == null && !freshness.loadedOnce ->
                    ProductsUiState.Loading
                products.isEmpty() ->
                    ProductsUiState.Empty(freshness.fromCache, freshness.cachedAt)
                filtered.isEmpty() ->
                    ProductsUiState.EmptySearch(query, freshness.fromCache, freshness.cachedAt)
                else ->
                    ProductsUiState.Success(filtered, freshness.fromCache, freshness.cachedAt)
            }
        }
    }.asLiveData(viewModelScope.coroutineContext)

    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    private val _lookupMessage = MutableLiveData<String?>()
    val lookupMessage: LiveData<String?> = _lookupMessage

    private val _lookupProduct = MutableLiveData<ProductDto?>()
    val lookupProduct: LiveData<ProductDto?> = _lookupProduct

    private val _lookupLoading = MutableLiveData(false)
    val lookupLoading: LiveData<Boolean> = _lookupLoading

    private var loadInFlight = false

    init {
        loadProducts(force = true)
    }

    fun loadProducts(force: Boolean = true) {
        if (loadInFlight) return
        if (!force && freshnessFlow.value.loadedOnce) return
        loadInFlight = true
        viewModelScope.launch {
            try {
                when (val result = repository.getProducts()) {
                    is CacheResult.Fresh -> {
                        errorFlow.value = null
                        freshnessFlow.value = Freshness(false, null, loadedOnce = true)
                    }
                    is CacheResult.Cached -> {
                        errorFlow.value = null
                        freshnessFlow.value = Freshness(true, result.cachedAt, loadedOnce = true)
                    }
                    CacheResult.Empty -> {
                        freshnessFlow.value = Freshness(false, null, loadedOnce = true)
                        errorFlow.value = AppStrings.get(R.string.offline_no_cached_data)
                    }
                    is CacheResult.Error -> {
                        freshnessFlow.value = freshnessFlow.value.copy(loadedOnce = true)
                        // Keep showing Room data if present; only surface error when empty.
                        errorFlow.value = result.message
                    }
                }
            } finally {
                loadInFlight = false
            }
        }
    }

    fun search(query: String) {
        queryFlow.value = query
    }

    fun deleteProduct(product: ProductDto) {
        viewModelScope.launch {
            when (val result = repository.deleteProduct(product.id)) {
                is com.example.stockflow.data.sync.WriteResult.Synced,
                is com.example.stockflow.data.sync.WriteResult.Queued -> {
                    val msg = if (result.savedOffline) {
                        getApplication<Application>().getString(R.string.saved_offline)
                    } else {
                        getApplication<Application>().getString(R.string.item_deleted, product.name)
                    }
                    _deleteMessage.postValue(msg)
                }
                is com.example.stockflow.data.sync.WriteResult.Failed -> {
                    _deleteMessage.postValue(
                        result.message.ifBlank {
                            getApplication<Application>().getString(R.string.error_failed_delete_product)
                        }
                    )
                }
            }
        }
    }

    fun clearDeleteMessage() { _deleteMessage.value = null }
    fun clearLookupMessage() { _lookupMessage.value = null }
    fun clearLookupProduct() { _lookupProduct.value = null }

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
                    result.exceptionOrNull()?.message
                        ?: getApplication<Application>().getString(R.string.product_not_found)
                )
            }
        }
    }

    private data class Freshness(
        val fromCache: Boolean,
        val cachedAt: Long?,
        val loadedOnce: Boolean = false
    )

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
