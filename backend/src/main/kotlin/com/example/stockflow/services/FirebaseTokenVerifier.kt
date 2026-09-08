package com.example.stockflow.services

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.UnauthorizedException
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import org.slf4j.LoggerFactory
import java.io.FileInputStream

data class VerifiedFirebaseUser(
    val uid: String,
    val email: String,
    val displayName: String
)

class FirebaseTokenVerifier {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Synchronized
    private fun ensureInitialized() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            return
        }

        val credentialsPath = AppConfig.firebaseCredentialsPath
            ?: throw IllegalStateException("Google authentication is not configured")

        val credentialsFile = java.io.File(credentialsPath)
        if (!credentialsFile.isFile) {
            throw IllegalStateException("Google authentication is not configured")
        }

        FileInputStream(credentialsFile).use { stream ->
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()
            FirebaseApp.initializeApp(options)
        }
        logger.info("Firebase Admin SDK initialized")
    }

    fun verifyIdToken(idToken: String): VerifiedFirebaseUser {
        if (idToken.isBlank()) {
            throw UnauthorizedException("Invalid Firebase token")
        }

        try {
            ensureInitialized()
        } catch (_: IllegalStateException) {
            logger.error("Firebase Admin SDK is not configured")
            throw IllegalStateException("Google authentication is not configured")
        }

        val decoded: FirebaseToken = try {
            FirebaseAuth.getInstance().verifyIdToken(idToken)
        } catch (_: FirebaseAuthException) {
            throw UnauthorizedException("Invalid Firebase token")
        } catch (_: IllegalArgumentException) {
            throw UnauthorizedException("Invalid Firebase token")
        }

        val email = decoded.email?.trim().orEmpty()
        if (email.isEmpty()) {
            throw UnauthorizedException("Invalid Firebase token")
        }

        val displayName = decoded.name?.trim().orEmpty().ifBlank {
            email.substringBefore("@")
        }

        return VerifiedFirebaseUser(
            uid = decoded.uid,
            email = email,
            displayName = displayName
        )
    }
}
