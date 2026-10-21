package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.AccountClosure
import com.example.stockflow.models.EmployeeResponse
import com.example.stockflow.models.Products
import com.example.stockflow.models.PurchaseOrders
import com.example.stockflow.models.Roles
import com.example.stockflow.models.Sales
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.Users
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class EmployeeAccount(
    val id: Int,
    val fullName: String,
    val username: String,
    val email: String,
    val role: String,
    val profileImageUrl: String?,
    val shopOwnerUserId: Int?,
    val closed: Boolean
) {
    fun toResponse() = EmployeeResponse(id, fullName, username, email, role, profileImageUrl)
}

interface EmployeeStore {
    suspend fun listForShop(ownerUserId: Int): List<EmployeeAccount>
    suspend fun findById(userId: Int): EmployeeAccount?
    suspend fun findByEmail(email: String): EmployeeAccount?
    /** True when the account already owns products, sales, suppliers or purchase orders. */
    suspend fun hasOwnShopRecords(userId: Int): Boolean
    suspend fun roleIdByName(name: String): Int?
    /** Sets shop + role only if the row is still unlinked (or linked to [ownerUserId]). */
    suspend fun link(userId: Int, ownerUserId: Int, roleId: Int): Boolean
    suspend fun updateRole(userId: Int, ownerUserId: Int, roleId: Int): Boolean
    suspend fun unlink(userId: Int, ownerUserId: Int): Boolean
}

class EmployeeStoreImpl : EmployeeStore {
    private val joined get() = Users.innerJoin(Roles, { Users.roleId }, { Roles.id })

    private fun org.jetbrains.exposed.sql.ResultRow.toAccount() = EmployeeAccount(
        id = this[Users.id],
        fullName = this[Users.fullName],
        username = this[Users.username],
        email = this[Users.email],
        role = this[Roles.name],
        profileImageUrl = this[Users.profileImageUrl],
        shopOwnerUserId = this[Users.shopOwnerUserId],
        closed = AccountClosure.isClosed(this[Users.id], this[Users.username], this[Users.email])
    )

    override suspend fun listForShop(ownerUserId: Int): List<EmployeeAccount> = dbQuery {
        joined.selectAll()
            .where { Users.shopOwnerUserId eq ownerUserId }
            .orderBy(Users.fullName to SortOrder.ASC)
            .map { it.toAccount() }
            .filterNot { it.closed }
    }

    override suspend fun findById(userId: Int): EmployeeAccount? = dbQuery {
        joined.selectAll().where { Users.id eq userId }.singleOrNull()?.toAccount()
    }

    override suspend fun findByEmail(email: String): EmployeeAccount? = dbQuery {
        joined.selectAll()
            .where { Users.email.lowerCase() eq email.lowercase() }
            .singleOrNull()
            ?.toAccount()
    }

    override suspend fun hasOwnShopRecords(userId: Int): Boolean = dbQuery {
        Products.selectAll().where { Products.ownerUserId eq userId }.limit(1).any() ||
            Sales.selectAll().where { Sales.ownerUserId eq userId }.limit(1).any() ||
            Suppliers.selectAll().where { Suppliers.ownerUserId eq userId }.limit(1).any() ||
            PurchaseOrders.selectAll().where { PurchaseOrders.ownerUserId eq userId }.limit(1).any()
    }

    override suspend fun roleIdByName(name: String): Int? = dbQuery {
        Roles.selectAll().where { Roles.name.lowerCase() eq name.lowercase() }.singleOrNull()?.get(Roles.id)
    }

    override suspend fun link(userId: Int, ownerUserId: Int, roleId: Int): Boolean = dbQuery {
        // Conditional update: never steals an account another shop linked in the meantime.
        Users.update({
            (Users.id eq userId) and (Users.shopOwnerUserId.isNull() or (Users.shopOwnerUserId eq ownerUserId))
        }) {
            it[Users.shopOwnerUserId] = ownerUserId
            it[Users.roleId] = roleId
        } > 0
    }

    override suspend fun updateRole(userId: Int, ownerUserId: Int, roleId: Int): Boolean = dbQuery {
        Users.update({ (Users.id eq userId) and (Users.shopOwnerUserId eq ownerUserId) }) {
            it[Users.roleId] = roleId
        } > 0
    }

    override suspend fun unlink(userId: Int, ownerUserId: Int): Boolean = dbQuery {
        Users.update({ (Users.id eq userId) and (Users.shopOwnerUserId eq ownerUserId) }) {
            it[shopOwnerUserId] = null
        } > 0
    }
}
