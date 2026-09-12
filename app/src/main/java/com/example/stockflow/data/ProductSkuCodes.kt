package com.example.stockflow.data

/**
 * Shared StockFlow SKU rules for scanned barcodes/QR payloads.
 *
 * Retail symbols are often reported as UPC-A (12 digits) or EAN-13
 * (same digits with a leading 0). Save + lookup must use the same form.
 */
object ProductSkuCodes {

    private val stockFlowSkuRegex = Regex("^[A-Za-z0-9_-]+$")

    /**
     * Normalize a scanned value into a StockFlow SKU.
     * Rejects URL-like QR payloads. Canonicalizes 12-digit UPC → EAN-13.
     */
    fun toStockFlowSku(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        val canonical = canonicalizeRetailDigits(trimmed)
        if (!canonical.matches(stockFlowSkuRegex)) return null
        if (canonical.length > 50) return null
        return canonical
    }

    /**
     * Lookup variants so a product saved as UPC-A still matches an EAN-13 scan
     * (and the reverse).
     */
    fun lookupCandidates(raw: String): List<String> {
        val primary = toStockFlowSku(raw) ?: return emptyList()
        val candidates = LinkedHashSet<String>()
        candidates += primary
        if (primary.matches(Regex("^\\d{12}$"))) {
            candidates += "0$primary"
        }
        if (primary.matches(Regex("^0\\d{12}$"))) {
            candidates += primary.substring(1)
        }
        // If canonicalization already prepended 0, also try the original 12-digit form
        // for products saved before this fix.
        val trimmed = raw.trim()
        if (trimmed.matches(Regex("^\\d{12}$"))) {
            candidates += trimmed
        }
        return candidates.toList()
    }

    private fun canonicalizeRetailDigits(value: String): String {
        if (value.matches(Regex("^\\d{12}$"))) {
            return "0$value"
        }
        return value
    }
}
