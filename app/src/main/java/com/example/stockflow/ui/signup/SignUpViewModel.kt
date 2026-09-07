package com.example.stockflow.ui.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.repository.AuthRepository
import kotlinx.coroutines.launch

class SignUpViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _signUpState = MutableLiveData<SignUpState>()
    val signUpState: LiveData<SignUpState> = _signUpState

    fun signUp(name: String, phone: String, email: String, password: String, confirmPass: String) {
        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            _signUpState.value = SignUpState.Error("Please fill in all fields")
            return
        }

        if (password != confirmPass) {
            _signUpState.value = SignUpState.Error("Passwords do not match")
            return
        }

        if (password.length < 6) {
            _signUpState.value = SignUpState.Error("Password must be at least 6 characters")
            return
        }

        _signUpState.value = SignUpState.Loading
        
        viewModelScope.launch {
            val result = repository.signUp(name, phone, email, password)
            if (result.isSuccess) {
                _signUpState.postValue(SignUpState.Success)
            } else {
                _signUpState.postValue(SignUpState.Error(result.exceptionOrNull()?.message ?: "Signup failed"))
            }
        }
    }

    sealed class SignUpState {
        object Loading : SignUpState()
        object Success : SignUpState()
        data class Error(val message: String) : SignUpState()
    }
}
