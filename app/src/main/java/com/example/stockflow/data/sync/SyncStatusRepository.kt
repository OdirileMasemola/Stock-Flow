package com.example.stockflow.data.sync

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase

/**
 * Read-only sync status for the current user (Settings + banners).
 */
class SyncStatusRepository(
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull()
) {
    suspend fun currentStatus(): SyncStatus {
        val userId = sessionStore.getUserId() ?: return SyncStatus()
        val dao = database?.pendingOperationDao() ?: return SyncStatus()
        val all = dao.getAllForUser(userId)
        val pending = all.count {
            it.status == PendingOpStatus.PENDING || it.status == PendingOpStatus.SYNCING
        }
        val failed = all.count { it.status == PendingOpStatus.FAILED }
        val syncing = all.any { it.status == PendingOpStatus.SYNCING }
        val lastError = all
            .filter { it.status == PendingOpStatus.FAILED }
            .maxByOrNull { it.updatedAt }
            ?.lastError
        return SyncStatus(
            pendingCount = pending,
            failedCount = failed,
            isSyncing = syncing,
            lastError = lastError
        )
    }
}
