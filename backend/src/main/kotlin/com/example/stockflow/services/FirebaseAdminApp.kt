package com.example.stockflow.services

import com.example.stockflow.config.AppConfig
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

/**
 * Shared Firebase Admin SDK bootstrap for Auth verification, FCM, and Firestore activity.
 *
 * Credential resolution order:
 * 1. [AppConfig.firebaseCredentialsJson] (inline JSON — preferred on Render)
 * 2. [AppConfig.firebaseCredentialsPath] / GOOGLE_APPLICATION_CREDENTIALS file
 * 3. Common local filenames under backend / repo root
 */
object FirebaseAdminApp {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var ready: Boolean? = null

    /**
     * Ensures a default [FirebaseApp] exists. Returns false when credentials are missing
     * or initialization failed (callers must degrade gracefully).
     */
    @Synchronized
    fun ensureInitialized(): Boolean {
        ready?.let { return it }

        if (FirebaseApp.getApps().isNotEmpty()) {
            ready = true
            return true
        }

        val credentials = loadCredentials()
        if (credentials == null) {
            logger.info(
                "Firebase Admin credentials not configured " +
                    "(set FIREBASE_CREDENTIALS_JSON or FIREBASE_CREDENTIALS_PATH). " +
                    "FCM push, Firestore activity, and Firebase ID-token fallback are disabled."
            )
            ready = false
            return false
        }

        return try {
            val builder = FirebaseOptions.builder().setCredentials(credentials)
            AppConfig.firebaseProjectId?.let { builder.setProjectId(it) }
            FirebaseApp.initializeApp(builder.build())
            logger.info("Firebase Admin SDK initialized (project={})", AppConfig.firebaseProjectId ?: "from-credentials")
            ready = true
            true
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase Admin SDK", e)
            ready = false
            false
        }
    }

    /** Test helper — resets cached init state. */
    @Synchronized
    internal fun resetForTests() {
        ready = null
    }

    private fun loadCredentials(): GoogleCredentials? {
        val inlineJson = AppConfig.firebaseCredentialsJson?.trim()?.takeIf { it.isNotEmpty() }
        if (inlineJson != null) {
            return try {
                GoogleCredentials.fromStream(
                    ByteArrayInputStream(inlineJson.toByteArray(StandardCharsets.UTF_8))
                )
            } catch (e: Exception) {
                logger.error("Invalid FIREBASE_CREDENTIALS_JSON", e)
                null
            }
        }

        val path = resolveCredentialsPath() ?: return null
        return try {
            FileInputStream(path).use { GoogleCredentials.fromStream(it) }
        } catch (e: Exception) {
            logger.error("Failed to read Firebase credentials file: {}", path, e)
            null
        }
    }

    private fun resolveCredentialsPath(): String? {
        val configured = listOfNotNull(
            AppConfig.firebaseCredentialsPath?.trim()?.takeIf { it.isNotEmpty() },
            System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.trim()?.takeIf { it.isNotEmpty() }
        )
        val candidates = configured + listOf(
            "backend/firebase-service-account.json",
            "./firebase-service-account.json",
            "../firebase-service-account.json"
        )
        return candidates.firstOrNull { File(it).isFile }
    }
}
