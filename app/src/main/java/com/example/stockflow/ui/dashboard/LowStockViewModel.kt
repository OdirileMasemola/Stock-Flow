package com.example.stockflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.ProductRepository
import kotlinx.coroutines.launch

class LowStockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(sessionStore = SessionStore(application))

    private val _uiState = MutableLiveData<LowStockUiState>(LowStockUiState.Loading)
    val uiState: LiveData<LowStockUiState> = _uiState

    fun loadLowStock() {
        _uiState.value = LowStockUiState.Loading
        viewModelScope.launch {
            val result = repository.getLowStockProducts()
            _uiState.value = if (result.isSuccess) {
                val products = result.getOrDefault(emptyList())
                if (products.isEmpty()) {
                    LowStockUiState.Empty
                } else {
                    LowStockUiState.Success(products)
                }
            } else {
                LowStockUiState.Error(
                    result.exceptionOrNull()?.message ?: "Unable to load low-stock products"
                )
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
