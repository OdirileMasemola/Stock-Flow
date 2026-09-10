package com.example.stockflow.models

import kotlinx.serialization.Serializable

/** One line in a create/update purchase-order request. */
@Serializable
data class CreatePurchaseOrderItemRequest(
    val productId: Int,
    val quantity: Int,
    val unitCost: Double
)

/** Body for creating a purchase order. Totals are calculated on the server. */
@Serializable
data class CreatePurchaseOrderRequest(
    val supplierId: Int,
    val expectedDeliveryDate: String? = null,
    val items: List<CreatePurchaseOrderItemRequest>
)

/** Body for updating a pending purchase order. */
@Serializable
data class UpdatePurchaseOrderRequest(
    val supplierId: Int,
    val expectedDeliveryDate: String? = null,
    val items: List<CreatePurchaseOrderItemRequest>
)

@Serializable
data class PurchaseOrderItemResponse(
    val id: Int,
    val productId: Int,
    val productName: String? = null,
    val quantity: Int,
    val unitCost: Double,
    val subtotal: Double
)

@Serializable
data class PurchaseOrderResponse(
    val id: Int,
    val supplierId: Int,
    val supplierName: String? = null,
    val totalAmount: Double,
    val status: String,
    val expectedDeliveryDate: String? = null,
    val createdAt: String,
    val items: List<PurchaseOrderItemResponse> = emptyList()
)
