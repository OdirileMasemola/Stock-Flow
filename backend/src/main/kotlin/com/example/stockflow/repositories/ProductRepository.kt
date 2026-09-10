package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.Categories
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.ProductResponse
import com.example.stockflow.models.Products
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.UpdateProductRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode

interface ProductRepository {
    suspend fun getAllProducts(): List<ProductResponse>
    suspend fun getProductById(id: Int): ProductResponse?
    suspend fun createProduct(request: CreateProductRequest): ProductResponse
    suspend fun updateProduct(id: Int, request: UpdateProductRequest): ProductResponse?
    suspend fun deleteProduct(id: Int): Boolean
    suspend fun findBySku(sku: String): ProductResponse?
    suspend fun categoryExists(categoryId: Int): Boolean
    suspend fun supplierExists(supplierId: Int): Boolean
}

class ProductRepositoryImpl : ProductRepository {

    override suspend fun getAllProducts(): List<ProductResponse> = dbQuery {
        // Left-join categories so we can show the category name in the inventory list.
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .orderBy(Products.name)
            .map { toProductResponse(it) }
    }

    override suspend fun getProductById(id: Int): ProductResponse? = dbQuery {
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { Products.id eq id }
            .map { toProductResponse(it) }
            .singleOrNull()
    }

    override suspend fun createProduct(request: CreateProductRequest): ProductResponse = dbQuery {
        val insertStatement = Products.insert {
            it[name] = request.name.trim()
            it[sku] = normalizeSku(request.sku)
            it[costPrice] = toMoney(request.costPrice)
            it[sellingPrice] = toMoney(request.sellingPrice)
            it[stockLevel] = request.stockLevel
            it[minStockLevel] = request.minStockLevel
            it[categoryId] = request.categoryId
            it[supplierId] = request.supplierId
        }

        val newId = insertStatement.resultedValues?.first()?.get(Products.id)
            ?: throw RuntimeException("Failed to create product")

        // Re-read with category join so the response includes categoryName.
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { Products.id eq newId }
            .map { toProductResponse(it) }
            .single()
    }

    override suspend fun updateProduct(id: Int, request: UpdateProductRequest): ProductResponse? = dbQuery {
        val updated = Products.update({ Products.id eq id }) {
            it[name] = request.name.trim()
            it[sku] = normalizeSku(request.sku)
            it[costPrice] = toMoney(request.costPrice)
            it[sellingPrice] = toMoney(request.sellingPrice)
            it[stockLevel] = request.stockLevel
            it[minStockLevel] = request.minStockLevel
            it[categoryId] = request.categoryId
            it[supplierId] = request.supplierId
        }

        if (updated == 0) {
            return@dbQuery null
        }

        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { Products.id eq id }
            .map { toProductResponse(it) }
            .singleOrNull()
    }

    override suspend fun deleteProduct(id: Int): Boolean = dbQuery {
        Products.deleteWhere { Products.id eq id } > 0
    }

    override suspend fun findBySku(sku: String): ProductResponse? = dbQuery {
        val normalized = sku.trim()
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { Products.sku eq normalized }
            .map { toProductResponse(it) }
            .singleOrNull()
    }

    override suspend fun categoryExists(categoryId: Int): Boolean = dbQuery {
        Categories.selectAll().where { Categories.id eq categoryId }.count() > 0
    }

    override suspend fun supplierExists(supplierId: Int): Boolean = dbQuery {
        Suppliers.selectAll().where { Suppliers.id eq supplierId }.count() > 0
    }

    private fun toProductResponse(row: ResultRow) = ProductResponse(
        id = row[Products.id],
        name = row[Products.name],
        sku = row[Products.sku],
        costPrice = row[Products.costPrice].toDouble(),
        sellingPrice = row[Products.sellingPrice].toDouble(),
        stockLevel = row[Products.stockLevel],
        minStockLevel = row[Products.minStockLevel],
        categoryId = row[Products.categoryId],
        // categoryName is null when the join did not match a category row
        categoryName = row.getOrNull(Categories.name),
        supplierId = row[Products.supplierId]
    )

    /** Blank SKUs are stored as null so they do not collide on the unique index. */
    private fun normalizeSku(sku: String?): String? =
        sku?.trim()?.takeIf { it.isNotEmpty() }

    private fun toMoney(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
}
