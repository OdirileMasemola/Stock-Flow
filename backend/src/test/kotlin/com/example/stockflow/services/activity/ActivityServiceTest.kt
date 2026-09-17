package com.example.stockflow.services.activity

import com.example.stockflow.models.ActivityTypes
import com.example.stockflow.models.Business
import com.example.stockflow.models.UpdateBusinessRequest
import com.example.stockflow.repositories.BusinessRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ActivityServiceTest {

    private class FakeBusinessRepo(
        var business: Business? = null
    ) : BusinessRepository {
        override suspend fun findByUserId(userId: Int): Business? =
            business?.takeIf { it.userId == userId }

        override suspend fun create(userId: Int, request: UpdateBusinessRequest): Business =
            error("not used")

        override suspend fun update(userId: Int, request: UpdateBusinessRequest): Business? =
            error("not used")
    }

    @Test
    fun resolveBusinessIdUsesPgBusinessWhenPresent() = runBlocking {
        val store = InMemoryActivityStore()
        val service = ActivityService(
            store = store,
            businessRepository = FakeBusinessRepo(
                Business(id = 42, userId = 7, storeName = "Shop")
            )
        )
        assertEquals("42", service.resolveBusinessId(7))
    }

    @Test
    fun resolveBusinessIdFallsBackToUserScopedId() = runBlocking {
        val service = ActivityService(
            store = InMemoryActivityStore(),
            businessRepository = FakeBusinessRepo(null)
        )
        assertEquals("user-9", service.resolveBusinessId(9))
    }

    @Test
    fun recordWritesProductCreated() = runBlocking {
        val store = InMemoryActivityStore()
        val service = ActivityService(
            store = store,
            businessRepository = FakeBusinessRepo(null)
        )
        val id = service.record(
            userId = 3,
            type = ActivityTypes.PRODUCT_CREATED,
            message = "Product created: Milk",
            productId = 10,
            productName = "Milk"
        )
        assertTrue(id != null)
        assertEquals(1, store.records.size)
        assertEquals("user-3", store.records[0].businessId)
        assertEquals(ActivityTypes.PRODUCT_CREATED, store.records[0].type)
        assertEquals(10, store.records[0].productId)
    }

    @Test
    fun writeFailureDoesNotThrow() = runBlocking {
        val store = InMemoryActivityStore().apply { writeFails = true }
        val service = ActivityService(
            store = store,
            businessRepository = FakeBusinessRepo(null)
        )
        val id = service.record(
            userId = 1,
            type = ActivityTypes.PRODUCT_UPDATED,
            message = "x",
            productId = 1,
            productName = "x"
        )
        assertEquals(null, id)
    }

    @Test
    fun listRecentReturnsNewestFirst() = runBlocking {
        val store = InMemoryActivityStore()
        val service = ActivityService(
            store = store,
            businessRepository = FakeBusinessRepo(
                Business(id = 5, userId = 1, storeName = "A")
            )
        )
        service.record(1, ActivityTypes.PRODUCT_CREATED, "first", 1, "A")
        Thread.sleep(5)
        service.record(1, ActivityTypes.PRODUCT_UPDATED, "second", 1, "A")
        val list = service.listRecentForUser(1, limit = 10)
        assertEquals("5", list.businessId)
        assertEquals(2, list.items.size)
        assertEquals("second", list.items[0].message)
    }

    @Test
    fun listRecentPropagatesReadErrors() = runBlocking {
        val store = InMemoryActivityStore().apply { readFails = true }
        val service = ActivityService(
            store = store,
            businessRepository = FakeBusinessRepo(null)
        )
        assertFailsWith<RuntimeException> {
            service.listRecentForUser(1)
        }
        Unit
    }
}

