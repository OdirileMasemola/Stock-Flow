package com.example.stockflow.data.remote

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
    val items: List<CreateSaleItemRequest>
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
    val items: List<SaleItemDto> = emptyList()
)
