package com.example.stockflow.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.stockflow.repositories.EmployeeAccount
import com.example.stockflow.repositories.EmployeeStore
import com.example.stockflow.services.EmployeeService
import com.example.stockflow.services.ShopAccessService
import com.example.stockflow.services.ShopMember
import com.example.stockflow.services.ShopMemberStore
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Employees endpoints with the real JWT checks, StatusPages and role rules over an in-memory store. */
class EmployeeRouteTest {

    private val secret = "test-only-secret"
    private val issuer = "test-issuer"
    private val audience = "test-audience"

    private val roleIds = mapOf("Owner" to 1, "Staff" to 2, "Supplier" to 3)
    private lateinit var accounts: MutableMap<Int, EmployeeAccount>
    private val shopRecords = mutableSetOf<Int>()

    @BeforeTest
    fun setUp() {
        accounts = mutableMapOf(
            1 to account(1, "Owner A", "Owner"),
            2 to account(2, "Sipho Staff", "Staff"),
            3 to account(3, "Sam Supplier", "Supplier"),
            4 to account(4, "Owner B", "Owner"),
            5 to account(5, "Taken Staff", "Staff", shop = 4),
            6 to account(6, "Busy Staff", "Staff"),
            7 to account(7, "Linked Staff", "Staff", shop = 1)
        )
        shopRecords.clear()
        shopRecords += 6
    }

    private fun account(id: Int, name: String, role: String, shop: Int? = null) = EmployeeAccount(
        id = id, fullName = name, username = "user$id", email = "user$id@test.local",
        role = role, profileImageUrl = null, shopOwnerUserId = shop, closed = false
    )

    private val store = object : EmployeeStore {
        override suspend fun listForShop(ownerUserId: Int) =
            accounts.values.filter { it.shopOwnerUserId == ownerUserId }.sortedBy { it.fullName }
        override suspend fun findById(userId: Int) = accounts[userId]
        override suspend fun findByEmail(email: String) =
            accounts.values.firstOrNull { it.email.equals(email, ignoreCase = true) }
        override suspend fun hasOwnShopRecords(userId: Int) = userId in shopRecords
        override suspend fun roleIdByName(name: String) = roleIds[name]
        override suspend fun link(userId: Int, ownerUserId: Int, roleId: Int): Boolean {
            val a = accounts[userId] ?: return false
            if (a.shopOwnerUserId != null && a.shopOwnerUserId != ownerUserId) return false
            accounts[userId] = a.copy(shopOwnerUserId = ownerUserId, role = roleName(roleId))
            return true
        }
        override suspend fun updateRole(userId: Int, ownerUserId: Int, roleId: Int): Boolean {
            val a = accounts[userId]?.takeIf { it.shopOwnerUserId == ownerUserId } ?: return false
            accounts[userId] = a.copy(role = roleName(roleId))
            return true
        }
        override suspend fun unlink(userId: Int, ownerUserId: Int): Boolean {
            val a = accounts[userId]?.takeIf { it.shopOwnerUserId == ownerUserId } ?: return false
            accounts[userId] = a.copy(shopOwnerUserId = null)
            return true
        }
    }

    private fun roleName(id: Int) = roleIds.entries.first { it.value == id }.key

    private val members = object : ShopMemberStore {
        override suspend fun findMember(userId: Int) =
            accounts[userId]?.let { ShopMember(it.id, it.role, it.closed, it.shopOwnerUserId) }
    }

    private fun ApplicationTestBuilder.app() {
        application {
            configureSerialization()
            configureStatusPages()
        }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(Algorithm.HMAC256(secret)).withAudience(audience).withIssuer(issuer).build())
                validate { c -> if (c.payload.getClaim("username").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        routing {
            authenticate("auth-jwt") {
                shopRoutes(ShopAccessService(members), EmployeeService(store))
            }
        }
    }

    private fun token(userId: Int) = JWT.create()
        .withAudience(audience).withIssuer(issuer)
        .withClaim("username", "user$userId").withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(secret))

    private fun io.ktor.client.request.HttpRequestBuilder.auth(userId: Int) =
        header(HttpHeaders.Authorization, "Bearer ${token(userId)}")

    @Test
    fun requiresAuthentication() = testApplication {
        app()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/shops/me/employees").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/shops/me/employees").status)
    }

    @Test
    fun onlyOwnerCanManageEmployees() = testApplication {
        app()
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/shops/me/employees") { auth(7) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/shops/me/employees/7") { auth(7) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/shops/me/employees") { auth(3) }.status)
    }

    @Test
    fun membershipReportsOwnerAndLinkedStaff() = testApplication {
        app()
        val owner = client.get("/api/shops/me") { auth(1) }.bodyAsText().filterNot { it.isWhitespace() }
        assertTrue(owner.contains("\"canManageEmployees\":true"))
        val staff = client.get("/api/shops/me") { auth(7) }.bodyAsText().filterNot { it.isWhitespace() }
        assertTrue(staff.contains("\"ownerUserId\":1"))
        assertTrue(staff.contains("\"canManageEmployees\":false"))
    }

    @Test
    fun ownerListsOnlyOwnStaff() = testApplication {
        app()
        val body = client.get("/api/shops/me/employees") { auth(1) }.bodyAsText().filterNot { it.isWhitespace() }
        assertTrue(body.contains("LinkedStaff"))
        assertFalse(body.contains("TakenStaff"))
        assertFalse(body.contains("password"))
    }

    @Test
    fun ownerAddsStaffByEmail() = testApplication {
        app()
        val response = client.post("/api/shops/me/employees") {
            auth(1); contentType(ContentType.Application.Json)
            setBody("""{"email":"USER2@test.local","role":"Staff"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(1, accounts[2]!!.shopOwnerUserId)
        // The new staff member now works in the owner's shop.
        assertTrue(client.get("/api/shops/me") { auth(2) }.bodyAsText().filterNot { it.isWhitespace() }.contains("\"ownerUserId\":1"))
    }

    @Test
    fun addRejectsInvalidOrIneligibleAccounts() = testApplication {
        app()
        suspend fun add(email: String, role: String = "Staff") = client.post("/api/shops/me/employees") {
            auth(1); contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","role":"$role"}""")
        }.status
        assertEquals(HttpStatusCode.BadRequest, add("not-an-email"))
        assertEquals(HttpStatusCode.NotFound, add("nobody@test.local"))
        assertEquals(HttpStatusCode.BadRequest, add("user4@test.local"))   // another owner
        assertEquals(HttpStatusCode.BadRequest, add("user1@test.local"))   // self
        assertEquals(HttpStatusCode.Conflict, add("user5@test.local"))     // works for shop B
        assertEquals(HttpStatusCode.Conflict, add("user7@test.local"))     // already on team
        assertEquals(HttpStatusCode.Conflict, add("user6@test.local"))     // has own records
        assertEquals(HttpStatusCode.BadRequest, add("user2@test.local", role = "Owner"))
        assertNull(accounts[2]!!.shopOwnerUserId)
    }

    @Test
    fun ownerChangesRoleAndRemovesStaff() = testApplication {
        app()
        val update = client.put("/api/shops/me/employees/7") {
            auth(1); contentType(ContentType.Application.Json); setBody("""{"role":"Supplier"}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertEquals("Supplier", accounts[7]!!.role)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/shops/me/employees/7") { auth(1) }.status)
        assertNull(accounts[7]!!.shopOwnerUserId)
    }

    @Test
    fun ownerCannotTouchAnotherShopsStaff() = testApplication {
        app()
        val update = client.put("/api/shops/me/employees/5") {
            auth(1); contentType(ContentType.Application.Json); setBody("""{"role":"Supplier"}""")
        }
        assertEquals(HttpStatusCode.NotFound, update.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/shops/me/employees/5") { auth(1) }.status)
        assertEquals(4, accounts[5]!!.shopOwnerUserId)
        assertEquals("Staff", accounts[5]!!.role)
    }
}
