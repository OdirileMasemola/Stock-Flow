package com.example.stockflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSkuCodesTest {

    @Test
    fun twelveDigitUpcBecomesThirteen() {
        assertEquals("0123456789012", ProductSkuCodes.toStockFlowSku("123456789012"))
    }

    @Test
    fun ean13Unchanged() {
        assertEquals("0123456789012", ProductSkuCodes.toStockFlowSku("0123456789012"))
    }

    @Test
    fun lookupCandidatesIncludeUpcAndEanForms() {
        val fromUpc = ProductSkuCodes.lookupCandidates("123456789012")
        assertTrue(fromUpc.contains("0123456789012"))
        assertTrue(fromUpc.contains("123456789012"))

        val fromEan = ProductSkuCodes.lookupCandidates("0123456789012")
        assertTrue(fromEan.contains("0123456789012"))
        assertTrue(fromEan.contains("123456789012"))
    }

    @Test
    fun rejectsHttpQrPayloads() {
        assertNull(ProductSkuCodes.toStockFlowSku("https://example.com/product/1"))
        assertTrue(ProductSkuCodes.lookupCandidates("https://example.com/product/1").isEmpty())
    }

    @Test
    fun alphanumericSkuPreserved() {
        assertEquals("ABC-123_X", ProductSkuCodes.toStockFlowSku(" ABC-123_X "))
    }
}
