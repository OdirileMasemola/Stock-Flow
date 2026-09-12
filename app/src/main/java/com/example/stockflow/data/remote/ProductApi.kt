package com.example.stockflow.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Product CRUD endpoints.
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface ProductApi {
    @GET("api/products")
    suspend fun getProducts(
        @Header("Authorization") authorization: String
    ): Response<List<ProductDto>>

    @GET("api/products/low-stock")
    suspend fun getLowStockProducts(
        @Header("Authorization") authorization: String
    ): Response<List<ProductDto>>

    @GET("api/products/{id}")
    suspend fun getProduct(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<ProductDto>

    @GET("api/products/sku/{sku}")
    suspend fun getProductBySku(
        @Header("Authorization") authorization: String,
        @Path("sku") sku: String
    ): Response<ProductDto>

    @POST("api/products")
    suspend fun createProduct(
        @Header("Authorization") authorization: String,
        @Body request: CreateProductRequest
    ): Response<ProductDto>

    @PUT("api/products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body request: UpdateProductRequest
    ): Response<ProductDto>

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<Unit>

    @Multipart
    @POST("api/products/images")
    suspend fun uploadProductImage(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): Response<ProductImageUploadResponse>
}
