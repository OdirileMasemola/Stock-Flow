package com.example.stockflow.services.notifications

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.DeviceTokenResponse
import com.example.stockflow.models.RegisterDeviceTokenRequest
import com.example.stockflow.models.UnregisterDeviceTokenRequest
import com.example.stockflow.repositories.DeviceTokenRepository
import com.example.stockflow.repositories.DeviceTokenRepositoryImpl

class DeviceTokenService(
    private val repository: DeviceTokenRepository = DeviceTokenRepositoryImpl()
) {
    suspend fun register(userId: Int, request: RegisterDeviceTokenRequest): DeviceTokenResponse {
        if (userId <= 0) {
            throw BadRequestException("Invalid user")
        }
        val token = request.fcmToken.trim()
        if (token.isEmpty()) {
            throw BadRequestException("fcmToken is required")
        }
        if (token.length > 512) {
            throw BadRequestException("fcmToken is too long")
        }
        val platform = request.platform?.trim()?.takeIf { it.isNotEmpty() }?.lowercase() ?: "android"
        if (platform.length > 32) {
            throw BadRequestException("platform is too long")
        }

        val stored = repository.upsert(userId, token, platform)
        return DeviceTokenResponse(
            id = stored.id,
            platform = stored.platform,
            active = stored.active
        )
    }

    suspend fun unregister(userId: Int, request: UnregisterDeviceTokenRequest) {
        if (userId <= 0) {
            throw BadRequestException("Invalid user")
        }
        val token = request.fcmToken.trim()
        if (token.isEmpty()) {
            throw BadRequestException("fcmToken is required")
        }
        // Soft-delete preferred so history is retained; fall back to hard delete if needed.
        val deactivated = repository.deactivate(userId, token)
        if (!deactivated) {
            repository.delete(userId, token)
        }
    }
}
