package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.DashboardCacheDao
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.DashboardApi
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.remote.ReportsDto
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.ui.common.AppStrings
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import retrofit2.Response
import java.io.IOException

/**
 * Dashboard/report endpoints. Summary is cached in Room for offline display;
 * reports remain online-only (server-generated).
 */
class DashboardRepository(
    private val api: DashboardApi = RetrofitClient.dashboardApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val dashboardDao: DashboardCacheDao? get() = database?.dashboardDao()

    fun observeSummary(): Flow<DashboardSummaryDto?> {
        val userId = sessionStore.getUserId() ?: return flowOf(null)
        val dao = dashboardDao ?: return flowOf(null)
        return dao.observe(userId).map { it?.toDto() }
    }

    suspend fun getSummary(): CacheResult<DashboardSummaryDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getSummary(authHeader())
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.error_unable_load_dashboard))
                if (userId != null) {
                    dashboardDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_dashboard)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = dashboardDao?.get(userId)
            if (cached != null) {
                CacheResult.Cached(cached.toDto(), cached.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_dashboard))
        }
    }

    suspend fun getReports(
        range: String? = null,
        from: String? = null,
        to: String? = null
    ): Result<ReportsDto> {
        return try {
            val response = api.getReports(
                authorization = authHeader(),
                range = range,
                from = from,
                to = to
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_unable_load_reports)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_reports))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_reports)))
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
