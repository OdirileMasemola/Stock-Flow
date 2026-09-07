package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class Category(
    val id: Int? = null,
    val name: String,
    val description: String? = null
)

object Categories : Table("categories") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()
    val description = varchar("description", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}
