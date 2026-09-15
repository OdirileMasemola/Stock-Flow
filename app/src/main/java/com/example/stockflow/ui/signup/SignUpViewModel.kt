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
import com.example.stockflow.R

class SignUpViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private var pendingGoogleIdToken: String? = null

    private val _signUpState = MutableLiveData<SignUpState>()
    val signUpState: LiveData<SignUpState> = _signUpState

    private val _googleSignUpState = MutableLiveData<GoogleSignUpState>(GoogleSignUpState.Idle)
    val googleSignUpState: LiveData<GoogleSignUpState> = _googleSignUpState

    private val _rolesState = MutableLiveData<RolesState>()
    val rolesState: LiveData<RolesState> = _rolesState

    init {
        loadRoles()
    }

    fun currentRoles(): List<RoleDto> {
        return (_rolesState.value as? RolesState.Success)?.roles.orEmpty()
    }

    fun loadRoles() {
        _rolesState.value = RolesState.Loading
        viewModelScope.launch {
            val result = repository.getRoles()
            if (result.isSuccess) {
                val roles = result.getOrDefault(emptyList())
                if (roles.isEmpty()) {
                    _rolesState.postValue(RolesState.Error(getApplication<Application>().getString(R.string.error_no_roles)))
                } else {
                    _rolesState.postValue(RolesState.Success(roles))
                }
            } else {
                _rolesState.postValue(
                    RolesState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_unable_load_roles))
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
            _signUpState.value = SignUpState.Error(getApplication<Application>().getString(R.string.error_fill_all_fields))
            return
        }

        if (roleId == null) {
            _signUpState.value = SignUpState.Error(getApplication<Application>().getString(R.string.error_select_role))
            return
        }

        if (password != confirmPass) {
            _signUpState.value = SignUpState.Error(getApplication<Application>().getString(R.string.error_passwords_mismatch))
            return
        }

        if (password.length < 8) {
            _signUpState.value = SignUpState.Error(getApplication<Application>().getString(R.string.error_password_min_length))
            return
        }

        _signUpState.value = SignUpState.Loading

        viewModelScope.launch {
            val result = repository.signUp(name, phone, email, password, roleId)
            if (result.isSuccess) {
                _signUpState.postValue(SignUpState.Success)
            } else {
                _signUpState.postValue(SignUpState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_signup_failed)))
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
                    null -> _googleSignUpState.postValue(GoogleSignUpState.Error(getApplication<Application>().getString(R.string.error_google_signin_failed)))
                }
            } else {
                _googleSignUpState.postValue(
                    GoogleSignUpState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_google_signin_failed))
                )
            }
        }
    }

    fun completeGoogleSignUp(roleId: Int) {
        val idToken = pendingGoogleIdToken
        if (idToken.isNullOrBlank()) {
            _googleSignUpState.value = GoogleSignUpState.Error(getApplication<Application>().getString(R.string.error_google_signin_expired))
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
                _googleSignUpState.postValue(GoogleSignUpState.Error(getApplication<Application>().getString(R.string.error_select_role_continue)))
            } else {
                _googleSignUpState.postValue(
                    GoogleSignUpState.Error(result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_google_signin_failed))
                )
            }
        }
    }

    fun cancelPendingGoogleSignUp() {
        pendingGoogleIdToken = null
        _googleSignUpState.value = GoogleSignUpState.Idle
    }

    /** Clears sticky NeedsRole after the dialog is shown so rotation does not re-open it. */
    fun acknowledgeRolePrompt() {
        if (_googleSignUpState.value is GoogleSignUpState.NeedsRole) {
            _googleSignUpState.value = GoogleSignUpState.Idle
        }
    }

    sealed class SignUpState {
        object Loading : SignUpState()
        object Success : SignUpState()
        data class Error(val message: String) : SignUpState()
    }

    sealed class GoogleSignUpState {
        object Idle : GoogleSignUpState()
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
