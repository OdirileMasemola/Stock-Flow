package com.odirilemasemola.stockflow.data.sync

import com.odirilemasemola.stockflow.data.local.cache.CategoryCacheDao
import com.odirilemasemola.stockflow.data.local.cache.PendingEntityType
import com.odirilemasemola.stockflow.data.local.cache.PendingOpStatus
import com.odirilemasemola.stockflow.data.local.cache.PendingOpType
import com.odirilemasemola.stockflow.data.local.cache.PendingOperationDao
import com.odirilemasemola.stockflow.data.local.cache.PendingOperationEntity
import com.odirilemasemola.stockflow.data.local.cache.ProductCacheDao
import com.odirilemasemola.stockflow.data.local.cache.toCachedEntity

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
        applyPendingSaleStock(userId, productDao, pendingDao, clock)
    }

    /**
     * A refreshed product list does not include sales that are still queued offline, so
     * subtract their quantities again. FAILED sales are excluded: their stock was restored.
     */
    suspend fun applyPendingSaleStock(
        userId: Int,
        productDao: ProductCacheDao,
        pendingDao: PendingOperationDao,
        clock: () -> Long
    ) {
        val quantities = pendingSaleQuantities(pendingDao.getAllForUser(userId))
        for ((productId, quantity) in quantities) {
            val cached = productDao.getById(userId, productId) ?: continue
            productDao.upsert(
                cached.copy(stockLevel = (cached.stockLevel - quantity).coerceAtLeast(0), cachedAt = clock())
            )
        }
    }

    /** Total quantity per product across PENDING/SYNCING offline sales. */
    fun pendingSaleQuantities(ops: List<PendingOperationEntity>): Map<Int, Int> {
        val totals = linkedMapOf<Int, Int>()
        ops.filter {
            it.entityType == PendingEntityType.SALE &&
                (it.status == PendingOpStatus.PENDING || it.status == PendingOpStatus.SYNCING)
        }.forEach { op ->
            SaleWritePayload.fromJson(op.payloadJson).lines.forEach { line ->
                totals[line.productId] = (totals[line.productId] ?: 0) + line.quantity
            }
        }
        return totals
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
