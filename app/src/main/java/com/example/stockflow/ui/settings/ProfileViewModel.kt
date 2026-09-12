package com.example.stockflow.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ProfileDto
import com.example.stockflow.data.repository.UserRepository
import com.example.stockflow.ui.common.ProductImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserRepository(sessionStore = SessionStore(application))

    private val _profile = MutableLiveData<ProfileDto?>()
    val profile: LiveData<ProfileDto?> = _profile

    private val _pendingImageUri = MutableLiveData<Uri?>()
    val pendingImageUri: LiveData<Uri?> = _pendingImageUri

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private var existingImageUrl: String? = null
    private var imageRemoved = false

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getProfile()
                .onSuccess { data ->
                    existingImageUrl = data.profileImageUrl
                    imageRemoved = false
                    _pendingImageUri.value = null
                    _profile.value = data
                    _uiState.value = UiState.Idle
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unable to load profile")
                }
        }
    }

    fun setPendingImage(uri: Uri?) {
        _pendingImageUri.value = uri
        imageRemoved = false
    }

    fun clearImage() {
        _pendingImageUri.value = null
        imageRemoved = true
    }

    fun currentDisplayImageUrl(): String? =
        if (imageRemoved && _pendingImageUri.value == null) null else existingImageUrl

    fun saveProfile(fullName: String) {
        val trimmed = fullName.trim()
        if (trimmed.isBlank()) {
            _uiState.value = UiState.Error("Full name cannot be blank")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val imageResult = resolveImageUrlForSave()
            if (imageResult.isFailure) {
                _uiState.value = UiState.Error(
                    imageResult.exceptionOrNull()?.message ?: "Image upload failed"
                )
                return@launch
            }

            repository.updateProfile(trimmed, imageResult.getOrNull())
                .onSuccess { data ->
                    existingImageUrl = data.profileImageUrl
                    imageRemoved = false
                    _pendingImageUri.value = null
                    _profile.value = data
                    _uiState.value = UiState.Success
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to update profile")
                }
        }
    }

    private suspend fun resolveImageUrlForSave(): Result<String?> {
        val localUri = _pendingImageUri.value
        if (localUri != null) {
            return uploadLocalImage(localUri)
        }
        if (imageRemoved) {
            return Result.success(null)
        }
        return Result.success(existingImageUrl)
    }

    private suspend fun uploadLocalImage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = ProductImages.readCompressedImageBytes(getApplication(), uri)
            repository.uploadProfileImage(bytes, "profile.jpg", "image/jpeg")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Could not upload the profile picture"))
        }
    }

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
