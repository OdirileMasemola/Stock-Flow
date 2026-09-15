package com.example.stockflow.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.repository.AuthRepository
import com.example.stockflow.data.repository.GoogleAuthOutcome
import kotlinx.coroutines.launch
import com.example.stockflow.R

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(username: String, password: String) {
        if (_loginState.value is LoginState.Loading) {
            return
        }

        if (username.isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState.Error(getApplication<Application>().getString(R.string.error_fill_all_fields))
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = repository.login(username, password)
            if (result.isSuccess) {
                _loginState.postValue(LoginState.Success)
            } else {
                _loginState.postValue(LoginState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_login_failed)))
            }
        }
    }

    fun loginWithGoogle(idToken: String) {
        if (_loginState.value is LoginState.Loading) {
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val result = repository.authenticateWithGoogle(idToken)
            if (result.isSuccess) {
                when (result.getOrNull()) {
                    GoogleAuthOutcome.Authenticated -> _loginState.postValue(LoginState.Success)
                    GoogleAuthOutcome.AccountNotFound -> _loginState.postValue(
                        LoginState.Error(getApplication<Application>().getString(R.string.error_google_account_not_found))
                    )
                    null -> _loginState.postValue(LoginState.Error(getApplication<Application>().getString(R.string.error_login_failed)))
                }
            } else {
                _loginState.postValue(
                    LoginState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_login_failed))
                )
            }
        }
    }

    sealed class LoginState {
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
