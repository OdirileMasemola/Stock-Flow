package com.example.stockflow.services

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.BadRequestException
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

/**
 * Stores product images on local disk and returns a public relative URL path
 * served by Ktor static files under `/uploads/products/...`.
 */
class ProductImageStorage(
    private val rootDir: File = File(AppConfig.uploadsDir)
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val productsDir: File = File(rootDir, "products")

    init {
        if (!productsDir.exists() && !productsDir.mkdirs()) {
            logger.warn("Unable to create product uploads directory at {}", productsDir.absolutePath)
        }
    }

    fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String {
        if (bytes.isEmpty()) {
            throw BadRequestException("Image file is empty")
        }
        if (bytes.size > MAX_BYTES) {
            throw BadRequestException("Image must be 5 MB or smaller")
        }

        val extension = resolveExtension(originalFileName, contentType)
            ?: throw BadRequestException("Only JPEG, PNG, or WebP images are allowed")

        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(productsDir, fileName)
        target.writeBytes(bytes)

        // Relative URL path stored on the product and returned to clients.
        return "/uploads/products/$fileName"
    }

    fun deleteIfManaged(imageUrl: String?) {
        val path = imageUrl?.trim().orEmpty()
        if (!path.startsWith(MANAGED_PREFIX)) return
        val fileName = path.removePrefix(MANAGED_PREFIX)
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return
        val file = File(productsDir, fileName)
        if (file.exists() && !file.delete()) {
            logger.warn("Failed to delete product image {}", file.absolutePath)
        }
    }

    fun uploadsRoot(): File = rootDir

    private fun resolveExtension(originalFileName: String?, contentType: String?): String? {
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

    companion object {
        private const val MAX_BYTES = 5 * 1024 * 1024
        private const val MANAGED_PREFIX = "/uploads/products/"
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
