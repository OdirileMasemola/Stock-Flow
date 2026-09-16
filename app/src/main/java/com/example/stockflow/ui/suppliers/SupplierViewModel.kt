package com.example.stockflow.ui.suppliers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.data.repository.SupplierRepository
import com.example.stockflow.ui.common.AppStrings
import kotlinx.coroutines.launch

/**
 * Loads suppliers for the Suppliers screen and supports local search + delete.
 */
class SupplierViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SupplierRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private var allSuppliers: List<SupplierDto> = emptyList()
    private var lastQuery: String = ""
    private var loadInFlight = false
    private var fromCache: Boolean = false
    private var cachedAt: Long? = null

    private val _uiState = MutableLiveData<SuppliersUiState>(SuppliersUiState.Loading)
    val uiState: LiveData<SuppliersUiState> = _uiState

    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    fun loadSuppliers(force: Boolean = true) {
        if (loadInFlight) return
        if (!force) {
            val current = _uiState.value
            if (current is SuppliersUiState.Success || current is SuppliersUiState.Empty) {
                return
            }
        }
        loadInFlight = true
        _uiState.value = SuppliersUiState.Loading
        viewModelScope.launch {
            try {
                when (val result = repository.getSuppliers()) {
                    is CacheResult.Fresh -> {
                        fromCache = false
                        cachedAt = null
                        allSuppliers = result.data
                        publishFiltered(lastQuery)
                    }
                    is CacheResult.Cached -> {
                        fromCache = true
                        cachedAt = result.cachedAt
                        allSuppliers = result.data
                        publishFiltered(lastQuery)
                    }
                    CacheResult.Empty -> {
                        fromCache = false
                        cachedAt = null
                        _uiState.postValue(
                            SuppliersUiState.Error(AppStrings.get(R.string.offline_no_cached_data))
                        )
                    }
                    is CacheResult.Error -> {
                        fromCache = false
                        cachedAt = null
                        _uiState.postValue(SuppliersUiState.Error(result.message))
                    }
                }
            } finally {
                loadInFlight = false
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
                _deleteMessage.postValue(getApplication<Application>().getString(R.string.item_deleted, supplier.name))
                publishFiltered(lastQuery)
            } else {
                _deleteMessage.postValue(
                    result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_failed_delete_supplier)
                )
            }
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

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
                allSuppliers.isEmpty() -> SuppliersUiState.Empty(fromCache, cachedAt)
                filtered.isEmpty() -> SuppliersUiState.EmptySearch(query, fromCache, cachedAt)
                else -> SuppliersUiState.Success(filtered, fromCache, cachedAt)
            }
        )
    }

    sealed class SuppliersUiState {
        object Loading : SuppliersUiState()
        data class Empty(val fromCache: Boolean = false, val cachedAt: Long? = null) : SuppliersUiState()
        data class EmptySearch(
            val query: String,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : SuppliersUiState()
        data class Success(
            val suppliers: List<SupplierDto>,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : SuppliersUiState()
        data class Error(val message: String) : SuppliersUiState()
    }
}
