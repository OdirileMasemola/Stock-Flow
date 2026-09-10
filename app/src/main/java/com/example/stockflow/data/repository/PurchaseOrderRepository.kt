package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CreatePurchaseOrderRequest
import com.example.stockflow.data.remote.PurchaseOrderApi
import com.example.stockflow.data.remote.PurchaseOrderDto
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.UpdatePurchaseOrderRequest
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the Ktor purchase-order endpoints using the JWT from [SessionStore].
 */
class PurchaseOrderRepository(
    private val api: PurchaseOrderApi = RetrofitClient.purchaseOrderApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getPurchaseOrders(): Result<List<PurchaseOrderDto>> {
        return try {
            val response = api.getPurchaseOrders(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load purchase orders")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load purchase orders"))
        }
    }

    suspend fun getPurchaseOrder(id: Int): Result<PurchaseOrderDto> {
        return try {
            val response = api.getPurchaseOrder(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Purchase order not found"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load purchase order")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load purchase order"))
        }
    }

    suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest): Result<PurchaseOrderDto> {
        return try {
            val response = api.createPurchaseOrder(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to create purchase order"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to create purchase order")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to create purchase order"))
        }
    }

    suspend fun updatePurchaseOrder(
        id: Int,
        request: UpdatePurchaseOrderRequest
    ): Result<PurchaseOrderDto> {
        return try {
            val response = api.updatePurchaseOrder(authHeader(), id, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to update purchase order"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to update purchase order")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to update purchase order"))
        }
    }

    suspend fun receivePurchaseOrder(id: Int): Result<PurchaseOrderDto> {
        return try {
            val response = api.receivePurchaseOrder(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to receive purchase order"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to receive purchase order")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to receive purchase order"))
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
