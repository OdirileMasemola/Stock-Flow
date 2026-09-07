package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class Sale(
    val id: Int? = null,
    val userId: Int,
    val totalAmount: Double,
    val paymentMethod: String,
    val timestamp: String // Simplified for JSON
)

@Serializable
data class SaleItem(
    val id: Int? = null,
    val saleId: Int,
    val productId: Int,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double
)

object Sales : Table("sales") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id).index()
    val totalAmount = decimal("total_amount", 12, 2)
    val paymentMethod = varchar("payment_method", 20) // e.g., Cash, Card, EFT
    val createdAt = datetime("created_at").default(LocalDateTime.now()).index()

    override val primaryKey = PrimaryKey(id)
}

object SaleItems : Table("sale_items") {
    val id = integer("id").autoIncrement()
    val saleId = integer("sale_id").references(Sales.id).index()
    val productId = integer("product_id").references(Products.id).index()
    val quantity = integer("quantity")
    val unitPrice = decimal("unit_price", 12, 2)
    val subtotal = decimal("subtotal", 12, 2)

    override val primaryKey = PrimaryKey(id)
}
