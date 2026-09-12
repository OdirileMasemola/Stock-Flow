package com.example.stockflow.models

import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val roleId: Int?,
    val profileImageUrl: String? = null
)

/**
 * Profile update payload.
 * Email is intentionally omitted — keep email read-only on the client.
 * [profileImageUrl] may be a relative upload path, or null to clear the picture.
 */
@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val profileImageUrl: String? = null
)

@Serializable
data class ImageUploadResponse(
    val imageUrl: String
)
