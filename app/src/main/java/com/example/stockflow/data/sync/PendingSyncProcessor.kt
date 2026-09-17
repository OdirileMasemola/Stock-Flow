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
import com.example.stockflow.data.remote.CategoryApi
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.RetrofitClient
import com.google.gson.Gson
import java.io.IOException

data class SyncSummary(
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failedPermanent: Int = 0,
    val deferredNetwork: Int = 0,
    val skippedWrongUser: Int = 0,
    val deferredDependency: Int = 0
)

/**
 * Processes PENDING write-queue ops for the **current** [SessionStore] user only.
 * CATEGORY ops are processed before PRODUCT so products never POST with a temp category id.
 * Server responses are authoritative after success.
 */
class PendingSyncProcessor(
    private val api: ProductApi = RetrofitClient.productApi,
    private val categoryApi: CategoryApi = RetrofitClient.categoryApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxTransientRetries: Int = 8
) {
    private val gson = Gson()
    private val pendingDao get() = database?.pendingOperationDao()
    private val productDao get() = database?.productDao()
    private val categoryDao get() = database?.categoryDao()

    suspend fun syncPendingForCurrentUser(): SyncSummary {
        val userId = sessionStore.getUserId()
        val token = sessionStore.getToken()
        if (userId == null || token.isNullOrBlank()) {
            Log.i(TAG, "sync skip: no signed-in user")
            return SyncSummary()
        }
        val dao = pendingDao ?: return SyncSummary()

        dao.resetSyncingToPending(userId, clock())

        // Dependency order: CATEGORY before PRODUCT (then FIFO by createdAt).
        val ops = dao.getPendingForUser(userId).sortedWith(
            compareBy<PendingOperationEntity> {
                when (it.entityType) {
                    PendingEntityType.CATEGORY -> 0
                    PendingEntityType.PRODUCT -> 1
                    else -> 2
                }
            }.thenBy { it.createdAt }
        )
        var summary = SyncSummary(processed = ops.size)
        val auth = "Bearer $token"

        for (seed in ops) {
            // Re-read from Room so CATEGORY→PRODUCT payload rewrites are visible in this batch.
            val op = dao.getById(seed.id) ?: continue
            if (op.status != PendingOpStatus.PENDING && op.status != PendingOpStatus.SYNCING) {
                continue
            }
            if (op.userId != userId) {
                summary = summary.copy(skippedWrongUser = summary.skippedWrongUser + 1)
                Log.w(TAG, "skip op ${op.id}: wrong user ${op.userId} != $userId")
                continue
            }

            val inflight = op.copy(
                status = PendingOpStatus.SYNCING,
                updatedAt = clock()
            )
            dao.upsert(inflight)

            val outcome = try {
                when (op.entityType) {
                    PendingEntityType.CATEGORY -> syncCategory(auth, userId, inflight)
                    PendingEntityType.PRODUCT -> syncProduct(auth, userId, inflight)
                    else -> SyncOpOutcome.PermanentFailure("Unsupported entity type: ${op.entityType}")
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
                    Log.i(TAG, "sync ok ${op.entityType} ${op.operationType} local=${op.localEntityId}")
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
                    break
                }
                is SyncOpOutcome.DeferredDependency -> {
                    dao.upsert(
                        inflight.copy(
                            status = PendingOpStatus.PENDING,
                            lastError = outcome.message,
                            updatedAt = clock()
                        )
                    )
                    summary = summary.copy(deferredDependency = summary.deferredDependency + 1)
                    Log.i(TAG, "sync defer dependency ${op.id}: ${outcome.message}")
                    // Continue — other independent ops may still sync.
                }
                is SyncOpOutcome.PermanentFailure -> {
                    markFailed(inflight, outcome.message)
                    summary = summary.copy(failedPermanent = summary.failedPermanent + 1)
                    Log.w(TAG, "sync permanent fail ${op.id}: ${outcome.message}")
                }
            }
        }

        // Keep dashboard inventory fields aligned after successful sync mutations.
        if (summary.succeeded > 0 && database != null) {
            LocalCacheReconciler.recalculateDashboardFromProducts(database, userId, clock)
        }
        return summary
    }

    private suspend fun syncCategory(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
        if (op.operationType != PendingOpType.CREATE) {
            return SyncOpOutcome.PermanentFailure("Unsupported category op: ${op.operationType}")
        }
        op.remoteEntityId?.let { remoteId ->
            refreshCategoryFromServer(auth, userId, remoteId, op.localEntityId)
            rewriteProductCategoryIds(userId, op.localEntityId, remoteId)
            return SyncOpOutcome.Success
        }

        val payload = CategoryWritePayload.fromJson(op.payloadJson)
        // Idempotency: if server already has this name, find-or-create returns existing.
        val response = categoryApi.createCategory(auth, payload.toCreateRequest())
        return when {
            response.isSuccessful -> {
                val body = response.body()
                    ?: return SyncOpOutcome.PermanentFailure("Empty category create response")
                pendingDao?.upsert(op.copy(remoteEntityId = body.id, updatedAt = clock()))
                val localId = op.localEntityId.toIntOrNull()
                if (localId != null && LocalTempIds.isTemporary(localId)) {
                    categoryDao?.deleteById(userId, localId)
                }
                categoryDao?.upsert(body.toCachedEntity(userId, clock()))
                rewriteProductCategoryIds(userId, op.localEntityId, body.id)
                SyncOpOutcome.Success
            }
            response.code() in 400..499 -> SyncOpOutcome.PermanentFailure(errorMessage(response))
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    private suspend fun syncProduct(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
        // Never sync a product that still references a temporary category id.
        if (op.operationType == PendingOpType.CREATE || op.operationType == PendingOpType.UPDATE) {
            val payload = ProductWritePayload.fromJson(op.payloadJson)
            if (LocalTempIds.isTemporary(payload.categoryId)) {
                val pendingCat = pendingDao?.getActiveForEntity(
                    userId,
                    PendingEntityType.CATEGORY,
                    payload.categoryId.toString()
                ).orEmpty()
                if (pendingCat.any { it.status != PendingOpStatus.FAILED }) {
                    return SyncOpOutcome.DeferredDependency(
                        "Waiting for category ${payload.categoryId} to sync"
                    )
                }
                return SyncOpOutcome.PermanentFailure(
                    "Product references unsynced category ${payload.categoryId}"
                )
            }
        }

        return when (op.operationType) {
            PendingOpType.CREATE -> syncProductCreate(auth, userId, op)
            PendingOpType.UPDATE -> syncProductUpdate(auth, userId, op)
            PendingOpType.DELETE -> syncProductDelete(auth, userId, op)
            else -> SyncOpOutcome.PermanentFailure("Unknown operation: ${op.operationType}")
        }
    }

    private suspend fun syncProductCreate(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
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
                pendingDao?.upsert(op.copy(remoteEntityId = body.id, updatedAt = clock()))
                val localId = op.localEntityId.toIntOrNull()
                if (localId != null && LocalTempIds.isTemporary(localId)) {
                    productDao?.deleteById(userId, localId)
                }
                productDao?.upsert(body.toCachedEntity(userId, clock()))
                remappedPendingOps(userId, op.localEntityId, body.id)
                SyncOpOutcome.Success
            }
            response.code() in 400..499 -> SyncOpOutcome.PermanentFailure(errorMessage(response))
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    private suspend fun syncProductUpdate(
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
            response.code() in 400..499 -> SyncOpOutcome.PermanentFailure(errorMessage(response))
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    private suspend fun syncProductDelete(
        auth: String,
        userId: Int,
        op: PendingOperationEntity
    ): SyncOpOutcome {
        val remoteId = op.remoteEntityId
            ?: op.localEntityId.toIntOrNull()?.takeIf { !LocalTempIds.isTemporary(it) }
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
            response.code() in 400..499 -> SyncOpOutcome.PermanentFailure(errorMessage(response))
            else -> {
                if (op.retryCount + 1 >= maxTransientRetries) {
                    SyncOpOutcome.PermanentFailure(errorMessage(response))
                } else {
                    SyncOpOutcome.NetworkFailure(errorMessage(response))
                }
            }
        }
    }

    /**
     * After a CATEGORY CREATE sync, rewrite pending PRODUCT payloads that still use the temp id.
     */
    private suspend fun rewriteProductCategoryIds(
        userId: Int,
        localCategoryId: String,
        remoteCategoryId: Int
    ) {
        val dao = pendingDao ?: return
        val localCatInt = localCategoryId.toIntOrNull() ?: return
        val products = dao.getAllForUser(userId).filter {
            it.entityType == PendingEntityType.PRODUCT &&
                it.status != PendingOpStatus.FAILED &&
                (it.operationType == PendingOpType.CREATE || it.operationType == PendingOpType.UPDATE)
        }
        for (productOp in products) {
            val payload = try {
                ProductWritePayload.fromJson(productOp.payloadJson)
            } catch (_: Exception) {
                continue
            }
            if (payload.categoryId != localCatInt) continue
            val rewritten = payload.copy(categoryId = remoteCategoryId)
            dao.upsert(
                productOp.copy(
                    payloadJson = ProductWritePayload.toJson(rewritten),
                    updatedAt = clock()
                )
            )
            // Also fix Room product rows that still point at the temp category.
            productOp.localEntityId.toIntOrNull()?.let { productLocalId ->
                productDao?.getById(userId, productLocalId)?.let { cached ->
                    if (cached.categoryId == localCatInt) {
                        productDao?.upsert(
                            cached.copy(
                                categoryId = remoteCategoryId,
                                categoryName = rewritten.categoryName ?: cached.categoryName
                            )
                        )
                    }
                }
            }
        }
        categoryDao?.getById(userId, remoteCategoryId)?.let { remoteCat ->
            // Ensure local temp category row is gone (already deleted on success path).
            if (LocalTempIds.isTemporary(localCatInt)) {
                categoryDao?.deleteById(userId, localCatInt)
            }
            // Touch name on products for UI consistency.
            productDao?.getAll(userId)?.filter { it.categoryId == remoteCategoryId }?.forEach { p ->
                if (p.categoryName != remoteCat.name) {
                    productDao?.upsert(p.copy(categoryName = remoteCat.name))
                }
            }
        }
    }

    private suspend fun refreshCategoryFromServer(
        auth: String,
        userId: Int,
        remoteId: Int,
        localEntityId: String
    ) {
        val response = categoryApi.getCategories(auth)
        if (response.isSuccessful) {
            val match = response.body().orEmpty().firstOrNull { it.id == remoteId }
            if (match != null) {
                localEntityId.toIntOrNull()?.takeIf { LocalTempIds.isTemporary(it) }?.let {
                    categoryDao?.deleteById(userId, it)
                }
                categoryDao?.upsert(match.toCachedEntity(userId, clock()))
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
    data class DeferredDependency(val message: String) : SyncOpOutcome()
}
