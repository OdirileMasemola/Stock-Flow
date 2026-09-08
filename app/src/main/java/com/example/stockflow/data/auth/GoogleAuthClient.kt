package com.example.stockflow.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.stockflow.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthClient(private val activity: Activity) {
    private val credentialManager = CredentialManager.create(activity)

    suspend fun signInWithGoogle(): Result<String> {
        val webClientId = resolveWebClientId()
        if (webClientId.isBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In is not configured."))
        }
        if (FirebaseApp.getApps(activity).isEmpty()) {
            return Result.failure(IllegalStateException("Google Sign-In is not configured."))
        }

        return try {
            val googleIdToken = requestGoogleIdToken(webClientId)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = FirebaseAuth.getInstance()
                .signInWithCredential(firebaseCredential)
                .await()
            val firebaseIdToken = authResult.user
                ?.getIdToken(true)
                ?.await()
                ?.token
            if (firebaseIdToken.isNullOrBlank()) {
                Result.failure(IllegalStateException("Unable to complete Google Sign-In. Please try again."))
            } else {
                Result.success(firebaseIdToken)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("Google Sign-In was cancelled."))
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to complete Google Sign-In. Please try again."))
        }
    }

    private suspend fun requestGoogleIdToken(webClientId: String): String {
        return try {
            requestToken(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId(webClientId)
                    .build()
            )
        } catch (_: NoCredentialException) {
            requestToken(
                GetSignInWithGoogleOption.Builder(webClientId).build()
            )
        }
    }

    private suspend fun requestToken(option: androidx.credentials.CredentialOption): String {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unable to complete Google Sign-In. Please try again.")
    }

    private fun resolveWebClientId(): String {
        val generatedId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        if (generatedId != 0) {
            val value = activity.getString(generatedId)
            if (value.isNotBlank()) {
                return value
            }
        }
        return activity.getString(R.string.google_web_client_id)
    }
}
