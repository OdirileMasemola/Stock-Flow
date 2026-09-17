package com.example.stockflow.ui.sales

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
import com.example.stockflow.data.remote.SaleDto
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.data.repository.SaleRepository
import com.example.stockflow.ui.common.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * POS catalog + sales history. Cart lives in [CartSession].
 *
 * Product catalog observes the same Room-backed [ProductRepository.observeProducts]
 * Flow that Inventory uses, so create/edit/delete (online or offline) appear on POS
 * immediately without a manual refresh.
 */
class SalesViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application.applicationContext)
    private val productRepository = ProductRepository(
        sessionStore = sessionStore,
        appContext = application.applicationContext
    )
    private val saleRepository = SaleRepository(sessionStore = sessionStore)

    private val queryFlow = MutableStateFlow("")
    private val freshnessFlow = MutableStateFlow(Freshness())
    private val errorFlow = MutableStateFlow<String?>(null)
    private var productsLoadInFlight = false

    private val productsFlow = productRepository.observeProducts().onEach { products ->
        CartSession.syncWithProducts(products)
    }

    val productsState: LiveData<ProductsUiState> = combine(
        productsFlow,
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
                    ProductsUiState.Empty
                filtered.isEmpty() ->
                    ProductsUiState.EmptySearch(query)
                else ->
                    ProductsUiState.Success(filtered)
            }
        }
    }.asLiveData(viewModelScope.coroutineContext)

    val cartState: LiveData<CartSession.CartUiState> = CartSession.state

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _historyState = MutableLiveData<HistoryUiState>(HistoryUiState.Loading)
    val historyState: LiveData<HistoryUiState> = _historyState

    init {
        loadProducts(force = true)
    }

    fun loadProducts(force: Boolean = true) {
        if (productsLoadInFlight) return
        if (!force && freshnessFlow.value.loadedOnce) return
        productsLoadInFlight = true
        viewModelScope.launch {
            try {
                when (val result = productRepository.getProducts()) {
                    is CacheResult.Fresh -> {
                        errorFlow.value = null
                        freshnessFlow.value = Freshness(loadedOnce = true)
                    }
                    is CacheResult.Cached -> {
                        errorFlow.value = null
                        freshnessFlow.value = Freshness(loadedOnce = true, fromCache = true)
                    }
                    CacheResult.Empty -> {
                        freshnessFlow.value = Freshness(loadedOnce = true)
                        errorFlow.value = AppStrings.get(R.string.offline_no_cached_data)
                    }
                    is CacheResult.Error -> {
                        freshnessFlow.value = freshnessFlow.value.copy(loadedOnce = true)
                        errorFlow.value = result.message
                    }
                }
            } finally {
                productsLoadInFlight = false
            }
        }
    }

    fun search(query: String) {
        queryFlow.value = query
    }

    fun addToCart(product: ProductDto) {
        val error = CartSession.addProduct(product)
        if (error != null) {
            _message.value = error
        } else {
            _message.value = getApplication<Application>().getString(R.string.msg_added_to_cart, product.name)
        }
    }

    fun addToCartBySku(sku: String) {
        val normalized = sku.trim()
        if (normalized.isEmpty() || normalized.length > 50) {
            _message.value = getApplication<Application>().getString(R.string.product_not_found)
            return
        }
        viewModelScope.launch {
            val result = productRepository.getProductBySku(normalized)
            if (result.isSuccess) {
                val product = result.getOrNull()
                if (product == null) {
                    _message.postValue(getApplication<Application>().getString(R.string.product_not_found))
                } else {
                    val error = CartSession.addProduct(product)
                    if (error != null) {
                        _message.postValue(error)
                    } else {
                        _message.postValue(getApplication<Application>().getString(R.string.msg_added_to_cart, product.name))
                    }
                }
            } else {
                _message.postValue(
                    result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.product_not_found)
                )
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun loadSalesHistory() {
        _historyState.value = HistoryUiState.Loading
        viewModelScope.launch {
            val result = saleRepository.getSales()
            if (result.isSuccess) {
                val sales = result.getOrDefault(emptyList())
                _historyState.postValue(
                    if (sales.isEmpty()) HistoryUiState.Empty else HistoryUiState.Success(sales)
                )
            } else {
                _historyState.postValue(
                    HistoryUiState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_unable_load_sales))
                )
            }
        }
    }

    fun loadSaleDetails(saleId: Int, onResult: (Result<SaleDto>) -> Unit) {
        viewModelScope.launch {
            onResult(saleRepository.getSale(saleId))
        }
    }

    private data class Freshness(
        val loadedOnce: Boolean = false,
        val fromCache: Boolean = false
    )

    sealed class ProductsUiState {
        object Loading : ProductsUiState()
        object Empty : ProductsUiState()
        data class EmptySearch(val query: String) : ProductsUiState()
        data class Success(val products: List<ProductDto>) : ProductsUiState()
        data class Error(val message: String) : ProductsUiState()
    }

    sealed class HistoryUiState {
        object Loading : HistoryUiState()
        object Empty : HistoryUiState()
        data class Success(val sales: List<SaleDto>) : HistoryUiState()
        data class Error(val message: String) : HistoryUiState()
    }
}
