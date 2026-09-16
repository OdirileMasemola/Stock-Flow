package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CategoryApi
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateCategoryRequest
import com.example.stockflow.data.remote.RetrofitClient
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
                Result.failure(Exception(errorMessage(response, "Unable to load categories")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load categories"))
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
                    ?: return Result.failure(Exception("Unable to set category"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to set category")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to set category"))
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
