package com.example.stockflow.data.sync

import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationDao
import com.example.stockflow.data.local.cache.ProductCacheDao
import com.example.stockflow.data.local.cache.toCachedEntity

/**
 * After a successful network product list refresh, re-apply unsynced local writes
 * so offline-created / edited / deleted products remain visible until sync completes.
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
}
