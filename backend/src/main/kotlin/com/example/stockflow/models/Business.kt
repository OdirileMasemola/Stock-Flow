package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class Business(
    val id: Int? = null,
    val userId: Int? = null,
    val storeName: String = "",
    val ownerName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class UpdateBusinessRequest(
    val storeName: String,
    val ownerName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Per-user store/business profile. One row per authenticated user (unique user_id).
 */
object Businesses : Table("businesses") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id).uniqueIndex()
    val storeName = varchar("store_name", 100)
    val ownerName = varchar("owner_name", 100).nullable()
    val phone = varchar("phone", 30).nullable()
    val email = varchar("email", 100).nullable()
    val address = varchar("address", 255).nullable()
    val imageUrl = varchar("image_url", 500).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
