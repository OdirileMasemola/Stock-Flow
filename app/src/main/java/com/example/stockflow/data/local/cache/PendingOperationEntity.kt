package com.example.stockflow.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline WRITE queue row. Never stores JWT, passwords, or other secrets —
 * [payloadJson] holds only product field payloads needed to replay the API call.
 */
@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["userId", "status"]),
        Index(value = ["userId", "entityType", "localEntityId"])
    ]
)
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val userId: Int,
    /** CREATE | UPDATE | DELETE */
    val operationType: String,
    /** PRODUCT | CATEGORY */
    val entityType: String,
    /** Local cache key (temp negative id or remote id as string). */
    val localEntityId: String,
    val remoteEntityId: Int?,
    val payloadJson: String,
    /** PENDING | SYNCING | FAILED */
    val status: String,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)

object PendingOpStatus {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val FAILED = "FAILED"
}

object PendingOpType {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
}

object PendingEntityType {
    const val PRODUCT = "PRODUCT"
    const val CATEGORY = "CATEGORY"
}
