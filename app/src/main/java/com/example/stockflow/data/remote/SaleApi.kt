package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Sales/POS endpoints.
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface SaleApi {
    @GET("api/sales")
    suspend fun getSales(
        @Header("Authorization") authorization: String
    ): Response<List<SaleDto>>

    @GET("api/sales/{id}")
    suspend fun getSale(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<SaleDto>

    @POST("api/sales")
    suspend fun createSale(
        @Header("Authorization") authorization: String,
        @Body request: CreateSaleRequest
    ): Response<SaleDto>
}
