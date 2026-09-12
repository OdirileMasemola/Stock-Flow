package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.Business
import com.example.stockflow.models.ImageUploadResponse
import com.example.stockflow.models.UpdateBusinessRequest
import com.example.stockflow.repositories.BusinessRepository
import com.example.stockflow.repositories.BusinessRepositoryImpl

class BusinessService(
    private val repository: BusinessRepository = BusinessRepositoryImpl(),
    private val imageStorage: ProductImageStorage = ProductImageStorage()
) {
    /**
     * Returns the authenticated user's business, or an empty placeholder when none exists yet.
     */
    suspend fun getBusinessForUser(userId: Int): Business {
        return repository.findByUserId(userId)
            ?: Business(userId = userId, storeName = "")
    }

    suspend fun upsertBusiness(userId: Int, request: UpdateBusinessRequest): Business {
        val storeName = request.storeName.trim()
        if (storeName.isBlank()) {
            throw BadRequestException("Store name cannot be blank")
        }
        if (storeName.length > 100) {
            throw BadRequestException("Store name is too long")
        }

        val ownerName = request.ownerName?.trim()?.takeIf { it.isNotEmpty() }
        val phone = request.phone?.trim()?.takeIf { it.isNotEmpty() }
        val email = request.email?.trim()?.takeIf { it.isNotEmpty() }
        val address = request.address?.trim()?.takeIf { it.isNotEmpty() }
        val imageUrl = request.imageUrl?.trim()?.takeIf { it.isNotEmpty() }

        if (email != null && !isValidEmail(email)) {
            throw BadRequestException("Invalid email format")
        }
        if (phone != null && phone.length > 30) {
            throw BadRequestException("Phone number is too long")
        }
        if (ownerName != null && ownerName.length > 100) {
            throw BadRequestException("Owner name is too long")
        }
        if (address != null && address.length > 255) {
            throw BadRequestException("Address is too long")
        }

        val latitude = request.latitude
        val longitude = request.longitude
        if ((latitude == null) != (longitude == null)) {
            throw BadRequestException("Latitude and longitude must both be set or both be empty")
        }
        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw BadRequestException("Latitude must be between -90 and 90")
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw BadRequestException("Longitude must be between -180 and 180")
        }

        val sanitized = UpdateBusinessRequest(
            storeName = storeName,
            ownerName = ownerName,
            phone = phone,
            email = email,
            address = address,
            imageUrl = imageUrl,
            latitude = latitude,
            longitude = longitude
        )

        val existing = repository.findByUserId(userId)
        val saved = if (existing == null) {
            repository.create(userId, sanitized)
        } else {
            repository.update(userId, sanitized)
                ?: repository.create(userId, sanitized)
        }

        val oldUrl = existing?.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val newUrl = saved.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (oldUrl != null && oldUrl != newUrl) {
            imageStorage.deleteIfManaged(oldUrl)
        }

        return saved
    }

    fun uploadBusinessImage(
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): ImageUploadResponse {
        val imageUrl = imageStorage.saveBusinessImage(bytes, originalFileName, contentType)
        return ImageUploadResponse(imageUrl = imageUrl)
    }

    private fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
}
