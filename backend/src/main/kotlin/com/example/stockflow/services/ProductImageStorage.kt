package com.example.stockflow.services

import com.example.stockflow.services.storage.ImageFolder
import com.example.stockflow.services.storage.ImageStorage
import com.example.stockflow.services.storage.ImageStorageFactory
import java.io.File

/**
 * Facade over [ImageStorage] so existing services keep a stable dependency.
 * Provider is selected via `STORAGE_PROVIDER` (`local` | `supabase`).
 */
class ProductImageStorage(
    private val delegate: ImageStorage = ImageStorageFactory.create()
) {
    fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        delegate.saveProductImage(bytes, originalFileName, contentType)

    fun saveProfileImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        delegate.saveProfileImage(bytes, originalFileName, contentType)

    fun saveBusinessImage(bytes: ByteArray, originalFileName: String?, contentType: String?): String =
        delegate.saveBusinessImage(bytes, originalFileName, contentType)

    fun save(
        folder: ImageFolder,
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): String = delegate.save(folder, bytes, originalFileName, contentType)

    fun deleteIfManaged(imageUrl: String?) = delegate.deleteIfManaged(imageUrl)

    /** Local uploads root, or an empty temp dir when using cloud storage (static route unused). */
    fun uploadsRoot(): File = delegate.uploadsRoot() ?: File(System.getProperty("java.io.tmpdir"), "stockflow-uploads-unused")
}
