package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.AuthApi
import com.example.stockflow.data.remote.LoginRequest
import com.example.stockflow.data.remote.GoogleAuthRequest
import com.example.stockflow.data.remote.RegisterRequest
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.RoleDto
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

class AuthRepository(
    private val api: AuthApi = RetrofitClient.authApi,
    private val sessionStore: SessionStore? = null
) {
    private val gson = Gson()

    suspend fun login(username: String, password: String): Result<Boolean> {
        return try {
            val response = api.login(LoginRequest(identifier = username, password = password))
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_login_failed)))
                val token = body.token?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_login_failed)))
                sessionStore?.saveToken(token)
                sessionStore?.saveUserFullName(displayNameFrom(body.user))
                Result.success(true)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = AppStrings.get(R.string.error_invalid_credentials))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (_: Exception) {
            Result.failure(Exception(AppStrings.get(R.string.error_login_failed_retry)))
        }
    }

    suspend fun getRoles(): Result<List<RoleDto>> {
        return try {
            val response = api.getRoles()
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, fallback = AppStrings.get(R.string.error_unable_load_roles))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (_: Exception) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_load_roles_retry)))
        }
    }

    suspend fun signUp(
        name: String,
        phone: String,
        email: String,
        password: String,
        roleId: Int
    ): Result<Boolean> {
        val username = phone.filterNot { it.isWhitespace() }
        if (username.isEmpty()) {
            return Result.failure(Exception(AppStrings.get(R.string.error_phone_required)))
        }

        return try {
            val response = api.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    fullName = name,
                    password = password,
                    roleId = roleId
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_signup_failed)))
                val token = body.token?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_signup_failed)))
                sessionStore?.saveToken(token)
                sessionStore?.saveUserFullName(displayNameFrom(body.user))
                Result.success(true)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = AppStrings.get(R.string.error_signup_failed))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (_: Exception) {
            Result.failure(Exception(AppStrings.get(R.string.error_signup_failed_retry)))
        }
    }

    suspend fun authenticateWithGoogle(idToken: String, roleId: Int? = null): Result<GoogleAuthOutcome> {
        return try {
            val response = api.authenticateGoogle(GoogleAuthRequest(idToken = idToken, roleId = roleId))
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_google_signin_failed)))
                val token = body.token?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_google_signin_failed)))
                sessionStore?.saveToken(token)
                sessionStore?.saveUserFullName(displayNameFrom(body.user))
                Result.success(GoogleAuthOutcome.Authenticated)
            } else if (response.code() == 401) {
                val apiError = parseError(response)
                if (apiError?.code == "ACCOUNT_NOT_FOUND") {
                    Result.success(GoogleAuthOutcome.AccountNotFound)
                } else {
                    Result.failure(Exception(apiError?.error?.takeIf { it.isNotBlank() } ?: AppStrings.get(R.string.error_google_signin_failed)))
                }
            } else {
                Result.failure(Exception(errorMessage(response, fallback = AppStrings.get(R.string.error_google_signin_failed))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (_: Exception) {
            Result.failure(Exception(AppStrings.get(R.string.error_google_signin_failed_retry)))
        }
    }

    private fun displayNameFrom(user: com.example.stockflow.data.remote.AuthUser): String {
        user.fullName?.takeIf { it.isNotBlank() }?.let { return it }
        user.username?.takeIf { it.isNotBlank() }?.let { return it }
        user.email?.takeIf { it.isNotBlank() }?.let { email ->
            return email.substringBefore("@").ifBlank { email }
        }
        return AppStrings.get(R.string.default_user_name)
    }

    private fun parseError(response: Response<*>): ApiErrorResponse? {
        val raw = response.errorBody()?.string() ?: return null
        return try {
            gson.fromJson(raw, ApiErrorResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun errorMessage(response: Response<*>, fallback: String): String {
        val raw = response.errorBody()?.string()
        val apiMessage = try {
            gson.fromJson(raw, ApiErrorResponse::class.java)?.error
        } catch (_: Exception) {
            null
        }
        return apiMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}

sealed class GoogleAuthOutcome {
    object Authenticated : GoogleAuthOutcome()
    object AccountNotFound : GoogleAuthOutcome()
}
