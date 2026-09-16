package com.example.stockflow.data.notifications

import android.util.Log
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.NotificationApi
import com.example.stockflow.data.remote.RegisterDeviceTokenRequest
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.UnregisterDeviceTokenRequest

/**
 * Registers / unregisters FCM device tokens with the StockFlow backend.
 * Tokens are never written to Room.
 */
class NotificationRepository(
    private val api: NotificationApi = RetrofitClient.notificationApi,
    private val sessionStore: SessionStore,
    private val tokenStore: FcmTokenStore
) {
    suspend fun registerToken(fcmToken: String): Result<Unit> {
        val jwt = sessionStore.getToken()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val token = fcmToken.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty FCM token"))
        }
        return try {
            val response = api.registerDeviceToken(
                authorization = "Bearer $jwt",
                request = RegisterDeviceTokenRequest(fcmToken = token, platform = "android")
            )
            if (response.isSuccessful) {
                tokenStore.saveRegisteredToken(token)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Token register failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM token register failed", e)
            Result.failure(e)
        }
    }

    suspend fun unregisterCurrentToken(): Result<Unit> {
        val jwt = sessionStore.getToken()?.takeIf { it.isNotBlank() }
        val token = tokenStore.getRegisteredToken()
        if (jwt == null || token.isNullOrBlank()) {
            tokenStore.clear()
            return Result.success(Unit)
        }
        return try {
            api.unregisterDeviceToken(
                authorization = "Bearer $jwt",
                request = UnregisterDeviceTokenRequest(fcmToken = token)
            )
            tokenStore.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "FCM token unregister failed", e)
            tokenStore.clear()
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "NotificationRepository"
    }
}
