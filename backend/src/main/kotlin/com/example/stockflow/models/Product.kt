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
    val supplierId: Int? = null,
    val imageUrl: String? = null
)

object Products : Table("products") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100).index()
    val sku = varchar("sku", 50).nullable()
    val costPrice = decimal("cost_price", 12, 2)
    val sellingPrice = decimal("selling_price", 12, 2)
    val stockLevel = integer("stock_level").default(0)
    val minStockLevel = integer("min_stock_level").default(5)
    val categoryId = integer("category_id").references(Categories.id).index()
    val supplierId = integer("supplier_id").references(Suppliers.id).nullable().index()
    val imageUrl = varchar("image_url", 1024).nullable()
    /** Shop owner. Null for rows created before shops existed; those are visible to no one. */
    val ownerUserId = integer("owner_user_id").references(Users.id).nullable().index()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("products_owner_sku_unique", ownerUserId, sku)
    }
}
