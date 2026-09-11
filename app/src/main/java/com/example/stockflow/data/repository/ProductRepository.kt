package com.example.stockflow.data.repository

import com.example.stockflow.data.local.SessionStore
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
 */
class ProductRepository(
    private val api: ProductApi = RetrofitClient.productApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    suspend fun getProducts(): Result<List<ProductDto>> {
        return try {
            val response = api.getProducts(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load products")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load products"))
        }
    }

    suspend fun getLowStockProducts(): Result<List<ProductDto>> {
        return try {
            val response = api.getLowStockProducts(authHeader())
            if (response.isSuccessful) {
                Result.success(response.body().orEmpty())
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load low-stock products")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load low-stock products"))
        }
    }

    suspend fun getProduct(id: Int): Result<ProductDto> {
        return try {
            val response = api.getProduct(authHeader(), id)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Product not found"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Unable to load product")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Unable to load product"))
        }
    }

    suspend fun createProduct(request: CreateProductRequest): Result<ProductDto> {
        return try {
            val response = api.createProduct(authHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to create product"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to create product")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to create product"))
        }
    }

    suspend fun updateProduct(id: Int, request: UpdateProductRequest): Result<ProductDto> {
        return try {
            val response = api.updateProduct(authHeader(), id, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Failed to update product"))
                Result.success(body)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to update product")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to update product"))
        }
    }

    suspend fun deleteProduct(id: Int): Result<Unit> {
        return try {
            val response = api.deleteProduct(authHeader(), id)
            // 204 No Content has an empty body — still treat as success
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(errorMessage(response, "Failed to delete product")))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach the server. Check your connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to delete product"))
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
