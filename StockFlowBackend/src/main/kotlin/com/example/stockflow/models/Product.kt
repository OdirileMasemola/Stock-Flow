package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class Product(
    val id: Int? = null,
    val name: String,
    val sku: String? = null,
    val costPrice: Double,
    val sellingPrice: Double,
    val stockLevel: Int,
    val minStockLevel: Int,
    val categoryId: Int,
    val supplierId: Int? = null
)

object Products : Table("products") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100).index()
    val sku = varchar("sku", 50).uniqueIndex().nullable()
    val costPrice = decimal("cost_price", 12, 2)
    val sellingPrice = decimal("selling_price", 12, 2)
    val stockLevel = integer("stock_level").default(0)
    val minStockLevel = integer("min_stock_level").default(5)
    val categoryId = integer("category_id").references(Categories.id).index()
    val supplierId = integer("supplier_id").references(Suppliers.id).nullable().index()

    override val primaryKey = PrimaryKey(id)
}
