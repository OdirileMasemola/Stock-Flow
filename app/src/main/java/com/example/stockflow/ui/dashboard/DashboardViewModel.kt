package com.example.stockflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.repository.DashboardRepository
import com.example.stockflow.ui.common.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DashboardRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val freshnessFlow = MutableStateFlow(Freshness())
    private val loadingFlow = MutableStateFlow(false)

    val uiState: LiveData<DashboardUiState> = combine(
        repository.observeSummary(),
        freshnessFlow,
        loadingFlow
    ) { summary, freshness, loading ->
        when {
            loading && summary == null && !freshness.loadedOnce -> DashboardUiState.Loading
            summary != null -> DashboardUiState.Success(
                summary = summary,
                fromCache = freshness.fromCache,
                cachedAt = freshness.cachedAt
            )
            freshness.loadedOnce && freshness.empty -> DashboardUiState.Empty
            freshness.loadedOnce && freshness.errorMessage != null ->
                DashboardUiState.Error(freshness.errorMessage)
            loading -> DashboardUiState.Loading
            else -> DashboardUiState.Loading
        }
    }.asLiveData(viewModelScope.coroutineContext)

    init {
        loadDashboard(force = true)
    }

    fun loadDashboard(force: Boolean = true) {
        if (!force && freshnessFlow.value.loadedOnce && !loadingFlow.value) return
        loadingFlow.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.getSummary()) {
                    is CacheResult.Fresh -> {
                        freshnessFlow.value = Freshness(
                            fromCache = false,
                            cachedAt = null,
                            loadedOnce = true
                        )
                    }
                    is CacheResult.Cached -> {
                        freshnessFlow.value = Freshness(
                            fromCache = true,
                            cachedAt = result.cachedAt,
                            loadedOnce = true
                        )
                    }
                    CacheResult.Empty -> {
                        freshnessFlow.value = Freshness(
                            loadedOnce = true,
                            empty = true
                        )
                    }
                    is CacheResult.Error -> {
                        // Prefer showing cached snapshot if Room already has one (via observe).
                        freshnessFlow.value = freshnessFlow.value.copy(
                            loadedOnce = true,
                            errorMessage = result.message
                        )
                    }
                }
            } finally {
                loadingFlow.value = false
            }
        }
    }

    private data class Freshness(
        val fromCache: Boolean = false,
        val cachedAt: Long? = null,
        val loadedOnce: Boolean = false,
        val empty: Boolean = false,
        val errorMessage: String? = null
    )

    sealed class DashboardUiState {
        object Loading : DashboardUiState()
        object Empty : DashboardUiState()
        data class Success(
            val summary: DashboardSummaryDto,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : DashboardUiState()
        data class Error(val message: String) : DashboardUiState()
    }
}
