package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class Supplier(
    val id: Int? = null,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)

object Suppliers : Table("suppliers") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val contactName = varchar("contact_name", 100).nullable()
    val phone = varchar("phone", 20).nullable()
    val email = varchar("email", 100).nullable()
    val address = varchar("address", 255).nullable()
    /** Shop owner. Null for rows created before shops existed; those are visible to no one. */
    val ownerUserId = integer("owner_user_id").references(Users.id).nullable().index()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("suppliers_owner_name_unique", ownerUserId, name)
    }
}
