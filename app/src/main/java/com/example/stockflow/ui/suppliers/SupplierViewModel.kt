package com.example.stockflow.ui.suppliers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.data.repository.SupplierRepository
import kotlinx.coroutines.launch

/**
 * Loads suppliers for the Suppliers screen and supports local search + delete.
 */
class SupplierViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SupplierRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private var allSuppliers: List<SupplierDto> = emptyList()

    private val _uiState = MutableLiveData<SuppliersUiState>(SuppliersUiState.Loading)
    val uiState: LiveData<SuppliersUiState> = _uiState

    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    fun loadSuppliers() {
        _uiState.value = SuppliersUiState.Loading
        viewModelScope.launch {
            val result = repository.getSuppliers()
            if (result.isSuccess) {
                allSuppliers = result.getOrDefault(emptyList())
                publishFiltered("")
            } else {
                _uiState.postValue(
                    SuppliersUiState.Error(
                        result.exceptionOrNull()?.message ?: "Unable to load suppliers"
                    )
                )
            }
        }
    }

    fun search(query: String) {
        if (_uiState.value is SuppliersUiState.Error) {
            return
        }
        publishFiltered(query)
    }

    fun deleteSupplier(supplier: SupplierDto) {
        viewModelScope.launch {
            val result = repository.deleteSupplier(supplier.id)
            if (result.isSuccess) {
                allSuppliers = allSuppliers.filterNot { it.id == supplier.id }
                _deleteMessage.postValue("\"${supplier.name}\" deleted")
                publishFiltered(lastQuery)
            } else {
                _deleteMessage.postValue(
                    result.exceptionOrNull()?.message ?: "Failed to delete supplier"
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
            allSuppliers
        } else {
            val q = query.trim().lowercase()
            allSuppliers.filter { supplier ->
                supplier.name.lowercase().contains(q) ||
                    supplier.contactName.orEmpty().lowercase().contains(q) ||
                    supplier.phone.orEmpty().lowercase().contains(q) ||
                    supplier.email.orEmpty().lowercase().contains(q)
            }
        }

        _uiState.postValue(
            when {
                allSuppliers.isEmpty() -> SuppliersUiState.Empty
                filtered.isEmpty() -> SuppliersUiState.EmptySearch(query)
                else -> SuppliersUiState.Success(filtered)
            }
        )
    }

    sealed class SuppliersUiState {
        object Loading : SuppliersUiState()
        object Empty : SuppliersUiState()
        data class EmptySearch(val query: String) : SuppliersUiState()
        data class Success(val suppliers: List<SupplierDto>) : SuppliersUiState()
        data class Error(val message: String) : SuppliersUiState()
    }
}
