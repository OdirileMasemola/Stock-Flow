package com.example.stockflow.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.ProductRepository
import kotlinx.coroutines.launch

/**
 * Loads products for the Inventory screen and supports local search + delete.
 */
class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private var allProducts: List<ProductDto> = emptyList()

    private val _uiState = MutableLiveData<ProductsUiState>(ProductsUiState.Loading)
    val uiState: LiveData<ProductsUiState> = _uiState

    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    fun loadProducts() {
        _uiState.value = ProductsUiState.Loading
        viewModelScope.launch {
            val result = repository.getProducts()
            if (result.isSuccess) {
                allProducts = result.getOrDefault(emptyList())
                publishFiltered("")
            } else {
                _uiState.postValue(
                    ProductsUiState.Error(result.exceptionOrNull()?.message ?: "Unable to load products")
                )
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
                _deleteMessage.postValue("\"${product.name}\" deleted")
                publishFiltered(lastQuery)
            } else {
                _deleteMessage.postValue(
                    result.exceptionOrNull()?.message ?: "Failed to delete product"
                )
            }
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

    private var lastQuery: String = ""

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
}
