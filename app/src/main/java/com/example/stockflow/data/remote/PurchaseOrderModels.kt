package com.example.stockflow.data.remote

/**
 * Purchase order models matching the Ktor API (camelCase JSON).
 */
data class PurchaseOrderItemDto(
    val id: Int,
    val productId: Int,
    val productName: String? = null,
    val quantity: Int,
    val unitCost: Double,
    val subtotal: Double
)

data class PurchaseOrderDto(
    val id: Int,
    val supplierId: Int,
    val supplierName: String? = null,
    val totalAmount: Double,
    val status: String,
    val expectedDeliveryDate: String? = null,
    val createdAt: String,
    val items: List<PurchaseOrderItemDto> = emptyList()
)

data class CreatePurchaseOrderItemRequest(
    val productId: Int,
    val quantity: Int,
    val unitCost: Double
)

data class CreatePurchaseOrderRequest(
    val supplierId: Int,
    val expectedDeliveryDate: String? = null,
    val items: List<CreatePurchaseOrderItemRequest>
)

data class UpdatePurchaseOrderRequest(
    val supplierId: Int,
    val expectedDeliveryDate: String? = null,
    val items: List<CreatePurchaseOrderItemRequest>
)
