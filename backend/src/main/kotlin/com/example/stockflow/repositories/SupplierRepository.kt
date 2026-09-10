package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.CreateSupplierRequest
import com.example.stockflow.models.Products
import com.example.stockflow.models.PurchaseOrders
import com.example.stockflow.models.SupplierResponse
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.UpdateSupplierRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

interface SupplierRepository {
    suspend fun getAllSuppliers(): List<SupplierResponse>
    suspend fun getSupplierById(id: Int): SupplierResponse?
    suspend fun createSupplier(request: CreateSupplierRequest): SupplierResponse
    suspend fun updateSupplier(id: Int, request: UpdateSupplierRequest): SupplierResponse?
    suspend fun deleteSupplier(id: Int): Boolean
    suspend fun findByName(name: String): SupplierResponse?
    suspend fun isReferencedByProducts(supplierId: Int): Boolean
    suspend fun isReferencedByPurchaseOrders(supplierId: Int): Boolean
}

class SupplierRepositoryImpl : SupplierRepository {

    override suspend fun getAllSuppliers(): List<SupplierResponse> = dbQuery {
        Suppliers
            .selectAll()
            .orderBy(Suppliers.name)
            .map { toSupplierResponse(it) }
    }

    override suspend fun getSupplierById(id: Int): SupplierResponse? = dbQuery {
        Suppliers
            .selectAll()
            .where { Suppliers.id eq id }
            .map { toSupplierResponse(it) }
            .singleOrNull()
    }

    override suspend fun createSupplier(request: CreateSupplierRequest): SupplierResponse = dbQuery {
        val insertStatement = Suppliers.insert {
            it[name] = request.name.trim()
            it[contactName] = normalizeOptional(request.contactName)
            it[phone] = normalizeOptional(request.phone)
            it[email] = normalizeOptional(request.email)?.lowercase()
            it[address] = normalizeOptional(request.address)
        }

        val newId = insertStatement.resultedValues?.first()?.get(Suppliers.id)
            ?: throw RuntimeException("Failed to create supplier")

        Suppliers
            .selectAll()
            .where { Suppliers.id eq newId }
            .map { toSupplierResponse(it) }
            .single()
    }

    override suspend fun updateSupplier(id: Int, request: UpdateSupplierRequest): SupplierResponse? = dbQuery {
        val updated = Suppliers.update({ Suppliers.id eq id }) {
            it[name] = request.name.trim()
            it[contactName] = normalizeOptional(request.contactName)
            it[phone] = normalizeOptional(request.phone)
            it[email] = normalizeOptional(request.email)?.lowercase()
            it[address] = normalizeOptional(request.address)
        }

        if (updated == 0) {
            return@dbQuery null
        }

        Suppliers
            .selectAll()
            .where { Suppliers.id eq id }
            .map { toSupplierResponse(it) }
            .singleOrNull()
    }

    override suspend fun deleteSupplier(id: Int): Boolean = dbQuery {
        Suppliers.deleteWhere { Suppliers.id eq id } > 0
    }

    override suspend fun findByName(name: String): SupplierResponse? = dbQuery {
        val normalized = name.trim()
        Suppliers
            .selectAll()
            .where { Suppliers.name eq normalized }
            .map { toSupplierResponse(it) }
            .singleOrNull()
    }

    override suspend fun isReferencedByProducts(supplierId: Int): Boolean = dbQuery {
        Products.selectAll().where { Products.supplierId eq supplierId }.count() > 0
    }

    override suspend fun isReferencedByPurchaseOrders(supplierId: Int): Boolean = dbQuery {
        PurchaseOrders.selectAll().where { PurchaseOrders.supplierId eq supplierId }.count() > 0
    }

    private fun toSupplierResponse(row: ResultRow) = SupplierResponse(
        id = row[Suppliers.id],
        name = row[Suppliers.name],
        contactName = row[Suppliers.contactName],
        phone = row[Suppliers.phone],
        email = row[Suppliers.email],
        address = row[Suppliers.address]
    )

    private fun normalizeOptional(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }
}
