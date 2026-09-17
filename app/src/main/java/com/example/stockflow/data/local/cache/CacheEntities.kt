package com.example.stockflow.data.local.cache

import androidx.room.Entity

@Entity(tableName = "cached_products", primaryKeys = ["userId", "id"])
data class CachedProduct(
    val userId: Int,
    val id: Int,
    val name: String,
    val sku: String?,
    val costPrice: Double,
    val sellingPrice: Double,
    val stockLevel: Int,
    val minStockLevel: Int,
    val categoryId: Int,
    val categoryName: String?,
    val supplierId: Int?,
    val imageUrl: String?,
    val cachedAt: Long
)

@Entity(tableName = "cached_categories", primaryKeys = ["userId", "id"])
data class CachedCategory(
    val userId: Int,
    val id: Int,
    val name: String,
    val description: String?,
    val cachedAt: Long
)

@Entity(tableName = "cached_suppliers", primaryKeys = ["userId", "id"])
data class CachedSupplier(
    val userId: Int,
    val id: Int,
    val name: String,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val cachedAt: Long
)

@Entity(tableName = "cached_purchase_orders", primaryKeys = ["userId", "id"])
data class CachedPurchaseOrder(
    val userId: Int,
    val id: Int,
    val supplierId: Int,
    val supplierName: String?,
    val totalAmount: Double,
    val status: String,
    val expectedDeliveryDate: String?,
    val createdAt: String,
    val cachedAt: Long
)

@Entity(tableName = "cached_purchase_order_items", primaryKeys = ["userId", "id"])
data class CachedPurchaseOrderItem(
    val userId: Int,
    val id: Int,
    val purchaseOrderId: Int,
    val productId: Int,
    val productName: String?,
    val quantity: Int,
    val unitCost: Double,
    val subtotal: Double,
    val cachedAt: Long
)

@Entity(tableName = "cached_profiles", primaryKeys = ["userId"])
data class CachedProfile(
    val userId: Int,
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val roleId: Int?,
    val profileImageUrl: String?,
    val cachedAt: Long
)

@Entity(tableName = "cached_businesses", primaryKeys = ["userId"])
data class CachedBusiness(
    val userId: Int,
    val id: Int?,
    val storeName: String?,
    val ownerName: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val imageUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: String?,
    val updatedAt: String?,
    val cachedAt: Long
)

@Entity(tableName = "cached_dashboard_snapshots", primaryKeys = ["userId"])
data class CachedDashboardSnapshot(
    val userId: Int,
    val totalProducts: Int,
    val totalStockQuantity: Int,
    val inventoryValue: Double,
    val todaySalesTotal: Double,
    val todaySalesCount: Int,
    val lowStockCount: Int,
    /** Gson JSON arrays for nested dashboard lists. */
    val weeklySalesJson: String,
    val recentSalesJson: String,
    val recentPurchaseOrdersJson: String,
    val lowStockPreviewJson: String,
    val cachedAt: Long
)
