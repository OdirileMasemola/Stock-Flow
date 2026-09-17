package com.example.stockflow.data.sync

import com.example.stockflow.data.local.cache.CachedDashboardSnapshot
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.DashboardLowStockItemDto
import com.example.stockflow.data.remote.DashboardSaleItemDto
import com.example.stockflow.data.remote.SaleDto
import com.google.gson.Gson

/**
 * Keeps Room product rows and dashboard snapshot aggregates consistent after
 * online mutations (sales, product CRUD) so Flow observers refresh UI without
 * a manual screen refresh. Does not send FCM — backend Stage 4 handles that.
 */
object LocalCacheReconciler {
    private val gson = Gson()

    suspend fun applySuccessfulSale(
        database: StockFlowCacheDatabase,
        userId: Int,
        sale: SaleDto,
        clock: () -> Long = { System.currentTimeMillis() }
    ) {
        val productDao = database.productDao()
        val now = clock()
        for (item in sale.items) {
            val cached = productDao.getById(userId, item.productId) ?: continue
            val newStock = (cached.stockLevel - item.quantity).coerceAtLeast(0)
            productDao.upsert(cached.copy(stockLevel = newStock, cachedAt = now))
        }
        recalculateDashboardFromProducts(
            database = database,
            userId = userId,
            clock = clock,
            saleDelta = SaleDelta(
                amount = sale.totalAmount,
                count = 1,
                recentSale = DashboardSaleItemDto(
                    id = sale.id,
                    totalAmount = sale.totalAmount,
                    paymentMethod = sale.paymentMethod,
                    createdAt = sale.createdAt
                )
            )
        )
    }

    /**
     * Recompute inventory-derived dashboard fields from cached products.
     * Preserves last-known sales/PO/weekly lists unless [saleDelta] is provided.
     */
    suspend fun recalculateDashboardFromProducts(
        database: StockFlowCacheDatabase,
        userId: Int,
        clock: () -> Long = { System.currentTimeMillis() },
        saleDelta: SaleDelta? = null
    ) {
        val products = database.productDao().getAll(userId)
        val existing = database.dashboardDao().get(userId)
        val now = clock()

        val totalProducts = products.size
        val totalStock = products.sumOf { it.stockLevel }
        val inventoryValue = products.sumOf { it.stockLevel * it.sellingPrice }
        val lowStock = products.filter { it.stockLevel <= it.minStockLevel }
            .sortedBy { it.stockLevel }
        val lowPreview = lowStock.take(5).map {
            DashboardLowStockItemDto(
                id = it.id,
                name = it.name,
                stockLevel = it.stockLevel,
                minStockLevel = it.minStockLevel
            )
        }

        val todaySalesTotal = (existing?.todaySalesTotal ?: 0.0) + (saleDelta?.amount ?: 0.0)
        val todaySalesCount = (existing?.todaySalesCount ?: 0) + (saleDelta?.count ?: 0)
        val recentSalesJson = if (saleDelta?.recentSale != null && existing != null) {
            val dto = existing.toDto()
            val updated = listOf(saleDelta.recentSale) + dto.recentSales.orEmpty()
            gson.toJson(updated.take(10))
        } else {
            existing?.recentSalesJson ?: "[]"
        }

        database.dashboardDao().upsert(
            CachedDashboardSnapshot(
                userId = userId,
                totalProducts = totalProducts,
                totalStockQuantity = totalStock,
                inventoryValue = inventoryValue,
                todaySalesTotal = todaySalesTotal,
                todaySalesCount = todaySalesCount,
                lowStockCount = lowStock.size,
                weeklySalesJson = existing?.weeklySalesJson ?: "[]",
                recentSalesJson = recentSalesJson,
                recentPurchaseOrdersJson = existing?.recentPurchaseOrdersJson ?: "[]",
                lowStockPreviewJson = gson.toJson(lowPreview),
                cachedAt = existing?.cachedAt ?: now
            )
        )
    }

    data class SaleDelta(
        val amount: Double,
        val count: Int,
        val recentSale: DashboardSaleItemDto? = null
    )
}
