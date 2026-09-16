package com.example.stockflow.services

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.UnauthorizedException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class VerifiedFirebaseUser(
    val uid: String,
    val email: String,
    val displayName: String
)

/**
 * Verifies identity tokens from Google Sign-In.
 *
 * Primary path: Google ID token via Google's tokeninfo endpoint (no Admin SDK required).
 * Fallback: Firebase ID token via Firebase Admin when credentials are configured.
 */
class FirebaseTokenVerifier {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    fun verifyIdToken(idToken: String): VerifiedFirebaseUser {
        if (idToken.isBlank()) {
            throw UnauthorizedException("Invalid Google token")
        }

        // Prefer Google ID token verification — this is what the Android app sends.
        try {
            return verifyGoogleIdToken(idToken)
        } catch (googleError: UnauthorizedException) {
            if (FirebaseAdminApp.ensureInitialized()) {
                logger.info("Google tokeninfo rejected token; trying Firebase Admin verification")
                return verifyFirebaseIdToken(idToken)
            }
            throw googleError
        }
    }

    private fun verifyGoogleIdToken(idToken: String): VerifiedFirebaseUser {
        val encoded = URLEncoder.encode(idToken, StandardCharsets.UTF_8)
        val url = URI("https://oauth2.googleapis.com/tokeninfo?id_token=$encoded").toURL()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        val responseCode = try {
            connection.responseCode
        } catch (e: Exception) {
            logger.error("Failed to reach Google tokeninfo", e)
            throw UnauthorizedException("Unable to verify Google Sign-In. Check the server connection.")
        }

        val body = try {
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }

        if (responseCode !in 200..299 || body.isBlank()) {
            logger.warn("Google tokeninfo failed with HTTP {}", responseCode)
            throw UnauthorizedException("Invalid Google token")
        }

        val payload = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            throw UnauthorizedException("Invalid Google token")
        }

        val audience = payload["aud"]?.jsonPrimitive?.content.orEmpty()
        val expectedAudience = AppConfig.googleWebClientId
        if (audience.isBlank() || audience != expectedAudience) {
            logger.warn("Google token audience mismatch")
            throw UnauthorizedException("Invalid Google token")
        }

        val email = payload["email"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (email.isEmpty()) {
            throw UnauthorizedException("Google account email is required")
        }

        val emailVerified = payload["email_verified"]?.jsonPrimitive?.content
        if (emailVerified != null && emailVerified != "true" && emailVerified != "1") {
            throw UnauthorizedException("Google account email is not verified")
        }

        val uid = payload["sub"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (uid.isEmpty()) {
            throw UnauthorizedException("Invalid Google token")
        }

        val displayName = payload["name"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank {
            email.substringBefore("@")
        }

        return VerifiedFirebaseUser(
            uid = uid,
            email = email,
            displayName = displayName
        )
    }

    private fun verifyFirebaseIdToken(idToken: String): VerifiedFirebaseUser {
        val decoded = try {
            FirebaseAuth.getInstance().verifyIdToken(idToken)
        } catch (_: FirebaseAuthException) {
            throw UnauthorizedException("Invalid Google token")
        } catch (_: IllegalArgumentException) {
            throw UnauthorizedException("Invalid Google token")
        }

        val email = decoded.email?.trim().orEmpty()
        if (email.isEmpty()) {
            throw UnauthorizedException("Google account email is required")
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
