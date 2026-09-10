package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
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
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getSuppliers(): Result<List<SupplierDto>> {
        return try {
            val response = api.getSuppliers(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load suppliers")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load suppliers"))
        }
    }

    suspend fun getSupplier(id: Int): Result<SupplierDto> {
        return try {
            val response = api.getSupplier(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Supplier not found"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load supplier")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load supplier"))
        }
    }

    suspend fun createSupplier(request: CreateSupplierRequest): Result<SupplierDto> {
        return try {
            val response = api.createSupplier(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to create supplier"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to create supplier")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to create supplier"))
        }
    }

    suspend fun updateSupplier(id: Int, request: UpdateSupplierRequest): Result<SupplierDto> {
        return try {
            val response = api.updateSupplier(authHeader(), id, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to update supplier"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to update supplier")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to update supplier"))
        }
    }

    suspend fun deleteSupplier(id: Int): Result<Unit> {
        return try {
            val response = api.deleteSupplier(authHeader(), id)
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to delete supplier")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to delete supplier"))
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
