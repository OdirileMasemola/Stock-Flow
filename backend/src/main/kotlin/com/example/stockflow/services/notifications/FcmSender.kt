package com.example.stockflow.services.notifications

/**
 * Abstraction over Firebase Cloud Messaging so unit tests can mock sends.
 */
interface FcmSender {
    /**
     * @return true if the message was accepted by FCM; false if the token is invalid
     *         or sending failed (caller should deactivate invalid tokens).
     */
    fun send(token: String, title: String, body: String, data: Map<String, String>): FcmSendResult
}

enum class FcmSendResult {
    SUCCESS,
    INVALID_TOKEN,
    FAILURE
}
