package com.example.stockflow.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.remote.DeviceTokenResponse
import com.example.stockflow.data.remote.NotificationApi
import com.example.stockflow.data.remote.RegisterDeviceTokenRequest
import com.example.stockflow.data.remote.UnregisterDeviceTokenRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
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
class NotificationRepositoryTest {

    private lateinit var api: NotificationApi
    private lateinit var sessionStore: SessionStore
    private lateinit var tokenStore: FcmTokenStore
    private lateinit var repo: NotificationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        api = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        tokenStore = FcmTokenStore(context)
        tokenStore.clear()
        every { sessionStore.getToken() } returns "jwt-token"
        repo = NotificationRepository(api, sessionStore, tokenStore)
    }

    @Test
    fun registerPostsTokenAndCachesLocally() = runBlocking {
        val bodySlot = slot<RegisterDeviceTokenRequest>()
        coEvery {
            api.registerDeviceToken(any(), capture(bodySlot))
        } returns Response.success(DeviceTokenResponse(1, "android", true))

        val result = repo.registerToken("fcm-abc")
        assertTrue(result.isSuccess)
        assertEquals("fcm-abc", bodySlot.captured.fcmToken)
        assertEquals("android", bodySlot.captured.platform)
        assertEquals("fcm-abc", tokenStore.getRegisteredToken())
    }

    @Test
    fun registerFailsWithoutSession() = runBlocking {
        every { sessionStore.getToken() } returns null
        val result = repo.registerToken("fcm-abc")
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.registerDeviceToken(any(), any()) }
    }

    @Test
    fun unregisterSendsCachedTokenThenClears() = runBlocking {
        tokenStore.saveRegisteredToken("fcm-xyz")
        val bodySlot = slot<UnregisterDeviceTokenRequest>()
        coEvery {
            api.unregisterDeviceToken(any(), capture(bodySlot))
        } returns Response.success(Unit)

        val result = repo.unregisterCurrentToken()
        assertTrue(result.isSuccess)
        assertEquals("fcm-xyz", bodySlot.captured.fcmToken)
        assertEquals(null, tokenStore.getRegisteredToken())
    }
}
