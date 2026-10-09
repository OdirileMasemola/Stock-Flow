package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.Categories
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.ProductResponse
import com.example.stockflow.models.Products
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.UpdateProductRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode

/** Every method is scoped to [ownerUserId]; rows from other shops behave as if they do not exist. */
interface ProductRepository {
    suspend fun getAllProducts(ownerUserId: Int): List<ProductResponse>
    suspend fun getLowStockProducts(ownerUserId: Int): List<ProductResponse>
    suspend fun getProductById(id: Int, ownerUserId: Int): ProductResponse?
    suspend fun createProduct(request: CreateProductRequest, ownerUserId: Int): ProductResponse
    suspend fun updateProduct(id: Int, request: UpdateProductRequest, ownerUserId: Int): ProductResponse?
    suspend fun deleteProduct(id: Int, ownerUserId: Int): Boolean
    suspend fun findBySku(sku: String, ownerUserId: Int): ProductResponse?
    suspend fun categoryExists(categoryId: Int): Boolean
    suspend fun supplierExists(supplierId: Int, ownerUserId: Int): Boolean
    /** True when any product in any shop still uses [imageUrl]. Guards shared photo files from deletion. */
    suspend fun isImageInUse(imageUrl: String): Boolean
}

class ProductRepositoryImpl : ProductRepository {

    override suspend fun getAllProducts(ownerUserId: Int): List<ProductResponse> = dbQuery {
        // Left-join categories so we can show the category name in the inventory list.
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { Products.ownerUserId eq ownerUserId }
            .orderBy(Products.name)
            .map { toProductResponse(it) }
    }

    /**
     * Products at or below their minimum stock threshold (`stockLevel <= minStockLevel`).
     * Ordered by stock ascending so out-of-stock items surface first.
     */
    override suspend fun getLowStockProducts(ownerUserId: Int): List<ProductResponse> = dbQuery {
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where {
                (Products.ownerUserId eq ownerUserId) and (Products.stockLevel lessEq Products.minStockLevel)
            }
            .orderBy(Products.stockLevel to SortOrder.ASC, Products.name to SortOrder.ASC)
            .map { toProductResponse(it) }
    }

    override suspend fun getProductById(id: Int, ownerUserId: Int): ProductResponse? = dbQuery {
        loadOwned(id, ownerUserId)
    }

    override suspend fun createProduct(request: CreateProductRequest, ownerUserId: Int): ProductResponse = dbQuery {
        val insertStatement = Products.insert {
            it[name] = request.name.trim()
            it[sku] = normalizeSku(request.sku)
            it[costPrice] = toMoney(request.costPrice)
            it[sellingPrice] = toMoney(request.sellingPrice)
            it[stockLevel] = request.stockLevel
            it[minStockLevel] = request.minStockLevel
            it[categoryId] = request.categoryId
            it[supplierId] = request.supplierId
            it[imageUrl] = normalizeImageUrl(request.imageUrl)
            it[Products.ownerUserId] = ownerUserId
        }

        val newId = insertStatement.resultedValues?.first()?.get(Products.id)
            ?: throw RuntimeException("Failed to create product")

        // Re-read with category join so the response includes categoryName.
        loadOwned(newId, ownerUserId)!!
    }

    override suspend fun updateProduct(
        id: Int,
        request: UpdateProductRequest,
        ownerUserId: Int
    ): ProductResponse? = dbQuery {
        val updated = Products.update({ (Products.id eq id) and (Products.ownerUserId eq ownerUserId) }) {
            it[name] = request.name.trim()
            it[sku] = normalizeSku(request.sku)
            it[costPrice] = toMoney(request.costPrice)
            it[sellingPrice] = toMoney(request.sellingPrice)
            it[stockLevel] = request.stockLevel
            it[minStockLevel] = request.minStockLevel
            it[categoryId] = request.categoryId
            it[supplierId] = request.supplierId
            it[imageUrl] = normalizeImageUrl(request.imageUrl)
        }

        if (updated == 0) {
            return@dbQuery null
        }

        loadOwned(id, ownerUserId)
    }

    override suspend fun deleteProduct(id: Int, ownerUserId: Int): Boolean = dbQuery {
        Products.deleteWhere { (Products.id eq id) and (Products.ownerUserId eq ownerUserId) } > 0
    }

    override suspend fun findBySku(sku: String, ownerUserId: Int): ProductResponse? = dbQuery {
        val normalized = sku.trim()
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { (Products.ownerUserId eq ownerUserId) and (Products.sku eq normalized) }
            .map { toProductResponse(it) }
            .singleOrNull()
    }

    override suspend fun categoryExists(categoryId: Int): Boolean = dbQuery {
        Categories.selectAll().where { Categories.id eq categoryId }.count() > 0
    }

    override suspend fun supplierExists(supplierId: Int, ownerUserId: Int): Boolean = dbQuery {
        Suppliers.selectAll()
            .where { (Suppliers.id eq supplierId) and (Suppliers.ownerUserId eq ownerUserId) }
            .count() > 0
    }

    override suspend fun isImageInUse(imageUrl: String): Boolean = dbQuery {
        Products.selectAll().where { Products.imageUrl eq imageUrl.trim() }.count() > 0
    }

    private fun loadOwned(id: Int, ownerUserId: Int): ProductResponse? =
        Products
            .leftJoin(Categories, { Products.categoryId }, { Categories.id })
            .selectAll()
            .where { (Products.id eq id) and (Products.ownerUserId eq ownerUserId) }
            .map { toProductResponse(it) }
            .singleOrNull()

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
        supplierId = row[Products.supplierId],
        imageUrl = row[Products.imageUrl]
    )

    /** Blank SKUs are stored as null so they do not collide on the unique index. */
    private fun normalizeSku(sku: String?): String? =
        sku?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeImageUrl(imageUrl: String?): String? =
        imageUrl?.trim()?.takeIf { it.isNotEmpty() }

    private fun toMoney(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
}
