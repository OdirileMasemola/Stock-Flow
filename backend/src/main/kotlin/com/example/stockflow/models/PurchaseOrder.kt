package com.example.stockflow.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class PurchaseOrder(
    val id: Int? = null,
    val supplierId: Int,
    val totalAmount: Double,
    val status: String, // e.g., Pending, Received, Cancelled
    val expectedDeliveryDate: String? = null,
    val createdAt: String
)

@Serializable
data class PurchaseOrderItem(
    val id: Int? = null,
    val purchaseOrderId: Int,
    val productId: Int,
    val quantity: Int,
    val unitCost: Double,
    val subtotal: Double
)

object PurchaseOrders : Table("purchase_orders") {
    val id = integer("id").autoIncrement()
    val supplierId = integer("supplier_id").references(Suppliers.id).index()
    val totalAmount = decimal("total_amount", 12, 2)
    val status = varchar("status", 20).default("Pending")
    val expectedDeliveryDate = datetime("expected_delivery_date").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now()).index()

    override val primaryKey = PrimaryKey(id)
}

object PurchaseOrderItems : Table("purchase_order_items") {
    val id = integer("id").autoIncrement()
    val purchaseOrderId = integer("purchase_order_id").references(PurchaseOrders.id).index()
    val productId = integer("product_id").references(Products.id).index()
    val quantity = integer("quantity")
    val unitCost = decimal("unit_cost", 12, 2)
    val subtotal = decimal("subtotal", 12, 2)

    override val primaryKey = PrimaryKey(id)
}
