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
 * Owners act on their own shop. Staff act on the shop they were added to by its owner
 * (users.shop_owner_user_id), or on their own records when they are not linked to a shop.
 */
data class ShopContext(
    val userId: Int,
    val ownerUserId: Int,
    val role: String
) {
    val isOwner: Boolean get() = role.equals("owner", ignoreCase = true)
}

data class ShopMember(
    val userId: Int,
    val roleName: String,
    val closed: Boolean,
    val shopOwnerUserId: Int? = null
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
                    closed = AccountClosure.isClosed(row[Users.id], row[Users.username], row[Users.email]),
                    shopOwnerUserId = row[Users.shopOwnerUserId]
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
        return ShopContext(userId = member.userId, ownerUserId = resolveShopOwner(member), role = member.roleName)
    }

    /** Owner-only actions (employees). Staff and suppliers get 403. */
    suspend fun requireShopOwner(userId: Int): ShopContext {
        val shop = requireShopMember(userId)
        if (!shop.isOwner) {
            throw ForbiddenException("Only the shop owner can manage employees")
        }
        return shop
    }

    /** A staff link only counts while the owner account is open and still an Owner. */
    private suspend fun resolveShopOwner(member: ShopMember): Int {
        if (member.roleName.equals("owner", ignoreCase = true)) return member.userId
        val linked = member.shopOwnerUserId ?: return member.userId
        if (linked == member.userId) return member.userId
        val owner = store.findMember(linked)
        return if (owner != null && !owner.closed && owner.roleName.equals("owner", ignoreCase = true)) {
            linked
        } else {
            member.userId
        }
    }
}
