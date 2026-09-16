package com.example.stockflow.data.repository

import com.example.stockflow.R
import com.example.stockflow.ui.common.AppStrings

import com.example.stockflow.data.ProductSkuCodes
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CacheDatabaseProvider
import com.example.stockflow.data.local.cache.CacheResult
import com.example.stockflow.data.local.cache.ProductCacheDao
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.local.cache.toCachedEntity
import com.example.stockflow.data.local.cache.toDto
import com.example.stockflow.data.remote.ApiErrorResponse
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.RetrofitClient
import com.example.stockflow.data.remote.UpdateProductRequest
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the Ktor product endpoints using the JWT from [SessionStore].
 * Successful READs populate the Room READ cache; offline IO falls back to cache.
 */
class ProductRepository(
    private val api: ProductApi = RetrofitClient.productApi,
    private val sessionStore: SessionStore,
    private val database: StockFlowCacheDatabase? = CacheDatabaseProvider.getOrNull(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = Gson()
    private val productDao: ProductCacheDao? get() = database?.productDao()

    suspend fun getProducts(): CacheResult<List<ProductDto>> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getProducts(authHeader())
            if (response.isSuccessful) {
                val data = response.body().orEmpty()
                if (userId != null) {
                    val cachedAt = clock()
                    productDao?.replaceAll(userId, data.map { it.toCachedEntity(userId, cachedAt) })
                }
                CacheResult.Fresh(data)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_products)))
            }
        } catch (_: IOException) {
            fallbackProducts(userId)
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_products))
        }
    }

    suspend fun getLowStockProducts(): Result<List<ProductDto>> {
        return try {
            val response = api.getLowStockProducts(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_load_low_stock))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_load_low_stock)))
        }
    }

    suspend fun getProduct(id: Int): CacheResult<ProductDto> {
        val userId = sessionStore.getUserId()
        return try {
            val response = api.getProduct(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return CacheResult.Error(AppStrings.get(R.string.product_not_found))
                if (userId != null) {
                    productDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                CacheResult.Fresh(body)
            } else {
                CacheResult.Error(errorMessage(response, AppStrings.get(R.string.error_unable_load_product)))
            }
        } catch (_: IOException) {
            if (userId == null) {
                return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
            }
            val cached = productDao?.getById(userId, id)
            if (cached != null) {
                CacheResult.Cached(cached.toDto(), cached.cachedAt)
            } else {
                CacheResult.Empty
            }
        } catch (e: Exception) {
            CacheResult.Error(e.message ?: AppStrings.get(R.string.error_unable_load_product))
        }
    }

    suspend fun getProductBySku(sku: String): Result<ProductDto> {
        return try {
            val candidates = ProductSkuCodes.lookupCandidates(sku)
            if (candidates.isEmpty()) {
                return Result.failure(Exception(AppStrings.get(R.string.product_not_found)))
            }
            var lastError: Exception? = null
            for (candidate in candidates) {
                val response = api.getProductBySku(authHeader(), candidate)
                if (response.isSuccessful) {
                    val body = response.body()
                        ?: return Result.failure(Exception(AppStrings.get(R.string.product_not_found)))
                    sessionStore.getUserId()?.let { userId ->
                        productDao?.upsert(body.toCachedEntity(userId, clock()))
                    }
                    return Result.success(body)
                }
                if (response.code() == 404) {
                    lastError = Exception(AppStrings.get(R.string.product_not_found))
                    continue
                }
                return Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_unable_find_product))))
            }
            Result.failure(lastError ?: Exception(AppStrings.get(R.string.product_not_found)))
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_unable_find_product)))
        }
    }

    suspend fun createProduct(request: CreateProductRequest): Result<ProductDto> {
        return try {
            val response = api.createProduct(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_create_product)))
                sessionStore.getUserId()?.let { userId ->
                    productDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_create_product))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_create_product)))
        }
    }

    suspend fun updateProduct(id: Int, request: UpdateProductRequest): Result<ProductDto> {
        return try {
            val response = api.updateProduct(authHeader(), id, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception(AppStrings.get(R.string.error_failed_update_product)))
                sessionStore.getUserId()?.let { userId ->
                    productDao?.upsert(body.toCachedEntity(userId, clock()))
                }
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_update_product))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_update_product)))
        }
    }

    suspend fun deleteProduct(id: Int): Result<Unit> {
        return try {
            val response = api.deleteProduct(authHeader(), id)
            if (response.isSuccessful || response.code() == 204) {
                sessionStore.getUserId()?.let { userId ->
                    productDao?.deleteById(userId, id)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, AppStrings.get(R.string.error_failed_delete_product))))
            }
        } catch (_: IOException) {
            Result.failure(Exception(AppStrings.get(R.string.error_unable_reach_server)))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: AppStrings.get(R.string.error_failed_delete_product)))
        }
    }

    /**
     * Uploads a product image before create/update. Returns the stored image URL path.
     * Callers must not save a product with a local-only image if this fails.
     */
    suspend fun uploadProductImage(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        return try {
            val mediaType = mimeType.toMediaTypeOrNull()
                ?: "image/jpeg".toMediaTypeOrNull()
            val body = imageBytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("image", fileName, body)
            val response = api.uploadProductImage(authHeader(), part)
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

    private suspend fun fallbackProducts(userId: Int?): CacheResult<List<ProductDto>> {
        if (userId == null) {
            return CacheResult.Error(AppStrings.get(R.string.error_unable_reach_server))
        }
        val cached = productDao?.getAll(userId).orEmpty()
        return if (cached.isNotEmpty()) {
            CacheResult.Cached(cached.map { it.toDto() }, cached.maxOf { it.cachedAt })
        } else {
            CacheResult.Empty
        }
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw Exception(AppStrings.get(R.string.error_not_signed_in))
        }
        return "Bearer $token"
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
