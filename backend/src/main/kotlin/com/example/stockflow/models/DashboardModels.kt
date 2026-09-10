package com.example.stockflow.models

import kotlinx.serialization.Serializable

/** Compact sale row for dashboard / reports lists. */
@Serializable
data class DashboardSaleItem(
    val id: Int,
    val totalAmount: Double,
    val paymentMethod: String,
    val createdAt: String
)

/** Compact purchase-order row for dashboard / reports lists. */
@Serializable
data class DashboardPurchaseOrderItem(
    val id: Int,
    val supplierId: Int,
    val supplierName: String? = null,
    val totalAmount: Double,
    val status: String,
    val createdAt: String
)

/** Compact low-stock product preview. */
@Serializable
data class DashboardLowStockItem(
    val id: Int,
    val name: String,
    val stockLevel: Int,
    val minStockLevel: Int
)

/** One day in the dashboard weekly sales chart. */
@Serializable
data class WeeklySalesDay(
    val date: String,
    val label: String,
    val totalAmount: Double,
    val salesCount: Int
)

/**
 * Aggregated dashboard summary.
 * Inventory value = Σ (stockLevel × costPrice) across all products.
 */
@Serializable
data class DashboardSummaryResponse(
    val totalProducts: Int,
    val totalStockQuantity: Int,
    val inventoryValue: Double,
    val todaySalesTotal: Double,
    val todaySalesCount: Int,
    val lowStockCount: Int,
    val weeklySales: List<WeeklySalesDay> = emptyList(),
    val recentSales: List<DashboardSaleItem> = emptyList(),
    val recentPurchaseOrders: List<DashboardPurchaseOrderItem> = emptyList(),
    val lowStockPreview: List<DashboardLowStockItem> = emptyList()
)

@Serializable
data class PaymentMethodBreakdown(
    val paymentMethod: String,
    val salesCount: Int,
    val totalAmount: Double
)

@Serializable
data class SalesReportSection(
    val totalSales: Double,
    val salesCount: Int,
    val averageSaleValue: Double,
    val byPaymentMethod: List<PaymentMethodBreakdown> = emptyList(),
    val recentSales: List<DashboardSaleItem> = emptyList()
)

@Serializable
data class InventoryReportSection(
    val totalProducts: Int,
    val totalStockQuantity: Int,
    val inventoryValue: Double,
    val lowStockCount: Int
)

@Serializable
data class PurchaseReportSection(
    val purchaseOrderCount: Int,
    val pendingCount: Int,
    val receivedCount: Int,
    val purchasingTotal: Double,
    val recentPurchaseOrders: List<DashboardPurchaseOrderItem> = emptyList()
)

@Serializable
data class ReportsResponse(
    val range: String,
    val sales: SalesReportSection,
    val inventory: InventoryReportSection,
    val purchases: PurchaseReportSection
)
