package com.example.stockflow.services.storage

import java.io.File

/**
 * Abstraction for product / profile / business image blobs.
 * Implementations return a usable URL (relative for local, absolute public URL for cloud).
 * PostgreSQL stores only the URL string — never binary image data.
 */
interface ImageStorage {
    fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String
    fun saveProfileImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String
    fun saveBusinessImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String

    fun save(
        folder: ImageFolder,
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): String

    /** Deletes the object when [imageUrl] is managed by this provider; no-op otherwise. */
    fun deleteIfManaged(imageUrl: String?)

    /**
     * Local disk root for static `/uploads` serving, or null when this provider
     * does not serve files from the API process.
     */
    fun uploadsRoot(): File? = null
}

enum class ImageFolder(val dirName: String) {
    PRODUCTS("products"),
    PROFILES("profiles"),
    BUSINESSES("businesses");

    /** Relative URL prefix used by the local disk provider. */
    val localUrlPrefix: String get() = "/uploads/$dirName/"
}

object ImageValidation {
    const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024
    val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun resolveExtension(originalFileName: String?, contentType: String?): String? {
        val fromName = originalFileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in ALLOWED_EXTENSIONS }
        if (fromName != null) return fromName

        return when (contentType?.lowercase()?.substringBefore(';')?.trim()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> null
        }
    }

    fun contentTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
