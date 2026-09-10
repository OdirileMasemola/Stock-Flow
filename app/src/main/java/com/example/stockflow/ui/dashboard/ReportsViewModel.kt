package com.example.stockflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ReportsDto
import com.example.stockflow.data.repository.DashboardRepository
import kotlinx.coroutines.launch

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DashboardRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _uiState = MutableLiveData<ReportsUiState>(ReportsUiState.Loading)
    val uiState: LiveData<ReportsUiState> = _uiState

    private var currentRange: String = "7d"

    fun loadReports(range: String = currentRange) {
        currentRange = range
        _uiState.value = ReportsUiState.Loading
        viewModelScope.launch {
            val result = repository.getReports(range)
            if (result.isSuccess) {
                _uiState.postValue(ReportsUiState.Success(result.getOrNull()!!))
            } else {
                _uiState.postValue(
                    ReportsUiState.Error(
                        result.exceptionOrNull()?.message ?: "Unable to load reports"
                    )
                )
            }
        }
    }

    fun currentRange(): String = currentRange

    sealed class ReportsUiState {
        object Loading : ReportsUiState()
        data class Success(val reports: ReportsDto) : ReportsUiState()
        data class Error(val message: String) : ReportsUiState()
    }
}
