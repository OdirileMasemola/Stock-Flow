package com.example.stockflow.ui.suppliers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.ui.common.AppStrings
import com.example.stockflow.data.remote.CreatePurchaseOrderItemRequest
import com.example.stockflow.data.remote.CreatePurchaseOrderRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.PurchaseOrderDto
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.data.repository.PurchaseOrderRepository
import com.example.stockflow.data.repository.SupplierRepository
import kotlinx.coroutines.launch
import com.example.stockflow.R

data class PoDraftLine(
    val productId: Int,
    val productName: String,
    val quantity: Int,
    val unitCost: Double
) {
    val subtotal: Double get() = quantity * unitCost
}

/**
 * Shared ViewModel helpers for purchase-order list, create, and detail flows.
 */
class PurchaseOrderViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application.applicationContext)
    private val poRepository = PurchaseOrderRepository(sessionStore = sessionStore)
    private val supplierRepository = SupplierRepository(sessionStore = sessionStore)
    private val productRepository = ProductRepository(sessionStore = sessionStore)

    private val _listState = MutableLiveData<ListUiState>(ListUiState.Loading)
    val listState: LiveData<ListUiState> = _listState

    private val _detailState = MutableLiveData<DetailUiState>(DetailUiState.Loading)
    val detailState: LiveData<DetailUiState> = _detailState

    private val _receiveMessage = MutableLiveData<String?>()
    val receiveMessage: LiveData<String?> = _receiveMessage

    private val _createState = MutableLiveData<CreateUiState>(CreateUiState.Idle)
    val createState: LiveData<CreateUiState> = _createState

    private val _suppliers = MutableLiveData<List<SupplierDto>>(emptyList())
    val suppliers: LiveData<List<SupplierDto>> = _suppliers

    private val _products = MutableLiveData<List<ProductDto>>(emptyList())
    val products: LiveData<List<ProductDto>> = _products

    private val _draftLines = MutableLiveData<List<PoDraftLine>>(emptyList())
    val draftLines: LiveData<List<PoDraftLine>> = _draftLines

    private var selectedSupplierId: Int? = null

    fun loadPurchaseOrders() {
        _listState.value = ListUiState.Loading
        viewModelScope.launch {
            when (val result = poRepository.getPurchaseOrders()) {
                is CacheResult.Fresh -> {
                    val orders = result.data
                    _listState.postValue(
                        if (orders.isEmpty()) ListUiState.Empty()
                        else ListUiState.Success(orders, fromCache = false, cachedAt = null)
                    )
                }
                is CacheResult.Cached -> {
                    val orders = result.data
                    _listState.postValue(
                        if (orders.isEmpty()) ListUiState.Empty(fromCache = true, cachedAt = result.cachedAt)
                        else ListUiState.Success(orders, fromCache = true, cachedAt = result.cachedAt)
                    )
                }
                CacheResult.Empty -> {
                    _listState.postValue(ListUiState.Error(AppStrings.get(R.string.offline_no_cached_data)))
                }
                is CacheResult.Error -> {
                    _listState.postValue(ListUiState.Error(result.message))
                }
            }
        }
    }

    fun loadPurchaseOrder(id: Int) {
        _detailState.value = DetailUiState.Loading
        viewModelScope.launch {
            when (val result = poRepository.getPurchaseOrder(id)) {
                is CacheResult.Fresh -> {
                    _detailState.postValue(DetailUiState.Success(result.data, fromCache = false, cachedAt = null))
                }
                is CacheResult.Cached -> {
                    _detailState.postValue(DetailUiState.Success(result.data, fromCache = true, cachedAt = result.cachedAt))
                }
                CacheResult.Empty -> {
                    _detailState.postValue(DetailUiState.Error(AppStrings.get(R.string.offline_no_cached_data)))
                }
                is CacheResult.Error -> {
                    _detailState.postValue(DetailUiState.Error(result.message))
                }
            }
        }
    }

    fun receivePurchaseOrder(id: Int) {
        viewModelScope.launch {
            val result = poRepository.receivePurchaseOrder(id)
            if (result.isSuccess) {
                val order = result.getOrNull()!!
                _detailState.postValue(DetailUiState.Success(order))
                _receiveMessage.postValue(getApplication<Application>().getString(R.string.msg_order_received, order.id))
            } else {
                _receiveMessage.postValue(
                    result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_failed_receive_po)
                )
            }
        }
    }

    fun clearReceiveMessage() {
        _receiveMessage.value = null
    }

    fun prepareCreateForm(preselectedSupplierId: Int?) {
        selectedSupplierId = preselectedSupplierId
        _draftLines.value = emptyList()
        _createState.value = CreateUiState.Idle
        viewModelScope.launch {
            val suppliersResult = supplierRepository.getSuppliers()
            val productsResult = productRepository.getProducts()

            val suppliers = suppliersResult.getOrNull()
            val products = productsResult.getOrNull()
            if (suppliers == null) {
                _createState.postValue(
                    CreateUiState.Error(
                        when (suppliersResult) {
                            CacheResult.Empty -> AppStrings.get(R.string.offline_no_cached_data)
                            is CacheResult.Error -> suppliersResult.message
                            else -> getApplication<Application>().getString(R.string.error_unable_load_suppliers)
                        }
                    )
                )
                return@launch
            }
            if (products == null) {
                _createState.postValue(
                    CreateUiState.Error(
                        when (productsResult) {
                            CacheResult.Empty -> AppStrings.get(R.string.offline_no_cached_data)
                            is CacheResult.Error -> productsResult.message
                            else -> getApplication<Application>().getString(R.string.error_unable_load_products)
                        }
                    )
                )
                return@launch
            }

            _suppliers.postValue(suppliers)
            _products.postValue(products)
            _createState.postValue(CreateUiState.Ready)
        }
    }

    fun setSelectedSupplier(supplierId: Int) {
        selectedSupplierId = supplierId
    }

    fun getSelectedSupplierId(): Int? = selectedSupplierId

    fun addOrUpdateLine(product: ProductDto, quantity: Int, unitCost: Double) {
        if (quantity <= 0) {
            _createState.value = CreateUiState.Error(getApplication<Application>().getString(R.string.po_invalid_quantity))
            return
        }
        if (unitCost < 0) {
            _createState.value = CreateUiState.Error(getApplication<Application>().getString(R.string.po_invalid_cost))
            return
        }

        val current = _draftLines.value.orEmpty().toMutableList()
        val index = current.indexOfFirst { it.productId == product.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(
                quantity = existing.quantity + quantity,
                unitCost = unitCost
            )
        } else {
            current.add(
                PoDraftLine(
                    productId = product.id,
                    productName = product.name,
                    quantity = quantity,
                    unitCost = unitCost
                )
            )
        }
        _draftLines.value = current
        _createState.value = CreateUiState.Ready
    }

    fun setLineQuantity(productId: Int, quantity: Int) {
        if (quantity <= 0) {
            removeLine(productId)
            return
        }
        _draftLines.value = _draftLines.value.orEmpty().map {
            if (it.productId == productId) it.copy(quantity = quantity) else it
        }
    }

    fun removeLine(productId: Int) {
        _draftLines.value = _draftLines.value.orEmpty().filterNot { it.productId == productId }
    }

    fun submitPurchaseOrder() {
        val supplierId = selectedSupplierId
        if (supplierId == null || supplierId <= 0) {
            _createState.value = CreateUiState.Error(getApplication<Application>().getString(R.string.error_select_supplier))
            return
        }
        val lines = _draftLines.value.orEmpty()
        if (lines.isEmpty()) {
            _createState.value = CreateUiState.Error(getApplication<Application>().getString(R.string.error_add_at_least_one_product))
            return
        }

        _createState.value = CreateUiState.Submitting
        viewModelScope.launch {
            val request = CreatePurchaseOrderRequest(
                supplierId = supplierId,
                items = lines.map {
                    CreatePurchaseOrderItemRequest(
                        productId = it.productId,
                        quantity = it.quantity,
                        unitCost = it.unitCost
                    )
                }
            )
            val result = poRepository.createPurchaseOrder(request)
            if (result.isSuccess) {
                _createState.postValue(CreateUiState.Success(result.getOrNull()!!))
            } else {
                _createState.postValue(
                    CreateUiState.Error(
                        result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_failed_create_po)
                    )
                )
            }
        }
    }

    sealed class ListUiState {
        object Loading : ListUiState()
        data class Empty(val fromCache: Boolean = false, val cachedAt: Long? = null) : ListUiState()
        data class Success(
            val orders: List<PurchaseOrderDto>,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : ListUiState()
        data class Error(val message: String) : ListUiState()
    }

    sealed class DetailUiState {
        object Loading : DetailUiState()
        data class Success(
            val order: PurchaseOrderDto,
            val fromCache: Boolean = false,
            val cachedAt: Long? = null
        ) : DetailUiState()
        data class Error(val message: String) : DetailUiState()
    }

    sealed class CreateUiState {
        object Idle : CreateUiState()
        object Ready : CreateUiState()
        object Submitting : CreateUiState()
        data class Success(val order: PurchaseOrderDto) : CreateUiState()
        data class Error(val message: String) : CreateUiState()
    }
}
