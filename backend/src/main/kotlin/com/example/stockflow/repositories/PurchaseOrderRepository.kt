package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.Products
import com.example.stockflow.models.PurchaseOrderItemResponse
import com.example.stockflow.models.PurchaseOrderItems
import com.example.stockflow.models.PurchaseOrderResponse
import com.example.stockflow.models.PurchaseOrders
import com.example.stockflow.models.Suppliers
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Prepared line after service merges quantities and validates costs. */
data class PurchaseOrderLineInput(
    val productId: Int,
    val quantity: Int,
    val unitCost: BigDecimal
)

interface PurchaseOrderRepository {
    suspend fun getAllPurchaseOrders(): List<PurchaseOrderResponse>
    suspend fun getPurchaseOrderById(id: Int): PurchaseOrderResponse?
    suspend fun createPurchaseOrder(
        supplierId: Int,
        expectedDeliveryDate: LocalDateTime?,
        lines: List<PurchaseOrderLineInput>
    ): PurchaseOrderResponse

    suspend fun updatePurchaseOrder(
        id: Int,
        supplierId: Int,
        expectedDeliveryDate: LocalDateTime?,
        lines: List<PurchaseOrderLineInput>
    ): PurchaseOrderResponse

    suspend fun receivePurchaseOrder(id: Int): PurchaseOrderResponse
    suspend fun supplierExists(supplierId: Int): Boolean
    suspend fun productExists(productId: Int): Boolean
}

class PurchaseOrderRepositoryImpl : PurchaseOrderRepository {

    companion object {
        const val STATUS_PENDING = "Pending"
        const val STATUS_RECEIVED = "Received"
    }

    override suspend fun getAllPurchaseOrders(): List<PurchaseOrderResponse> = dbQuery {
        PurchaseOrders
            .selectAll()
            .orderBy(PurchaseOrders.createdAt, SortOrder.DESC)
            .map { row ->
                val poId = row[PurchaseOrders.id]
                val supplierId = row[PurchaseOrders.supplierId]
                PurchaseOrderResponse(
                    id = poId,
                    supplierId = supplierId,
                    supplierName = supplierName(supplierId),
                    totalAmount = row[PurchaseOrders.totalAmount].toDouble(),
                    status = row[PurchaseOrders.status],
                    expectedDeliveryDate = row[PurchaseOrders.expectedDeliveryDate]?.let { formatDateTime(it) },
                    createdAt = formatDateTime(row[PurchaseOrders.createdAt]),
                    items = emptyList()
                )
            }
    }

    override suspend fun getPurchaseOrderById(id: Int): PurchaseOrderResponse? = dbQuery {
        loadPurchaseOrder(id)
    }

    override suspend fun createPurchaseOrder(
        supplierId: Int,
        expectedDeliveryDate: LocalDateTime?,
        lines: List<PurchaseOrderLineInput>
    ): PurchaseOrderResponse = dbQuery {
        val priced = priceLines(lines)
        val total = priced.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, line ->
            acc.add(line.subtotal)
        }
        val now = LocalDateTime.now()

        val insert = PurchaseOrders.insert {
            it[PurchaseOrders.supplierId] = supplierId
            it[totalAmount] = total
            it[status] = STATUS_PENDING
            it[PurchaseOrders.expectedDeliveryDate] = expectedDeliveryDate
            it[createdAt] = now
        }

        val poId = insert.resultedValues?.first()?.get(PurchaseOrders.id)
            ?: throw RuntimeException("Failed to create purchase order")

        insertItems(poId, priced)
        loadPurchaseOrder(poId)!!
    }

    override suspend fun updatePurchaseOrder(
        id: Int,
        supplierId: Int,
        expectedDeliveryDate: LocalDateTime?,
        lines: List<PurchaseOrderLineInput>
    ): PurchaseOrderResponse = dbQuery {
        val existing = PurchaseOrders
            .selectAll()
            .where { PurchaseOrders.id eq id }
            .singleOrNull()
            ?: throw NotFoundException("Purchase order not found")

        if (existing[PurchaseOrders.status] != STATUS_PENDING) {
            throw ConflictException("Only pending purchase orders can be updated")
        }

        val priced = priceLines(lines)
        val total = priced.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, line ->
            acc.add(line.subtotal)
        }

        PurchaseOrders.update({ PurchaseOrders.id eq id }) {
            it[PurchaseOrders.supplierId] = supplierId
            it[totalAmount] = total
            it[PurchaseOrders.expectedDeliveryDate] = expectedDeliveryDate
        }

        PurchaseOrderItems.deleteWhere { PurchaseOrderItems.purchaseOrderId eq id }
        insertItems(id, priced)
        loadPurchaseOrder(id)!!
    }

    override suspend fun receivePurchaseOrder(id: Int): PurchaseOrderResponse = dbQuery {
        val existing = PurchaseOrders
            .selectAll()
            .where { PurchaseOrders.id eq id }
            .singleOrNull()
            ?: throw NotFoundException("Purchase order not found")

        val status = existing[PurchaseOrders.status]
        if (status == STATUS_RECEIVED) {
            throw ConflictException("Purchase order has already been received")
        }
        if (status != STATUS_PENDING) {
            throw BadRequestException("Purchase order cannot be received in status: $status")
        }

        val items = PurchaseOrderItems
            .selectAll()
            .where { PurchaseOrderItems.purchaseOrderId eq id }
            .toList()

        if (items.isEmpty()) {
            throw BadRequestException("Purchase order has no items")
        }

        for (item in items) {
            val productId = item[PurchaseOrderItems.productId]
            val quantity = item[PurchaseOrderItems.quantity]

            val product = Products
                .selectAll()
                .where { Products.id eq productId }
                .singleOrNull()
                ?: throw NotFoundException("Product not found: id=$productId")

            val updated = Products.update({ Products.id eq productId }) {
                with(SqlExpressionBuilder) {
                    it[stockLevel] = stockLevel + quantity
                }
            }

            if (updated == 0) {
                throw NotFoundException("Product not found: ${product[Products.name]}")
            }
        }

        val updatedRows = PurchaseOrders.update({
            (PurchaseOrders.id eq id) and (PurchaseOrders.status eq STATUS_PENDING)
        }) {
            it[PurchaseOrders.status] = STATUS_RECEIVED
        }

        if (updatedRows == 0) {
            throw ConflictException("Purchase order has already been received")
        }

        loadPurchaseOrder(id)!!
    }

    override suspend fun supplierExists(supplierId: Int): Boolean = dbQuery {
        Suppliers.selectAll().where { Suppliers.id eq supplierId }.count() > 0
    }

    override suspend fun productExists(productId: Int): Boolean = dbQuery {
        Products.selectAll().where { Products.id eq productId }.count() > 0
    }

    private fun priceLines(lines: List<PurchaseOrderLineInput>): List<PricedLine> {
        val result = mutableListOf<PricedLine>()
        for (line in lines) {
            val product = Products
                .selectAll()
                .where { Products.id eq line.productId }
                .singleOrNull()
                ?: throw NotFoundException("Product not found: id=${line.productId}")

            val unitCost = line.unitCost.setScale(2, RoundingMode.HALF_UP)
            val subtotal = unitCost
                .multiply(BigDecimal.valueOf(line.quantity.toLong()))
                .setScale(2, RoundingMode.HALF_UP)

            result += PricedLine(
                productId = line.productId,
                productName = product[Products.name],
                quantity = line.quantity,
                unitCost = unitCost,
                subtotal = subtotal
            )
        }
        return result
    }

    private fun insertItems(poId: Int, lines: List<PricedLine>) {
        for (line in lines) {
            PurchaseOrderItems.insert {
                it[purchaseOrderId] = poId
                it[productId] = line.productId
                it[quantity] = line.quantity
                it[unitCost] = line.unitCost
                it[subtotal] = line.subtotal
            }
        }
    }

    private fun loadPurchaseOrder(id: Int): PurchaseOrderResponse? {
        val row = PurchaseOrders
            .selectAll()
            .where { PurchaseOrders.id eq id }
            .singleOrNull()
            ?: return null

        val supplierId = row[PurchaseOrders.supplierId]
        val items = PurchaseOrderItems
            .selectAll()
            .where { PurchaseOrderItems.purchaseOrderId eq id }
            .map { itemRow ->
                val productId = itemRow[PurchaseOrderItems.productId]
                val productName = Products
                    .selectAll()
                    .where { Products.id eq productId }
                    .singleOrNull()
                    ?.get(Products.name)

                PurchaseOrderItemResponse(
                    id = itemRow[PurchaseOrderItems.id],
                    productId = productId,
                    productName = productName,
                    quantity = itemRow[PurchaseOrderItems.quantity],
                    unitCost = itemRow[PurchaseOrderItems.unitCost].toDouble(),
                    subtotal = itemRow[PurchaseOrderItems.subtotal].toDouble()
                )
            }

        return PurchaseOrderResponse(
            id = row[PurchaseOrders.id],
            supplierId = supplierId,
            supplierName = supplierName(supplierId),
            totalAmount = row[PurchaseOrders.totalAmount].toDouble(),
            status = row[PurchaseOrders.status],
            expectedDeliveryDate = row[PurchaseOrders.expectedDeliveryDate]?.let { formatDateTime(it) },
            createdAt = formatDateTime(row[PurchaseOrders.createdAt]),
            items = items
        )
    }

    private fun supplierName(supplierId: Int): String? =
        Suppliers
            .selectAll()
            .where { Suppliers.id eq supplierId }
            .singleOrNull()
            ?.get(Suppliers.name)

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private data class PricedLine(
        val productId: Int,
        val productName: String,
        val quantity: Int,
        val unitCost: BigDecimal,
        val subtotal: BigDecimal
    )
}

/** Parse optional expected delivery date from client ISO local date-time or date. */
fun parseOptionalDateTime(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    return try {
        LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: DateTimeParseException) {
        try {
            java.time.LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
        } catch (_: DateTimeParseException) {
            throw BadRequestException("Invalid expected delivery date format")
        }
    }
}
