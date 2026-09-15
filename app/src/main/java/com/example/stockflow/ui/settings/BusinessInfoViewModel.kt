package com.example.stockflow.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.BusinessDto
import com.example.stockflow.data.remote.UpdateBusinessRequest
import com.example.stockflow.data.repository.BusinessRepository
import com.example.stockflow.ui.common.ProductImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.stockflow.R

class BusinessInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BusinessRepository(sessionStore = SessionStore(application))

    private val _business = MutableLiveData<BusinessDto?>()
    val business: LiveData<BusinessDto?> = _business

    private val _pendingImageUri = MutableLiveData<Uri?>()
    val pendingImageUri: LiveData<Uri?> = _pendingImageUri

    private val _pendingLatitude = MutableLiveData<Double?>()
    val pendingLatitude: LiveData<Double?> = _pendingLatitude

    private val _pendingLongitude = MutableLiveData<Double?>()
    val pendingLongitude: LiveData<Double?> = _pendingLongitude

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private var existingImageUrl: String? = null
    private var imageRemoved = false
    private var locationCleared = false

    fun loadBusiness() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getBusiness()
                .onSuccess { data ->
                    existingImageUrl = data.imageUrl
                    imageRemoved = false
                    locationCleared = false
                    _pendingImageUri.value = null
                    _pendingLatitude.value = data.latitude
                    _pendingLongitude.value = data.longitude
                    _business.value = data
                    _uiState.value = UiState.Idle
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(
                        error.message ?: getApplication<Application>().getString(R.string.error_unable_load_business)
                    )
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

    fun setCurrentLocation(latitude: Double, longitude: Double) {
        locationCleared = false
        _pendingLatitude.value = latitude
        _pendingLongitude.value = longitude
    }

    fun currentDisplayImageUrl(): String? =
        if (imageRemoved && _pendingImageUri.value == null) null else existingImageUrl

    fun saveBusiness(
        storeName: String,
        ownerName: String,
        phone: String,
        email: String,
        address: String
    ) {
        val trimmedStore = storeName.trim()
        if (trimmedStore.isBlank()) {
            _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.error_store_name_blank))
            return
        }

        val trimmedEmail = email.trim()
        if (trimmedEmail.isNotEmpty() && (!trimmedEmail.contains("@") || !trimmedEmail.contains("."))) {
            _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.error_invalid_email))
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val imageResult = resolveImageUrlForSave()
            if (imageResult.isFailure) {
                _uiState.value = UiState.Error(
                    imageResult.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_image_upload_failed)
                )
                return@launch
            }

            val lat = if (locationCleared) null else _pendingLatitude.value
            val lng = if (locationCleared) null else _pendingLongitude.value

            val request = UpdateBusinessRequest(
                storeName = trimmedStore,
                ownerName = ownerName.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null },
                email = trimmedEmail.ifBlank { null },
                address = address.trim().ifBlank { null },
                imageUrl = imageResult.getOrNull(),
                latitude = lat,
                longitude = lng
            )
            repository.updateBusiness(request)
                .onSuccess { data ->
                    existingImageUrl = data.imageUrl
                    imageRemoved = false
                    locationCleared = false
                    _pendingImageUri.value = null
                    _pendingLatitude.value = data.latitude
                    _pendingLongitude.value = data.longitude
                    _business.value = data
                    _uiState.value = UiState.Success
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(
                        error.message ?: getApplication<Application>().getString(R.string.error_failed_save_business)
                    )
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
            repository.uploadBusinessImage(bytes, "business.jpg", "image/jpeg")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: getApplication<Application>().getString(R.string.error_upload_store_image)))
        }
    }

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
