package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.Business
import com.example.stockflow.models.Businesses
import com.example.stockflow.models.UpdateBusinessRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

interface BusinessRepository {
    suspend fun findByUserId(userId: Int): Business?
    suspend fun create(userId: Int, request: UpdateBusinessRequest): Business
    suspend fun update(userId: Int, request: UpdateBusinessRequest): Business?
}

class BusinessRepositoryImpl : BusinessRepository {
    override suspend fun findByUserId(userId: Int): Business? = dbQuery {
        Businesses.selectAll()
            .where { Businesses.userId eq userId }
            .map { toBusiness(it) }
            .singleOrNull()
    }

    override suspend fun create(userId: Int, request: UpdateBusinessRequest): Business = dbQuery {
        val now = LocalDateTime.now()
        val insertStatement = Businesses.insert {
            it[Businesses.userId] = userId
            it[storeName] = request.storeName
            it[ownerName] = request.ownerName
            it[phone] = request.phone
            it[email] = request.email
            it[address] = request.address
            it[imageUrl] = normalizeImageUrl(request.imageUrl)
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[createdAt] = now
            it[updatedAt] = now
        }
        insertStatement.resultedValues?.first()?.let { toBusiness(it) }
            ?: throw RuntimeException("Failed to create business")
    }

    override suspend fun update(userId: Int, request: UpdateBusinessRequest): Business? = dbQuery {
        val updated = Businesses.update({ Businesses.userId eq userId }) {
            it[storeName] = request.storeName
            it[ownerName] = request.ownerName
            it[phone] = request.phone
            it[email] = request.email
            it[address] = request.address
            it[imageUrl] = normalizeImageUrl(request.imageUrl)
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[updatedAt] = LocalDateTime.now()
        }
        if (updated == 0) return@dbQuery null
        Businesses.selectAll()
            .where { Businesses.userId eq userId }
            .map { toBusiness(it) }
            .singleOrNull()
    }

    private fun normalizeImageUrl(imageUrl: String?): String? =
        imageUrl?.trim()?.takeIf { it.isNotEmpty() }

    private fun toBusiness(row: ResultRow) = Business(
        id = row[Businesses.id],
        userId = row[Businesses.userId],
        storeName = row[Businesses.storeName],
        ownerName = row[Businesses.ownerName],
        phone = row[Businesses.phone],
        email = row[Businesses.email],
        address = row[Businesses.address],
        imageUrl = row[Businesses.imageUrl],
        latitude = row[Businesses.latitude],
        longitude = row[Businesses.longitude],
        createdAt = row[Businesses.createdAt].toString(),
        updatedAt = row[Businesses.updatedAt].toString()
    )
}
