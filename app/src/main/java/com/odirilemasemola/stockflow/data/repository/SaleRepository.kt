package com.odirilemasemola.stockflow.data.repository

import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.data.local.cache.CacheDatabaseProvider
import com.odirilemasemola.stockflow.data.local.cache.StockFlowCacheDatabase
import com.odirilemasemola.stockflow.data.remote.ApiErrorResponse
import com.odirilemasemola.stockflow.data.remote.CreateSaleRequest
import com.odirilemasemola.stockflow.data.remote.RetrofitClient
import com.odirilemasemola.stockflow.data.remote.SaleApi
import com.odirilemasemola.stockflow.data.remote.SaleDto
import com.odirilemasemola.stockflow.data.sync.LocalCacheReconciler
import com.odirilemasemola.stockflow.ui.common.AppStrings
import com.odirilemasemola.stockflow.data.local.cache.PendingEntityType
import com.odirilemasemola.stockflow.data.local.cache.PendingOpStatus
import com.odirilemasemola.stockflow.data.local.cache.PendingOpType
import com.odirilemasemola.stockflow.data.local.cache.PendingOperationEntity
import com.odirilemasemola.stockflow.data.sync.LocalTempIds
import com.odirilemasemola.stockflow.data.sync.SaleWritePayload
import com.odirilemasemola.stockflow.data.sync.SyncScheduler
import android.content.Context
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Sales endpoints with an offline fallback.
 *
 * Every sale carries a client-generated [CreateSaleRequest.clientSaleId]. If the server cannot be
 * reached, the sale is validated against cached stock, saved to the pending queue (entity SALE),
 * local stock and dashboard aggregates are updated, and WorkManager syncs it later. The server
 * deduplicates by clientSaleId, so a retry after a lost response never records the sale twice.
 * After a successful online sale, Room products + dashboard aggregates are updated immediately.
 */
class SaleRepository(
    private val api: SaleApi = RetrofitClient.saleApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val appContext: Context? = null,
    private val onEnqueueSync: (() -> Unit)? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val gson = Gson()

    /** Sales recorded offline that have not synced yet (newest first). */
    suspend fun getPendingSales(): List<SaleDto> {
        val userId = sessionStore.getUserId() ?: return emptyList()
        val dao = database?.pendingOperationDao() ?: return emptyList()
        return dao.getAllForUser(userId)
            .filter {
                it.entityType == PendingEntityType.SALE &&
                    (it.status == PendingOpStatus.PENDING || it.status == PendingOpStatus.SYNCING)
            }
            .sortedByDescending { it.createdAt }
            .mapIndexed { index, op ->
                SaleWritePayload.fromJson(op.payloadJson).toPendingSaleDto(localId = -(index + 1), userId = userId)
            }
    }

    /** Server history with queued offline sales on top; offline, only the queued sales are shown. */
    suspend fun getSalesWithPending(): Result<List<SaleDto>> {
        val pending = getPendingSales()
        val remote = getSales()
        return when {
            remote.isSuccess -> Result.success(pending + remote.getOrDefault(emptyList()))
            pending.isNotEmpty() -> Result.success(pending)
            else -> remote
        }
    }

    suspend fun getSales(): Result<List<SaleDto>> {
        return try {
            val response = api.getSales(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_sales))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_sales)))
        }
    }

    suspend fun getSale(id: Int): Result<SaleDto> {
        return try {
            val response = api.getSale(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_sale_not_found)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_sale))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_sale)))
        }
    }

    suspend fun createSale(request: CreateSaleRequest): Result<SaleDto> {
        val keyed = if (request.clientSaleId.isNullOrBlank()) request.copy(clientSaleId = idGenerator()) else request
        return try {
            val response = api.createSale(authHeader(), keyed)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_complete_sale)))
                val userId = sessionStore.getUserId()
                val db = database
                if (userId != null && db != null) {
                    LocalCacheReconciler.applySuccessfulSale(db, userId, body, clock)
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_complete_sale))))
            }
        } catch (_: IOException) {
            queueOfflineSale(keyed)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_complete_sale)))
        }
    }

    /**
     * Records [request] locally. Rejected (nothing queued) when there is no cache, a product is
     * missing or not yet synced, or cached stock (minus other queued sales) is insufficient.
     */
    internal suspend fun queueOfflineSale(request: CreateSaleRequest): Result<SaleDto> {
        val userId = sessionStore.getUserId()
        val db = database
        val clientSaleId = request.clientSaleId
        if (userId == null || db == null || clientSaleId.isNullOrBlank()) {
            return Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        }
        val productDao = db.productDao()
        val pendingDao = db.pendingOperationDao()

        val merged = linkedMapOf<Int, Int>()
        request.items.forEach { merged[it.productId] = (merged[it.productId] ?: 0) + it.quantity }

        val lines = mutableListOf<SaleWritePayload.Line>()
        for ((productId, quantity) in merged) {
            if (LocalTempIds.isTemporary(productId)) {
                return Result.failure(Exception(AppStrings.get(R.string.error_offline_sale_unsynced_product)))
            }
            val cached = productDao.getById(userId, productId)
                ?: return Result.failure(Exception(AppStrings.get(R.string.error_offline_sale_unavailable)))
            // Cached stock already has earlier queued sales subtracted.
            if (quantity > cached.stockLevel) {
                return Result.failure(
                    Exception(AppStrings.get(R.string.error_offline_sale_insufficient_stock, cached.name))
                )
            }
            lines += SaleWritePayload.Line(productId, cached.name, quantity, cached.sellingPrice)
        }
        if (lines.isEmpty()) {
            return Result.failure(Exception(AppStrings.get(R.string.error_cart_empty)))
        }

        val now = clock()
        val payload = SaleWritePayload(
            clientSaleId = clientSaleId,
            paymentMethod = request.paymentMethod,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(now)),
            lines = lines
        )
        pendingDao.upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.SALE,
                localEntityId = clientSaleId,
                remoteEntityId = null,
                payloadJson = payload.toJson(),
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
        val pendingSale = payload.toPendingSaleDto(localId = -1, userId = userId)
        // Same local stock + dashboard update as an online sale.
        LocalCacheReconciler.applySuccessfulSale(db, userId, pendingSale, clock)
        onEnqueueSync?.invoke()
        appContext?.let { SyncScheduler.enqueueSync(it, expedited = true) }
        return Result.success(pendingSale)
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception(AppStrings.get(R.string.error_not_signed_in))
        }
        return "Bearer $token"
    }

    private fun errorMessage(response: Response<*>, fallback: String): String {
        val raw = response.errorBody()?.string()
        val apiMessage = try {
            gson.fromJson(raw, ApiErrorResponse::class.java)?.error
        } catch (_: Exception) {
            null
        }
        return apiMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
