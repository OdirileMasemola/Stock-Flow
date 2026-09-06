package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class User(
    val id: Int? = null,
    val username: String,
    val email: String,
    val fullName: String
)

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 100).uniqueIndex()
    val fullName = varchar("full_name", 100)

    override val primaryKey = PrimaryKey(id)
}
