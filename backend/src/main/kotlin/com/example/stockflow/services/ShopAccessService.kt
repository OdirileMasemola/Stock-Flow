package com.example.stockflow.services

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.AccountClosure
import com.example.stockflow.models.ForbiddenException
import com.example.stockflow.models.Roles
import com.example.stockflow.models.UnauthorizedException
import com.example.stockflow.models.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll

/**
 * The shop a request acts on. [ownerUserId] always comes from the server, never from the client.
 * Each account is its own shop until staff membership exists.
 */
data class ShopContext(
    val userId: Int,
    val ownerUserId: Int,
    val role: String
)

data class ShopMember(
    val userId: Int,
    val roleName: String,
    val closed: Boolean
)

interface ShopMemberStore {
    suspend fun findMember(userId: Int): ShopMember?
}

class ShopMemberStoreImpl : ShopMemberStore {
    override suspend fun findMember(userId: Int): ShopMember? = dbQuery {
        Users.innerJoin(Roles, { Users.roleId }, { Roles.id })
            .selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.let { row ->
                ShopMember(
                    userId = row[Users.id],
                    roleName = row[Roles.name],
                    closed = AccountClosure.isClosed(row[Users.id], row[Users.username], row[Users.email])
                )
            }
    }
}

class ShopAccessService(
    private val store: ShopMemberStore = ShopMemberStoreImpl()
) {
    companion object {
        private val SHOP_ROLES = setOf("owner", "staff")
    }

    /** Owner and Staff accounts act on their own shop. Supplier accounts have no shop records. */
    suspend fun requireShopMember(userId: Int): ShopContext {
        val member = store.findMember(userId)
        if (member == null || member.closed) {
            throw UnauthorizedException("Authentication required")
        }
        if (member.roleName.lowercase() !in SHOP_ROLES) {
            throw ForbiddenException("This account cannot access shop records")
        }
        return ShopContext(userId = member.userId, ownerUserId = member.userId, role = member.roleName)
    }
}
