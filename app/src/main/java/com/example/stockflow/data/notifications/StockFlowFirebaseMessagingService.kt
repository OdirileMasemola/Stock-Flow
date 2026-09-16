package com.example.stockflow.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.stockflow.MainActivity
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.ui.dashboard.LowStockActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM data/notification payloads and shows a system notification.
 * onNewToken re-registers the token with the StockFlow backend when logged in.
 */
class StockFlowFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val session = SessionStore(this)
        if (!session.hasValidSession()) return
        scope.launch {
            NotificationRepository(
                sessionStore = session,
                tokenStore = FcmTokenStore(this@StockFlowFirebaseMessagingService)
            ).registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"].orEmpty()

        val title = message.notification?.title
            ?: data["title"]
            ?: getString(R.string.fcm_low_stock_title)

        val body = message.notification?.body
            ?: data["body"]
            ?: buildLowStockBody(data)
            ?: getString(R.string.fcm_low_stock_body_fallback)

        showNotification(title, body, type)
    }

    private fun buildLowStockBody(data: Map<String, String>): String? {
        val name = data["productName"] ?: return null
        val stock = data["stockLevel"]
        val min = data["minStockLevel"]
        return if (stock != null && min != null) {
            getString(R.string.fcm_low_stock_body, name, stock, min)
        } else {
            getString(R.string.fcm_low_stock_body_name_only, name)
        }
    }

    private fun showNotification(title: String, body: String, type: String) {
        ensureChannel()

        val target = if (type == "low_stock") {
            Intent(this, LowStockActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pending = PendingIntent.getActivity(
            this,
            0,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                notification
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing notification permission", e)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fcm_channel_low_stock_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.fcm_channel_low_stock_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "StockFlowFCM"
        const val CHANNEL_ID = "stockflow_low_stock"
    }
}
