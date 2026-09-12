package com.example.stockflow.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ReportsDto
import com.example.stockflow.data.repository.DashboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DashboardRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _uiState = MutableLiveData<ReportsUiState>(ReportsUiState.Loading)
    val uiState: LiveData<ReportsUiState> = _uiState

    private val _exportState = MutableLiveData<ExportState>(ExportState.Idle)
    val exportState: LiveData<ExportState> = _exportState

    private var currentRange: String = "7d"
    private var customFrom: String? = null
    private var customTo: String? = null
    private var latestReports: ReportsDto? = null
    private var latestRangeLabel: String = "7d"

    fun loadReports(range: String = currentRange) {
        currentRange = range
        customFrom = null
        customTo = null
        _uiState.value = ReportsUiState.Loading
        viewModelScope.launch {
            val result = repository.getReports(range = range)
            publishReportsResult(result, range)
        }
    }

    fun loadCustomReports(fromIso: String, toIso: String, label: String) {
        currentRange = "custom"
        customFrom = fromIso
        customTo = toIso
        latestRangeLabel = label
        _uiState.value = ReportsUiState.Loading
        viewModelScope.launch {
            val result = repository.getReports(from = fromIso, to = toIso)
            publishReportsResult(result, label)
        }
    }

    fun exportPdf(fromIso: String, toIso: String, label: String) {
        if (_exportState.value is ExportState.Loading) return
        _exportState.value = ExportState.Loading
        viewModelScope.launch {
            val result = repository.getReports(from = fromIso, to = toIso)
            if (result.isFailure) {
                _exportState.postValue(
                    ExportState.Error(
                        result.exceptionOrNull()?.message ?: "Unable to export report"
                    )
                )
                return@launch
            }
            val reports = result.getOrNull()
            if (reports == null) {
                _exportState.postValue(ExportState.Error("Unable to export report"))
                return@launch
            }
            try {
                val file = withContext(Dispatchers.IO) {
                    ReportsPdfExporter.export(
                        context = getApplication(),
                        reports = reports,
                        rangeLabel = label
                    )
                }
                latestReports = reports
                latestRangeLabel = label
                _exportState.postValue(ExportState.Success(file, label))
            } catch (e: Exception) {
                _exportState.postValue(
                    ExportState.Error(e.message ?: "Unable to create PDF")
                )
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    fun currentRange(): String = currentRange

    fun customFrom(): String? = customFrom

    fun customTo(): String? = customTo

    private fun publishReportsResult(result: Result<ReportsDto>, label: String) {
        if (result.isSuccess) {
            val reports = result.getOrNull()
            if (reports != null) {
                latestReports = reports
                latestRangeLabel = label
                _uiState.postValue(ReportsUiState.Success(reports, label))
            } else {
                _uiState.postValue(ReportsUiState.Error("Unable to load reports"))
            }
        } else {
            _uiState.postValue(
                ReportsUiState.Error(
                    result.exceptionOrNull()?.message ?: "Unable to load reports"
                )
            )
        }
    }

    sealed class ReportsUiState {
        object Loading : ReportsUiState()
        data class Success(val reports: ReportsDto, val rangeLabel: String) : ReportsUiState()
        data class Error(val message: String) : ReportsUiState()
    }

    sealed class ExportState {
        object Idle : ExportState()
        object Loading : ExportState()
        data class Success(val file: File, val rangeLabel: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }
}
