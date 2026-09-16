package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Category list / find-or-create endpoints.
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface CategoryApi {
    @GET("api/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String
    ): Response<List<CategoryDto>>

    @POST("api/categories")
    suspend fun createCategory(
        @Header("Authorization") authorization: String,
        @Body request: CreateCategoryRequest
    ): Response<CategoryDto>
}
