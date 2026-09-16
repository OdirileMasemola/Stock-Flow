package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.LowStockCrossing
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.Products
import com.example.stockflow.models.SaleItemResponse
import com.example.stockflow.models.SaleItems
import com.example.stockflow.models.SaleResponse
import com.example.stockflow.models.Sales
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Prepared line after service merges quantities.
 * [unitPrice] and [subtotal] are filled inside the repository transaction from DB prices.
 */
data class SaleLineInput(
    val productId: Int,
    val quantity: Int
)

data class CreateSaleResult(
    val sale: SaleResponse,
    /** Products that crossed into low stock during this sale (for FCM alerts). */
    val lowStockCrossings: List<LowStockCrossing> = emptyList()
)

interface SaleRepository {
    suspend fun createSale(
        userId: Int,
        paymentMethod: String,
        lines: List<SaleLineInput>
    ): CreateSaleResult

    suspend fun getAllSales(): List<SaleResponse>
    suspend fun getSaleById(id: Int): SaleResponse?
}

class SaleRepositoryImpl : SaleRepository {

    override suspend fun createSale(
        userId: Int,
        paymentMethod: String,
        lines: List<SaleLineInput>
    ): CreateSaleResult = dbQuery {
        // Entire sale + stock deduction runs in one suspended transaction.
        val pricedLines = mutableListOf<PricedLine>()
        var total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

        for (line in lines) {
            val product = Products
                .selectAll()
                .where { Products.id eq line.productId }
                .singleOrNull()
                ?: throw NotFoundException("Product not found: id=${line.productId}")

            val name = product[Products.name]
            val stock = product[Products.stockLevel]
            val minStock = product[Products.minStockLevel]
            val unitPrice = product[Products.sellingPrice].setScale(2, RoundingMode.HALF_UP)

            if (line.quantity > stock) {
                throw BadRequestException("Insufficient stock for product: $name")
            }

            val subtotal = unitPrice
                .multiply(BigDecimal.valueOf(line.quantity.toLong()))
                .setScale(2, RoundingMode.HALF_UP)

            pricedLines += PricedLine(
                productId = line.productId,
                productName = name,
                quantity = line.quantity,
                previousStock = stock,
                minStockLevel = minStock,
                unitPrice = unitPrice,
                subtotal = subtotal
            )
            total = total.add(subtotal)
        }

        val now = LocalDateTime.now()
        val saleInsert = Sales.insert {
            it[Sales.userId] = userId
            it[totalAmount] = total
            it[Sales.paymentMethod] = paymentMethod
            it[createdAt] = now
        }

        val saleId = saleInsert.resultedValues?.first()?.get(Sales.id)
            ?: throw RuntimeException("Failed to create sale")

        val itemResponses = mutableListOf<SaleItemResponse>()

        for (line in pricedLines) {
            // Conditional update prevents oversell under concurrent sales.
            val updated = Products.update({
                (Products.id eq line.productId) and (Products.stockLevel greaterEq line.quantity)
            }) {
                with(SqlExpressionBuilder) {
                    it[stockLevel] = stockLevel - line.quantity
                }
            }

            if (updated == 0) {
                throw BadRequestException("Insufficient stock for product: ${line.productName}")
            }

            val itemInsert = SaleItems.insert {
                it[SaleItems.saleId] = saleId
                it[productId] = line.productId
                it[quantity] = line.quantity
                it[unitPrice] = line.unitPrice
                it[subtotal] = line.subtotal
            }

            val itemId = itemInsert.resultedValues?.first()?.get(SaleItems.id)
                ?: throw RuntimeException("Failed to create sale item")

            itemResponses += SaleItemResponse(
                id = itemId,
                productId = line.productId,
                productName = line.productName,
                quantity = line.quantity,
                unitPrice = line.unitPrice.toDouble(),
                subtotal = line.subtotal.toDouble()
            )
        }

        val crossings = pricedLines.map { line ->
            LowStockCrossing(
                productId = line.productId,
                productName = line.productName,
                previousStock = line.previousStock,
                currentStock = line.previousStock - line.quantity,
                minStockLevel = line.minStockLevel
            )
        }

        CreateSaleResult(
            sale = SaleResponse(
                id = saleId,
                userId = userId,
                totalAmount = total.toDouble(),
                paymentMethod = paymentMethod,
                createdAt = formatDateTime(now),
                items = itemResponses
            ),
            lowStockCrossings = crossings
        )
    }

    override suspend fun getAllSales(): List<SaleResponse> = dbQuery {
        Sales
            .selectAll()
            .orderBy(Sales.createdAt, SortOrder.DESC)
            .map { row ->
                val saleId = row[Sales.id]
                SaleResponse(
                    id = saleId,
                    userId = row[Sales.userId],
                    totalAmount = row[Sales.totalAmount].toDouble(),
                    paymentMethod = row[Sales.paymentMethod],
                    createdAt = formatDateTime(row[Sales.createdAt]),
                    items = emptyList()
                )
            }
    }

    override suspend fun getSaleById(id: Int): SaleResponse? = dbQuery {
        val saleRow = Sales
            .selectAll()
            .where { Sales.id eq id }
            .singleOrNull()
            ?: return@dbQuery null

        val items = SaleItems
            .selectAll()
            .where { SaleItems.saleId eq id }
            .map { itemRow ->
                val productId = itemRow[SaleItems.productId]
                val productName = Products
                    .selectAll()
                    .where { Products.id eq productId }
                    .singleOrNull()
                    ?.get(Products.name)

                SaleItemResponse(
                    id = itemRow[SaleItems.id],
                    productId = productId,
                    productName = productName,
                    quantity = itemRow[SaleItems.quantity],
                    unitPrice = itemRow[SaleItems.unitPrice].toDouble(),
                    subtotal = itemRow[SaleItems.subtotal].toDouble()
                )
            }

        SaleResponse(
            id = saleRow[Sales.id],
            userId = saleRow[Sales.userId],
            totalAmount = saleRow[Sales.totalAmount].toDouble(),
            paymentMethod = saleRow[Sales.paymentMethod],
            createdAt = formatDateTime(saleRow[Sales.createdAt]),
            items = items
        )
    }

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private data class PricedLine(
        val productId: Int,
        val productName: String,
        val quantity: Int,
        val previousStock: Int,
        val minStockLevel: Int,
        val unitPrice: BigDecimal,
        val subtotal: BigDecimal
    )
}
