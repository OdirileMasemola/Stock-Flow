package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.DashboardLowStockItem
import com.example.stockflow.models.DashboardPurchaseOrderItem
import com.example.stockflow.models.DashboardSaleItem
import com.example.stockflow.models.DashboardSummaryResponse
import com.example.stockflow.models.InventoryReportSection
import com.example.stockflow.models.PaymentMethodBreakdown
import com.example.stockflow.models.Products
import com.example.stockflow.models.PurchaseOrders
import com.example.stockflow.models.PurchaseReportSection
import com.example.stockflow.models.ReportsResponse
import com.example.stockflow.models.Sales
import com.example.stockflow.models.SalesReportSection
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.WeeklySalesDay
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

interface DashboardRepository {
    suspend fun getSummary(recentLimit: Int = 5): DashboardSummaryResponse
    suspend fun getReports(rangeStart: LocalDateTime, rangeEnd: LocalDateTime, rangeLabel: String): ReportsResponse
}

class DashboardRepositoryImpl : DashboardRepository {

    override suspend fun getSummary(recentLimit: Int): DashboardSummaryResponse = dbQuery {
        val inventory = loadInventoryStats(recentLimit)

        val todayStart = LocalDate.now().atStartOfDay()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay()
        val todayAmountSum = Sales.totalAmount.sum()
        val todayTotal = Sales
            .select(todayAmountSum)
            .where { (Sales.createdAt greaterEq todayStart) and (Sales.createdAt less todayEnd) }
            .singleOrNull()
            ?.get(todayAmountSum)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        val todaySalesCount = Sales
            .selectAll()
            .where { (Sales.createdAt greaterEq todayStart) and (Sales.createdAt less todayEnd) }
            .count()
            .toInt()

        DashboardSummaryResponse(
            totalProducts = inventory.totalProducts,
            totalStockQuantity = inventory.totalStock,
            inventoryValue = inventory.inventoryValue.toDouble(),
            todaySalesTotal = todayTotal.toDouble(),
            todaySalesCount = todaySalesCount,
            lowStockCount = inventory.lowStockCount,
            weeklySales = loadWeeklySales(),
            recentSales = loadRecentSales(recentLimit),
            recentPurchaseOrders = loadRecentPurchaseOrders(recentLimit),
            lowStockPreview = inventory.lowStockPreview
        )
    }

    override suspend fun getReports(
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
        rangeLabel: String
    ): ReportsResponse = dbQuery {
        val salesInRange = Sales
            .selectAll()
            .where { (Sales.createdAt greaterEq rangeStart) and (Sales.createdAt less rangeEnd) }
            .orderBy(Sales.createdAt, SortOrder.DESC)
            .toList()

        var salesTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        val byMethod = linkedMapOf<String, Pair<Int, BigDecimal>>()
        for (sale in salesInRange) {
            val amount = sale[Sales.totalAmount]
            salesTotal = salesTotal.add(amount)
            val method = sale[Sales.paymentMethod]
            val existing = byMethod[method] ?: (0 to BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            byMethod[method] = (existing.first + 1) to existing.second.add(amount)
        }

        val salesCount = salesInRange.size
        val average = if (salesCount == 0) {
            0.0
        } else {
            salesTotal
                .divide(BigDecimal.valueOf(salesCount.toLong()), 2, RoundingMode.HALF_UP)
                .toDouble()
        }

        val inventory = loadInventoryStats(previewLimit = 0)

        val purchaseCount = PurchaseOrders.selectAll().count().toInt()
        val pending = PurchaseOrders
            .selectAll()
            .where { PurchaseOrders.status eq "Pending" }
            .count()
            .toInt()
        val received = PurchaseOrders
            .selectAll()
            .where { PurchaseOrders.status eq "Received" }
            .count()
            .toInt()
        val purchasingSum = PurchaseOrders.totalAmount.sum()
        val purchasingTotal = PurchaseOrders
            .select(purchasingSum)
            .singleOrNull()
            ?.get(purchasingSum)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

        ReportsResponse(
            range = rangeLabel,
            sales = SalesReportSection(
                totalSales = salesTotal.toDouble(),
                salesCount = salesCount,
                averageSaleValue = average,
                byPaymentMethod = byMethod.map { (method, pair) ->
                    PaymentMethodBreakdown(
                        paymentMethod = method,
                        salesCount = pair.first,
                        totalAmount = pair.second.toDouble()
                    )
                },
                recentSales = salesInRange.take(10).map { row ->
                    DashboardSaleItem(
                        id = row[Sales.id],
                        totalAmount = row[Sales.totalAmount].toDouble(),
                        paymentMethod = row[Sales.paymentMethod],
                        createdAt = formatDateTime(row[Sales.createdAt])
                    )
                }
            ),
            inventory = InventoryReportSection(
                totalProducts = inventory.totalProducts,
                totalStockQuantity = inventory.totalStock,
                inventoryValue = inventory.inventoryValue.toDouble(),
                lowStockCount = inventory.lowStockCount
            ),
            purchases = PurchaseReportSection(
                purchaseOrderCount = purchaseCount,
                pendingCount = pending,
                receivedCount = received,
                purchasingTotal = purchasingTotal.toDouble(),
                recentPurchaseOrders = loadRecentPurchaseOrders(10)
            )
        )
    }

    /**
     * Aggregates inventory using SQL COUNT/SUM where possible, and only reads
     * cost/stock columns for inventory value (not full product rows / image URLs).
     */
    private fun loadInventoryStats(previewLimit: Int): InventoryStats {
        val totalProducts = Products.selectAll().count().toInt()
        val stockSum = Products.stockLevel.sum()
        val totalStock = Products
            .select(stockSum)
            .singleOrNull()
            ?.get(stockSum)
            ?: 0

        var inventoryValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        Products
            .select(Products.costPrice, Products.stockLevel)
            .forEach { row ->
                val stock = row[Products.stockLevel]
                inventoryValue = inventoryValue.add(
                    row[Products.costPrice]
                        .multiply(BigDecimal.valueOf(stock.toLong()))
                        .setScale(2, RoundingMode.HALF_UP)
                )
            }

        val lowStockCount = Products
            .selectAll()
            .where { Products.stockLevel lessEq Products.minStockLevel }
            .count()
            .toInt()

        val lowStockPreview = if (previewLimit <= 0) {
            emptyList()
        } else {
            Products
                .selectAll()
                .where { Products.stockLevel lessEq Products.minStockLevel }
                .orderBy(Products.stockLevel, SortOrder.ASC)
                .limit(previewLimit)
                .map { row ->
                    DashboardLowStockItem(
                        id = row[Products.id],
                        name = row[Products.name],
                        stockLevel = row[Products.stockLevel],
                        minStockLevel = row[Products.minStockLevel]
                    )
                }
        }

        return InventoryStats(totalProducts, totalStock, inventoryValue, lowStockCount, lowStockPreview)
    }

    private fun loadWeeklySales(): List<WeeklySalesDay> {
        val today = LocalDate.now()
        val weekStart = today.minusDays(6).atStartOfDay()
        val weekEnd = today.plusDays(1).atStartOfDay()

        val sales = Sales
            .select(Sales.createdAt, Sales.totalAmount)
            .where { (Sales.createdAt greaterEq weekStart) and (Sales.createdAt less weekEnd) }
            .toList()

        val totalsByDate = linkedMapOf<LocalDate, BigDecimal>()
        val countsByDate = linkedMapOf<LocalDate, Int>()
        for (i in 6 downTo 0) {
            val day = today.minusDays(i.toLong())
            totalsByDate[day] = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            countsByDate[day] = 0
        }

        for (sale in sales) {
            val day = sale[Sales.createdAt].toLocalDate()
            if (totalsByDate.containsKey(day)) {
                totalsByDate[day] = totalsByDate[day]!!.add(sale[Sales.totalAmount])
                countsByDate[day] = countsByDate[day]!! + 1
            }
        }

        return totalsByDate.map { (day, total) ->
            WeeklySalesDay(
                date = day.toString(),
                label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                totalAmount = total.toDouble(),
                salesCount = countsByDate[day] ?: 0
            )
        }
    }

    private fun loadRecentSales(limit: Int): List<DashboardSaleItem> =
        Sales
            .selectAll()
            .orderBy(Sales.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                DashboardSaleItem(
                    id = row[Sales.id],
                    totalAmount = row[Sales.totalAmount].toDouble(),
                    paymentMethod = row[Sales.paymentMethod],
                    createdAt = formatDateTime(row[Sales.createdAt])
                )
            }

    private fun loadRecentPurchaseOrders(limit: Int): List<DashboardPurchaseOrderItem> =
        PurchaseOrders
            .leftJoin(Suppliers)
            .selectAll()
            .orderBy(PurchaseOrders.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                DashboardPurchaseOrderItem(
                    id = row[PurchaseOrders.id],
                    supplierId = row[PurchaseOrders.supplierId],
                    supplierName = row.getOrNull(Suppliers.name),
                    totalAmount = row[PurchaseOrders.totalAmount].toDouble(),
                    status = row[PurchaseOrders.status],
                    createdAt = formatDateTime(row[PurchaseOrders.createdAt])
                )
            }

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private data class InventoryStats(
        val totalProducts: Int,
        val totalStock: Int,
        val inventoryValue: BigDecimal,
        val lowStockCount: Int,
        val lowStockPreview: List<DashboardLowStockItem>
    )
}

/** Resolve a simple report range label into [start, end). */
fun resolveReportRange(range: String): Triple<LocalDateTime, LocalDateTime, String> {
    val today = LocalDate.now()
    val end = today.plusDays(1).atStartOfDay()
    return when (range.lowercase().trim()) {
        "today" -> Triple(today.atStartOfDay(), end, "today")
        "7d", "last7days", "last_7_days" ->
            Triple(today.minusDays(6).atStartOfDay(), end, "7d")
        "30d", "last30days", "last_30_days" ->
            Triple(today.minusDays(29).atStartOfDay(), end, "30d")
        else ->
            Triple(today.minusDays(6).atStartOfDay(), end, "7d")
    }
}

/**
 * Resolve an inclusive calendar date range from ISO-8601 dates (yyyy-MM-dd).
 * End is exclusive at the start of the day after [toDate].
 */
fun resolveCustomReportRange(fromDate: String, toDate: String): Triple<LocalDateTime, LocalDateTime, String> {
    val from = try {
        LocalDate.parse(fromDate.trim())
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid from date. Use yyyy-MM-dd.")
    }
    val to = try {
        LocalDate.parse(toDate.trim())
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid to date. Use yyyy-MM-dd.")
    }
    if (to.isBefore(from)) {
        throw IllegalArgumentException("End date must be on or after start date.")
    }
    val label = "${from} to ${to}"
    return Triple(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), label)
}
