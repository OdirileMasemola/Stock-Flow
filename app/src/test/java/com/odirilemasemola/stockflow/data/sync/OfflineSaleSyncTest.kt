package com.odirilemasemola.stockflow.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.odirilemasemola.stockflow.StockFlowApp
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.data.local.cache.CachedProduct
import com.odirilemasemola.stockflow.data.local.cache.PendingEntityType
import com.odirilemasemola.stockflow.data.local.cache.PendingOpStatus
import com.odirilemasemola.stockflow.data.local.cache.StockFlowCacheDatabase
import com.odirilemasemola.stockflow.data.remote.CreateSaleItemRequest
import com.odirilemasemola.stockflow.data.remote.CreateSaleRequest
import com.odirilemasemola.stockflow.data.remote.ProductApi
import com.odirilemasemola.stockflow.data.remote.SaleApi
import com.odirilemasemola.stockflow.data.remote.SaleDto
import com.odirilemasemola.stockflow.data.repository.SaleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class OfflineSaleSyncTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var saleApi: SaleApi
    private lateinit var productApi: ProductApi
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: SaleRepository
    private lateinit var processor: PendingSyncProcessor
    private var now = 50_000L
    private var syncRequests = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        saleApi = mockk(relaxed = true)
        productApi = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { sessionStore.getToken() } returns "token"
        every { sessionStore.getUserId() } returns 7
        repository = SaleRepository(
            api = saleApi,
            sessionStore = sessionStore,
            database = db,
            clock = { now },
            onEnqueueSync = { syncRequests++ },
            idGenerator = { "client-sale-0001" }
        )
        processor = PendingSyncProcessor(
            api = productApi,
            sessionStore = sessionStore,
            database = db,
            clock = { now },
            saleApi = saleApi
        )
        runBlocking {
            db.productDao().upsert(CachedProduct(7, 10, "Milk", "M1", 5.0, 8.0, 5, 1, 2, "Dairy", null, null, now))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun request(quantity: Int, productId: Int = 10) =
        CreateSaleRequest(paymentMethod = "Cash", items = listOf(CreateSaleItemRequest(productId, quantity)))

    private fun serverSale(id: Int = 99) = SaleDto(
        id = id, userId = 7, totalAmount = 16.0, paymentMethod = "Cash", createdAt = "2026-10-22T10:00:00"
    )

    @Test
    fun offlineSaleIsQueuedUpdatesStockAndShowsAsPending() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")

        val result = repository.createSale(request(2))

        assertTrue(result.isSuccess)
        val sale = result.getOrThrow()
        assertTrue(sale.pending)
        assertEquals(16.0, sale.totalAmount, 0.001)
        assertEquals(3, db.productDao().getById(7, 10)?.stockLevel)
        val op = db.pendingOperationDao().getAllForUser(7).single()
        assertEquals(PendingEntityType.SALE, op.entityType)
        assertEquals("client-sale-0001", op.localEntityId)
        assertEquals(1, syncRequests)
        val pending = repository.getPendingSales()
        assertEquals(1, pending.size)
        assertTrue(pending.single().pending)
    }

    @Test
    fun offlineSaleIsRejectedWhenCachedStockIsTooLow() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")

        val result = repository.createSale(request(6))

        assertTrue(result.isFailure)
        assertEquals(5, db.productDao().getById(7, 10)?.stockLevel)
        assertEquals(0, db.pendingOperationDao().countActive(7))
    }

    @Test
    fun offlineSaleIsRejectedForUnknownOrUnsyncedProducts() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")

        assertTrue(repository.createSale(request(1, productId = 404)).isFailure)
        assertTrue(repository.createSale(request(1, productId = -3)).isFailure)
        assertEquals(0, db.pendingOperationDao().countActive(7))
    }

    @Test
    fun syncPostsWithClientSaleIdAndClearsQueueWithoutDoubleDeductingStock() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")
        repository.createSale(request(2))

        val sent = slot<CreateSaleRequest>()
        coEvery { saleApi.createSale(any(), capture(sent)) } returns Response.success(serverSale())
        val summary = processor.syncPendingForCurrentUser()

        assertEquals(1, summary.succeeded)
        assertEquals("client-sale-0001", sent.captured.clientSaleId)
        assertEquals(0, db.pendingOperationDao().countActive(7))
        assertEquals(3, db.productDao().getById(7, 10)?.stockLevel)
        assertTrue(repository.getPendingSales().isEmpty())
    }

    @Test
    fun retryAfterNetworkFailureReusesTheSameClientSaleId() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")
        repository.createSale(request(1))

        val first = processor.syncPendingForCurrentUser()
        assertEquals(1, first.deferredNetwork)

        val sent = mutableListOf<CreateSaleRequest>()
        coEvery { saleApi.createSale(any(), capture(sent)) } returns Response.success(serverSale())
        processor.syncPendingForCurrentUser()

        coVerify(exactly = 3) { saleApi.createSale(any(), any()) }
        assertEquals(listOf("client-sale-0001"), sent.map { it.clientSaleId })
        assertEquals(0, db.pendingOperationDao().countActive(7))
    }

    @Test
    fun serverRejectionMarksFailedAndRestoresLocalStock() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")
        repository.createSale(request(2))

        val body = """{"error":"Insufficient stock for product: Milk"}""".toResponseBody("application/json".toMediaType())
        coEvery { saleApi.createSale(any(), any()) } returns Response.error(400, body)
        val summary = processor.syncPendingForCurrentUser()

        assertEquals(1, summary.failedPermanent)
        assertEquals(PendingOpStatus.FAILED, db.pendingOperationDao().getAllForUser(7).single().status)
        assertEquals(5, db.productDao().getById(7, 10)?.stockLevel)
        assertTrue(repository.getPendingSales().isEmpty())
    }

    @Test
    fun refreshedProductListKeepsQueuedSaleDeduction() = runBlocking {
        coEvery { saleApi.createSale(any(), any()) } throws IOException("offline")
        repository.createSale(request(2))
        // Simulate a server refresh that does not know about the queued sale yet.
        db.productDao().upsert(CachedProduct(7, 10, "Milk", "M1", 5.0, 8.0, 5, 1, 2, "Dairy", null, null, now))

        PendingOverlayApplier.applyPendingSaleStock(7, db.productDao(), db.pendingOperationDao()) { now }

        assertEquals(3, db.productDao().getById(7, 10)?.stockLevel)
    }

    @Test
    fun onlineSaleSendsClientSaleIdAndIsNotQueued() = runBlocking {
        val sent = slot<CreateSaleRequest>()
        coEvery { saleApi.createSale(any(), capture(sent)) } returns Response.success(serverSale())

        val result = repository.createSale(request(1))

        assertFalse(result.getOrThrow().pending)
        assertEquals("client-sale-0001", sent.captured.clientSaleId)
        assertEquals(0, db.pendingOperationDao().countActive(7))
    }
}
