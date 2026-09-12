package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Dashboard and reports endpoints.
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface DashboardApi {
    @GET("api/dashboard/summary")
    suspend fun getSummary(
        @Header("Authorization") authorization: String
    ): Response<DashboardSummaryDto>

    @GET("api/reports")
    suspend fun getReports(
        @Header("Authorization") authorization: String,
        @Query("range") range: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<ReportsDto>
}
