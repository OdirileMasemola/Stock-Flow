package com.example.stockflow.data.sync

import com.example.stockflow.data.local.cache.CategoryCacheDao
import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationDao
import com.example.stockflow.data.local.cache.ProductCacheDao
import com.example.stockflow.data.local.cache.toCachedEntity

/**
 * After a successful network list refresh, re-apply unsynced local writes
 * so offline-created / edited / deleted rows remain visible until sync completes.
 */
object PendingOverlayApplier {
    suspend fun applyProductOverlays(
        userId: Int,
        productDao: ProductCacheDao,
        pendingDao: PendingOperationDao,
        clock: () -> Long
    ) {
        val ops = pendingDao.getAllForUser(userId)
            .filter {
                it.entityType == PendingEntityType.PRODUCT &&
                    (it.status == PendingOpStatus.PENDING ||
                        it.status == PendingOpStatus.SYNCING)
            }
            .sortedBy { it.createdAt }

        for (op in ops) {
            when (op.operationType) {
                PendingOpType.CREATE, PendingOpType.UPDATE -> {
                    val payload = ProductWritePayload.fromJson(op.payloadJson)
                    val id = op.localEntityId.toIntOrNull() ?: continue
                    productDao.upsert(payload.toProductDto(id).toCachedEntity(userId, clock()))
                }
                PendingOpType.DELETE -> {
                    val id = op.localEntityId.toIntOrNull() ?: continue
                    productDao.deleteById(userId, id)
                    op.remoteEntityId?.let { productDao.deleteById(userId, it) }
                }
            }
        }
    }

    suspend fun applyCategoryOverlays(
        userId: Int,
        categoryDao: CategoryCacheDao,
        pendingDao: PendingOperationDao,
        clock: () -> Long
    ) {
        val ops = pendingDao.getAllForUser(userId)
            .filter {
                it.entityType == PendingEntityType.CATEGORY &&
                    (it.status == PendingOpStatus.PENDING ||
                        it.status == PendingOpStatus.SYNCING)
            }
            .sortedBy { it.createdAt }

        for (op in ops) {
            if (op.operationType != PendingOpType.CREATE) continue
            val payload = CategoryWritePayload.fromJson(op.payloadJson)
            val id = op.localEntityId.toIntOrNull() ?: continue
            categoryDao.upsert(payload.toCategoryDto(id).toCachedEntity(userId, clock()))
        }
    }
}
