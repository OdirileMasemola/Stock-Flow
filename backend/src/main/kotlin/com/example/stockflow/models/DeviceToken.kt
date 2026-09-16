package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * FCM device registration for push notifications.
 * Multiple devices per user are allowed; [token] is globally unique.
 */
@Serializable
data class RegisterDeviceTokenRequest(
    val fcmToken: String,
    val platform: String? = "android"
)

@Serializable
data class UnregisterDeviceTokenRequest(
    val fcmToken: String
)

@Serializable
data class DeviceTokenResponse(
    val id: Int,
    val platform: String,
    val active: Boolean
)

object DeviceTokens : Table("device_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id).index()
    /** FCM registration token — unique across all users (one physical device). */
    val token = varchar("token", 512).uniqueIndex()
    val platform = varchar("platform", 32).default("android")
    val active = bool("active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

/**
 * Snapshot used when deciding whether to fire a low-stock push.
 * Dedup rule: notify only when stock **crosses into** low
 * (`previousStock > minStockLevel && currentStock <= minStockLevel`).
 */
data class LowStockCrossing(
    val productId: Int,
    val productName: String,
    val previousStock: Int,
    val currentStock: Int,
    val minStockLevel: Int
) {
    val crossedIntoLow: Boolean
        get() = previousStock > minStockLevel && currentStock <= minStockLevel
}
