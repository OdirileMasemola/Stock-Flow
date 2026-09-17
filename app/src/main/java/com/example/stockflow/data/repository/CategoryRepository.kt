package com.example.stockflow.data.repository

import android.content.Context
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.CategoryCacheDao
import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationDao
import com.example.stockflow.data.local.cache.PendingOperationEntity
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CategoryApi
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateCategoryRequest
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.sync.CategoryWritePayload
import com.example.stockflow.data.sync.LocalTempIds
import com.example.stockflow.data.sync.PendingOverlayApplier
import com.example.stockflow.data.sync.SyncScheduler
import com.example.stockflow.data.sync.WriteResult
import com.example.stockflow.ui.common.AppStrings
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import retrofit2.Response
import java.io.IOException
import java.util.UUID

/**
 * Talks to the Ktor category endpoints using the JWT from [SessionStore].
 * Offline CREATE caches locally + queues CATEGORY op (synced before PRODUCT).
 */
class CategoryRepository(
    private val api: CategoryApi = RetrofitClient.categoryApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val appContext: Context? = null,
    private val onEnqueueSync: (() -> Unit)? = null
) {
    private val gson = Gson()
    private val categoryDao: CategoryCacheDao? get() = database?.categoryDao()
    private val pendingDao: PendingOperationDao? get() = database?.pendingOperationDao()

    fun observeCategories(): Flow<List<CategoryDto>> {
        val userId = sessionStore.getUserId() ?: return flowOf(emptyList())
        val dao = categoryDao ?: return flowOf(emptyList())
        return dao.observeAll(userId).map { list -> list.map { it.toDto() } }
    }

    suspend fun getCategories(): CacheResult<List<CategoryDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getCategories(authHeader())
            if (response.isSuccessful) {
                val data = response.body().orEmpty()
                if (userId != null) {
                    val cachedAt = clock()
                    categoryDao?.replaceAll(userId, data.map { it.toCachedEntity(userId, cachedAt) })
                    val cDao = categoryDao
                    val qDao = pendingDao
                    if (cDao != null && qDao != null) {
                        PendingOverlayApplier.applyCategoryOverlays(userId, cDao, qDao, clock)
                    }
                }
                val merged = if (userId != null) {
                    categoryDao?.getAll(userId)?.map { it.toDto() } ?: data
                } else {
                    data
                }
                CacheResult.Fresh(merged)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_categories)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = categoryDao?.getAll(userId).orEmpty()
            if (cached.isNotEmpty()) {
                CacheResult.Cached(cached.map { it.toDto() }, cached.maxOf { it.cachedAt })
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_categories))
        }
    }

    /**
     * Find-or-create by name. Online: API. Offline: reuse cache or queue CATEGORY CREATE.
     */
    suspend fun findOrCreateCategory(name: String): WriteResult<CategoryDto> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return WriteResult.Failed(AppStrings.get(R.string.error_category_required))
        }
        // Prefer an existing cached match (case-insensitive) — never duplicate locally.
        sessionStore.getUserId()?.let { userId ->
            categoryDao?.getAll(userId).orEmpty().firstOrNull {
                it.name.equals(trimmed, ignoreCase = true)
            }?.let { return WriteResult.Synced(it.toDto()) }
        }

        return try {
            val response = api.createCategory(
                authHeader(),
                CreateCategoryRequest(name = trimmed)
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return WriteResult.Failed(AppStrings.get(R.string.error_failed_resolve_category))
                sessionStore.getUserId()?.let { userId ->
                    categoryDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                WriteResult.Synced(body)
            } else {
                WriteResult.Failed(errorMessage(response, AppStrings.get(R.string.error_failed_resolve_category)))
            }
        } catch (_: IOException) {
            enqueueOfflineCreate(CreateCategoryRequest(name = trimmed))
        } catch (e: Exception) {
            WriteResult.Failed(e.message ?: AppStrings.get(R.string.error_failed_resolve_category))
        }
    }

    private suspend fun enqueueOfflineCreate(request: CreateCategoryRequest): WriteResult<CategoryDto> {
        val userId = sessionStore.getUserId()
            ?: return WriteResult.Failed(AppStrings.get(R.string.error_not_signed_in))
        val dao = categoryDao
        val qDao = pendingDao
        if (dao == null || qDao == null) {
            return WriteResult.Failed(AppStrings.get(R.string.error_unable_reach_server))
        }
        // Duplicate pending CREATE with same name → reuse that local row.
        val pending = qDao.getAllForUser(userId).filter {
            it.entityType == PendingEntityType.CATEGORY &&
                it.operationType == PendingOpType.CREATE &&
                it.status != PendingOpStatus.FAILED
        }
        for (op in pending) {
            val payload = try {
                CategoryWritePayload.fromJson(op.payloadJson)
            } catch (_: Exception) {
                continue
            }
            if (payload.name.equals(request.name.trim(), ignoreCase = true)) {
                val localId = op.localEntityId.toIntOrNull() ?: continue
                val cached = dao.getById(userId, localId)
                if (cached != null) return WriteResult.Queued(cached.toDto())
            }
        }

        val localId = LocalTempIds.nextCategoryId()
        val payload = CategoryWritePayload.fromCreate(request)
        val now = clock()
        val dto = payload.toCategoryDto(localId)
        dao.upsert(dto.toCachedEntity(userId, now))
        qDao.upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.CATEGORY,
                localEntityId = localId.toString(),
                remoteEntityId = null,
                payloadJson = CategoryWritePayload.toJson(payload),
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

    private fun triggerSync() {
        onEnqueueSync?.invoke()
        appContext?.let { SyncScheduler.enqueueSync(it, expedited = true) }
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
