package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CreateSaleRequest
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.SaleApi
import com.example.stockflow.data.remote.SaleDto
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the Ktor sales endpoints using the JWT from [SessionStore].
 */
class SaleRepository(
    private val api: SaleApi = RetrofitClient.saleApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getSales(): Result<List<SaleDto>> {
        return try {
            val response = api.getSales(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_sales))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_sales)))
        }
    }

    suspend fun getSale(id: Int): Result<SaleDto> {
        return try {
            val response = api.getSale(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_sale_not_found)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_sale))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_sale)))
        }
    }

    suspend fun createSale(request: CreateSaleRequest): Result<SaleDto> {
        return try {
            val response = api.createSale(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_complete_sale)))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_complete_sale))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_complete_sale)))
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
