package com.example.stockflow.services.activity

import com.example.stockflow.models.ActivityItemResponse
import com.example.stockflow.services.FirebaseAdminApp
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Firestore-backed activity / audit history.
 *
 * Collection path: `businesses/{businessId}/activity/{activityId}`
 *
 * Writes use the Firebase Admin SDK (same credentials as FCM). Failures are
 * logged and never thrown to callers — product CRUD must not fail because of Firestore.
 */
interface ActivityStore {
    suspend fun write(
        businessId: String,
        type: String,
        message: String,
        userId: Int,
        productId: Int? = null,
        productName: String? = null,
        metadata: Map<String, String>? = null
    ): String?

    suspend fun listRecent(businessId: String, limit: Int = 20): List<ActivityItemResponse>
}

class FirestoreActivityService(
    private val firestoreProvider: () -> Firestore? = { resolveFirestore() }
) : ActivityStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun write(
        businessId: String,
        type: String,
        message: String,
        userId: Int,
        productId: Int?,
        productName: String?,
        metadata: Map<String, String>?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val db = firestoreProvider()
            if (db == null) {
                logger.debug("Firestore unavailable — skipping activity write type={}", type)
                return@withContext null
            }
            val activityId = UUID.randomUUID().toString()
            val data = hashMapOf<String, Any>(
                "type" to type,
                "message" to message,
                "userId" to userId,
                "timestamp" to FieldValue.serverTimestamp()
            )
            if (productId != null) data["productId"] = productId
            if (!productName.isNullOrBlank()) data["productName"] = productName
            if (!metadata.isNullOrEmpty()) data["metadata"] = metadata

            db.collection("businesses")
                .document(businessId)
                .collection("activity")
                .document(activityId)
                .set(data)
                .get()

            logger.info(
                "Activity written businesses/{}/activity/{} type={}",
                businessId,
                activityId,
                type
            )
            activityId
        } catch (e: Exception) {
            logger.error("Firestore activity write failed (non-fatal) type={}", type, e)
            null
        }
    }

    override suspend fun listRecent(businessId: String, limit: Int): List<ActivityItemResponse> =
        withContext(Dispatchers.IO) {
            try {
                val db = firestoreProvider()
                    ?: return@withContext emptyList()
                val capped = limit.coerceIn(1, 50)
                val snapshot = db.collection("businesses")
                    .document(businessId)
                    .collection("activity")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(capped)
                    .get()
                    .get()

                snapshot.documents.map { doc ->
                    val ts = doc.getTimestamp("timestamp")
                    val instant = ts?.toDate()?.toInstant() ?: Instant.EPOCH
                    @Suppress("UNCHECKED_CAST")
                    val meta = doc.get("metadata") as? Map<String, Any?>
                    ActivityItemResponse(
                        id = doc.id,
                        type = doc.getString("type") ?: "",
                        message = doc.getString("message") ?: "",
                        userId = (doc.getLong("userId") ?: doc.get("userId")?.toString()?.toLongOrNull() ?: 0L).toInt(),
                        productId = doc.getLong("productId")?.toInt()
                            ?: (doc.get("productId") as? Number)?.toInt(),
                        productName = doc.getString("productName"),
                        timestamp = instant.toString(),
                        metadata = meta?.mapNotNull { (k, v) ->
                            v?.let { k to it.toString() }
                        }?.toMap()
                    )
                }
            } catch (e: Exception) {
                logger.error("Firestore activity read failed businessId={}", businessId, e)
                throw e
            }
        }

    companion object {
        private fun resolveFirestore(): Firestore? {
            if (!FirebaseAdminApp.ensureInitialized()) return null
            return try {
                FirestoreClient.getFirestore()
            } catch (e: Exception) {
                LoggerFactory.getLogger(FirestoreActivityService::class.java)
                    .error("Failed to obtain Firestore client", e)
                null
            }
        }
    }
}

/** In-memory store for unit tests. */
class InMemoryActivityStore : ActivityStore {
    data class Record(
        val id: String,
        val businessId: String,
        val type: String,
        val message: String,
        val userId: Int,
        val productId: Int?,
        val productName: String?,
        val metadata: Map<String, String>?,
        val timestamp: Instant = Instant.now()
    )

    val records = mutableListOf<Record>()
    var writeFails: Boolean = false
    var readFails: Boolean = false

    override suspend fun write(
        businessId: String,
        type: String,
        message: String,
        userId: Int,
        productId: Int?,
        productName: String?,
        metadata: Map<String, String>?
    ): String? {
        if (writeFails) return null
        val id = UUID.randomUUID().toString()
        records += Record(id, businessId, type, message, userId, productId, productName, metadata)
        return id
    }

    override suspend fun listRecent(businessId: String, limit: Int): List<ActivityItemResponse> {
        if (readFails) throw RuntimeException("Firestore read failed")
        return records
            .filter { it.businessId == businessId }
            .sortedByDescending { it.timestamp }
            .take(limit.coerceIn(1, 50))
            .map {
                ActivityItemResponse(
                    id = it.id,
                    type = it.type,
                    message = it.message,
                    userId = it.userId,
                    productId = it.productId,
                    productName = it.productName,
                    timestamp = it.timestamp.toString(),
                    metadata = it.metadata
                )
            }
    }
}
