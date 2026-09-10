package com.example.stockflow.ui.suppliers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.CreateSupplierRequest
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.data.remote.UpdateSupplierRequest
import com.example.stockflow.data.repository.SupplierRepository
import kotlinx.coroutines.launch

/**
 * Create or edit a supplier.
 */
class AddSupplierViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SupplierRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    private val _loadedSupplier = MutableLiveData<SupplierDto?>()
    val loadedSupplier: LiveData<SupplierDto?> = _loadedSupplier

    private val _formState = MutableLiveData<FormState>(FormState.Idle)
    val formState: LiveData<FormState> = _formState

    fun loadSupplier(id: Int) {
        _formState.value = FormState.Loading
        viewModelScope.launch {
            val result = repository.getSupplier(id)
            if (result.isSuccess) {
                _loadedSupplier.postValue(result.getOrNull())
                _formState.postValue(FormState.Idle)
            } else {
                _formState.postValue(
                    FormState.Error(result.exceptionOrNull()?.message ?: "Unable to load supplier")
                )
            }
        }
    }

    fun saveSupplier(
        supplierId: Int?,
        name: String,
        contactName: String,
        phone: String,
        email: String,
        address: String
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _formState.value = FormState.Error("Supplier name is required")
            return
        }

        val contact = contactName.trim().ifEmpty { null }
        val phoneVal = phone.trim().ifEmpty { null }
        val emailVal = email.trim().ifEmpty { null }
        val addressVal = address.trim().ifEmpty { null }

        if (emailVal != null && (!emailVal.contains("@") || !emailVal.contains("."))) {
            _formState.value = FormState.Error("Invalid email format")
            return
        }

        _formState.value = FormState.Loading
        viewModelScope.launch {
            val result = if (supplierId == null) {
                repository.createSupplier(
                    CreateSupplierRequest(
                        name = trimmedName,
                        contactName = contact,
                        phone = phoneVal,
                        email = emailVal,
                        address = addressVal
                    )
                )
            } else {
                repository.updateSupplier(
                    supplierId,
                    UpdateSupplierRequest(
                        name = trimmedName,
                        contactName = contact,
                        phone = phoneVal,
                        email = emailVal,
                        address = addressVal
                    )
                )
            }

            if (result.isSuccess) {
                _formState.postValue(FormState.Success(isUpdate = supplierId != null))
            } else {
                _formState.postValue(
                    FormState.Error(result.exceptionOrNull()?.message ?: "Failed to save supplier")
                )
            }
        }
    }

    sealed class FormState {
        object Idle : FormState()
        object Loading : FormState()
        data class Success(val isUpdate: Boolean) : FormState()
        data class Error(val message: String) : FormState()
    }
}
