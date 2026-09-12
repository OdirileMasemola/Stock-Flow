package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class Role(
    val id: Int? = null,
    val name: String,
    val description: String? = null
)

object Roles : Table("roles") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 20).uniqueIndex()
    val description = varchar("description", 500).nullable()

    override val primaryKey = PrimaryKey(id)
}
