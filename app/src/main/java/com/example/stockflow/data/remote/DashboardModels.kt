package com.example.stockflow.data.remote

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
    val weeklySales: List<WeeklySalesDayDto> = emptyList(),
    val recentSales: List<DashboardSaleItemDto> = emptyList(),
    val recentPurchaseOrders: List<DashboardPurchaseOrderItemDto> = emptyList(),
    val lowStockPreview: List<DashboardLowStockItemDto> = emptyList()
)

data class PaymentMethodBreakdownDto(
    val paymentMethod: String,
    val salesCount: Int,
    val totalAmount: Double
)

data class SalesReportSectionDto(
    val totalSales: Double,
    val salesCount: Int,
    val averageSaleValue: Double,
    val byPaymentMethod: List<PaymentMethodBreakdownDto> = emptyList(),
    val recentSales: List<DashboardSaleItemDto> = emptyList()
)

data class InventoryReportSectionDto(
    val totalProducts: Int,
    val totalStockQuantity: Int,
    val inventoryValue: Double,
    val lowStockCount: Int
)

data class PurchaseReportSectionDto(
    val purchaseOrderCount: Int,
    val pendingCount: Int,
    val receivedCount: Int,
    val purchasingTotal: Double,
    val recentPurchaseOrders: List<DashboardPurchaseOrderItemDto> = emptyList()
)

data class ReportsDto(
    val range: String,
    val sales: SalesReportSectionDto,
    val inventory: InventoryReportSectionDto,
    val purchases: PurchaseReportSectionDto
)
