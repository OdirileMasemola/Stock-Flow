package com.example.stockflow.data.sync

import java.util.concurrent.atomic.AtomicInteger

/**
 * Generates negative IDs for offline-created rows so they never collide
 * with positive server IDs. Mapped to a real remote id after CREATE sync succeeds.
 */
object LocalTempIds {
    private val productCounter = AtomicInteger(-1)
    private val categoryCounter = AtomicInteger(-1)

    fun nextProductId(): Int = next(productCounter)

    fun nextCategoryId(): Int = next(categoryCounter)

    private fun next(counter: AtomicInteger): Int =
        counter.getAndDecrement().let { if (it == 0) -1 else it }

    /** Test helper to reset the sequences. */
    fun resetForTests(start: Int = -1) {
        productCounter.set(start)
        categoryCounter.set(start)
    }

    fun isTemporary(id: Int): Boolean = id < 0
}
