package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class ActivityItemDto(
    val id: String,
    val type: String,
    val message: String,
    val userId: Int,
    val productId: Int? = null,
    val productName: String? = null,
    val timestamp: String,
    val metadata: Map<String, String>? = null
)

data class ActivityListDto(
    val items: List<ActivityItemDto> = emptyList(),
    val businessId: String = ""
)

interface ActivityApi {
    @GET("api/activity")
    suspend fun getRecentActivity(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20
    ): Response<ActivityListDto>
}
