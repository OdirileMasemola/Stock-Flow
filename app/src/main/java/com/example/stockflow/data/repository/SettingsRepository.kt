package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.BusinessApi
import com.example.stockflow.data.remote.BusinessDto
import com.example.stockflow.data.remote.ProfileDto
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.UpdateBusinessRequest
import com.example.stockflow.data.remote.UpdateProfileRequest
import com.example.stockflow.data.remote.UserApi
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException

class UserRepository(
    private val api: UserApi = RetrofitClient.userApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getProfile(): Result<ProfileDto> {
        return try {
            val response = api.getProfile(authHeader())
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Unable to load profile"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load profile")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load profile"))
        }
    }

    suspend fun updateProfile(fullName: String, profileImageUrl: String?): Result<ProfileDto> {
        return try {
            val response = api.updateProfile(
                authHeader(),
                UpdateProfileRequest(
                    fullName = fullName.trim(),
                    profileImageUrl = profileImageUrl
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to update profile"))
                sessionStore.saveUserFullName(body.fullName)
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to update profile")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to update profile"))
        }
    }

    suspend fun uploadProfileImage(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        return try {
            val mediaType = mimeType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("image", fileName, body)
            val response = api.uploadProfileImage(authHeader(), part)
            if (response.isSuccessful) {
                val uploaded = response.body()?.imageUrl?.trim().orEmpty()
                if (uploaded.isEmpty()) {
                    Result.failure(Exception("Image upload failed"))
                } else {
                    Result.success(uploaded)
                }
            } else {
                Result.failure(Exception(errorMessage(response, "Image upload failed")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to upload image. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Image upload failed"))
        }
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception("You are not signed in. Please log in again.")
        }
        return "Bearer $token"
    }

    private fun <T> errorMessage(response: Response<T>, fallback: String): String {
        val raw = response.errorBody()?.string().orEmpty()
        return try {
            gson.fromJson(raw, ApiErrorResponse::class.java)?.error?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}

class BusinessRepository(
    private val api: BusinessApi = RetrofitClient.businessApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getBusiness(): Result<BusinessDto> {
        return try {
            val response = api.getBusiness(authHeader())
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Unable to load business information"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load business information")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load business information"))
        }
    }

    suspend fun updateBusiness(request: UpdateBusinessRequest): Result<BusinessDto> {
        return try {
            val response = api.updateBusiness(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to save business information"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to save business information")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to save business information"))
        }
    }

    suspend fun uploadBusinessImage(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        return try {
            val mediaType = mimeType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("image", fileName, body)
            val response = api.uploadBusinessImage(authHeader(), part)
            if (response.isSuccessful) {
                val uploaded = response.body()?.imageUrl?.trim().orEmpty()
                if (uploaded.isEmpty()) {
                    Result.failure(Exception("Image upload failed"))
                } else {
                    Result.success(uploaded)
                }
            } else {
                Result.failure(Exception(errorMessage(response, "Image upload failed")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to upload image. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Image upload failed"))
        }
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception("You are not signed in. Please log in again.")
        }
        return "Bearer $token"
    }

    private fun <T> errorMessage(response: Response<T>, fallback: String): String {
        val raw = response.errorBody()?.string().orEmpty()
        return try {
            gson.fromJson(raw, ApiErrorResponse::class.java)?.error?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
