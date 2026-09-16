package com.example.stockflow.data.local.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.example.stockflow.data.repository.ProductRepository
import com.example.stockflow.data.sync.LocalTempIds
import com.example.stockflow.data.sync.WriteResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class PendingOperationQueueTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var api: ProductApi
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProductRepository
    private var now = 10_000L
    private var syncTriggered = 0

    private val createReq = CreateProductRequest(
        name = "Milk",
        sku = "M1",
        costPrice = 5.0,
        sellingPrice = 8.0,
        stockLevel = 3,
        minStockLevel = 1,
        categoryId = 2,
        supplierId = null,
        imageUrl = null
    )

    @Before
    fun setUp() {
        LocalTempIds.resetForTests(-1)
        syncTriggered = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { sessionStore.getToken() } returns "token"
        every { sessionStore.getUserId() } returns 7
        repository = ProductRepository(
            api = api,
            sessionStore = sessionStore,
            database = db,
            clock = { now },
            onEnqueueSync = { syncTriggered++ }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun offlineCreateEnqueuesPendingOpAndCachesLocalProduct() = runBlocking {
        coEvery { api.createProduct(any(), any()) } throws IOException("offline")

        val result = repository.createProduct(createReq, categoryName = "Dairy")

        assertTrue(result is WriteResult.Queued)
        val dto = (result as WriteResult.Queued).data
        assertTrue(LocalTempIds.isTemporary(dto.id))
        assertEquals("Milk", dto.name)
        assertEquals(1, db.pendingOperationDao().countActive(7))
        assertEquals("Milk", db.productDao().getById(7, dto.id)?.name)
        assertTrue(syncTriggered >= 1)
    }

    @Test
    fun offlineUpdateEnqueuesPendingOp() = runBlocking {
        // Seed a remote product in cache
        val seeded = ProductDto(
            id = 42, name = "Old", sku = "O1", costPrice = 1.0, sellingPrice = 2.0,
            stockLevel = 1, minStockLevel = 1, categoryId = 2, categoryName = "Dairy"
        )
        db.productDao().upsert(
            com.example.stockflow.data.local.cache.CachedProduct(
                7, 42, "Old", "O1", 1.0, 2.0, 1, 1, 2, "Dairy", null, null, now
            )
        )
        coEvery { api.updateProduct(any(), any(), any()) } throws IOException("offline")

        val result = repository.updateProduct(
            42,
            UpdateProductRequest(
                name = "New", sku = "O1", costPrice = 1.0, sellingPrice = 3.0,
                stockLevel = 2, minStockLevel = 1, categoryId = 2
            ),
            categoryName = "Dairy"
        )

        assertTrue(result is WriteResult.Queued)
        assertEquals("New", db.productDao().getById(7, 42)?.name)
        assertEquals(1, db.pendingOperationDao().countActive(7))
    }

    @Test
    fun offlineDeleteEnqueuesPendingOp() = runBlocking {
        db.productDao().upsert(
            com.example.stockflow.data.local.cache.CachedProduct(
                7, 42, "Old", "O1", 1.0, 2.0, 1, 1, 2, "Dairy", null, null, now
            )
        )
        coEvery { api.deleteProduct(any(), any()) } throws IOException("offline")

        val result = repository.deleteProduct(42)

        assertTrue(result is WriteResult.Queued)
        assertEquals(null, db.productDao().getById(7, 42))
        assertEquals(1, db.pendingOperationDao().countActive(7))
        assertEquals("DELETE", db.pendingOperationDao().getPendingForUser(7).single().operationType)
    }

    @Test
    fun queueIsUserScoped() = runBlocking {
        coEvery { api.createProduct(any(), any()) } throws IOException("offline")
        repository.createProduct(createReq)

        every { sessionStore.getUserId() } returns 99
        assertEquals(0, db.pendingOperationDao().countActive(99))
        assertEquals(1, db.pendingOperationDao().countActive(7))
    }

    @Test
    fun onlineCreateDoesNotUseQueue() = runBlocking {
        val server = ProductDto(
            id = 100, name = "Milk", sku = "M1", costPrice = 5.0, sellingPrice = 8.0,
            stockLevel = 3, minStockLevel = 1, categoryId = 2, categoryName = "Dairy"
        )
        coEvery { api.createProduct(any(), any()) } returns retrofit2.Response.success(server)

        val result = repository.createProduct(createReq)

        assertTrue(result is WriteResult.Synced)
        assertEquals(0, db.pendingOperationDao().countActive(7))
        assertEquals("Milk", db.productDao().getById(7, 100)?.name)
    }
}
