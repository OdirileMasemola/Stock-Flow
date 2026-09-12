package com.example.stockflow.services

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.BadRequestException
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

/**
 * Local disk image storage under [AppConfig.uploadsDir].
 * Paths returned as relative URLs (`/uploads/{folder}/…`) for PostgreSQL and static serving.
 * Compatible with a future Part 3 blob backend: swap this class without changing API contracts.
 */
class ProductImageStorage(
    private val rootDir: File = File(AppConfig.uploadsDir)
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        ImageFolder.entries.forEach { folder ->
            val dir = File(rootDir, folder.dirName)
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Unable to create uploads directory at {}", dir.absolutePath)
            }
        }
    }

    fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        save(ImageFolder.PRODUCTS, bytes, originalFileName, contentType)

    fun saveProfileImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        save(ImageFolder.PROFILES, bytes, originalFileName, contentType)

    fun saveBusinessImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        save(ImageFolder.BUSINESSES, bytes, originalFileName, contentType)

    fun save(
        folder: ImageFolder,
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): String {
        if (bytes.isEmpty()) {
            throw BadRequestException("Image file is empty")
        }
        if (bytes.size > MAX_BYTES) {
            throw BadRequestException("Image must be 5 MB or smaller")
        }

        val extension = resolveExtension(originalFileName, contentType)
            ?: throw BadRequestException("Only JPEG, PNG, or WebP images are allowed")

        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(File(rootDir, folder.dirName), fileName)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)

        return "${folder.urlPrefix}$fileName"
    }

    fun deleteIfManaged(imageUrl: String?) {
        val path = imageUrl?.trim().orEmpty()
        val folder = ImageFolder.entries.firstOrNull { path.startsWith(it.urlPrefix) } ?: return
        val fileName = path.removePrefix(folder.urlPrefix)
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return
        val file = File(File(rootDir, folder.dirName), fileName)
        if (file.exists() && !file.delete()) {
            logger.warn("Failed to delete image {}", file.absolutePath)
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

    enum class ImageFolder(val dirName: String) {
        PRODUCTS("products"),
        PROFILES("profiles"),
        BUSINESSES("businesses");

        val urlPrefix: String get() = "/uploads/$dirName/"
    }

    companion object {
        private const val MAX_BYTES = 5 * 1024 * 1024
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
