package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @GET("api/roles")
    suspend fun getRoles(): Response<List<RoleDto>>

    @POST("api/auth/google")
    suspend fun authenticateGoogle(@Body request: GoogleAuthRequest): Response<LoginResponse>
}
