package com.example.stockflow.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues WorkManager sync with [NetworkType.CONNECTED] and exponential backoff.
 */
object SyncScheduler {
    private const val TAG = "SyncScheduler"

    fun enqueueSync(context: Context, expedited: Boolean = false) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val builder = OneTimeWorkRequestBuilder<PendingSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(PendingSyncWorker.UNIQUE_WORK)

            if (expedited) {
                try {
                    builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                } catch (_: Exception) {
                    // Older platforms / quota — fall through as normal one-time work.
                }
            }

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                PendingSyncWorker.UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                builder.build()
            )
            Log.i(TAG, "enqueued pending sync (expedited=$expedited)")
        } catch (e: Exception) {
            Log.w(TAG, "unable to enqueue sync: ${e.message}")
        }
    }

    /** Force a new attempt even if one is already queued (e.g. Settings Retry). */
    fun enqueueSyncReplace(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(PendingSyncWorker.UNIQUE_WORK)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                PendingSyncWorker.UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.i(TAG, "enqueued pending sync (replace)")
        } catch (e: Exception) {
            Log.w(TAG, "unable to enqueue sync replace: ${e.message}")
        }
    }
}
