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

class AddEmployeeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmployeeRepository(sessionStore = SessionStore(application.applicationContext))

    private val _formState = MutableLiveData<FormState>(FormState.Idle)
    val formState: LiveData<FormState> = _formState

    fun save(email: String, role: String) {
        if (_formState.value is FormState.Loading) return
        val app = getApplication<Application>()
        EmployeeFormValidator.emailError(email)?.let {
            _formState.value = FormState.EmailError(app.getString(it))
            return
        }
        if (!EmployeeFormValidator.isAssignableRole(role)) {
            _formState.value = FormState.Error(app.getString(R.string.error_employee_role_required))
            return
        }
        _formState.value = FormState.Loading
        viewModelScope.launch {
            _formState.value = repository.addEmployee(email, role).fold(
                onSuccess = { FormState.Success(it) },
                onFailure = { FormState.Error(it.message ?: app.getString(R.string.error_unable_add_employee)) }
            )
        }
    }

    sealed class FormState {
        object Idle : FormState()
        object Loading : FormState()
        data class EmailError(val message: String) : FormState()
        data class Error(val message: String) : FormState()
        data class Success(val employee: EmployeeDto) : FormState()
    }
}
