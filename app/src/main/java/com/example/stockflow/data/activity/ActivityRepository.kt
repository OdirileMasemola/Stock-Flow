package com.example.stockflow.data.activity

import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ActivityApi
import com.example.stockflow.data.remote.ActivityItemDto
import com.example.stockflow.data.remote.ActivityListDto
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.ui.common.AppStrings
import java.io.IOException

/**
 * Reads recent activity from Firestore via the Ktor Admin-SDK proxy.
 * Does not write to Firestore from Android (writes happen on the backend).
 */
class ActivityRepository(
    private val api: ActivityApi = RetrofitClient.activityApi,
    private val sessionStore: SessionStore
) {
    sealed class Result {
        data class Success(val items: List<ActivityItemDto>, val businessId: String) : Result()
        object Empty : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun getRecentActivity(limit: Int = 15): Result {
        val token = sessionStore.getToken()?.takeIf { it.isNotBlank() }
            ?: return Result.Error(AppStrings.get(R.string.error_not_signed_in))
        return try {
            val response = api.getRecentActivity("Bearer $token", limit)
            if (!response.isSuccessful) {
                return Result.Error(
                    AppStrings.get(R.string.activity_error_load)
                )
            }
            val body: ActivityListDto = response.body() ?: ActivityListDto()
            if (body.items.isEmpty()) Result.Empty
            else Result.Success(body.items, body.businessId)
        } catch (_: IOException) {
            Result.Error(AppStrings.get(R.string.activity_error_unavailable))
        } catch (_: Exception) {
            Result.Error(AppStrings.get(R.string.activity_error_load))
        }
    }
}
