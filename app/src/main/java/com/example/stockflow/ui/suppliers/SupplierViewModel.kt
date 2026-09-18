package com.example.stockflow.ui.suppliers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.data.repository.SupplierRepository
import com.example.stockflow.ui.common.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Suppliers screen: Room Flow is the observable source of truth.
 * Network refresh writes into Room; UI updates without blanking on refresh.
 */
class SupplierViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SupplierRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val queryFlow = MutableStateFlow("")
    private val freshnessFlow = MutableStateFlow(Freshness())
    private val errorFlow = MutableStateFlow<String?>(null)
    private var loadInFlight = false

    val uiState: LiveData<SuppliersUiState> = combine(
        repository.observeSuppliers(),
        queryFlow,
        freshnessFlow,
        errorFlow
    ) { suppliers, query, freshness, error ->
        if (error != null && suppliers.isEmpty()) {
            SuppliersUiState.Error(error)
        } else {
            val filtered = if (query.isBlank()) {
                suppliers
            } else {
                val q = query.trim().lowercase()
                suppliers.filter { supplier ->
                    supplier.name.lowercase().contains(q) ||
                        supplier.contactName.orEmpty().lowercase().contains(q) ||
                        supplier.phone.orEmpty().lowercase().contains(q) ||
                        supplier.email.orEmpty().lowercase().contains(q)
                }
            }
            when {
                suppliers.isEmpty() && error == null && !freshness.loadedOnce ->
                    SuppliersUiState.Loading
                suppliers.isEmpty() ->
                    SuppliersUiState.Empty(freshness.fromCache, freshness.cachedAt)
                filtered.isEmpty() ->
                    SuppliersUiState.EmptySearch(query, freshness.fromCache, freshness.cachedAt)
                else ->
                    SuppliersUiState.Success(filtered, freshness.fromCache, freshness.cachedAt)
            }
        }
    }.asLiveData(viewModelScope.coroutineContext)

    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    init {
        loadSuppliers(force = true)
    }

    fun loadSuppliers(force: Boolean = true) {
        if (loadInFlight) return
        if (!force && freshnessFlow.value.loadedOnce) return
        loadInFlight = true
        viewModelScope.launch {
            try {
                when (val result = repository.getSuppliers()) {
                    is CacheResult.Fresh -> {
                        errorFlow.value = null
                        freshnessFlow.value = Freshness(false, null, loadedOnce = true)
                    }
                    is CacheResult.Cached -> {
                        errorFlow.value = null
                        freshnessFlow.value = Freshness(true, result.cachedAt, loadedOnce = true)
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
                loadInFlight = false
            }
        }
    }

    fun search(query: String) {
        queryFlow.value = query
    }

    fun deleteSupplier(supplier: SupplierDto) {
        viewModelScope.launch {
            val result = repository.deleteSupplier(supplier.id)
            if (result.isSuccess) {
                _deleteMessage.postValue(
                    getApplication<Application>().getString(R.string.item_deleted, supplier.name)
                )
            } else {
                _deleteMessage.postValue(
                    result.exceptionOrNull()?.message
                        ?: getApplication<Application>().getString(R.string.error_failed_delete_supplier)
                )
            }
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

    private data class Freshness(
        val fromCache: Boolean = false,
        val cachedAt: Long? = null,
        val loadedOnce: Boolean = false
    )

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
