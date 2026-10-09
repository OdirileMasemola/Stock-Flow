package com.odirilemasemola.stockflow.data.remote

import com.odirilemasemola.stockflow.data.local.SessionStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryTest {

    // --- Interceptor ------------------------------------------------------------------------

    @Test
    fun unauthorizedAuthenticatedCallReportsTheRejectedToken() {
        val rejected = mutableListOf<String>()
        intercept(code = 401, authorization = "Bearer old-token", onRejected = rejected::add)
        assertEquals(listOf("old-token"), rejected)
    }

    @Test
    fun failedLoginWithoutTokenIsNotTreatedAsExpiry() {
        val rejected = mutableListOf<String>()
        intercept(code = 401, authorization = null, onRejected = rejected::add)
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun otherStatusCodesAreIgnored() {
        val rejected = mutableListOf<String>()
        for (code in listOf(200, 400, 403, 404, 500)) {
            intercept(code = code, authorization = "Bearer t", onRejected = rejected::add)
        }
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun responseIsPassedThroughUnchanged() {
        val response = intercept(code = 401, authorization = "Bearer t", onRejected = {})
        assertEquals(401, response.code)
    }

    // --- Handler ----------------------------------------------------------------------------

    @Test
    fun rejectedCurrentTokenClearsOnlyTheTokenAndRequestsLoginOnce() {
        val store = mockk<SessionStore>(relaxed = true)
        every { store.getToken() } returnsMany listOf("t1", null)
        var loginRequests = 0
        val handler = SessionExpiryHandler(store) { loginRequests++ }

        handler.onTokenRejected("t1")
        handler.onTokenRejected("t1")

        assertEquals(1, loginRequests)
        verify(exactly = 1) { store.clearExpiredToken() }
        verify(exactly = 0) { store.clearSession() }
    }

    @Test
    fun lateRejectionOfAnOlderTokenKeepsTheNewSession() {
        val store = mockk<SessionStore>(relaxed = true)
        every { store.getToken() } returns "new-token"
        var loginRequests = 0
        val handler = SessionExpiryHandler(store) { loginRequests++ }

        handler.onTokenRejected("old-token")

        assertEquals(0, loginRequests)
        verify(exactly = 0) { store.clearExpiredToken() }
    }

    private fun intercept(code: Int, authorization: String?, onRejected: (String) -> Unit): Response {
        val request = Request.Builder()
            .url("https://example.test/api/products")
            .apply { authorization?.let { header("Authorization", it) } }
            .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .body("{}".toResponseBody())
            .build()
        return UnauthorizedInterceptor(onRejected).intercept(chain)
    }
}
