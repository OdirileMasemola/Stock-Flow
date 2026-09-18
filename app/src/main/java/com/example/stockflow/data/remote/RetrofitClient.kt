package com.example.stockflow.data.remote

import com.example.stockflow.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    /**
     * Mobile-friendly timeouts: fail fast on unreachable hosts.
     * GET-only retry interceptor (idempotent). Never blind-retry POST/PUT/DELETE —
     * offline mutations stay on the WorkManager queue.
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(IdempotentGetRetryInterceptor(maxExtraAttempts = 1))
            .build()
    }

    // Shared Retrofit instance for auth and product APIs
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    /** Authenticated product CRUD — callers pass the Bearer JWT header. */
    val productApi: ProductApi by lazy {
        retrofit.create(ProductApi::class.java)
    }

    /** Authenticated sales/POS — callers pass the Bearer JWT header. */
    val saleApi: SaleApi by lazy {
        retrofit.create(SaleApi::class.java)
    }

    /** Authenticated supplier CRUD — callers pass the Bearer JWT header. */
    val supplierApi: SupplierApi by lazy {
        retrofit.create(SupplierApi::class.java)
    }

    /** Authenticated categories — callers pass the Bearer JWT header. */
    val categoryApi: CategoryApi by lazy {
        retrofit.create(CategoryApi::class.java)
    }

    /** Authenticated purchase orders — callers pass the Bearer JWT header. */
    val purchaseOrderApi: PurchaseOrderApi by lazy {
        retrofit.create(PurchaseOrderApi::class.java)
    }

    /** Authenticated dashboard / reports — callers pass the Bearer JWT header. */
    val dashboardApi: DashboardApi by lazy {
        retrofit.create(DashboardApi::class.java)
    }

    /** Authenticated profile — callers pass the Bearer JWT header. */
    val userApi: UserApi by lazy {
        retrofit.create(UserApi::class.java)
    }

    /** Authenticated business/store — callers pass the Bearer JWT header. */
    val businessApi: BusinessApi by lazy {
        retrofit.create(BusinessApi::class.java)
    }

    /** Authenticated FCM device-token registration. */
    val notificationApi: NotificationApi by lazy {
        retrofit.create(NotificationApi::class.java)
    }

    /** Authenticated activity / audit history (Firestore via API). */
    val activityApi: ActivityApi by lazy {
        retrofit.create(ActivityApi::class.java)
    }
}

/**
 * Retries only idempotent GET requests after a connection/timeout failure.
 * POST/PUT/PATCH/DELETE are never retried here (duplicate product/sale risk).
 */
internal class IdempotentGetRetryInterceptor(
    private val maxExtraAttempts: Int = 1
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastError: IOException? = null
        while (attempt <= maxExtraAttempts) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                lastError = e
                val method = request.method
                if (method != "GET" || attempt >= maxExtraAttempts) {
                    throw e
                }
                attempt++
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}

