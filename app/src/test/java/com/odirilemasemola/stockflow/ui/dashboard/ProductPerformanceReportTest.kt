package com.odirilemasemola.stockflow.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.odirilemasemola.stockflow.StockFlowApp
import com.odirilemasemola.stockflow.data.local.ReportsCache
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.data.remote.DashboardApi
import com.odirilemasemola.stockflow.data.remote.ProductPerformanceItemDto
import com.odirilemasemola.stockflow.data.remote.ProductPerformanceSectionDto
import com.odirilemasemola.stockflow.data.remote.ReportsDto
import com.odirilemasemola.stockflow.data.repository.DashboardRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class ProductPerformanceReportTest {

    private lateinit var context: Context
    private lateinit var api: DashboardApi
    private lateinit var cache: ReportsCache
    private lateinit var repository: DashboardRepository

    private val report = ReportsDto(
        range = "30d",
        productPerformance = ProductPerformanceSectionDto(
            bestSelling = listOf(ProductPerformanceItemDto(1, "Milk", 24, 480.0, 6)),
            slowMoving = listOf(ProductPerformanceItemDto(2, "Rice", 0, 0.0, 40))
        )
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        api = mockk()
        cache = ReportsCache(context)
        val session = mockk<SessionStore>()
        every { session.getToken() } returns "token"
        every { session.getUserId() } returns 7
        repository = DashboardRepository(api = api, sessionStore = session, database = null, clock = { 1_000L }, reportsCache = cache)
    }

    @Test
    fun linesAreRankedAndFormatted() {
        val best = ProductPerformanceText.bestSellingLines(context, report.productPerformance?.bestSelling)
        val slow = ProductPerformanceText.slowMovingLines(context, report.productPerformance?.slowMoving)
        assertEquals(listOf("1. Milk — 24 sold · R480.00"), best)
        assertEquals(listOf("1. Rice — 0 sold · 40 in stock"), slow)
    }

    @Test
    fun emptyListsShowAFriendlyMessage() {
        assertEquals(listOf("No products sold in this period."), ProductPerformanceText.bestSellingLines(context, null))
        assertEquals(listOf("No in-stock products to show."), ProductPerformanceText.slowMovingLines(context, emptyList()))
    }

    @Test
    fun missingNameFallsBackToProductId() {
        val lines = ProductPerformanceText.bestSellingLines(context, listOf(ProductPerformanceItemDto(9, null, 1, 5.0, 2)))
        assertTrue(lines.single().contains("Product #9"))
    }

    @Test
    fun offlineReportFallsBackToLastSavedReportForThatPeriod() = runBlocking {
        coEvery { api.getReports(any(), "30d", null, null) } returns Response.success(report)
        val online = repository.getReports(range = "30d").getOrThrow()
        assertNull(online.cachedAt)

        coEvery { api.getReports(any(), any(), any(), any()) } throws IOException("offline")
        val offline = repository.getReports(range = "30d").getOrThrow()
        assertEquals(1_000L, offline.cachedAt)
        assertEquals("Milk", offline.productPerformance?.bestSelling?.single()?.name)

        // A period that was never loaded online has nothing cached.
        assertTrue(repository.getReports(range = "today").isFailure)
    }

    @Test
    fun cacheIsClearedPerUser() {
        cache.save(7, "7d", report, 5L)
        cache.save(8, "7d", report, 5L)
        cache.clearUser(7)
        assertNull(cache.load(7, "7d"))
        assertEquals(5L, cache.load(8, "7d")?.cachedAt)
    }
}
