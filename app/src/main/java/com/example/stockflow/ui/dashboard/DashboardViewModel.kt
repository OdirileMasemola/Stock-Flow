package com.example.stockflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.repository.DashboardRepository
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DashboardRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _uiState = MutableLiveData<DashboardUiState>(DashboardUiState.Loading)
    val uiState: LiveData<DashboardUiState> = _uiState

    fun loadDashboard() {
        _uiState.value = DashboardUiState.Loading
        viewModelScope.launch {
            val result = repository.getSummary()
            if (result.isSuccess) {
                _uiState.postValue(DashboardUiState.Success(result.getOrNull()!!))
            } else {
                _uiState.postValue(
                    DashboardUiState.Error(
                        result.exceptionOrNull()?.message ?: "Unable to load dashboard"
                    )
                )
            }
        }
    }

    sealed class DashboardUiState {
        object Loading : DashboardUiState()
        data class Success(val summary: DashboardSummaryDto) : DashboardUiState()
        data class Error(val message: String) : DashboardUiState()
    }
}
