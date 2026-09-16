package com.example.stockflow.services.notifications

import com.example.stockflow.services.FirebaseAdminApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory

/**
 * Real FCM sender via Firebase Admin SDK.
 * No-ops (FAILURE) when Admin credentials are not configured.
 */
class FirebaseFcmSender : FcmSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): FcmSendResult {
        if (!FirebaseAdminApp.ensureInitialized()) {
            logger.debug("Skipping FCM send — Firebase Admin not initialized")
            return FcmSendResult.FAILURE
        }

        return try {
            val messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
            data.forEach { (k, v) -> messageBuilder.putData(k, v) }
            val messageId = FirebaseMessaging.getInstance().send(messageBuilder.build())
            logger.info("FCM message sent: {}", messageId)
            FcmSendResult.SUCCESS
        } catch (e: FirebaseMessagingException) {
            if (isInvalidToken(e)) {
                logger.warn("FCM token invalid/unregistered: {}", e.messagingErrorCode)
                FcmSendResult.INVALID_TOKEN
            } else {
                logger.error("FCM send failed: {}", e.messagingErrorCode, e)
                FcmSendResult.FAILURE
            }
        } catch (e: Exception) {
            logger.error("FCM send unexpected error", e)
            FcmSendResult.FAILURE
        }
    }

    private fun isInvalidToken(e: FirebaseMessagingException): Boolean {
        val code = e.messagingErrorCode
        return code == MessagingErrorCode.UNREGISTERED ||
            code == MessagingErrorCode.INVALID_ARGUMENT ||
            code == MessagingErrorCode.SENDER_ID_MISMATCH
    }
}
