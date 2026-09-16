package com.example.stockflow.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.POST

data class RegisterDeviceTokenRequest(
    val fcmToken: String,
    val platform: String = "android"
)

data class UnregisterDeviceTokenRequest(
    val fcmToken: String
)

data class DeviceTokenResponse(
    val id: Int,
    val platform: String,
    val active: Boolean
)

interface NotificationApi {
    @POST("api/notifications/device-token")
    suspend fun registerDeviceToken(
        @Header("Authorization") authorization: String,
        @Body request: RegisterDeviceTokenRequest
    ): Response<DeviceTokenResponse>

    /** Retrofit DELETE with body — FCM unregister payload. */
    @HTTP(method = "DELETE", path = "api/notifications/device-token", hasBody = true)
    suspend fun unregisterDeviceToken(
        @Header("Authorization") authorization: String,
        @Body request: UnregisterDeviceTokenRequest
    ): Response<Unit>
}
