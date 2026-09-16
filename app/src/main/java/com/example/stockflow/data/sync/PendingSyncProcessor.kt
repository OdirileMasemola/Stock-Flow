package com.example.stockflow.data.sync

import android.util.Log
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationEntity
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.RetrofitClient
import com.google.gson.Gson
import java.io.IOException

data class SyncSummary(
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failedPermanent: Int = 0,
    val deferredNetwork: Int = 0,
    val skippedWrongUser: Int = 0
)

/**
 * Processes PENDING write-queue ops for the **current** [SessionStore] user only.
 * Server responses are authoritative after success.
 */
class PendingSyncProcessor(
    private val api: ProductApi = RetrofitClient.productApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxTransientRetries: Int = 8
) {
    private val gson = Gson()
    private val pendingDao get() = database?.pendingOperationDao()
    private val productDao get() = database?.productDao()

    suspend fun syncPendingForCurrentUser(): SyncSummary {
        val userId = sessionStore.getUserId()
        val token = sessionStore.getToken()
        if (userId == null || token.isNullOrBlank()) {
            Log.i(TAG, "sync skip: no signed-in user")
            return SyncSummary()
        }
        val dao = pendingDao ?: return SyncSummary()

        // Recover crashed SYNCING rows for this user only.
        dao.resetSyncingToPending(userId, clock())

        val ops = dao.getPendingForUser(userId)
        var summary = SyncSummary(processed = ops.size)
        val auth = "Bearer $token"

        for (op in ops) {
            if (op.userId != userId) {
                summary = summary.copy(skippedWrongUser = summary.skippedWrongUser + 1)
                Log.w(TAG, "skip op ${op.id}: wrong user ${op.userId} != $userId")
                continue
            }
            if (op.entityType != PendingEntityType.PRODUCT) {
                markFailed(op, "Unsupported entity type: ${op.entityType}")
                summary = summary.copy(failedPermanent = summary.failedPermanent + 1)
                continue
            }

            val inflight = op.copy(
                status = PendingOpStatus.SYNCING,
                updatedAt = clock()
            )
            dao.upsert(inflight)

            val outcome = try {
                when (op.operationType) {
                    PendingOpType.CREATE -> syncCreate(auth, userId, inflight)
                    PendingOpType.UPDATE -> syncUpdate(auth, userId, inflight)
                    PendingOpType.DELETE -> syncDelete(auth, userId, inflight)
                    else -> SyncOpOutcome.PermanentFailure("Unknown operation: ${op.operationType}")
                }
            } catch (e: IOException) {
                SyncOpOutcome.NetworkFailure(e.message ?: "network")
            } catch (e: Exception) {
                SyncOpOutcome.PermanentFailure(e.message ?: "unexpected error")
            }

            when (outcome) {
                is SyncOpOutcome.Success -> {
                    dao.deleteById(op.id)
                    summary = summary.copy(succeeded = summary.succeeded + 1)
                    Log.i(TAG, "sync ok ${op.operationType} product local=${op.localEntityId}")
                }
                is SyncOpOutcome.NetworkFailure -> {
                    val retries = op.retryCount + 1
                    dao.upsert(
                        inflight.copy(
                            status = PendingOpStatus.PENDING,
                            retryCount = retries,
                            lastError = outcome.message,
                            updatedAt = clock()
                        )
                    )
                    summary = summary.copy(deferredNetwork = summary.deferredNetwork + 1)
                    Log.i(TAG, "sync network defer ${op.id} retry=$retries")
                    // Stop the batch so WorkManager backoff applies; remaining stay PENDING.
                    break
                }
                is SyncOpOutcome.PermanentFailure -> {
                    markFailed(inflight, outcome.message)
                    summary = summary.copy(failedPermanent = summary.failedPermanent + 1)
                    Log.w(TAG, "sync permanent fail ${op.id}: ${outcome.message}")
                }
            }
        }
        return summary
    }

    private suspend fun syncCreate(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
        // Idempotency: if a prior attempt already stored remoteEntityId, finish without re-POST.
        op.remoteEntityId?.let { remoteId ->
            refreshProductFromServer(auth, userId, remoteId, op.localEntityId)
            return SyncOpOutcome.Success
        }

        val payload = ProductWritePayload.fromJson(op.payloadJson)
        val response = api.createProduct(auth, payload.toCreateRequest())
        return when {
            response.isSuccessful -> {
                val body = response.body()
                    ?: return SyncOpOutcome.PermanentFailure("Empty create response")
                // Persist remote id on the op first so a crash mid-finish won't duplicate forever
                // on the next retry (best-effort; backend has no clientRequestId).
                pendingDao?.upsert(
                    op.copy(remoteEntityId = body.id, updatedAt = clock())
                )
                val localId = op.localEntityId.toIntOrNull()
                if (localId != null && LocalTempIds.isTemporary(localId)) {
                    productDao?.deleteById(userId, localId)
                }
                productDao?.upsert(body.toCachedEntity(userId, clock()))
                remappedPendingOps(userId, op.localEntityId, body.id)
                SyncOpOutcome.Success
            }
            response.code() in 400..499 -> {
                SyncOpOutcome.PermanentFailure(errorMessage(response))
            }
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    private suspend fun syncUpdate(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
        val remoteId = op.remoteEntityId
            ?: op.localEntityId.toIntOrNull()?.takeIf { !LocalTempIds.isTemporary(it) }
            ?: return SyncOpOutcome.PermanentFailure("Missing remote id for update")

        val payload = ProductWritePayload.fromJson(op.payloadJson)
        val response = api.updateProduct(auth, remoteId, payload.toUpdateRequest())
        return when {
            response.isSuccessful -> {
                val body = response.body()
                    ?: return SyncOpOutcome.PermanentFailure("Empty update response")
                productDao?.upsert(body.toCachedEntity(userId, clock()))
                SyncOpOutcome.Success
            }
            response.code() in 400..499 -> {
                SyncOpOutcome.PermanentFailure(errorMessage(response))
            }
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    private suspend fun syncDelete(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
        val remoteId = op.remoteEntityId
            ?: op.localEntityId.toIntOrNull()?.takeIf { !LocalTempIds.isTemporary(it) }
        // Unsynced local-only create that was deleted: nothing to send.
        if (remoteId == null) {
            op.localEntityId.toIntOrNull()?.let { productDao?.deleteById(userId, it) }
            return SyncOpOutcome.Success
        }

        val response = api.deleteProduct(auth, remoteId)
        return when {
            response.isSuccessful || response.code() == 204 || response.code() == 404 -> {
                productDao?.deleteById(userId, remoteId)
                SyncOpOutcome.Success
            }
            response.code() in 400..499 -> {
                SyncOpOutcome.PermanentFailure(errorMessage(response))
            }
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    private suspend fun refreshProductFromServer(
        auth: String,
        userId: Int,
        remoteId: Int,
        localEntityId: String
    ) {
        val response = api.getProduct(auth, remoteId)
        if (response.isSuccessful) {
            response.body()?.let { body ->
                localEntityId.toIntOrNull()?.takeIf { LocalTempIds.isTemporary(it) }?.let {
                    productDao?.deleteById(userId, it)
                }
                productDao?.upsert(body.toCachedEntity(userId, clock()))
            }
        }
    }

    /**
     * After a CREATE sync, rewrite any later UPDATE/DELETE ops that still point at the temp id.
     */
    private suspend fun remappedPendingOps(userId: Int, localTempId: String, remoteId: Int) {
        val dao = pendingDao ?: return
        val related = dao.getActiveForEntity(userId, PendingEntityType.PRODUCT, localTempId)
        for (relatedOp in related) {
            if (relatedOp.operationType == PendingOpType.CREATE) continue
            dao.upsert(
                relatedOp.copy(
                    localEntityId = remoteId.toString(),
                    remoteEntityId = remoteId,
                    updatedAt = clock()
                )
            )
        }
    }

    private suspend fun markFailed(op: PendingOperationEntity, message: String) {
        pendingDao?.upsert(
            op.copy(
                status = PendingOpStatus.FAILED,
                lastError = message,
                retryCount = op.retryCount + 1,
                updatedAt = clock()
            )
        )
    }

    private fun errorMessage(response: retrofit2.Response<*>): String {
        val raw = response.errorBody()?.string()
        val apiMessage = try {
            gson.fromJson(raw, ApiErrorResponse::class.java)?.error
        } catch (_: Exception) {
            null
        }
        return apiMessage?.takeIf { it.isNotBlank() }
            ?: "HTTP ${response.code()}"
    }

    companion object {
        private const val TAG = "PendingSync"
    }
}

private sealed class SyncOpOutcome {
    data object Success : SyncOpOutcome()
    data class NetworkFailure(val message: String) : SyncOpOutcome()
    data class PermanentFailure(val message: String) : SyncOpOutcome()
}
