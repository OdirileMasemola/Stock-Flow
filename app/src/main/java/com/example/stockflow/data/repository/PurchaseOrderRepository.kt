package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.PurchaseOrderCacheDao
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toCachedOrderEntity
import com.example.stockflow.data.local.cache.toDto
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
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val poDao: PurchaseOrderCacheDao? get() = database?.purchaseOrderDao()

    suspend fun getPurchaseOrders(): CacheResult<List<PurchaseOrderDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getPurchaseOrders(authHeader())
            if (response.isSuccessful) {
                val data = response.body().orEmpty()
                if (userId != null) {
                    val cachedAt = clock()
                    val orders = data.map { it.toCachedOrderEntity(userId, cachedAt) }
                    val items = data.flatMap { po ->
                        po.items.map { it.toCachedEntity(userId, po.id, cachedAt) }
                    }
                    poDao?.replaceAll(userId, orders, items)
                }
                CacheResult.Fresh(data)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_purchase_orders)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val orders = poDao?.getAllOrders(userId).orEmpty()
            if (orders.isNotEmpty()) {
                val dtos = orders.map { order ->
                    val items = poDao?.getItemsForOrder(userId, order.id).orEmpty()
                    order.toDto(items)
                }
                CacheResult.Cached(dtos, orders.maxOf { it.cachedAt })
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_purchase_orders))
        }
    }

    suspend fun getPurchaseOrder(id: Int): CacheResult<PurchaseOrderDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getPurchaseOrder(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.po_not_found))
                if (userId != null) {
                    val cachedAt = clock()
                    poDao?.upsertOrderWithItems(
                        body.toCachedOrderEntity(userId, cachedAt),
                        body.items.map { it.toCachedEntity(userId, body.id, cachedAt) }
                    )
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_purchase_order)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val order = poDao?.getOrderById(userId, id)
            if (order != null) {
                val items = poDao?.getItemsForOrder(userId, id).orEmpty()
                CacheResult.Cached(order.toDto(items), order.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_purchase_order))
        }
    }

    suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest): Result<PurchaseOrderDto> {
        return try {
            val response = api.createPurchaseOrder(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_create_po)))
                sessionStore.getUserId()?.let { userId ->
                    val cachedAt = clock()
                    poDao?.upsertOrderWithItems(
                        body.toCachedOrderEntity(userId, cachedAt),
                        body.items.map { it.toCachedEntity(userId, body.id, cachedAt) }
                    )
                }
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
                sessionStore.getUserId()?.let { userId ->
                    val cachedAt = clock()
                    poDao?.upsertOrderWithItems(
                        body.toCachedOrderEntity(userId, cachedAt),
                        body.items.map { it.toCachedEntity(userId, body.id, cachedAt) }
                    )
                }
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
                sessionStore.getUserId()?.let { userId ->
                    val cachedAt = clock()
                    poDao?.upsertOrderWithItems(
                        body.toCachedOrderEntity(userId, cachedAt),
                        body.items.map { it.toCachedEntity(userId, body.id, cachedAt) }
                    )
                }
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
