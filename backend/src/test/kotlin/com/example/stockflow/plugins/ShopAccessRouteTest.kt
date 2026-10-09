package com.example.stockflow.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.stockflow.services.ShopAccessService
import com.example.stockflow.services.ShopMember
import com.example.stockflow.services.ShopMemberStore
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Same JWT checks as [configureSecurity] (signature, issuer, audience, expiry) with a test-only
 * secret, plus the app's real StatusPages and [ShopAccessService].
 */
class ShopAccessRouteTest {

    private val secret = "test-only-secret"
    private val issuer = "test-issuer"
    private val audience = "test-audience"

    private val members = mapOf(
        1 to ShopMember(1, "Owner", closed = false),
        2 to ShopMember(2, "Staff", closed = false),
        3 to ShopMember(3, "Supplier", closed = false),
        4 to ShopMember(4, "Owner", closed = true)
    )

    private fun ApplicationTestBuilder.shopApp() {
        val access = ShopAccessService(object : ShopMemberStore {
            override suspend fun findMember(userId: Int) = members[userId]
        })
        application {
            configureSerialization()
            configureStatusPages()
        }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(
                    JWT.require(Algorithm.HMAC256(secret))
                        .withAudience(audience)
                        .withIssuer(issuer)
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("username").asString() != null) JWTPrincipal(credential.payload) else null
                }
            }
        }
        routing {
            authenticate("auth-jwt") {
                get("/api/products") {
                    val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()
                    val shop = access.requireShopMember(userId)
                    call.respond(mapOf("ownerUserId" to shop.ownerUserId))
                }
            }
        }
    }

    private fun token(
        userId: Int,
        signingSecret: String = secret,
        tokenAudience: String = audience,
        expiresAt: Date = Date(System.currentTimeMillis() + 60_000)
    ): String = JWT.create()
        .withAudience(tokenAudience)
        .withIssuer(issuer)
        .withClaim("username", "user$userId")
        .withClaim("userId", userId)
        .withExpiresAt(expiresAt)
        .sign(Algorithm.HMAC256(signingSecret))

    @Test
    fun missingTokenIsRejected() = testApplication {
        shopApp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/products").status)
    }

    @Test
    fun forgedExpiredOrWrongAudienceTokensAreRejected() = testApplication {
        shopApp()
        val bad = listOf(
            token(1, signingSecret = "attacker-secret"),
            token(1, tokenAudience = "other-app"),
            token(1, expiresAt = Date(System.currentTimeMillis() - 60_000))
        )
        for (t in bad) {
            val response = client.get("/api/products") { header(HttpHeaders.Authorization, "Bearer $t") }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun ownerAndStaffAreAllowed() = testApplication {
        shopApp()
        for (id in listOf(1, 2)) {
            val response = client.get("/api/products") { header(HttpHeaders.Authorization, "Bearer ${token(id)}") }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun supplierRoleIsForbidden() = testApplication {
        shopApp()
        val response = client.get("/api/products") { header(HttpHeaders.Authorization, "Bearer ${token(3)}") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun closedOrUnknownAccountIsUnauthorized() = testApplication {
        shopApp()
        for (id in listOf(4, 999)) {
            val response = client.get("/api/products") { header(HttpHeaders.Authorization, "Bearer ${token(id)}") }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
