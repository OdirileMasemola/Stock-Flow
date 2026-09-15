package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

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
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_purchase_orders))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_purchase_orders)))
        }
    }

    suspend fun getPurchaseOrder(id: Int): Result<PurchaseOrderDto> {
        return try {
            val response = api.getPurchaseOrder(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.po_not_found)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_purchase_order))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_purchase_order)))
        }
    }

    suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest): Result<PurchaseOrderDto> {
        return try {
            val response = api.createPurchaseOrder(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_create_po)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_create_po))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_create_po)))
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
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_update_po)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_update_po))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_update_po)))
        }
    }

    suspend fun receivePurchaseOrder(id: Int): Result<PurchaseOrderDto> {
        return try {
            val response = api.receivePurchaseOrder(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_receive_po)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_receive_po))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_receive_po)))
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
