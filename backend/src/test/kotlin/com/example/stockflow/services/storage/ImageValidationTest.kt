package com.example.stockflow.services.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageValidationTest {
    @Test
    fun resolveExtensionFromFileName() {
        assertEquals("png", ImageValidation.resolveExtension("photo.PNG", null))
        assertEquals("jpg", ImageValidation.resolveExtension("a.jpg", "application/octet-stream"))
        assertEquals("webp", ImageValidation.resolveExtension("x.webp", null))
    }

    @Test
    fun resolveExtensionFromContentType() {
        assertEquals("jpg", ImageValidation.resolveExtension(null, "image/jpeg"))
        assertEquals("png", ImageValidation.resolveExtension("noext", "image/png; charset=binary"))
        assertEquals("webp", ImageValidation.resolveExtension(null, "image/webp"))
    }

    @Test
    fun rejectUnknownTypes() {
        assertNull(ImageValidation.resolveExtension("note.txt", "text/plain"))
        assertNull(ImageValidation.resolveExtension(null, "application/pdf"))
    }

    @Test
    fun contentTypeForExtension() {
        assertEquals("image/jpeg", ImageValidation.contentTypeForExtension("jpg"))
        assertEquals("image/png", ImageValidation.contentTypeForExtension("png"))
        assertEquals("image/webp", ImageValidation.contentTypeForExtension("webp"))
    }
}
