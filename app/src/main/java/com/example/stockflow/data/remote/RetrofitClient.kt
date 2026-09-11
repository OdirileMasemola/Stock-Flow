package com.example.stockflow.data.remote

import com.example.stockflow.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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

    /** Authenticated purchase orders — callers pass the Bearer JWT header. */
    val purchaseOrderApi: PurchaseOrderApi by lazy {
        retrofit.create(PurchaseOrderApi::class.java)
    }

    /** Authenticated dashboard / reports — callers pass the Bearer JWT header. */
    val dashboardApi: DashboardApi by lazy {
        retrofit.create(DashboardApi::class.java)
    }
}
