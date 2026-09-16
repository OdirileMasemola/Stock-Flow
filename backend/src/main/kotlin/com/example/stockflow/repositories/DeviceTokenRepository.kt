package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.DeviceTokens
import com.example.stockflow.models.Roles
import com.example.stockflow.models.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

data class StoredDeviceToken(
    val id: Int,
    val userId: Int,
    val token: String,
    val platform: String,
    val active: Boolean
)

interface DeviceTokenRepository {
    suspend fun upsert(userId: Int, token: String, platform: String): StoredDeviceToken
    suspend fun deactivate(userId: Int, token: String): Boolean
    suspend fun delete(userId: Int, token: String): Boolean
    suspend fun markInactiveByToken(token: String)
    suspend fun findActiveTokensForUserIds(userIds: Collection<Int>): List<StoredDeviceToken>
    /** Owner-role user IDs plus [actingUserId] (deduplicated). */
    suspend fun resolveAlertRecipientUserIds(actingUserId: Int): List<Int>
}

class DeviceTokenRepositoryImpl : DeviceTokenRepository {

    override suspend fun upsert(userId: Int, token: String, platform: String): StoredDeviceToken = dbQuery {
        val now = LocalDateTime.now()
        val existing = DeviceTokens
            .selectAll()
            .where { DeviceTokens.token eq token }
            .singleOrNull()

        if (existing != null) {
            DeviceTokens.update({ DeviceTokens.token eq token }) {
                it[DeviceTokens.userId] = userId
                it[DeviceTokens.platform] = platform
                it[DeviceTokens.active] = true
                it[DeviceTokens.updatedAt] = now
            }
            StoredDeviceToken(
                id = existing[DeviceTokens.id],
                userId = userId,
                token = token,
                platform = platform,
                active = true
            )
        } else {
            val insert = DeviceTokens.insert {
                it[DeviceTokens.userId] = userId
                it[DeviceTokens.token] = token
                it[DeviceTokens.platform] = platform
                it[DeviceTokens.active] = true
                it[DeviceTokens.createdAt] = now
                it[DeviceTokens.updatedAt] = now
            }
            val id = insert.resultedValues?.first()?.get(DeviceTokens.id)
                ?: throw RuntimeException("Failed to register device token")
            StoredDeviceToken(
                id = id,
                userId = userId,
                token = token,
                platform = platform,
                active = true
            )
        }
    }

    override suspend fun deactivate(userId: Int, token: String): Boolean = dbQuery {
        DeviceTokens.update({
            (DeviceTokens.userId eq userId) and (DeviceTokens.token eq token)
        }) {
            it[active] = false
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    override suspend fun delete(userId: Int, token: String): Boolean = dbQuery {
        DeviceTokens.deleteWhere {
            (DeviceTokens.userId eq userId) and (DeviceTokens.token eq token)
        } > 0
    }

    override suspend fun markInactiveByToken(token: String) {
        dbQuery {
            DeviceTokens.update({ DeviceTokens.token eq token }) {
                it[active] = false
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override suspend fun findActiveTokensForUserIds(userIds: Collection<Int>): List<StoredDeviceToken> {
        if (userIds.isEmpty()) return emptyList()
        return dbQuery {
            DeviceTokens
                .selectAll()
                .where {
                    (DeviceTokens.userId inList userIds.toList()) and (DeviceTokens.active eq true)
                }
                .map { row ->
                    StoredDeviceToken(
                        id = row[DeviceTokens.id],
                        userId = row[DeviceTokens.userId],
                        token = row[DeviceTokens.token],
                        platform = row[DeviceTokens.platform],
                        active = row[DeviceTokens.active]
                    )
                }
        }
    }

    override suspend fun resolveAlertRecipientUserIds(actingUserId: Int): List<Int> = dbQuery {
        val ownerIds = (Users innerJoin Roles)
            .select(Users.id)
            .where { Roles.name eq "Owner" }
            .map { it[Users.id] }
            .toMutableSet()
        if (actingUserId > 0) {
            ownerIds.add(actingUserId)
        }
        ownerIds.toList()
    }
}
