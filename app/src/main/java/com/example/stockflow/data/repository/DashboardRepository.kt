package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.DashboardApi
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.remote.ReportsDto
import com.example.stockflow.data.remote.RetrofitClient
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the Ktor dashboard/report endpoints using the JWT from [SessionStore].
 */
class DashboardRepository(
    private val api: DashboardApi = RetrofitClient.dashboardApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getSummary(): Result<DashboardSummaryDto> {
        return try {
            val response = api.getSummary(authHeader())
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Unable to load dashboard"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load dashboard")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load dashboard"))
        }
    }

    suspend fun getReports(range: String): Result<ReportsDto> {
        return try {
            val response = api.getReports(authHeader(), range)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Unable to load reports"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load reports")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load reports"))
        }
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception("You are not signed in. Please log in again.")
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
