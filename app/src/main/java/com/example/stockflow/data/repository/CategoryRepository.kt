package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.CategoryCacheDao
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CategoryApi
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateCategoryRequest
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.ui.common.AppStrings
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the Ktor category endpoints using the JWT from [SessionStore].
 */
class CategoryRepository(
    private val api: CategoryApi = RetrofitClient.categoryApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val categoryDao: CategoryCacheDao? get() = database?.categoryDao()

    suspend fun getCategories(): CacheResult<List<CategoryDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getCategories(authHeader())
            if (response.isSuccessful) {
                val data = response.body().orEmpty()
                if (userId != null) {
                    val cachedAt = clock()
                    categoryDao?.replaceAll(userId, data.map { it.toCachedEntity(userId, cachedAt) })
                }
                CacheResult.Fresh(data)
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
     * Find-or-create by name. Backend returns 200 when reusing an existing category,
     * or 201 when a new row is created.
     */
    suspend fun findOrCreateCategory(name: String): Result<CategoryDto> {
        return try {
            val response = api.createCategory(
                authHeader(),
                CreateCategoryRequest(name = name.trim())
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_resolve_category)))
                sessionStore.getUserId()?.let { userId ->
                    categoryDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_resolve_category))))
            }
        } catch (_: IOException) {
            // Offline: reuse an existing cached category by name; never create new categories offline.
            val userId = sessionStore.getUserId()
            if (userId != null) {
                val cached = categoryDao?.getAll(userId).orEmpty().firstOrNull {
                    it.name.equals(name.trim(), ignoreCase = true)
                }
                if (cached != null) {
                    return Result.success(cached.toDto())
                }
            }
            Result.failure(Exception(AppStrings.get(R.string.error_offline_category_requires_existing)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_resolve_category)))
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
