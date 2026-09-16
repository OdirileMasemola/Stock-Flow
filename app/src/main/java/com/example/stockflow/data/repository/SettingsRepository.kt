package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.BusinessCacheDao
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.ProfileCacheDao
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
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
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val profileDao: ProfileCacheDao? get() = database?.profileDao()

    suspend fun getProfile(): CacheResult<ProfileDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getProfile(authHeader())
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.error_unable_load_profile))
                if (userId != null) {
                    profileDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_profile)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = profileDao?.get(userId)
            if (cached != null) {
                CacheResult.Cached(cached.toDto(), cached.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_profile))
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
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_update_profile)))
                sessionStore.saveUserFullName(body.fullName)
                sessionStore.getUserId()?.let { userId ->
                    profileDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_update_profile))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_update_profile)))
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
                    Result.failure(Exception(AppStrings.get(R.string.error_image_upload_failed)))
                } else {
                    Result.success(uploaded)
                }
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_image_upload_failed))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_upload_image_connection)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_image_upload_failed)))
        }
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception(AppStrings.get(R.string.error_not_signed_in))
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
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val businessDao: BusinessCacheDao? get() = database?.businessDao()

    suspend fun getBusiness(): CacheResult<BusinessDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getBusiness(authHeader())
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.error_unable_load_business))
                if (userId != null) {
                    businessDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_business)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = businessDao?.get(userId)
            if (cached != null) {
                CacheResult.Cached(cached.toDto(), cached.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_business))
        }
    }

    suspend fun updateBusiness(request: UpdateBusinessRequest): Result<BusinessDto> {
        return try {
            val response = api.updateBusiness(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_save_business)))
                sessionStore.getUserId()?.let { userId ->
                    businessDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_save_business))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_save_business)))
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
                    Result.failure(Exception(AppStrings.get(R.string.error_image_upload_failed)))
                } else {
                    Result.success(uploaded)
                }
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_image_upload_failed))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_upload_image_connection)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_image_upload_failed)))
        }
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception(AppStrings.get(R.string.error_not_signed_in))
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
