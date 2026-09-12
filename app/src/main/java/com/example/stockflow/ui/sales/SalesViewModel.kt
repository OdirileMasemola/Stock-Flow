package com.example.stockflow.ui.sales

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.SaleDto
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.data.repository.SaleRepository
import kotlinx.coroutines.launch

/**
 * POS catalog + sales history. Cart lives in [CartSession].
 */
class SalesViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application.applicationContext)
    private val productRepository = ProductRepository(sessionStore = sessionStore)
    private val saleRepository = SaleRepository(sessionStore = sessionStore)

    private var allProducts: List<ProductDto> = emptyList()
    private var lastQuery: String = ""
    private var productsLoadInFlight = false

    private val _productsState = MutableLiveData<ProductsUiState>(ProductsUiState.Loading)
    val productsState: LiveData<ProductsUiState> = _productsState

    val cartState: LiveData<CartSession.CartUiState> = CartSession.state

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _historyState = MutableLiveData<HistoryUiState>(HistoryUiState.Loading)
    val historyState: LiveData<HistoryUiState> = _historyState

    fun loadProducts(force: Boolean = true) {
        if (productsLoadInFlight) return
        if (!force) {
            val current = _productsState.value
            if (current is ProductsUiState.Success || current is ProductsUiState.Empty) {
                return
            }
        }
        productsLoadInFlight = true
        _productsState.value = ProductsUiState.Loading
        viewModelScope.launch {
            try {
                val result = productRepository.getProducts()
                if (result.isSuccess) {
                    allProducts = result.getOrDefault(emptyList())
                    CartSession.syncWithProducts(allProducts)
                    publishFiltered(lastQuery)
                } else {
                    _productsState.postValue(
                        ProductsUiState.Error(result.exceptionOrNull()?.message ?: "Unable to load products")
                    )
                }
            } finally {
                productsLoadInFlight = false
            }
        }
    }

    fun search(query: String) {
        if (_productsState.value is ProductsUiState.Error) return
        publishFiltered(query)
    }

    fun addToCart(product: ProductDto) {
        val error = CartSession.addProduct(product)
        if (error != null) {
            _message.value = error
        } else {
            _message.value = "Added \"${product.name}\" to cart"
        }
    }

    fun addToCartBySku(sku: String) {
        val normalized = sku.trim()
        if (normalized.isEmpty() || normalized.length > 50) {
            _message.value = "Product not found"
            return
        }
        viewModelScope.launch {
            val result = productRepository.getProductBySku(normalized)
            if (result.isSuccess) {
                val product = result.getOrNull()
                if (product == null) {
                    _message.postValue("Product not found")
                } else {
                    val error = CartSession.addProduct(product)
                    if (error != null) {
                        _message.postValue(error)
                    } else {
                        _message.postValue("Added \"${product.name}\" to cart")
                    }
                }
            } else {
                _message.postValue(
                    result.exceptionOrNull()?.message ?: "Product not found"
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
                    HistoryUiState.Error(result.exceptionOrNull()?.message ?: "Unable to load sales")
                )
            }
        }
    }

    fun loadSaleDetails(saleId: Int, onResult: (Result<SaleDto>) -> Unit) {
        viewModelScope.launch {
            onResult(saleRepository.getSale(saleId))
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

        _productsState.postValue(
            when {
                allProducts.isEmpty() -> ProductsUiState.Empty
                filtered.isEmpty() -> ProductsUiState.EmptySearch(query)
                else -> ProductsUiState.Success(filtered)
            }
        )
    }

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
