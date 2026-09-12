package com.example.stockflow.ui.sales

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.stockflow.data.remote.ProductDto

/**
 * In-memory cart shared by the POS screen and CartActivity.
 */
object CartSession {

    data class CartLine(
        val productId: Int,
        val name: String,
        val unitPrice: Double,
        val quantity: Int,
        val stockLevel: Int
    ) {
        val lineTotal: Double get() = unitPrice * quantity
    }

    data class CartUiState(
        val lines: List<CartLine> = emptyList(),
        /** Total units in the cart (badge count). */
        val itemCount: Int = 0,
        val total: Double = 0.0
    )

    private val linesById = linkedMapOf<Int, CartLine>()
    private val _state = MutableLiveData(CartUiState())
    val state: LiveData<CartUiState> = _state

    private val _paymentMethod = MutableLiveData("Cash")
    val paymentMethod: LiveData<String> = _paymentMethod

    fun selectPaymentMethod(method: String) {
        _paymentMethod.value = method
    }

    /** @return error message, or null on success */
    fun addProduct(product: ProductDto): String? {
        if (product.stockLevel <= 0) {
            return "\"${product.name}\" is out of stock"
        }
        val existing = linesById[product.id]
        val nextQty = (existing?.quantity ?: 0) + 1
        if (nextQty > product.stockLevel) {
            return "Only ${product.stockLevel} in stock for \"${product.name}\""
        }
        linesById[product.id] = CartLine(
            productId = product.id,
            name = product.name,
            unitPrice = product.sellingPrice,
            quantity = nextQty,
            stockLevel = product.stockLevel
        )
        publish()
        return null
    }

    fun increase(productId: Int): String? {
        val line = linesById[productId] ?: return null
        if (line.quantity >= line.stockLevel) {
            return "Only ${line.stockLevel} in stock for \"${line.name}\""
        }
        linesById[productId] = line.copy(quantity = line.quantity + 1)
        publish()
        return null
    }

    fun decrease(productId: Int) {
        val line = linesById[productId] ?: return
        if (line.quantity <= 1) {
            linesById.remove(productId)
        } else {
            linesById[productId] = line.copy(quantity = line.quantity - 1)
        }
        publish()
    }

    fun remove(productId: Int) {
        linesById.remove(productId)
        publish()
    }

    fun clear() {
        linesById.clear()
        publish()
    }

    fun syncWithProducts(products: List<ProductDto>) {
        val byId = products.associateBy { it.id }
        val iterator = linesById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val product = byId[entry.key]
            if (product == null || product.stockLevel <= 0) {
                iterator.remove()
            } else {
                val qty = minOf(entry.value.quantity, product.stockLevel)
                entry.setValue(
                    entry.value.copy(
                        name = product.name,
                        unitPrice = product.sellingPrice,
                        quantity = qty,
                        stockLevel = product.stockLevel
                    )
                )
            }
        }
        publish()
    }

    fun snapshotLines(): List<CartLine> = linesById.values.toList()

    private fun publish() {
        val lines = linesById.values.toList()
        _state.value = CartUiState(
            lines = lines,
            itemCount = lines.sumOf { it.quantity },
            total = lines.sumOf { it.unitPrice * it.quantity }
        )
    }
}
