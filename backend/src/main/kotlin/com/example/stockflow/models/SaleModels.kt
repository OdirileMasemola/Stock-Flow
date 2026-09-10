package com.example.stockflow.models

import kotlinx.serialization.Serializable

/** One line in a create-sale request. Prices are never accepted from the client. */
@Serializable
data class CreateSaleItemRequest(
    val productId: Int,
    val quantity: Int
)

/** Body for creating a sale. Server calculates prices and totals from PostgreSQL. */
@Serializable
data class CreateSaleRequest(
    val paymentMethod: String,
    val items: List<CreateSaleItemRequest>
)

@Serializable
data class SaleItemResponse(
    val id: Int,
    val productId: Int,
    val productName: String? = null,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double
)

@Serializable
data class SaleResponse(
    val id: Int,
    val userId: Int,
    val totalAmount: Double,
    val paymentMethod: String,
    val createdAt: String,
    val items: List<SaleItemResponse> = emptyList()
)
