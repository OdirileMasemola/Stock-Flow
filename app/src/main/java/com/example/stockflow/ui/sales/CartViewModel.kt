package com.example.stockflow.ui.sales

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.CreateSaleItemRequest
import com.example.stockflow.data.remote.CreateSaleRequest
import com.example.stockflow.data.remote.SaleDto
import com.example.stockflow.data.repository.SaleRepository
import kotlinx.coroutines.launch

/**
 * Cart screen: qty edits, payment method, complete sale.
 */
class CartViewModel(application: Application) : AndroidViewModel(application) {

    private val saleRepository = SaleRepository(
        sessionStore = SessionStore(application.applicationContext)
    )

    val cartState: LiveData<CartSession.CartUiState> = CartSession.state
    val paymentMethod: LiveData<String> = CartSession.paymentMethod

    private val _checkoutState = MutableLiveData<CheckoutUiState>(CheckoutUiState.Idle)
    val checkoutState: LiveData<CheckoutUiState> = _checkoutState

    fun increase(productId: Int) {
        CartSession.increase(productId)?.let { message ->
            _checkoutState.value = CheckoutUiState.Error(message)
        }
    }

    fun decrease(productId: Int) = CartSession.decrease(productId)

    fun remove(productId: Int) = CartSession.remove(productId)

    fun clearCart() = CartSession.clear()

    fun selectPaymentMethod(method: String) = CartSession.selectPaymentMethod(method)

    fun completeSale() {
        val lines = CartSession.snapshotLines()
        if (lines.isEmpty()) {
            _checkoutState.value = CheckoutUiState.Error("Cart is empty")
            return
        }
        if (lines.any { it.quantity <= 0 }) {
            _checkoutState.value = CheckoutUiState.Error("All quantities must be greater than zero")
            return
        }
        val payment = CartSession.paymentMethod.value?.trim().orEmpty()
        if (payment.isEmpty()) {
            _checkoutState.value = CheckoutUiState.Error("Select a payment method")
            return
        }

        _checkoutState.value = CheckoutUiState.Loading
        viewModelScope.launch {
            val request = CreateSaleRequest(
                paymentMethod = payment,
                items = lines.map {
                    CreateSaleItemRequest(productId = it.productId, quantity = it.quantity)
                }
            )
            val result = saleRepository.createSale(request)
            if (result.isSuccess) {
                val sale = result.getOrNull()!!
                CartSession.clear()
                _checkoutState.postValue(CheckoutUiState.Success(sale))
            } else {
                _checkoutState.postValue(
                    CheckoutUiState.Error(result.exceptionOrNull()?.message ?: "Failed to complete sale")
                )
            }
        }
    }

    fun clearCheckoutMessage() {
        if (_checkoutState.value !is CheckoutUiState.Loading) {
            _checkoutState.value = CheckoutUiState.Idle
        }
    }

    sealed class CheckoutUiState {
        object Idle : CheckoutUiState()
        object Loading : CheckoutUiState()
        data class Success(val sale: SaleDto) : CheckoutUiState()
        data class Error(val message: String) : CheckoutUiState()
    }
}
