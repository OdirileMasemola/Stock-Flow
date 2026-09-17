package com.example.stockflow.data.activity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.ActivityApi
import com.example.stockflow.data.remote.ActivityItemDto
import com.example.stockflow.data.remote.ActivityListDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
class ActivityRepositoryTest {

    private lateinit var api: ActivityApi
    private lateinit var sessionStore: SessionStore
    private lateinit var repo: ActivityRepository

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
        api = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { sessionStore.getToken() } returns "jwt-token"
        repo = ActivityRepository(api, sessionStore)
    }

    @Test
    fun successReturnsItems() = runBlocking {
        coEvery { api.getRecentActivity(any(), any()) } returns Response.success(
            ActivityListDto(
                items = listOf(
                    ActivityItemDto(
                        id = "a1",
                        type = "PRODUCT_CREATED",
                        message = "Product created: Milk",
                        userId = 1,
                        productId = 10,
                        productName = "Milk",
                        timestamp = "2026-09-18T10:00:00Z"
                    )
                ),
                businessId = "user-1"
            )
        )
        val result = repo.getRecentActivity()
        assertTrue(result is ActivityRepository.Result.Success)
        val success = result as ActivityRepository.Result.Success
        assertEquals(1, success.items.size)
        assertEquals("Milk", success.items[0].productName)
    }

    @Test
    fun emptyListMapsToEmpty() = runBlocking {
        coEvery { api.getRecentActivity(any(), any()) } returns Response.success(
            ActivityListDto(items = emptyList(), businessId = "user-1")
        )
        assertTrue(repo.getRecentActivity() is ActivityRepository.Result.Empty)
    }

    @Test
    fun networkErrorMapsToError() = runBlocking {
        coEvery { api.getRecentActivity(any(), any()) } throws IOException("offline")
        val result = repo.getRecentActivity()
        assertTrue(result is ActivityRepository.Result.Error)
    }

    @Test
    fun missingSessionFails() = runBlocking {
        every { sessionStore.getToken() } returns null
        val result = repo.getRecentActivity()
        assertTrue(result is ActivityRepository.Result.Error)
    }

    @Test
    fun httpErrorMapsToError() = runBlocking {
        coEvery { api.getRecentActivity(any(), any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "fail")
        )
        assertTrue(repo.getRecentActivity() is ActivityRepository.Result.Error)
    }
}
