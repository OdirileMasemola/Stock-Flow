package com.example.stockflow.services.storage

import com.example.stockflow.models.BadRequestException
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseImageStorageTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val lastPath = AtomicReference<String?>(null)
    private val lastMethod = AtomicReference<String?>(null)

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            lastMethod.set(exchange.requestMethod)
            lastPath.set(exchange.requestURI.path)
            val body = exchange.requestBody.readBytes()
            val status = when (exchange.requestMethod) {
                "POST", "PUT" -> if (body.isNotEmpty()) 200 else 400
                "DELETE" -> 200
                else -> 404
            }
            val response = """{"ok":true}"""
            exchange.sendResponseHeaders(status, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun storage(): SupabaseImageStorage = SupabaseImageStorage(
        supabaseUrl = baseUrl,
        serviceRoleKey = "test-service-role",
        bucket = "stockflow-images",
        maxBytes = 1024,
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    )

    @Test
    fun uploadReturnsPublicUrl() {
        val url = storage().saveProductImage(ByteArray(16) { 9 }, "cam.jpg", "image/jpeg")
        assertTrue(url.startsWith("$baseUrl/storage/v1/object/public/stockflow-images/products/"))
        assertTrue(url.endsWith(".jpg"))
        assertEquals("POST", lastMethod.get())
        assertTrue(lastPath.get()!!.contains("/storage/v1/object/stockflow-images/products/"))
    }

    @Test
    fun rejectInvalidUploadsBeforeHttp() {
        assertFailsWith<BadRequestException> {
            storage().saveProfileImage(ByteArray(0), "a.png", "image/png")
        }
        assertFailsWith<BadRequestException> {
            storage().saveBusinessImage(ByteArray(10), "a.bmp", "image/bmp")
        }
    }

    @Test
    fun deleteIfManagedHitsDeleteEndpoint() {
        val s = storage()
        val publicUrl = "$baseUrl/storage/v1/object/public/stockflow-images/profiles/abc.png"
        s.deleteIfManaged(publicUrl)
        assertEquals("DELETE", lastMethod.get())
        assertEquals("/storage/v1/object/stockflow-images/profiles/abc.png", lastPath.get())
    }
}
