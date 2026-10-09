package com.example.stockflow.plugins

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyPolicyRouteTest {

    @Test
    fun publicPrivacyPolicyRequiresNoLogin() = testApplication {
        routing {
            get("/privacy-policy") {
                call.respondText(PrivacyPolicyPage.html, ContentType.Text.Html)
            }
        }

        val response = client.get("/privacy-policy")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<title>StockFlow Privacy Policy</title>"))
        assertTrue(body.contains("mailto:odirilemasemola1@gmail.com"))
        assertTrue(body.contains("https://stock-flow-trbq.onrender.com/account-deletion"))
        assertFalse(body.contains("[CONFIRM"))
        assertFalse(body.contains("DRAFT"))
    }
}
