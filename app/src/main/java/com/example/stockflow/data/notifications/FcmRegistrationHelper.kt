package com.example.stockflow.data.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.stockflow.data.local.SessionStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Obtains the FCM token and POSTs it to the backend after login / session restore.
 * No-ops when Firebase is not initialized (missing google-services.json).
 */
object FcmRegistrationHelper {
    private const val TAG = "FcmRegistration"
    const val REQUEST_POST_NOTIFICATIONS = 4101

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerIfLoggedIn(context: Context) {
        val appContext = context.applicationContext
        val session = SessionStore(appContext)
        if (!session.hasValidSession()) return
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            Log.d(TAG, "Firebase not initialized; skip FCM registration")
            return
        }

        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                NotificationRepository(
                    sessionStore = session,
                    tokenStore = FcmTokenStore(appContext)
                ).registerToken(token)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to obtain/register FCM token", e)
            }
        }
    }

    fun unregisterOnLogout(context: Context, sessionStore: SessionStore) {
        val appContext = context.applicationContext
        // Capture JWT before clearSession if caller hasn't cleared yet.
        scope.launch {
            try {
                NotificationRepository(
                    sessionStore = sessionStore,
                    tokenStore = FcmTokenStore(appContext)
                ).unregisterCurrentToken()
            } catch (e: Exception) {
                Log.w(TAG, "Unregister on logout failed", e)
            }
        }
    }

    /** Request POST_NOTIFICATIONS on Android 13+ when not already granted. */
    fun requestNotificationPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    fun areNotificationsLikelyEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
