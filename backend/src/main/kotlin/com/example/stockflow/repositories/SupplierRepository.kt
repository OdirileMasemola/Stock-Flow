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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Every lookup is scoped to [ownerUserId]; suppliers of other shops behave as if they do not exist. */
interface SupplierRepository {
    suspend fun getAllSuppliers(ownerUserId: Int): List<SupplierResponse>
    suspend fun getSupplierById(id: Int, ownerUserId: Int): SupplierResponse?
    suspend fun createSupplier(request: CreateSupplierRequest, ownerUserId: Int): SupplierResponse
    suspend fun updateSupplier(id: Int, request: UpdateSupplierRequest, ownerUserId: Int): SupplierResponse?
    suspend fun deleteSupplier(id: Int, ownerUserId: Int): Boolean
    suspend fun findByName(name: String, ownerUserId: Int): SupplierResponse?
    suspend fun isReferencedByProducts(supplierId: Int): Boolean
    suspend fun isReferencedByPurchaseOrders(supplierId: Int): Boolean
}

class SupplierRepositoryImpl : SupplierRepository {

    override suspend fun getAllSuppliers(ownerUserId: Int): List<SupplierResponse> = dbQuery {
        Suppliers
            .selectAll()
            .where { Suppliers.ownerUserId eq ownerUserId }
            .orderBy(Suppliers.name)
            .map { toSupplierResponse(it) }
    }

    override suspend fun getSupplierById(id: Int, ownerUserId: Int): SupplierResponse? = dbQuery {
        loadOwned(id, ownerUserId)
    }

    override suspend fun createSupplier(request: CreateSupplierRequest, ownerUserId: Int): SupplierResponse = dbQuery {
        val insertStatement = Suppliers.insert {
            it[name] = request.name.trim()
            it[contactName] = normalizeOptional(request.contactName)
            it[phone] = normalizeOptional(request.phone)
            it[email] = normalizeOptional(request.email)?.lowercase()
            it[address] = normalizeOptional(request.address)
            it[Suppliers.ownerUserId] = ownerUserId
        }

        val newId = insertStatement.resultedValues?.first()?.get(Suppliers.id)
            ?: throw RuntimeException("Failed to create supplier")

        loadOwned(newId, ownerUserId)!!
    }

    override suspend fun updateSupplier(
        id: Int,
        request: UpdateSupplierRequest,
        ownerUserId: Int
    ): SupplierResponse? = dbQuery {
        val updated = Suppliers.update({ (Suppliers.id eq id) and (Suppliers.ownerUserId eq ownerUserId) }) {
            it[name] = request.name.trim()
            it[contactName] = normalizeOptional(request.contactName)
            it[phone] = normalizeOptional(request.phone)
            it[email] = normalizeOptional(request.email)?.lowercase()
            it[address] = normalizeOptional(request.address)
        }

        if (updated == 0) {
            return@dbQuery null
        }

        loadOwned(id, ownerUserId)
    }

    override suspend fun deleteSupplier(id: Int, ownerUserId: Int): Boolean = dbQuery {
        Suppliers.deleteWhere { (Suppliers.id eq id) and (Suppliers.ownerUserId eq ownerUserId) } > 0
    }

    override suspend fun findByName(name: String, ownerUserId: Int): SupplierResponse? = dbQuery {
        val normalized = name.trim()
        Suppliers
            .selectAll()
            .where { (Suppliers.ownerUserId eq ownerUserId) and (Suppliers.name eq normalized) }
            .map { toSupplierResponse(it) }
            .singleOrNull()
    }

    override suspend fun isReferencedByProducts(supplierId: Int): Boolean = dbQuery {
        Products.selectAll().where { Products.supplierId eq supplierId }.count() > 0
    }

    override suspend fun isReferencedByPurchaseOrders(supplierId: Int): Boolean = dbQuery {
        PurchaseOrders.selectAll().where { PurchaseOrders.supplierId eq supplierId }.count() > 0
    }

    private fun loadOwned(id: Int, ownerUserId: Int): SupplierResponse? =
        Suppliers
            .selectAll()
            .where { (Suppliers.id eq id) and (Suppliers.ownerUserId eq ownerUserId) }
            .map { toSupplierResponse(it) }
            .singleOrNull()

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
