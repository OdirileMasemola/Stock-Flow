package com.example.stockflow.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductCacheDaoTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var dao: ProductCacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun apiDataSavesToRoomAndIsReadable() = runBlocking {
        val cachedAt = 1_111L
        dao.replaceAll(
            userId = 1,
            items = listOf(
                CachedProduct(
                    userId = 1, id = 10, name = "Bread", sku = "B1",
                    costPrice = 1.0, sellingPrice = 2.0, stockLevel = 5, minStockLevel = 1,
                    categoryId = 1, categoryName = "Bakery", supplierId = null,
                    imageUrl = "https://img/bread.png", cachedAt = cachedAt
                )
            )
        )
        val all = dao.getAll(1)
        assertEquals(1, all.size)
        assertEquals("Bread", all[0].name)
        assertEquals("https://img/bread.png", all[0].imageUrl)
        assertEquals(cachedAt, all[0].cachedAt)
        assertEquals("Bread", dao.getById(1, 10)?.name)
    }

    @Test
    fun newApiDataReplacesStaleCache() = runBlocking {
        dao.replaceAll(
            1,
            listOf(
                CachedProduct(
                    1, 1, "Old", null, 1.0, 2.0, 1, 1, 1, null, null, null, 100L
                )
            )
        )
        dao.replaceAll(
            1,
            listOf(
                CachedProduct(
                    1, 2, "New", null, 1.0, 2.0, 1, 1, 1, null, null, null, 200L
                )
            )
        )
        val all = dao.getAll(1)
        assertEquals(1, all.size)
        assertEquals("New", all[0].name)
        assertEquals(200L, all[0].cachedAt)
    }

    @Test
    fun clearUserRemovesOnlyThatUser() = runBlocking {
        dao.upsertAll(
            listOf(
                CachedProduct(1, 1, "A", null, 1.0, 2.0, 1, 1, 1, null, null, null, 1L),
                CachedProduct(2, 1, "B", null, 1.0, 2.0, 1, 1, 1, null, null, null, 1L)
            )
        )
        dao.clearUser(1)
        assertTrue(dao.getAll(1).isEmpty())
        assertEquals(1, dao.getAll(2).size)
    }

    @Test
    fun cachedAtBehavesCorrectlyOnUpsert() = runBlocking {
        dao.upsert(CachedProduct(1, 5, "X", null, 1.0, 2.0, 1, 1, 1, null, null, null, 50L))
        dao.upsert(CachedProduct(1, 5, "X2", null, 1.0, 2.0, 1, 1, 1, null, null, null, 75L))
        assertEquals(75L, dao.getById(1, 5)?.cachedAt)
        assertEquals("X2", dao.getById(1, 5)?.name)
    }
}
