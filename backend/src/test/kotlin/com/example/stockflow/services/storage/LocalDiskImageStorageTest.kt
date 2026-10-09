package com.example.stockflow.services.storage

import com.example.stockflow.models.BadRequestException
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalDiskImageStorageTest {
    private lateinit var root: File
    private lateinit var storage: LocalDiskImageStorage

    @BeforeTest
    fun setUp() {
        root = File.createTempFile("stockflow-uploads-", "").also {
            it.delete()
            it.mkdirs()
        }
        storage = LocalDiskImageStorage(rootDir = root, maxBytes = 1024)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun saveProductImageWritesRelativeUrlAndFile() {
        val bytes = ByteArray(32) { 1 }
        val url = storage.saveProductImage(bytes, "item.jpg", "image/jpeg")
        assertTrue(url.startsWith("/uploads/products/"))
        assertTrue(url.endsWith(".jpg"))
        val file = File(root, "products/${url.substringAfterLast('/')}")
        assertTrue(file.exists())
        assertEquals(32, file.length().toInt())
    }

    @Test
    fun rejectEmptyAndOversizedAndBadType() {
        assertFailsWith<BadRequestException> {
            storage.saveProfileImage(ByteArray(0), "a.png", "image/png")
        }
        assertFailsWith<BadRequestException> {
            storage.saveBusinessImage(ByteArray(2048) { 2 }, "a.png", "image/png")
        }
        assertFailsWith<BadRequestException> {
            storage.saveProductImage(ByteArray(10) { 3 }, "a.gif", "image/gif")
        }
    }

    @Test
    fun deleteIfManagedRemovesLocalFileOnly() {
        val url = storage.saveProductImage(ByteArray(8) { 4 }, "x.png", "image/png")
        val file = File(root, "products/${url.substringAfterLast('/')}")
        assertTrue(file.exists())
        storage.deleteIfManaged(url)
        assertFalse(file.exists())
        // Foreign / cloud URLs are ignored
        storage.deleteIfManaged("https://example.supabase.co/storage/v1/object/public/bucket/products/a.jpg")
    }
}
