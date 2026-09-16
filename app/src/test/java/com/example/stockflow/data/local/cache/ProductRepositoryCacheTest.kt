package com.example.stockflow.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class ProductRepositoryCacheTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var api: ProductApi
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProductRepository
    private var now = 5_000L

    private val sample = ProductDto(
        id = 1,
        name = "Soap",
        sku = "S1",
        costPrice = 2.0,
        sellingPrice = 4.0,
        stockLevel = 8,
        minStockLevel = 2,
        categoryId = 3,
        categoryName = "Hygiene",
        supplierId = null,
        imageUrl = "https://img/soap.png"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { sessionStore.getToken() } returns "token"
        every { sessionStore.getUserId() } returns 99
        repository = ProductRepository(
            api = api,
            sessionStore = sessionStore,
            database = db,
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun apiSuccessWritesRoomAndReturnsFresh() = runBlocking {
        coEvery { api.getProducts(any()) } returns Response.success(listOf(sample))

        val result = repository.getProducts()

        assertTrue(result is CacheResult.Fresh)
        assertEquals(listOf(sample), (result as CacheResult.Fresh).data)
        val cached = db.productDao().getAll(99)
        assertEquals(1, cached.size)
        assertEquals("Soap", cached[0].name)
        assertEquals(5_000L, cached[0].cachedAt)
    }

    @Test
    fun cachedDataReadableAfterApiFailure() = runBlocking {
        coEvery { api.getProducts(any()) } returns Response.success(listOf(sample))
        repository.getProducts()

        coEvery { api.getProducts(any()) } throws IOException("offline")
        val result = repository.getProducts()

        assertTrue(result is CacheResult.Cached)
        val cached = result as CacheResult.Cached
        assertEquals("Soap", cached.data.single().name)
        assertEquals(5_000L, cached.cachedAt)
    }

    @Test
    fun apiFailureWithNoCacheReturnsEmpty() = runBlocking {
        coEvery { api.getProducts(any()) } throws IOException("offline")
        val result = repository.getProducts()
        assertTrue(result is CacheResult.Empty)
    }

    @Test
    fun httpErrorDoesNotWipeCache() = runBlocking {
        coEvery { api.getProducts(any()) } returns Response.success(listOf(sample))
        repository.getProducts()

        val errorBody = """{"error":"boom"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.getProducts(any()) } returns Response.error(500, errorBody)
        val result = repository.getProducts()

        assertTrue(result is CacheResult.Error)
        assertEquals(1, db.productDao().getAll(99).size)
    }

    @Test
    fun newApiDataReplacesStaleCache() = runBlocking {
        coEvery { api.getProducts(any()) } returns Response.success(listOf(sample))
        repository.getProducts()

        now = 9_000L
        val updated = sample.copy(name = "Soap XL", stockLevel = 20)
        coEvery { api.getProducts(any()) } returns Response.success(listOf(updated))
        val result = repository.getProducts()

        assertTrue(result is CacheResult.Fresh)
        val cached = db.productDao().getAll(99)
        assertEquals("Soap XL", cached.single().name)
        assertEquals(9_000L, cached.single().cachedAt)
        assertEquals(20, cached.single().stockLevel)
    }

    @Test
    fun cachedAtPropagatesOnOfflineFallback() = runBlocking {
        now = 42_000L
        coEvery { api.getProducts(any()) } returns Response.success(listOf(sample))
        repository.getProducts()
        coEvery { api.getProducts(any()) } throws IOException("offline")

        val result = repository.getProducts() as CacheResult.Cached
        assertEquals(42_000L, result.cachedAt)
    }
}
