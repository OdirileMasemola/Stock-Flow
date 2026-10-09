package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.AccountClosure
import com.example.stockflow.models.Businesses
import com.example.stockflow.models.DeviceTokens
import com.example.stockflow.models.Products
import com.example.stockflow.models.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class AccountSnapshot(
    val id: Int,
    val closed: Boolean,
    val profileImageUrl: String?,
    val businessId: Int?,
    val businessImageUrl: String?
)

/**
 * PostgreSQL changes for closing the authenticated account.
 * Sales, sale items, products, categories, suppliers and purchase orders are not modified.
 */
interface AccountDeletionStore {
    suspend fun load(userId: Int): AccountSnapshot?
    suspend fun close(userId: Int)
    suspend fun imageReferencedElsewhere(url: String, userId: Int): Boolean
}

class AccountDeletionRepository : AccountDeletionStore {
    override suspend fun load(userId: Int): AccountSnapshot? = dbQuery {
        val row = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@dbQuery null
        val business = Businesses.selectAll().where { Businesses.userId eq userId }.singleOrNull()
        val id = row[Users.id]
        AccountSnapshot(
            id = id,
            closed = AccountClosure.isClosed(id, row[Users.username], row[Users.email]),
            profileImageUrl = row[Users.profileImageUrl],
            businessId = business?.get(Businesses.id),
            businessImageUrl = business?.get(Businesses.imageUrl)
        )
    }

    override suspend fun close(userId: Int): Unit = dbQuery {
        DeviceTokens.deleteWhere { DeviceTokens.userId eq userId }
        Businesses.deleteWhere { Businesses.userId eq userId }
        Users.update({ Users.id eq userId }) {
            it[username] = AccountClosure.username(userId)
            it[email] = AccountClosure.email(userId)
            it[fullName] = AccountClosure.FULL_NAME
            it[passwordHash] = null
            it[firebaseUid] = null
            it[profileImageUrl] = null
        }
    }

    override suspend fun imageReferencedElsewhere(url: String, userId: Int): Boolean = dbQuery {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return@dbQuery false
        val otherProfiles = Users.selectAll().where {
            (Users.profileImageUrl eq trimmed) and (Users.id neq userId)
        }.count()
        val otherBusinesses = Businesses.selectAll().where {
            (Businesses.imageUrl eq trimmed) and (Businesses.userId neq userId)
        }.count()
        val products = Products.selectAll().where { Products.imageUrl eq trimmed }.count()
        otherProfiles + otherBusinesses + products > 0
    }
}
