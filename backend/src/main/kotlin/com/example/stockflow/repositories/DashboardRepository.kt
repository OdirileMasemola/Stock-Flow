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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
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
        val productRows = Products.selectAll().toList()
        var totalStock = 0
        var inventoryValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var lowStockCount = 0
        val lowStockPreview = mutableListOf<DashboardLowStockItem>()

        for (row in productRows) {
            val stock = row[Products.stockLevel]
            val min = row[Products.minStockLevel]
            val cost = row[Products.costPrice]
            totalStock += stock
            inventoryValue = inventoryValue.add(
                cost.multiply(BigDecimal.valueOf(stock.toLong())).setScale(2, RoundingMode.HALF_UP)
            )
            if (stock <= min) {
                lowStockCount++
                if (lowStockPreview.size < recentLimit) {
                    lowStockPreview += DashboardLowStockItem(
                        id = row[Products.id],
                        name = row[Products.name],
                        stockLevel = stock,
                        minStockLevel = min
                    )
                }
            }
        }

        val todayStart = LocalDate.now().atStartOfDay()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay()
        val todaySales = Sales
            .selectAll()
            .where { (Sales.createdAt greaterEq todayStart) and (Sales.createdAt less todayEnd) }
            .toList()

        var todayTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        for (sale in todaySales) {
            todayTotal = todayTotal.add(sale[Sales.totalAmount])
        }

        DashboardSummaryResponse(
            totalProducts = productRows.size,
            totalStockQuantity = totalStock,
            inventoryValue = inventoryValue.toDouble(),
            todaySalesTotal = todayTotal.toDouble(),
            todaySalesCount = todaySales.size,
            lowStockCount = lowStockCount,
            weeklySales = loadWeeklySales(),
            recentSales = loadRecentSales(recentLimit),
            recentPurchaseOrders = loadRecentPurchaseOrders(recentLimit),
            lowStockPreview = lowStockPreview
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

        val productRows = Products.selectAll().toList()
        var totalStock = 0
        var inventoryValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var lowStockCount = 0
        for (row in productRows) {
            val stock = row[Products.stockLevel]
            totalStock += stock
            inventoryValue = inventoryValue.add(
                row[Products.costPrice]
                    .multiply(BigDecimal.valueOf(stock.toLong()))
                    .setScale(2, RoundingMode.HALF_UP)
            )
            if (stock <= row[Products.minStockLevel]) {
                lowStockCount++
            }
        }

        val purchaseRows = PurchaseOrders.selectAll().toList()
        var pending = 0
        var received = 0
        var purchasingTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        for (po in purchaseRows) {
            purchasingTotal = purchasingTotal.add(po[PurchaseOrders.totalAmount])
            when (po[PurchaseOrders.status]) {
                "Pending" -> pending++
                "Received" -> received++
            }
        }

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
                totalProducts = productRows.size,
                totalStockQuantity = totalStock,
                inventoryValue = inventoryValue.toDouble(),
                lowStockCount = lowStockCount
            ),
            purchases = PurchaseReportSection(
                purchaseOrderCount = purchaseRows.size,
                pendingCount = pending,
                receivedCount = received,
                purchasingTotal = purchasingTotal.toDouble(),
                recentPurchaseOrders = loadRecentPurchaseOrders(10)
            )
        )
    }

    private fun loadWeeklySales(): List<WeeklySalesDay> {
        val today = LocalDate.now()
        val weekStart = today.minusDays(6).atStartOfDay()
        val weekEnd = today.plusDays(1).atStartOfDay()

        val sales = Sales
            .selectAll()
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
            .selectAll()
            .orderBy(PurchaseOrders.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                val supplierId = row[PurchaseOrders.supplierId]
                val supplierName = Suppliers
                    .selectAll()
                    .where { Suppliers.id eq supplierId }
                    .singleOrNull()
                    ?.get(Suppliers.name)

                DashboardPurchaseOrderItem(
                    id = row[PurchaseOrders.id],
                    supplierId = supplierId,
                    supplierName = supplierName,
                    totalAmount = row[PurchaseOrders.totalAmount].toDouble(),
                    status = row[PurchaseOrders.status],
                    createdAt = formatDateTime(row[PurchaseOrders.createdAt])
                )
            }

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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
