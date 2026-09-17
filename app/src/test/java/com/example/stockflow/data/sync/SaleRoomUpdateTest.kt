package com.example.stockflow.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.CachedDashboardSnapshot
import com.example.stockflow.data.local.cache.CachedProduct
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.remote.CreateSaleRequest
import com.example.stockflow.data.remote.CreateSaleItemRequest
import com.example.stockflow.data.remote.SaleApi
import com.example.stockflow.data.remote.SaleDto
import com.example.stockflow.data.remote.SaleItemDto
import com.example.stockflow.data.repository.SaleRepository
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
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class SaleRoomUpdateTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var api: SaleApi
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: SaleRepository
    private var now = 80_000L

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
        repository = SaleRepository(
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
    fun successfulSaleUpdatesProductStockAndDashboard() = runBlocking {
        db.productDao().upsert(
            CachedProduct(
                7, 10, "Milk", "M1", 5.0, 8.0, 5, 2, 1, "Dairy", null, null, now
            )
        )
        db.dashboardDao().upsert(
            CachedDashboardSnapshot(
                userId = 7,
                totalProducts = 1,
                totalStockQuantity = 5,
                inventoryValue = 40.0,
                todaySalesTotal = 0.0,
                todaySalesCount = 0,
                lowStockCount = 0,
                weeklySalesJson = "[]",
                recentSalesJson = "[]",
                recentPurchaseOrdersJson = "[]",
                lowStockPreviewJson = "[]",
                cachedAt = now
            )
        )
        val sale = SaleDto(
            id = 501,
            userId = 7,
            totalAmount = 16.0,
            paymentMethod = "CASH",
            createdAt = "2026-09-18T10:00:00",
            items = listOf(
                SaleItemDto(id = 1, productId = 10, productName = "Milk", quantity = 3, unitPrice = 8.0, subtotal = 24.0)
            )
        )
        coEvery { api.createSale(any(), any()) } returns Response.success(sale)

        val result = repository.createSale(
            CreateSaleRequest(
                paymentMethod = "CASH",
                items = listOf(CreateSaleItemRequest(productId = 10, quantity = 3))
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(2, db.productDao().getById(7, 10)?.stockLevel) // 5-3
        val dash = db.dashboardDao().get(7)!!
        assertEquals(16.0, dash.todaySalesTotal, 0.01)
        assertEquals(1, dash.todaySalesCount)
        assertEquals(2, dash.totalStockQuantity)
        // stock 2 <= min 2 → low stock
        assertEquals(1, dash.lowStockCount)
    }
}
