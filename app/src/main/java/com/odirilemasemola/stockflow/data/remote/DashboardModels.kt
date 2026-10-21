package com.odirilemasemola.stockflow.data.remote

data class DashboardSaleItemDto(
    val id: Int,
    val totalAmount: Double,
    val paymentMethod: String,
    val createdAt: String
)

data class DashboardPurchaseOrderItemDto(
    val id: Int,
    val supplierId: Int,
    val supplierName: String? = null,
    val totalAmount: Double,
    val status: String,
    val createdAt: String
)

data class DashboardLowStockItemDto(
    val id: Int,
    val name: String,
    val stockLevel: Int,
    val minStockLevel: Int
)

data class WeeklySalesDayDto(
    val date: String,
    val label: String,
    val totalAmount: Double,
    val salesCount: Int
)

data class DashboardSummaryDto(
    val totalProducts: Int,
    val totalStockQuantity: Int,
    val inventoryValue: Double,
    val todaySalesTotal: Double,
    val todaySalesCount: Int,
    val lowStockCount: Int,
    val weeklySales: List<WeeklySalesDayDto>? = emptyList(),
    val recentSales: List<DashboardSaleItemDto>? = emptyList(),
    val recentPurchaseOrders: List<DashboardPurchaseOrderItemDto>? = emptyList(),
    val lowStockPreview: List<DashboardLowStockItemDto>? = emptyList()
)

data class PaymentMethodBreakdownDto(
    val paymentMethod: String? = null,
    val salesCount: Int = 0,
    val totalAmount: Double = 0.0
)

data class SalesReportSectionDto(
    val totalSales: Double = 0.0,
    val salesCount: Int = 0,
    val averageSaleValue: Double = 0.0,
    val byPaymentMethod: List<PaymentMethodBreakdownDto>? = emptyList(),
    val recentSales: List<DashboardSaleItemDto>? = emptyList()
)

data class InventoryReportSectionDto(
    val totalProducts: Int = 0,
    val totalStockQuantity: Int = 0,
    val inventoryValue: Double = 0.0,
    val lowStockCount: Int = 0
)

data class PurchaseReportSectionDto(
    val purchaseOrderCount: Int = 0,
    val pendingCount: Int = 0,
    val receivedCount: Int = 0,
    val purchasingTotal: Double = 0.0,
    val recentPurchaseOrders: List<DashboardPurchaseOrderItemDto>? = emptyList()
)

data class ProductPerformanceItemDto(
    val productId: Int = 0,
    val name: String? = null,
    val quantitySold: Int = 0,
    val revenue: Double = 0.0,
    val stockLevel: Int = 0
)

data class ProductPerformanceSectionDto(
    val bestSelling: List<ProductPerformanceItemDto>? = emptyList(),
    val slowMoving: List<ProductPerformanceItemDto>? = emptyList()
)

data class ReportsDto(
    val range: String? = null,
    val sales: SalesReportSectionDto? = null,
    val inventory: InventoryReportSectionDto? = null,
    val purchases: PurchaseReportSectionDto? = null,
    val productPerformance: ProductPerformanceSectionDto? = null,
    /** Local only: set when this report came from the offline cache (time it was saved). */
    val cachedAt: Long? = null
)
