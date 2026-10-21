package com.example.stockflow.services

import com.example.stockflow.models.ProductPerformanceItem
import com.example.stockflow.models.ProductPerformanceSection

/** Pure ranking logic for the report's best-selling / slow-moving lists (unit tested). */
object ProductPerformanceCalculator {
    const val DEFAULT_LIMIT = 5

    data class ProductStock(val productId: Int, val name: String, val stockLevel: Int)
    data class Sold(val quantity: Int, val revenue: Double)

    fun calculate(
        products: List<ProductStock>,
        soldByProduct: Map<Int, Sold>,
        limit: Int = DEFAULT_LIMIT
    ): ProductPerformanceSection {
        val items = products.map { p ->
            val sold = soldByProduct[p.productId]
            ProductPerformanceItem(
                productId = p.productId,
                name = p.name,
                quantitySold = sold?.quantity ?: 0,
                revenue = sold?.revenue ?: 0.0,
                stockLevel = p.stockLevel
            )
        }
        val best = items
            .filter { it.quantitySold > 0 }
            .sortedWith(
                compareByDescending<ProductPerformanceItem> { it.quantitySold }
                    .thenByDescending { it.revenue }
                    .thenBy { it.name.lowercase() }
            )
            .take(limit)
        val bestIds = best.map { it.productId }.toSet()
        // Stock sitting on the shelf: in stock, fewest sales first, then most stock tied up.
        val slow = items
            .filter { it.stockLevel > 0 && it.productId !in bestIds }
            .sortedWith(
                compareBy<ProductPerformanceItem> { it.quantitySold }
                    .thenByDescending { it.stockLevel }
                    .thenBy { it.name.lowercase() }
            )
            .take(limit)
        return ProductPerformanceSection(bestSelling = best, slowMoving = slow)
    }
}
