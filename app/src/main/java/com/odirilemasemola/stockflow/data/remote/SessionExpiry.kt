package com.odirilemasemola.stockflow.data.remote

import com.odirilemasemola.stockflow.data.local.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * App-wide hook for the API rejecting the stored JWT (HTTP 401 on a request that sent one).
 * Installed by the Application; RetrofitClient stays free of Android context.
 */
object SessionExpiry {
    fun interface Handler {
        fun onTokenRejected(token: String)
    }

    @Volatile
    var handler: Handler? = null

    internal fun tokenRejected(token: String) {
        handler?.onTokenRejected(token)
    }
}

/**
 * Reports 401s for authenticated calls only. Login / signup / Google auth send no Authorization
 * header, so a wrong password there never triggers the expired-session flow.
 */
internal class UnauthorizedInterceptor(
    private val onTokenRejected: (String) -> Unit = SessionExpiry::tokenRejected
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401) {
            request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(onTokenRejected)
        }
        return response
    }
}

/**
 * Clears the rejected token and asks for the login screen, once per token. A late 401 carrying an
 * older token cannot sign out a newer session, and parallel 401s cause a single redirect.
 */
class SessionExpiryHandler(
    private val sessionStore: SessionStore,
    private val requestLogin: () -> Unit
) : SessionExpiry.Handler {
    private val lock = Any()

    override fun onTokenRejected(token: String) {
        val cleared = synchronized(lock) {
            if (sessionStore.getToken() != token) {
                false
            } else {
                sessionStore.clearExpiredToken()
                true
            }
        }
        if (cleared) requestLogin()
    }
}
