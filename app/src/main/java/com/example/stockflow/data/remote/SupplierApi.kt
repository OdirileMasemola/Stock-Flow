package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Supplier CRUD endpoints.
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface SupplierApi {
    @GET("api/suppliers")
    suspend fun getSuppliers(
        @Header("Authorization") authorization: String
    ): Response<List<SupplierDto>>

    @GET("api/suppliers/{id}")
    suspend fun getSupplier(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<SupplierDto>

    @POST("api/suppliers")
    suspend fun createSupplier(
        @Header("Authorization") authorization: String,
        @Body request: CreateSupplierRequest
    ): Response<SupplierDto>

    @PUT("api/suppliers/{id}")
    suspend fun updateSupplier(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body request: UpdateSupplierRequest
    ): Response<SupplierDto>

    @DELETE("api/suppliers/{id}")
    suspend fun deleteSupplier(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<Unit>
}
