package com.example.stockflow.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(username: String, password: String) {
        if (username.isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState.Error("Please fill in all fields")
            return
        }

        _loginState.value = LoginState.Loading
        
        viewModelScope.launch {
            val result = repository.login(username, password)
            if (result.isSuccess) {
                _loginState.postValue(LoginState.Success)
            } else {
                _loginState.postValue(LoginState.Error(result.exceptionOrNull()?.message ?: "Login failed"))
            }
        }
    }

    sealed class LoginState {
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
