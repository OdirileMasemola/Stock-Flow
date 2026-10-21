package com.odirilemasemola.stockflow.data.remote

/**
 * Sale DTOs — field names match the Ktor JSON (camelCase).
 * Client never sends prices; the server calculates them.
 */
data class CreateSaleItemRequest(
    val productId: Int,
    val quantity: Int
)

data class CreateSaleRequest(
    val paymentMethod: String,
    val items: List<CreateSaleItemRequest>,
    /** Idempotency key: retries of the same sale (e.g. offline queue) are recorded once. */
    val clientSaleId: String? = null
)

data class SaleItemDto(
    val id: Int,
    val productId: Int,
    val productName: String? = null,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double
)

data class SaleDto(
    val id: Int,
    val userId: Int,
    val totalAmount: Double,
    val paymentMethod: String,
    val createdAt: String,
    val items: List<SaleItemDto> = emptyList(),
    /** Local only: recorded offline and still waiting to sync. Never sent by the server. */
    val pending: Boolean = false
)
