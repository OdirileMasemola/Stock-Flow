package com.example.stockflow.services

import com.example.stockflow.services.ProductPerformanceCalculator.ProductStock
import com.example.stockflow.services.ProductPerformanceCalculator.Sold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductPerformanceCalculatorTest {

    private val products = listOf(
        ProductStock(1, "Milk", 20),
        ProductStock(2, "Bread", 5),
        ProductStock(3, "Eggs", 0),
        ProductStock(4, "Rice", 40),
        ProductStock(5, "Tea", 12),
        ProductStock(6, "Sugar", 8)
    )

    @Test
    fun bestSellingIsOrderedByUnitsThenRevenue() {
        val result = ProductPerformanceCalculator.calculate(
            products,
            mapOf(1 to Sold(10, 100.0), 2 to Sold(10, 150.0), 3 to Sold(4, 40.0), 5 to Sold(1, 30.0)),
            limit = 3
        )
        assertEquals(listOf(2, 1, 3), result.bestSelling.map { it.productId })
        assertEquals(10, result.bestSelling.first().quantitySold)
    }

    @Test
    fun slowMovingListsInStockProductsWithFewestSalesIncludingZero() {
        val result = ProductPerformanceCalculator.calculate(
            products,
            mapOf(1 to Sold(10, 100.0), 5 to Sold(1, 30.0)),
            limit = 3
        )
        // Rice and Sugar sold nothing (more stock first), Tea sold 1. Eggs is out of stock.
        assertEquals(listOf(4, 6, 2), result.slowMoving.map { it.productId })
        assertTrue(result.slowMoving.none { it.stockLevel == 0 })
    }

    @Test
    fun productIsNeverInBothLists() {
        val result = ProductPerformanceCalculator.calculate(
            listOf(ProductStock(1, "Milk", 3), ProductStock(2, "Bread", 3)),
            mapOf(1 to Sold(1, 10.0))
        )
        assertEquals(listOf(1), result.bestSelling.map { it.productId })
        assertEquals(listOf(2), result.slowMoving.map { it.productId })
    }

    @Test
    fun noSalesMeansNoBestSellers() {
        val result = ProductPerformanceCalculator.calculate(products, emptyMap())
        assertTrue(result.bestSelling.isEmpty())
        assertEquals(5, result.slowMoving.size)
    }
}
