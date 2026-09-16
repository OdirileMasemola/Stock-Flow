package com.example.stockflow.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationEntity
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.ProductDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class PendingSyncProcessorTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var api: ProductApi
    private lateinit var sessionStore: SessionStore
    private lateinit var processor: PendingSyncProcessor
    private var now = 20_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { sessionStore.getToken() } returns "token"
        every { sessionStore.getUserId() } returns 7
        processor = PendingSyncProcessor(
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

    private fun pendingCreate(localId: Int = -1): PendingOperationEntity {
        val payload = ProductWritePayload(
            name = "Milk", sku = "M1", costPrice = 5.0, sellingPrice = 8.0,
            stockLevel = 3, minStockLevel = 1, categoryId = 2, categoryName = "Dairy"
        )
        return PendingOperationEntity(
            id = UUID.randomUUID().toString(),
            userId = 7,
            operationType = PendingOpType.CREATE,
            entityType = PendingEntityType.PRODUCT,
            localEntityId = localId.toString(),
            remoteEntityId = null,
            payloadJson = ProductWritePayload.toJson(payload),
            status = PendingOpStatus.PENDING,
            retryCount = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun successRemovesQueueItemAndUpdatesCache() = runBlocking {
        val op = pendingCreate(-5)
        db.pendingOperationDao().upsert(op)
        db.productDao().upsert(
            com.example.stockflow.data.local.cache.CachedProduct(
                7, -5, "Milk", "M1", 5.0, 8.0, 3, 1, 2, "Dairy", null, null, now
            )
        )
        val server = ProductDto(
            id = 55, name = "Milk", sku = "M1", costPrice = 5.0, sellingPrice = 8.0,
            stockLevel = 3, minStockLevel = 1, categoryId = 2, categoryName = "Dairy"
        )
        coEvery { api.createProduct(any(), any()) } returns Response.success(server)

        val summary = processor.syncPendingForCurrentUser()

        assertEquals(1, summary.succeeded)
        assertEquals(0, db.pendingOperationDao().countActive(7))
        assertNull(db.productDao().getById(7, -5))
        assertEquals("Milk", db.productDao().getById(7, 55)?.name)
    }

    @Test
    fun networkFailureKeepsPendingAndBumpsRetry() = runBlocking {
        db.pendingOperationDao().upsert(pendingCreate())
        coEvery { api.createProduct(any(), any()) } throws IOException("offline")

        val summary = processor.syncPendingForCurrentUser()

        assertEquals(1, summary.deferredNetwork)
        val remaining = db.pendingOperationDao().getPendingForUser(7).single()
        assertEquals(PendingOpStatus.PENDING, remaining.status)
        assertEquals(1, remaining.retryCount)
    }

    @Test
    fun permanentValidationFailureMarksFailed() = runBlocking {
        db.pendingOperationDao().upsert(pendingCreate())
        val body = """{"error":"Invalid category"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.createProduct(any(), any()) } returns Response.error(400, body)

        val summary = processor.syncPendingForCurrentUser()

        assertEquals(1, summary.failedPermanent)
        assertEquals(1, db.pendingOperationDao().countFailed(7))
        assertEquals(0, db.pendingOperationDao().countActive(7))
        val failed = db.pendingOperationDao().getAllForUser(7).single()
        assertEquals(PendingOpStatus.FAILED, failed.status)
        assertTrue(failed.lastError!!.contains("Invalid category"))
    }

    @Test
    fun wrongUserOpsAreNotSynced() = runBlocking {
        val other = pendingCreate().copy(userId = 99, id = UUID.randomUUID().toString())
        db.pendingOperationDao().upsert(other)
        coEvery { api.createProduct(any(), any()) } returns Response.success(
            ProductDto(1, "X", null, 1.0, 2.0, 1, 1, 1)
        )

        val summary = processor.syncPendingForCurrentUser()

        assertEquals(0, summary.processed)
        assertEquals(1, db.pendingOperationDao().getAllForUser(99).size)
    }

    @Test
    fun createWithExistingRemoteIdDoesNotRepost() = runBlocking {
        val op = pendingCreate(-3).copy(remoteEntityId = 77)
        db.pendingOperationDao().upsert(op)
        val server = ProductDto(
            id = 77, name = "Milk", sku = "M1", costPrice = 5.0, sellingPrice = 8.0,
            stockLevel = 3, minStockLevel = 1, categoryId = 2, categoryName = "Dairy"
        )
        coEvery { api.getProduct(any(), 77) } returns Response.success(server)
        coEvery { api.createProduct(any(), any()) } throws AssertionError("should not create again")

        val summary = processor.syncPendingForCurrentUser()

        assertEquals(1, summary.succeeded)
        assertEquals("Milk", db.productDao().getById(7, 77)?.name)
    }
}
