package com.example.stockflow.plugins

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDeletionRouteTest {

    @Test
    fun unauthenticatedDeletionIsRejected() = testApplication {
        install(Authentication) {
            basic("auth-jwt") {
                realm = "stockflow"
                validate { null }
            }
        }
        routing {
            authenticate("auth-jwt") {
                delete("/api/users/me") {
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        val response = client.delete("/api/users/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun publicDeletionPageRequiresNoLogin() = testApplication {
        routing {
            get("/account-deletion") {
                call.respondText(AccountDeletionPage.html, ContentType.Text.Html)
            }
        }

        val response = client.get("/account-deletion")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("What is kept"))
        assertTrue(body.contains("mailto:odirilemasemola1@gmail.com"))
        assertFalse(body.contains("permanently deletes all"))
    }
}
