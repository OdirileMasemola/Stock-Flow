package com.example.stockflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Low-stock list observes Room products (stockLevel <= minStockLevel).
 * Network refresh is best-effort; FCM Stage 4 push path is unchanged.
 */
class LowStockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        sessionStore = SessionStore(application),
        appContext = application.applicationContext
    )

    private val loadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val loadedOnce = MutableStateFlow(false)

    val uiState: LiveData<LowStockUiState> = combine(
        repository.observeLowStockProducts(),
        loadingFlow,
        errorFlow,
        loadedOnce
    ) { products, loading, error, loaded ->
        when {
            loading && !loaded && products.isEmpty() -> LowStockUiState.Loading
            products.isNotEmpty() -> LowStockUiState.Success(products)
            products.isEmpty() && error != null && loaded -> LowStockUiState.Error(error)
            products.isEmpty() && loaded -> LowStockUiState.Empty
            else -> LowStockUiState.Loading
        }
    }.asLiveData(viewModelScope.coroutineContext)

    init {
        loadLowStock()
    }

    fun loadLowStock() {
        loadingFlow.value = true
        viewModelScope.launch {
            try {
                val result = repository.getLowStockProducts()
                loadedOnce.value = true
                errorFlow.value = if (result.isFailure) {
                    result.exceptionOrNull()?.message
                } else {
                    null
                }
            } finally {
                loadingFlow.value = false
            }
        }
    }

    sealed class LowStockUiState {
        object Loading : LowStockUiState()
        object Empty : LowStockUiState()
        data class Success(val products: List<ProductDto>) : LowStockUiState()
        data class Error(val message: String) : LowStockUiState()
    }
}
