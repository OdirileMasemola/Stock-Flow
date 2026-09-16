package com.example.stockflow.data.sync

import java.util.concurrent.atomic.AtomicInteger

/**
 * Generates negative product IDs for offline-created rows so they never collide
 * with positive server IDs. Mapped to a real remote id after CREATE sync succeeds.
 */
object LocalTempIds {
    private val counter = AtomicInteger(-1)

    fun nextProductId(): Int = counter.getAndDecrement().let { if (it == 0) -1 else it }

    /** Test helper to reset the sequence. */
    fun resetForTests(start: Int = -1) {
        counter.set(start)
    }

    fun isTemporary(id: Int): Boolean = id < 0
}
