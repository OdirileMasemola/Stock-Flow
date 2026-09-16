package com.example.stockflow.services.notifications

import com.example.stockflow.models.LowStockCrossing
import com.example.stockflow.repositories.DeviceTokenRepository
import com.example.stockflow.repositories.DeviceTokenRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Sends FCM low-stock alerts when inventory **crosses into** low stock.
 *
 * Dedup rule (documented): notify only when
 * `previousStock > minStockLevel && currentStock <= minStockLevel`.
 * Further sales while already low do **not** re-notify.
 *
 * Recipients: all active tokens for Owner-role users plus the acting user
 * (shared store inventory has no per-product owner).
 *
 * FCM failures never fail the originating sale/update — errors are logged only.
 */
class LowStockAlertService(
    private val deviceTokens: DeviceTokenRepository = DeviceTokenRepositoryImpl(),
    private val fcmSender: FcmSender = FirebaseFcmSender(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Fire-and-forget: inspect crossings and push asynchronously so the HTTP
     * response is not blocked by FCM latency.
     */
    fun notifyCrossingsAsync(actingUserId: Int, crossings: List<LowStockCrossing>) {
        val toNotify = crossings.filter { it.crossedIntoLow }
        if (toNotify.isEmpty()) return

        scope.launch {
            try {
                notifyCrossings(actingUserId, toNotify)
            } catch (e: Exception) {
                logger.error("Low-stock FCM notify failed (non-fatal)", e)
            }
        }
    }

    /**
     * Synchronous path for unit tests.
     */
    suspend fun notifyCrossings(actingUserId: Int, crossings: List<LowStockCrossing>) {
        val relevant = crossings.filter { it.crossedIntoLow }
        if (relevant.isEmpty()) return

        val recipientIds = deviceTokens.resolveAlertRecipientUserIds(actingUserId)
        val tokens = deviceTokens.findActiveTokensForUserIds(recipientIds)
        if (tokens.isEmpty()) {
            logger.info(
                "Low-stock alert skipped — no active device tokens for recipients {}",
                recipientIds
            )
            return
        }

        for (crossing in relevant) {
            val title = "Low stock alert"
            val body = "${crossing.productName}: ${crossing.currentStock} left " +
                "(min ${crossing.minStockLevel})"
            val data = mapOf(
                "type" to "low_stock",
                "productId" to crossing.productId.toString(),
                "productName" to crossing.productName,
                "stockLevel" to crossing.currentStock.toString(),
                "minStockLevel" to crossing.minStockLevel.toString()
            )

            for (device in tokens) {
                when (fcmSender.send(device.token, title, body, data)) {
                    FcmSendResult.INVALID_TOKEN -> {
                        try {
                            deviceTokens.markInactiveByToken(device.token)
                        } catch (e: Exception) {
                            logger.warn("Failed to deactivate invalid FCM token", e)
                        }
                    }
                    FcmSendResult.SUCCESS, FcmSendResult.FAILURE -> Unit
                }
            }
        }
    }
}
