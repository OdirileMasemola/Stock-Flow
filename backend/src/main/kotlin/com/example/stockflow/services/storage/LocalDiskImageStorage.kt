package com.example.stockflow.services.storage

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.BadRequestException
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

/**
 * Local-disk image storage under [AppConfig.uploadsDir].
 * Paths returned as relative URLs (`/uploads/{folder}/…`) for PostgreSQL and static serving.
 */
class LocalDiskImageStorage(
    private val rootDir: File = File(AppConfig.uploadsDir),
    private val maxBytes: Int = AppConfig.uploadMaxBytes
) : ImageStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        ImageFolder.entries.forEach { folder ->
            val dir = File(rootDir, folder.dirName)
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Unable to create uploads directory at {}", dir.absolutePath)
            }
        }
    }

    override fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        save(ImageFolder.PRODUCTS, bytes, originalFileName, contentType)

    override fun saveProfileImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        save(ImageFolder.PROFILES, bytes, originalFileName, contentType)

    override fun saveBusinessImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        save(ImageFolder.BUSINESSES, bytes, originalFileName, contentType)

    override fun save(
        folder: ImageFolder,
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): String {
        if (bytes.isEmpty()) {
            throw BadRequestException("Image file is empty")
        }
        if (bytes.size > maxBytes) {
            throw BadRequestException("Image must be ${maxBytes / (1024 * 1024)} MB or smaller")
        }

        val extension = ImageValidation.resolveExtension(originalFileName, contentType)
            ?: throw BadRequestException("Only JPEG, PNG, or WebP images are allowed")

        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(File(rootDir, folder.dirName), fileName)
        target.parentFile?.mkdirs()
        try {
            target.writeBytes(bytes)
        } catch (e: Exception) {
            logger.error("Failed to write image to {}", target.absolutePath, e)
            throw IllegalStateException("Failed to store image on disk")
        }

        return "${folder.localUrlPrefix}$fileName"
    }

    override fun deleteIfManaged(imageUrl: String?) {
        val path = imageUrl?.trim().orEmpty()
        val folder = ImageFolder.entries.firstOrNull { path.startsWith(it.localUrlPrefix) } ?: return
        val fileName = path.removePrefix(folder.localUrlPrefix)
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return
        val file = File(File(rootDir, folder.dirName), fileName)
        if (file.exists() && !file.delete()) {
            logger.warn("Failed to delete image {}", file.absolutePath)
        }
    }

    override fun uploadsRoot(): File = rootDir
}
