package com.example.stockflow.data.repository

import android.content.Context
import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.ProductSkuCodes
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationDao
import com.example.stockflow.data.local.cache.PendingOperationEntity
import com.example.stockflow.data.local.cache.ProductCacheDao
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.sync.LocalTempIds
import com.example.stockflow.data.sync.PendingOverlayApplier
import com.example.stockflow.data.sync.ProductWritePayload
import com.example.stockflow.data.sync.SyncScheduler
import com.example.stockflow.data.sync.WriteResult
import com.example.stockflow.data.sync.LocalCacheReconciler
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Talks to the Ktor product endpoints using the JWT from [SessionStore].
 * Successful READs populate the Room READ cache; offline IO falls back to cache.
 * Offline WRITEs (create/update/delete) update Room then enqueue a pending op —
 * online writes still go direct to the API and never through the queue.
 */
class ProductRepository(
    private val api: ProductApi = RetrofitClient.productApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val appContext: Context? = null,
    private val onEnqueueSync: (() -> Unit)? = null
) {
    private val gson = Gson()
    private val productDao: ProductCacheDao? get() = database?.productDao()
    private val pendingDao: PendingOperationDao? get() = database?.pendingOperationDao()

    fun observeProducts(): Flow<List<ProductDto>> {
        val userId = sessionStore.getUserId() ?: return flowOf(emptyList())
        val dao = productDao ?: return flowOf(emptyList())
        return dao.observeAll(userId).map { list -> list.map { it.toDto() } }
    }

    fun observeLowStockProducts(): Flow<List<ProductDto>> {
        val userId = sessionStore.getUserId() ?: return flowOf(emptyList())
        val dao = productDao ?: return flowOf(emptyList())
        return dao.observeLowStock(userId).map { list -> list.map { it.toDto() } }
    }

    suspend fun getProducts(): CacheResult<List<ProductDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getProducts(authHeader())
            if (response.isSuccessful) {
                val data = response.body().orEmpty()
                if (userId != null) {
                    val cachedAt = clock()
                    productDao?.replaceAll(userId, data.map { it.toCachedEntity(userId, cachedAt) })
                    val pDao = productDao
                    val qDao = pendingDao
                    if (pDao != null && qDao != null) {
                        PendingOverlayApplier.applyProductOverlays(userId, pDao, qDao, clock)
                    }
                }
                // Return merged view (server + pending overlays) when possible.
                val merged = if (userId != null) {
                    productDao?.getAll(userId)?.map { it.toDto() } ?: data
                } else {
                    data
                }
                CacheResult.Fresh(merged)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_products)))
            }
        } catch (_: IOException) {
            fallbackProducts(userId)
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_products))
        }
    }

    suspend fun getLowStockProducts(): Result<List<ProductDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getLowStockProducts(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_low_stock))))
            }
        } catch (_: IOException) {
            if (userId != null) {
                val cached = productDao?.getAll(userId).orEmpty()
                    .filter { it.stockLevel <= it.minStockLevel }
                    .sortedBy { it.stockLevel }
                if (cached.isNotEmpty()) {
                    return Result.success(cached.map { it.toDto() })
                }
            }
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_low_stock)))
        }
    }

    suspend fun getProduct(id: Int): CacheResult<ProductDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getProduct(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.product_not_found))
                if (userId != null) {
                    productDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_product)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = productDao?.getById(userId, id)
            if (cached != null) {
                CacheResult.Cached(cached.toDto(), cached.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_product))
        }
    }

    suspend fun getProductBySku(sku: String): Result<ProductDto> {
        return try {
            val candidates = ProductSkuCodes.lookupCandidates(sku)
            if (candidates.isEmpty()) {
                return Result.failure(Exception(AppStrings.get(R.string.product_not_found)))
            }
            var lastError: Exception? = null
            for (candidate in candidates) {
                val response = api.getProductBySku(authHeader(), candidate)
                if (response.isSuccessful) {
                    val body = response.body()
                        ?: return Result.failure(Exception(AppStrings.get(R.string.product_not_found)))
                    sessionStore.getUserId()?.let { userId ->
                        productDao?.upsert(body.toCachedEntity(userId, clock()))
                    }
                    return Result.success(body)
                }
                if (response.code() == 404) {
                    lastError = Exception(AppStrings.get(R.string.product_not_found))
                    continue
                }
                return Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_find_product))))
            }
            Result.failure(lastError ?: Exception(AppStrings.get(R.string.product_not_found)))
        } catch (_: IOException) {
            // Offline SKU lookup against Room cache (read-only).
            val userId = sessionStore.getUserId()
            if (userId != null) {
                val candidates = ProductSkuCodes.lookupCandidates(sku).map { it.lowercase() }.toSet()
                val match = productDao?.getAll(userId)?.firstOrNull { product ->
                    val s = product.sku?.lowercase().orEmpty()
                    s.isNotEmpty() && (s in candidates || candidates.any { s.contains(it) || it.contains(s) })
                }
                if (match != null) return Result.success(match.toDto())
            }
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_find_product)))
        }
    }

    suspend fun createProduct(
        request: CreateProductRequest,
        categoryName: String? = null
    ): WriteResult<ProductDto> {
        return try {
            val response = api.createProduct(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return WriteResult.Failed(AppStrings.get(R.string.error_failed_create_product))
                sessionStore.getUserId()?.let { userId ->
                    productDao?.upsert(body.toCachedEntity(userId, clock()))
                    touchDashboard(userId)
                }
                WriteResult.Synced(body)
            } else {
                WriteResult.Failed(errorMessage(response, AppStrings.get(R.string.error_failed_create_product)))
            }
        } catch (_: IOException) {
            enqueueOfflineCreate(request, categoryName)
        } catch (e: Exception) {
            WriteResult.Failed(e.message ?: AppStrings.get(R.string.error_failed_create_product))
        }
    }

    suspend fun updateProduct(
        id: Int,
        request: UpdateProductRequest,
        categoryName: String? = null
    ): WriteResult<ProductDto> {
        // Temp local products: coalesce into the pending CREATE payload (no remote PUT yet).
        if (LocalTempIds.isTemporary(id)) {
            return coalesceOfflineUpdate(id, request, categoryName)
        }
        return try {
            val response = api.updateProduct(authHeader(), id, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return WriteResult.Failed(AppStrings.get(R.string.error_failed_update_product))
                sessionStore.getUserId()?.let { userId ->
                    productDao?.upsert(body.toCachedEntity(userId, clock()))
                    touchDashboard(userId)
                }
                WriteResult.Synced(body)
            } else {
                WriteResult.Failed(errorMessage(response, AppStrings.get(R.string.error_failed_update_product)))
            }
        } catch (_: IOException) {
            enqueueOfflineUpdate(id, request, categoryName)
        } catch (e: Exception) {
            WriteResult.Failed(e.message ?: AppStrings.get(R.string.error_failed_update_product))
        }
    }

    suspend fun deleteProduct(id: Int): WriteResult<Unit> {
        if (LocalTempIds.isTemporary(id)) {
            return cancelOfflineCreate(id)
        }
        return try {
            val response = api.deleteProduct(authHeader(), id)
            if (response.isSuccessful || response.code() == 204) {
                sessionStore.getUserId()?.let { userId ->
                    productDao?.deleteById(userId, id)
                    touchDashboard(userId)
                }
                WriteResult.Synced(Unit)
            } else {
                WriteResult.Failed(errorMessage(response, AppStrings.get(R.string.error_failed_delete_product)))
            }
        } catch (_: IOException) {
            enqueueOfflineDelete(id)
        } catch (e: Exception) {
            WriteResult.Failed(e.message ?: AppStrings.get(R.string.error_failed_delete_product))
        }
    }

    /**
     * Uploads a product image before create/update. Returns the stored image URL path.
     * Callers must not save a product with a local-only image if this fails.
     * Image upload is never queued offline.
     */
    suspend fun uploadProductImage(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        return try {
            val mediaType = mimeType.toMediaTypeOrNull()
                ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("image", fileName, body)
            val response = api.uploadProductImage(authHeader(), part)
            if (response.isSuccessful) {
                val uploaded = response.body()?.imageUrl?.trim().orEmpty()
                if (uploaded.isEmpty()) {
                    Result.failure(Exception(AppStrings.get(R.string.error_image_upload_failed)))
                } else {
                    Result.success(uploaded)
                }
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_image_upload_failed))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_upload_image_connection)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_image_upload_failed)))
        }
    }

    private suspend fun enqueueOfflineCreate(
        request: CreateProductRequest,
        categoryName: String?
    ): WriteResult<ProductDto> {
        val userId = sessionStore.getUserId()
            ?: return WriteResult.Failed(AppStrings.get(R.string.error_not_signed_in))
        val dao = productDao
        val qDao = pendingDao
        if (dao == null || qDao == null) {
            return WriteResult.Failed(AppStrings.get(R.string.error_unable_reach_server))
        }
        val localId = LocalTempIds.nextProductId()
        val payload = ProductWritePayload.fromCreate(request, categoryName)
        val now = clock()
        val dto = payload.toProductDto(localId)
        dao.upsert(dto.toCachedEntity(userId, now))
        touchDashboard(userId)
        qDao.upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.PRODUCT,
                localEntityId = localId.toString(),
                remoteEntityId = null,
                payloadJson = ProductWritePayload.toJson(payload),
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
        triggerSync()
        return WriteResult.Queued(dto)
    }

    private suspend fun enqueueOfflineUpdate(
        id: Int,
        request: UpdateProductRequest,
        categoryName: String?
    ): WriteResult<ProductDto> {
        val userId = sessionStore.getUserId()
            ?: return WriteResult.Failed(AppStrings.get(R.string.error_not_signed_in))
        val dao = productDao
        val qDao = pendingDao
        if (dao == null || qDao == null) {
            return WriteResult.Failed(AppStrings.get(R.string.error_unable_reach_server))
        }
        val payload = ProductWritePayload.fromUpdate(request, categoryName)
        val now = clock()
        val dto = payload.toProductDto(id)
        dao.upsert(dto.toCachedEntity(userId, now))
        touchDashboard(userId)

        // Coalesce with an existing PENDING UPDATE for the same entity.
        val existing = qDao.getActiveForEntity(userId, PendingEntityType.PRODUCT, id.toString())
            .firstOrNull { it.operationType == PendingOpType.UPDATE && it.status == PendingOpStatus.PENDING }
        if (existing != null) {
            qDao.upsert(
                existing.copy(
                    payloadJson = ProductWritePayload.toJson(payload),
                    updatedAt = now,
                    lastError = null
                )
            )
        } else {
            qDao.upsert(
                PendingOperationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    operationType = PendingOpType.UPDATE,
                    entityType = PendingEntityType.PRODUCT,
                    localEntityId = id.toString(),
                    remoteEntityId = id,
                    payloadJson = ProductWritePayload.toJson(payload),
                    status = PendingOpStatus.PENDING,
                    retryCount = 0,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        triggerSync()
        return WriteResult.Queued(dto)
    }

    private suspend fun coalesceOfflineUpdate(
        localId: Int,
        request: UpdateProductRequest,
        categoryName: String?
    ): WriteResult<ProductDto> {
        val userId = sessionStore.getUserId()
            ?: return WriteResult.Failed(AppStrings.get(R.string.error_not_signed_in))
        val dao = productDao
        val qDao = pendingDao
        if (dao == null || qDao == null) {
            return WriteResult.Failed(AppStrings.get(R.string.error_unable_reach_server))
        }
        val payload = ProductWritePayload.fromUpdate(request, categoryName)
        val now = clock()
        val dto = payload.toProductDto(localId)
        dao.upsert(dto.toCachedEntity(userId, now))
        touchDashboard(userId)

        val createOp = qDao.getActiveForEntity(userId, PendingEntityType.PRODUCT, localId.toString())
            .firstOrNull { it.operationType == PendingOpType.CREATE }
        if (createOp != null) {
            qDao.upsert(
                createOp.copy(
                    payloadJson = ProductWritePayload.toJson(payload),
                    updatedAt = now,
                    status = PendingOpStatus.PENDING,
                    lastError = null
                )
            )
        } else {
            // No CREATE left — treat as normal offline update (unlikely for temp ids).
            return enqueueOfflineUpdate(localId, request, categoryName)
        }
        triggerSync()
        return WriteResult.Queued(dto)
    }

    private suspend fun enqueueOfflineDelete(id: Int): WriteResult<Unit> {
        val userId = sessionStore.getUserId()
            ?: return WriteResult.Failed(AppStrings.get(R.string.error_not_signed_in))
        val dao = productDao
        val qDao = pendingDao
        if (dao == null || qDao == null) {
            return WriteResult.Failed(AppStrings.get(R.string.error_unable_reach_server))
        }
        val now = clock()
        dao.deleteById(userId, id)
        touchDashboard(userId)

        // Drop pending CREATE/UPDATE for this id; replace with DELETE.
        val related = qDao.getActiveForEntity(userId, PendingEntityType.PRODUCT, id.toString())
        for (op in related) {
            when (op.operationType) {
                PendingOpType.CREATE -> {
                    qDao.deleteById(op.id)
                    triggerSync()
                    return WriteResult.Queued(Unit)
                }
                PendingOpType.UPDATE -> qDao.deleteById(op.id)
            }
        }
        qDao.upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                operationType = PendingOpType.DELETE,
                entityType = PendingEntityType.PRODUCT,
                localEntityId = id.toString(),
                remoteEntityId = id,
                payloadJson = "{}",
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
        triggerSync()
        return WriteResult.Queued(Unit)
    }

    private suspend fun cancelOfflineCreate(localId: Int): WriteResult<Unit> {
        val userId = sessionStore.getUserId()
            ?: return WriteResult.Failed(AppStrings.get(R.string.error_not_signed_in))
        productDao?.deleteById(userId, localId)
        pendingDao?.getActiveForEntity(userId, PendingEntityType.PRODUCT, localId.toString())
            ?.forEach { pendingDao?.deleteById(it.id) }
        touchDashboard(userId)
        return WriteResult.Queued(Unit)
    }


    private suspend fun touchDashboard(userId: Int) {
        val db = database ?: return
        LocalCacheReconciler.recalculateDashboardFromProducts(db, userId, clock)
    }

    private fun triggerSync() {
        onEnqueueSync?.invoke()
        appContext?.let { SyncScheduler.enqueueSync(it, expedited = true) }
    }

    private suspend fun fallbackProducts(userId: Int?): CacheResult<List<ProductDto>> {
        if (userId == null) {
            return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
        }
        val cached = productDao?.getAll(userId).orEmpty()
        return if (cached.isNotEmpty()) {
            CacheResult.Cached(cached.map { it.toDto() }, cached.maxOf { it.cachedAt })
        } else {
            CacheResult.Empty
        }
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
