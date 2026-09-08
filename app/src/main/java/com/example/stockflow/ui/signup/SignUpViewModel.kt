package com.example.stockflow.ui.signup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.RoleDto
import com.example.stockflow.data.repository.AuthRepository
import com.example.stockflow.data.repository.GoogleAuthOutcome
import kotlinx.coroutines.launch

class SignUpViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private var pendingGoogleIdToken: String? = null

    private val _signUpState = MutableLiveData<SignUpState>()
    val signUpState: LiveData<SignUpState> = _signUpState

    private val _googleSignUpState = MutableLiveData<GoogleSignUpState>()
    val googleSignUpState: LiveData<GoogleSignUpState> = _googleSignUpState

    private val _rolesState = MutableLiveData<RolesState>()
    val rolesState: LiveData<RolesState> = _rolesState

    init {
        loadRoles()
    }

    fun loadRoles() {
        _rolesState.value = RolesState.Loading
        viewModelScope.launch {
            val result = repository.getRoles()
            if (result.isSuccess) {
                val roles = result.getOrDefault(emptyList())
                if (roles.isEmpty()) {
                    _rolesState.postValue(RolesState.Error("No roles available. Please try again later."))
                } else {
                    _rolesState.postValue(RolesState.Success(roles))
                }
            } else {
                _rolesState.postValue(
                    RolesState.Error(result.exceptionOrNull()?.message ?: "Unable to load roles")
                )
            }
        }
    }

    fun signUp(
        name: String,
        phone: String,
        email: String,
        password: String,
        confirmPass: String,
        roleId: Int?
    ) {
        if (_signUpState.value is SignUpState.Loading) {
            return
        }

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            _signUpState.value = SignUpState.Error("Please fill in all fields")
            return
        }

        if (roleId == null) {
            _signUpState.value = SignUpState.Error("Please select a role")
            return
        }

        if (password != confirmPass) {
            _signUpState.value = SignUpState.Error("Passwords do not match")
            return
        }

        if (password.length < 8) {
            _signUpState.value = SignUpState.Error("Password must be at least 8 characters")
            return
        }

        _signUpState.value = SignUpState.Loading

        viewModelScope.launch {
            val result = repository.signUp(name, phone, email, password, roleId)
            if (result.isSuccess) {
                _signUpState.postValue(SignUpState.Success)
            } else {
                _signUpState.postValue(SignUpState.Error(result.exceptionOrNull()?.message ?: "Signup failed"))
            }
        }
    }

    fun continueGoogleSignUp(idToken: String) {
        if (_googleSignUpState.value is GoogleSignUpState.Loading) {
            return
        }

        _googleSignUpState.value = GoogleSignUpState.Loading
        viewModelScope.launch {
            val result = repository.authenticateWithGoogle(idToken)
            if (result.isSuccess) {
                when (result.getOrNull()) {
                    GoogleAuthOutcome.Authenticated -> {
                        pendingGoogleIdToken = null
                        _googleSignUpState.postValue(GoogleSignUpState.Success)
                    }
                    GoogleAuthOutcome.AccountNotFound -> {
                        pendingGoogleIdToken = idToken
                        _googleSignUpState.postValue(GoogleSignUpState.NeedsRole)
                    }
                    null -> _googleSignUpState.postValue(GoogleSignUpState.Error("Google Sign-In failed"))
                }
            } else {
                _googleSignUpState.postValue(
                    GoogleSignUpState.Error(result.exceptionOrNull()?.message ?: "Google Sign-In failed")
                )
            }
        }
    }

    fun completeGoogleSignUp(roleId: Int) {
        val idToken = pendingGoogleIdToken
        if (idToken.isNullOrBlank()) {
            _googleSignUpState.value = GoogleSignUpState.Error("Google Sign-In expired. Please try again.")
            return
        }
        if (_googleSignUpState.value is GoogleSignUpState.Loading) {
            return
        }

        _googleSignUpState.value = GoogleSignUpState.Loading
        viewModelScope.launch {
            val result = repository.authenticateWithGoogle(idToken, roleId)
            if (result.isSuccess && result.getOrNull() is GoogleAuthOutcome.Authenticated) {
                pendingGoogleIdToken = null
                _googleSignUpState.postValue(GoogleSignUpState.Success)
            } else if (result.isSuccess && result.getOrNull() is GoogleAuthOutcome.AccountNotFound) {
                _googleSignUpState.postValue(GoogleSignUpState.Error("Please select a role to continue."))
            } else {
                _googleSignUpState.postValue(
                    GoogleSignUpState.Error(result.exceptionOrNull()?.message ?: "Google Sign-In failed")
                )
            }
        }
    }

    fun cancelPendingGoogleSignUp() {
        pendingGoogleIdToken = null
    }

    sealed class SignUpState {
        object Loading : SignUpState()
        object Success : SignUpState()
        data class Error(val message: String) : SignUpState()
    }

    sealed class GoogleSignUpState {
        object Loading : GoogleSignUpState()
        object NeedsRole : GoogleSignUpState()
        object Success : GoogleSignUpState()
        data class Error(val message: String) : GoogleSignUpState()
    }

    sealed class RolesState {
        object Loading : RolesState()
        data class Success(val roles: List<RoleDto>) : RolesState()
        data class Error(val message: String) : RolesState()
    }
}
