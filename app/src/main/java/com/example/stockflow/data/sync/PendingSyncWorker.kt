package com.example.stockflow.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stockflow.data.local.SessionStore

/**
 * WorkManager worker: syncs PENDING category+product writes when network is available.
 */
class PendingSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val sessionStore = SessionStore(applicationContext)
            val processor = PendingSyncProcessor(sessionStore = sessionStore)
            val summary = processor.syncPendingForCurrentUser()
            Log.i(
                TAG,
                "worker done processed=${summary.processed} ok=${summary.succeeded} " +
                    "net=${summary.deferredNetwork} fail=${summary.failedPermanent}"
            )
            when {
                summary.deferredNetwork > 0 -> Result.retry()
                else -> Result.success()
            }
        } catch (e: Exception) {
            Log.w(TAG, "worker error: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK = "stockflow_pending_sync"
        private const val TAG = "PendingSyncWorker"
    }
}
