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
import kotlinx.coroutines.flow.first
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

    @Test
    fun observeProductsEmitsCreateUpdateAndDelete() = runBlocking {
        // POS and Inventory both subscribe to this Flow; Room mutations must be visible promptly.
        assertTrue(repository.observeProducts().first().isEmpty())

        coEvery { api.createProduct(any(), any()) } returns Response.success(sample)
        val created = repository.createProduct(
            com.example.stockflow.data.remote.CreateProductRequest(
                name = sample.name,
                sku = sample.sku,
                costPrice = sample.costPrice,
                sellingPrice = sample.sellingPrice,
                stockLevel = sample.stockLevel,
                minStockLevel = sample.minStockLevel,
                categoryId = sample.categoryId,
                supplierId = sample.supplierId,
                imageUrl = sample.imageUrl
            )
        )
        assertTrue(created is com.example.stockflow.data.sync.WriteResult.Synced)
        assertEquals(listOf(sample), repository.observeProducts().first())

        val updated = sample.copy(name = "Soap XL", sellingPrice = 5.0)
        coEvery { api.updateProduct(any(), any(), any()) } returns Response.success(updated)
        val updateResult = repository.updateProduct(
            sample.id,
            com.example.stockflow.data.remote.UpdateProductRequest(
                name = updated.name,
                sku = updated.sku,
                costPrice = updated.costPrice,
                sellingPrice = updated.sellingPrice,
                stockLevel = updated.stockLevel,
                minStockLevel = updated.minStockLevel,
                categoryId = updated.categoryId,
                supplierId = updated.supplierId,
                imageUrl = updated.imageUrl
            )
        )
        assertTrue(updateResult is com.example.stockflow.data.sync.WriteResult.Synced)
        val afterUpdate = repository.observeProducts().first()
        assertEquals("Soap XL", afterUpdate.single().name)
        assertEquals(5.0, afterUpdate.single().sellingPrice, 0.001)

        coEvery { api.deleteProduct(any(), any()) } returns Response.success(Unit)
        val deleted = repository.deleteProduct(sample.id)
        assertTrue(deleted is com.example.stockflow.data.sync.WriteResult.Synced)
        assertTrue(repository.observeProducts().first().isEmpty())
    }

    @Test
    fun observeProductsEmitsOfflineCreate() = runBlocking {
        coEvery { api.createProduct(any(), any()) } throws IOException("offline")
        val queued = repository.createProduct(
            com.example.stockflow.data.remote.CreateProductRequest(
                name = "Offline Soap",
                sku = "OFF1",
                costPrice = 1.0,
                sellingPrice = 2.0,
                stockLevel = 3,
                minStockLevel = 1,
                categoryId = 1,
                supplierId = null,
                imageUrl = null
            ),
            categoryName = "Hygiene"
        )
        assertTrue(queued is com.example.stockflow.data.sync.WriteResult.Queued)
        val local = (queued as com.example.stockflow.data.sync.WriteResult.Queued).data
        val observed = repository.observeProducts().first()
        assertEquals(1, observed.size)
        assertEquals("Offline Soap", observed.single().name)
        assertEquals(local.id, observed.single().id)
    }
}
