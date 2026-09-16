package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.SupplierCacheDao
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CreateSupplierRequest
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.SupplierApi
import com.example.stockflow.data.remote.SupplierDto
import com.example.stockflow.data.remote.UpdateSupplierRequest
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the Ktor supplier endpoints using the JWT from [SessionStore].
 */
class SupplierRepository(
    private val api: SupplierApi = RetrofitClient.supplierApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val supplierDao: SupplierCacheDao? get() = database?.supplierDao()

    suspend fun getSuppliers(): CacheResult<List<SupplierDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getSuppliers(authHeader())
            if (response.isSuccessful) {
                val data = response.body().orEmpty()
                if (userId != null) {
                    val cachedAt = clock()
                    supplierDao?.replaceAll(userId, data.map { it.toCachedEntity(userId, cachedAt) })
                }
                CacheResult.Fresh(data)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_suppliers)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = supplierDao?.getAll(userId).orEmpty()
            if (cached.isNotEmpty()) {
                CacheResult.Cached(cached.map { it.toDto() }, cached.maxOf { it.cachedAt })
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_suppliers))
        }
    }

    suspend fun getSupplier(id: Int): CacheResult<SupplierDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getSupplier(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.error_supplier_not_found))
                if (userId != null) {
                    supplierDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_supplier)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = supplierDao?.getById(userId, id)
            if (cached != null) {
                CacheResult.Cached(cached.toDto(), cached.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_supplier))
        }
    }

    suspend fun createSupplier(request: CreateSupplierRequest): Result<SupplierDto> {
        return try {
            val response = api.createSupplier(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_create_supplier)))
                sessionStore.getUserId()?.let { userId ->
                    supplierDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_create_supplier))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_create_supplier)))
        }
    }

    suspend fun updateSupplier(id: Int, request: UpdateSupplierRequest): Result<SupplierDto> {
        return try {
            val response = api.updateSupplier(authHeader(), id, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_update_supplier)))
                sessionStore.getUserId()?.let { userId ->
                    supplierDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_update_supplier))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_update_supplier)))
        }
    }

    suspend fun deleteSupplier(id: Int): Result<Unit> {
        return try {
            val response = api.deleteSupplier(authHeader(), id)
            if (response.isSuccessful || response.code() == 204) {
                sessionStore.getUserId()?.let { userId ->
                    supplierDao?.deleteById(userId, id)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_delete_supplier))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_delete_supplier)))
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
