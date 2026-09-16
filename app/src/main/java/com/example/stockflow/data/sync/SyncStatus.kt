package com.example.stockflow.data.sync

/**
 * Compact sync status for Settings / inventory banners.
 */
data class SyncStatus(
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastError: String? = null
) {
    val hasPending: Boolean get() = pendingCount > 0
    val hasFailed: Boolean get() = failedCount > 0
}
