package com.example.stockflow.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.DashboardApi
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.repository.DashboardRepository
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class DashboardRepositoryCacheTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var api: DashboardApi
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: DashboardRepository
    private var now = 70_000L

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
        repository = DashboardRepository(
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
    fun onlineFetchCachesSnapshot() = runBlocking {
        val summary = DashboardSummaryDto(
            totalProducts = 3,
            totalStockQuantity = 10,
            inventoryValue = 100.0,
            todaySalesTotal = 20.0,
            todaySalesCount = 2,
            lowStockCount = 1
        )
        coEvery { api.getSummary(any()) } returns Response.success(summary)

        val result = repository.getSummary()

        assertTrue(result is CacheResult.Fresh)
        assertEquals(3, db.dashboardDao().get(7)?.totalProducts)
        assertEquals(20.0, db.dashboardDao().get(7)?.todaySalesTotal ?: -1.0, 0.01)
    }

    @Test
    fun offlineReturnsCachedSnapshot() = runBlocking {
        db.dashboardDao().upsert(
            CachedDashboardSnapshot(
                userId = 7,
                totalProducts = 2,
                totalStockQuantity = 4,
                inventoryValue = 40.0,
                todaySalesTotal = 5.0,
                todaySalesCount = 1,
                lowStockCount = 0,
                weeklySalesJson = "[]",
                recentSalesJson = "[]",
                recentPurchaseOrdersJson = "[]",
                lowStockPreviewJson = "[]",
                cachedAt = now
            )
        )
        coEvery { api.getSummary(any()) } throws IOException("offline")

        val result = repository.getSummary()

        assertTrue(result is CacheResult.Cached)
        assertEquals(2, result.getOrNull()?.totalProducts)
    }

    @Test
    fun offlineEmptyWhenNeverCached() = runBlocking {
        coEvery { api.getSummary(any()) } throws IOException("offline")

        val result = repository.getSummary()

        assertTrue(result is CacheResult.Empty)
    }
}
