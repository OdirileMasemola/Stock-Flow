package com.example.stockflow.services.storage

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.BadRequestException
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * Uploads images to Supabase Storage via the REST API using the service-role key
 * (server-side only — never expose to Android clients).
 *
 * Returns public object URLs of the form:
 * `{SUPABASE_URL}/storage/v1/object/public/{bucket}/{folder}/{uuid}.{ext}`
 */
class SupabaseImageStorage(
    private val supabaseUrl: String = AppConfig.supabaseUrl
        ?: throw IllegalStateException("SUPABASE_URL is required when STORAGE_PROVIDER=supabase"),
    private val serviceRoleKey: String = AppConfig.supabaseServiceRoleKey
        ?: throw IllegalStateException("SUPABASE_SERVICE_ROLE_KEY is required when STORAGE_PROVIDER=supabase"),
    private val bucket: String = AppConfig.supabaseStorageBucket,
    private val maxBytes: Int = AppConfig.uploadMaxBytes,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
) : ImageStorage {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val baseUrl = supabaseUrl.trimEnd('/')
    private val publicUrlPrefix = "$baseUrl/storage/v1/object/public/$bucket/"

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

        val objectPath = "${folder.dirName}/${UUID.randomUUID()}.$extension"
        val mime = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.startsWith("image/") }
            ?: ImageValidation.contentTypeForExtension(extension)

        val uploadUri = URI.create("$baseUrl/storage/v1/object/$bucket/$objectPath")
        val request = HttpRequest.newBuilder(uploadUri)
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("apikey", serviceRoleKey)
            .header("Content-Type", mime)
            .header("x-upsert", "true")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            logger.error("Supabase Storage upload failed for {}", objectPath, e)
            throw IllegalStateException("Failed to upload image to cloud storage")
        }

        if (response.statusCode() !in 200..299) {
            logger.error(
                "Supabase Storage upload rejected (HTTP {}): {}",
                response.statusCode(),
                response.body().take(500)
            )
            throw IllegalStateException("Cloud storage rejected the image upload")
        }

        return "$publicUrlPrefix$objectPath"
    }

    override fun deleteIfManaged(imageUrl: String?) {
        val path = imageUrl?.trim().orEmpty()
        if (path.isEmpty() || !path.startsWith(publicUrlPrefix)) return

        val objectPath = path.removePrefix(publicUrlPrefix)
        if (objectPath.isBlank() || objectPath.contains("..")) return

        // Only delete objects under our known folders.
        val folderOk = ImageFolder.entries.any { objectPath.startsWith("${it.dirName}/") }
        if (!folderOk) return

        val deleteUri = URI.create("$baseUrl/storage/v1/object/$bucket/$objectPath")
        val request = HttpRequest.newBuilder(deleteUri)
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("apikey", serviceRoleKey)
            .DELETE()
            .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299 && response.statusCode() != 404) {
                logger.warn(
                    "Supabase Storage delete returned HTTP {} for {}: {}",
                    response.statusCode(),
                    objectPath,
                    response.body().take(300)
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to delete Supabase object {}", objectPath, e)
        }
    }
}
