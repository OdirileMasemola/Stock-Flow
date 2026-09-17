package com.example.stockflow.services.activity

import com.example.stockflow.models.ActivityListResponse
import com.example.stockflow.models.ActivityTypes
import com.example.stockflow.repositories.BusinessRepository
import com.example.stockflow.repositories.BusinessRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Resolves business scope and records activity events (fire-and-forget writes).
 *
 * **businessId**: PostgreSQL `businesses.id` when the user has a store profile;
 * otherwise `"user-{userId}"` so email/password users without a business row
 * still get a stable Firestore path. Products are not migrated to Firestore.
 */
class ActivityService(
    private val store: ActivityStore = FirestoreActivityService(),
    private val businessRepository: BusinessRepository = BusinessRepositoryImpl(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun resolveBusinessId(userId: Int): String {
        val business = businessRepository.findByUserId(userId)
        return business?.id?.toString() ?: "user-$userId"
    }

    fun recordAsync(
        userId: Int,
        type: String,
        message: String,
        productId: Int? = null,
        productName: String? = null,
        metadata: Map<String, String>? = null
    ) {
        if (userId <= 0) {
            logger.debug("Skipping activity write — no acting userId type={}", type)
            return
        }
        scope.launch {
            try {
                record(userId, type, message, productId, productName, metadata)
            } catch (e: Exception) {
                logger.error("Activity recordAsync failed (non-fatal) type={}", type, e)
            }
        }
    }

    suspend fun record(
        userId: Int,
        type: String,
        message: String,
        productId: Int? = null,
        productName: String? = null,
        metadata: Map<String, String>? = null
    ): String? {
        val businessId = resolveBusinessId(userId)
        return store.write(
            businessId = businessId,
            type = type,
            message = message,
            userId = userId,
            productId = productId,
            productName = productName,
            metadata = metadata
        )
    }

    fun recordProductCreated(userId: Int, productId: Int, productName: String) {
        recordAsync(
            userId = userId,
            type = ActivityTypes.PRODUCT_CREATED,
            message = "Product created: $productName",
            productId = productId,
            productName = productName
        )
    }

    fun recordProductUpdated(userId: Int, productId: Int, productName: String) {
        recordAsync(
            userId = userId,
            type = ActivityTypes.PRODUCT_UPDATED,
            message = "Product updated: $productName",
            productId = productId,
            productName = productName
        )
    }

    fun recordProductDeleted(userId: Int, productId: Int, productName: String) {
        recordAsync(
            userId = userId,
            type = ActivityTypes.PRODUCT_DELETED,
            message = "Product deleted: $productName",
            productId = productId,
            productName = productName
        )
    }

    fun recordLowStock(
        userId: Int,
        productId: Int,
        productName: String,
        currentStock: Int,
        minStockLevel: Int
    ) {
        recordAsync(
            userId = userId,
            type = ActivityTypes.LOW_STOCK,
            message = "Low stock: $productName ($currentStock / min $minStockLevel)",
            productId = productId,
            productName = productName,
            metadata = mapOf(
                "currentStock" to currentStock.toString(),
                "minStockLevel" to minStockLevel.toString()
            )
        )
    }

    suspend fun listRecentForUser(userId: Int, limit: Int = 20): ActivityListResponse {
        val businessId = resolveBusinessId(userId)
        val items = store.listRecent(businessId, limit)
        return ActivityListResponse(items = items, businessId = businessId)
    }
}
