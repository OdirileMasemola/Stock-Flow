package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.AuthApi
import com.example.stockflow.data.remote.LoginRequest
import com.example.stockflow.data.remote.RegisterRequest
import com.example.stockflow.data.remote.RetrofitClient
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
                    ?: return Result.failure(Exception("Login failed"))
                sessionStore?.saveToken(body.token)
                Result.success(true)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = "Invalid username/email or password")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (_: Exception) {
            Result.failure(Exception("Login failed. Please try again."))
        }
    }

    suspend fun signUp(name: String, phone: String, email: String, password: String): Result<Boolean> {
        val username = phone.filterNot { it.isWhitespace() }
        if (username.isEmpty()) {
            return Result.failure(Exception("Phone number is required"))
        }

        return try {
            val response = api.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    fullName = name,
                    password = password,
                    roleId = DEFAULT_REGISTER_ROLE_ID
                )
            )
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(errorMessage(response, fallback = "Signup failed")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (_: Exception) {
            Result.failure(Exception("Signup failed. Please try again."))
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

    companion object {
        private const val DEFAULT_REGISTER_ROLE_ID = 1
    }
}
