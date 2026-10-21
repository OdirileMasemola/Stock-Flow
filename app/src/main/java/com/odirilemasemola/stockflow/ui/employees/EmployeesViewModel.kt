package com.odirilemasemola.stockflow.ui.employees

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.data.remote.EmployeeDto
import com.odirilemasemola.stockflow.data.repository.EmployeeRepository
import kotlinx.coroutines.launch

class EmployeesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmployeeRepository(sessionStore = SessionStore(application.applicationContext))

    private val _state = MutableLiveData<UiState>(UiState.Loading)
    val state: LiveData<UiState> = _state

    /** One-off messages (snackbar/toast). Cleared by [clearMessage]. */
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getEmployees()
            _state.value = result.fold(
                onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
                onFailure = { e ->
                    val kind = (e as? EmployeeRepository.EmployeeError)?.kind
                    UiState.Error(
                        e.message ?: string(R.string.error_unable_load_employees),
                        notOwner = kind == EmployeeRepository.EmployeeError.Kind.NOT_OWNER
                    )
                }
            )
        }
    }

    fun changeRole(employee: EmployeeDto, role: String) {
        if (role.equals(employee.role, ignoreCase = true) || !EmployeeFormValidator.isAssignableRole(role)) return
        runAction {
            repository.updateRole(employee.id, role).map {
                string(R.string.employee_role_updated, it.fullName.ifBlank { it.username })
            }
        }
    }

    fun remove(employee: EmployeeDto) {
        runAction {
            repository.removeEmployee(employee.id).map {
                string(R.string.employee_removed, employee.fullName.ifBlank { employee.username })
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun runAction(block: suspend () -> Result<String>) {
        if (_busy.value == true) return
        _busy.value = true
        viewModelScope.launch {
            val result = block()
            _busy.value = false
            _message.value = result.getOrElse { it.message ?: string(R.string.error_unable_update_employee) }
            if (result.isSuccess) load()
        }
    }

    private fun string(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    sealed class UiState {
        object Loading : UiState()
        object Empty : UiState()
        data class Success(val employees: List<EmployeeDto>) : UiState()
        data class Error(val message: String, val notOwner: Boolean = false) : UiState()
    }
}
