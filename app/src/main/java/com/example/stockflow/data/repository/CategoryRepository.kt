package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
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
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getCategories(): Result<List<CategoryDto>> {
        return try {
            val response = api.getCategories(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_categories))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_categories)))
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
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_resolve_category))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
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
