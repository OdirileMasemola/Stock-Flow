package com.example.stockflow.data.remote

data class ProfileDto(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val roleId: Int?,
    val profileImageUrl: String? = null
)

data class UpdateProfileRequest(
    val fullName: String,
    val profileImageUrl: String? = null
)

data class BusinessDto(
    val id: Int? = null,
    val userId: Int? = null,
    val storeName: String? = "",
    val ownerName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class UpdateBusinessRequest(
    val storeName: String,
    val ownerName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class ImageUploadResponse(
    val imageUrl: String
)
