package com.example.stockflow.models

import kotlinx.serialization.Serializable

/** Activity / audit event types written to Firestore. */
object ActivityTypes {
    const val PRODUCT_CREATED = "PRODUCT_CREATED"
    const val PRODUCT_UPDATED = "PRODUCT_UPDATED"
    const val PRODUCT_DELETED = "PRODUCT_DELETED"
    const val LOW_STOCK = "LOW_STOCK"
}

@Serializable
data class ActivityItemResponse(
    val id: String,
    val type: String,
    val message: String,
    val userId: Int,
    val productId: Int? = null,
    val productName: String? = null,
    val timestamp: String,
    val metadata: Map<String, String>? = null
)

@Serializable
data class ActivityListResponse(
    val items: List<ActivityItemResponse>,
    val businessId: String
)
