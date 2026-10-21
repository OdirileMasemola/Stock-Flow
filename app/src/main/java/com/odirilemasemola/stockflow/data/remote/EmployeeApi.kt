package com.odirilemasemola.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Shop membership and employee management. The employee endpoints are owner-only (403 otherwise).
 * Every call requires the StockFlow JWT as a Bearer token.
 */
interface EmployeeApi {
    @GET("api/shops/me")
    suspend fun getMembership(
        @Header("Authorization") authorization: String
    ): Response<ShopMembershipDto>

    @GET("api/shops/me/employees")
    suspend fun getEmployees(
        @Header("Authorization") authorization: String
    ): Response<List<EmployeeDto>>

    @POST("api/shops/me/employees")
    suspend fun addEmployee(
        @Header("Authorization") authorization: String,
        @Body request: AddEmployeeRequest
    ): Response<EmployeeDto>

    @PUT("api/shops/me/employees/{id}")
    suspend fun updateRole(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body request: UpdateEmployeeRoleRequest
    ): Response<EmployeeDto>

    @DELETE("api/shops/me/employees/{id}")
    suspend fun removeEmployee(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): Response<Unit>
}
