package com.odirilemasemola.stockflow.data.sync

import com.odirilemasemola.stockflow.data.remote.CreateSaleItemRequest
import com.odirilemasemola.stockflow.data.remote.CreateSaleRequest
import com.odirilemasemola.stockflow.data.remote.SaleDto
import com.odirilemasemola.stockflow.data.remote.SaleItemDto
import com.google.gson.Gson

/**
 * Queue payload for a sale recorded offline. Prices are a local estimate for display only;
 * the server recalculates prices and totals from PostgreSQL when the sale syncs.
 */
data class SaleWritePayload(
    val clientSaleId: String,
    val paymentMethod: String,
    val createdAt: String,
    val lines: List<Line>
) {
    data class Line(
        val productId: Int,
        val productName: String?,
        val quantity: Int,
        val unitPrice: Double
    )

    val estimatedTotal: Double get() = lines.sumOf { it.unitPrice * it.quantity }

    fun toCreateRequest(): CreateSaleRequest = CreateSaleRequest(
        paymentMethod = paymentMethod,
        items = lines.map { CreateSaleItemRequest(productId = it.productId, quantity = it.quantity) },
        clientSaleId = clientSaleId
    )

    /** Local, pending representation for the sales history list. [localId] is negative. */
    fun toPendingSaleDto(localId: Int, userId: Int): SaleDto = SaleDto(
        id = localId,
        userId = userId,
        totalAmount = estimatedTotal,
        paymentMethod = paymentMethod,
        createdAt = createdAt,
        items = lines.mapIndexed { index, line ->
            SaleItemDto(
                id = -(index + 1),
                productId = line.productId,
                productName = line.productName,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                subtotal = line.unitPrice * line.quantity
            )
        },
        pending = true
    )

    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()
        fun fromJson(json: String): SaleWritePayload = gson.fromJson(json, SaleWritePayload::class.java)
    }
}
