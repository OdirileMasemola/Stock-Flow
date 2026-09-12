package com.example.stockflow.data.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
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
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Outcome of parsing a classic Google Sign-In Activity result.
 * Cancellation is only reported when Google/Play Services indicate a real cancel.
 */
sealed class GoogleAccountResult {
    data class Success(val account: GoogleSignInAccount) : GoogleAccountResult()
    object Cancelled : GoogleAccountResult()
    data class Error(val message: String, val statusCode: Int? = null) : GoogleAccountResult()
}

/**
 * Google Sign-In helper.
 *
 * Primary path: classic [GoogleSignInClient] intent (more reliable on device than
 * Credential Manager for this project). Credential Manager remains available as a
 * secondary helper API.
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
     * Resolve an Activity Result from [getSignInIntent].
     *
     * Important: Google Sign-In can return [Activity.RESULT_CANCELED] even when
     * account selection succeeded but OAuth/config failed. Always inspect Intent
     * data / status codes before treating the flow as a user cancel.
     */
    fun resolveSignInResult(resultCode: Int, data: Intent?): GoogleAccountResult {
        Log.d(
            TAG,
            "Google Sign-In activity result: resultCode=$resultCode hasData=${data != null}"
        )

        if (data != null) {
            when (val parsed = parseSignInIntent(data)) {
                is GoogleAccountResult.Success -> return parsed
                is GoogleAccountResult.Error -> {
                    Log.w(TAG, "Google Sign-In parse error status=${parsed.statusCode}")
                    return parsed
                }
                is GoogleAccountResult.Cancelled -> {
                    Log.d(TAG, "Google Sign-In Intent reported cancellation")
                }
            }
        }

        // Some devices leave a signed-in account even when resultCode is CANCELED.
        val lastAccount = GoogleSignIn.getLastSignedInAccount(activity)
        if (lastAccount != null && !lastAccount.idToken.isNullOrBlank()) {
            Log.d(TAG, "Recovered Google account via getLastSignedInAccount")
            return GoogleAccountResult.Success(lastAccount)
        }

        return if (resultCode == Activity.RESULT_CANCELED) {
            GoogleAccountResult.Cancelled
        } else {
            GoogleAccountResult.Error(
                "Google Sign-In failed. Please try again.",
                statusCode = resultCode
            )
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
            Log.w(TAG, "Google account had no ID token (often SHA-1 / OAuth client mismatch)")
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In failed. Add this app's debug SHA-1 in Firebase Console, " +
                        "download an updated google-services.json, and try again."
                )
            )
        }
        return exchangeGoogleIdToken(googleIdToken)
    }

    fun parseSignInIntent(data: Intent?): GoogleAccountResult {
        if (data == null) {
            return GoogleAccountResult.Cancelled
        }
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            GoogleAccountResult.Success(account)
        } catch (e: ApiException) {
            Log.w(TAG, "GoogleSignIn ApiException status=${e.statusCode}")
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> GoogleAccountResult.Cancelled
                CommonStatusCodes.DEVELOPER_ERROR,
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> GoogleAccountResult.Error(
                    "Google Sign-In is misconfigured. Add the app debug SHA-1/SHA-256 in " +
                        "Firebase Console (Project settings → Your apps), then download a new " +
                        "google-services.json.",
                    statusCode = e.statusCode
                )
                CommonStatusCodes.NETWORK_ERROR -> GoogleAccountResult.Error(
                    "Network error during Google Sign-In. Check your connection and try again.",
                    statusCode = e.statusCode
                )
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> GoogleAccountResult.Error(
                    "Google Sign-In is already in progress. Please wait and try again.",
                    statusCode = e.statusCode
                )
                else -> GoogleAccountResult.Error(
                    "Google Sign-In failed (code ${e.statusCode}). Please try again.",
                    statusCode = e.statusCode
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "GoogleSignIn unexpected error: ${e.javaClass.simpleName}")
            GoogleAccountResult.Error(
                e.message ?: "Unable to complete Google Sign-In. Please try again."
            )
        }
    }

    private suspend fun exchangeGoogleIdToken(googleIdToken: String): Result<String> {
        return try {
            // Keep a local Firebase session for logout / auth-state helpers when Firebase is present.
            if (FirebaseApp.getApps(activity).isNotEmpty()) {
                try {
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    FirebaseAuth.getInstance()
                        .signInWithCredential(firebaseCredential)
                        .await()
                    Log.d(TAG, "Local Firebase session established")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Local Firebase sign-in failed; continuing with Google ID token: ${e.javaClass.simpleName}"
                    )
                }
            }
            // StockFlow backend verifies the Google ID token directly (no Admin SDK required).
            Result.success(googleIdToken)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google token exchange failed: ${e.javaClass.simpleName}")
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
        return resolveWebClientId().isNotBlank()
    }

    companion object {
        private const val TAG = "StockFlowGoogleAuth"
    }
}
