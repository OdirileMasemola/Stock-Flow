package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class Category(
    val id: Int? = null,
    val name: String,
    val description: String? = null
)

/** Category returned to API clients. */
@Serializable
data class CategoryResponse(
    val id: Int,
    val name: String,
    val description: String? = null
)

/** Body for creating a category (or find-or-create by name). */
@Serializable
data class CreateCategoryRequest(
    val name: String,
    val description: String? = null
)

object Categories : Table("categories") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()
    val description = varchar("description", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}
