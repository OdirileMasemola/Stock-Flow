package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Purchase order endpoints.
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface PurchaseOrderApi {
    @GET("api/purchase-orders")
    suspend fun getPurchaseOrders(
        @Header("Authorization") authorization: String
    ): Response<List<PurchaseOrderDto>>

    @GET("api/purchase-orders/{id}")
    suspend fun getPurchaseOrder(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<PurchaseOrderDto>

    @POST("api/purchase-orders")
    suspend fun createPurchaseOrder(
        @Header("Authorization") authorization: String,
        @Body request: CreatePurchaseOrderRequest
    ): Response<PurchaseOrderDto>

    @PUT("api/purchase-orders/{id}")
    suspend fun updatePurchaseOrder(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body request: UpdatePurchaseOrderRequest
    ): Response<PurchaseOrderDto>

    @POST("api/purchase-orders/{id}/receive")
    suspend fun receivePurchaseOrder(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<PurchaseOrderDto>
}
