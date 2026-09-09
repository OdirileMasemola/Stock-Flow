package com.example.stockflow.data.auth

import android.app.Activity
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.stockflow.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In helper.
 *
 * Uses Credential Manager first, then falls back to the classic Google Sign-In
 * intent when Credential Manager reports cancel/no-credential after the picker.
 */
class GoogleAuthClient(private val activity: Activity) {
    private val credentialManager = CredentialManager.create(activity)

    fun resolveWebClientId(): String {
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

    fun buildSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(resolveWebClientId())
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun getSignInIntent(): Intent = buildSignInClient().signInIntent

    /** Clear the last Google account so the picker always appears. */
    suspend fun clearLastGoogleAccount() {
        try {
            buildSignInClient().signOut().await()
        } catch (_: Exception) {
            // Ignore — picker can still work.
        }
    }

    /**
     * Credential Manager path (may return cancel on some devices after account pick).
     * Prefer [exchangeGoogleAccount] with the classic sign-in intent when this fails.
     */
    suspend fun signInWithCredentialManager(): Result<String> {
        if (!isConfigured()) {
            return Result.failure(IllegalStateException("Google Sign-In is not configured."))
        }
        val webClientId = resolveWebClientId()
        return try {
            val googleIdToken = requestGoogleIdToken(webClientId)
            exchangeGoogleIdToken(googleIdToken)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.message ?: "Unable to complete Google Sign-In. Please try again."
                )
            )
        }
    }

    suspend fun exchangeGoogleAccount(account: GoogleSignInAccount): Result<String> {
        if (!isConfigured()) {
            return Result.failure(IllegalStateException("Google Sign-In is not configured."))
        }
        val googleIdToken = account.idToken
        if (googleIdToken.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In failed. Add this app's SHA-1 in Firebase and try again."
                )
            )
        }
        return exchangeGoogleIdToken(googleIdToken)
    }

    fun parseSignInIntent(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            Result.success(account)
        } catch (e: ApiException) {
            when (e.statusCode) {
                // Common Google Sign-In status codes
                12501 -> Result.failure(IllegalStateException("Google Sign-In was cancelled."))
                10 -> Result.failure(
                    IllegalStateException(
                        "Google Sign-In misconfigured. Add the debug SHA-1 in Firebase Console."
                    )
                )
                else -> Result.failure(
                    IllegalStateException("Google Sign-In failed (code ${e.statusCode}). Please try again.")
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(e.message ?: "Unable to complete Google Sign-In. Please try again.")
            )
        }
    }

    private suspend fun exchangeGoogleIdToken(googleIdToken: String): Result<String> {
        return try {
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
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(e.message ?: "Unable to complete Google Sign-In. Please try again.")
            )
        }
    }

    private suspend fun requestGoogleIdToken(webClientId: String): String {
        // Button flow: account picker via Sign in with Google.
        return try {
            extractIdToken(
                credentialManager.getCredential(
                    activity,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                        .build()
                ).credential
            )
        } catch (first: GetCredentialException) {
            // Fallback bottom sheet
            extractIdToken(
                credentialManager.getCredential(
                    activity,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setAutoSelectEnabled(false)
                                .setServerClientId(webClientId)
                                .build()
                        )
                        .build()
                ).credential
            )
        }
    }

    private fun extractIdToken(credential: androidx.credentials.Credential): String {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unable to complete Google Sign-In. Please try again.")
    }

    private fun isConfigured(): Boolean {
        return resolveWebClientId().isNotBlank() && FirebaseApp.getApps(activity).isNotEmpty()
    }
}
