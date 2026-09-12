package com.example.stockflow.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

/**
 * Authenticated profile and business/store endpoints.
 */
interface UserApi {
    @GET("api/users/me")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): Response<ProfileDto>

    @PUT("api/users/me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: UpdateProfileRequest
    ): Response<ProfileDto>

    @Multipart
    @POST("api/users/me/image")
    suspend fun uploadProfileImage(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): Response<ImageUploadResponse>
}

interface BusinessApi {
    @GET("api/business")
    suspend fun getBusiness(
        @Header("Authorization") authorization: String
    ): Response<BusinessDto>

    @PUT("api/business")
    suspend fun updateBusiness(
        @Header("Authorization") authorization: String,
        @Body request: UpdateBusinessRequest
    ): Response<BusinessDto>

    @Multipart
    @POST("api/business/image")
    suspend fun uploadBusinessImage(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): Response<ImageUploadResponse>
}
