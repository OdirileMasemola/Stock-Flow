package com.example.stockflow.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheMigration12Test {

    @Test
    fun migrationSqlCreatesPendingOperationsTablePreservingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE cached_products (
                            userId INTEGER NOT NULL,
                            id INTEGER NOT NULL,
                            name TEXT NOT NULL,
                            sku TEXT,
                            costPrice REAL NOT NULL,
                            sellingPrice REAL NOT NULL,
                            stockLevel INTEGER NOT NULL,
                            minStockLevel INTEGER NOT NULL,
                            categoryId INTEGER NOT NULL,
                            categoryName TEXT,
                            supplierId INTEGER,
                            imageUrl TEXT,
                            cachedAt INTEGER NOT NULL,
                            PRIMARY KEY(userId, id)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO cached_products " +
                            "(userId, id, name, sku, costPrice, sellingPrice, stockLevel, minStockLevel, " +
                            "categoryId, categoryName, supplierId, imageUrl, cachedAt) " +
                            "VALUES (1, 10, 'Bread', 'B1', 1.0, 2.0, 5, 1, 1, 'Bakery', NULL, NULL, 100)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        assertEquals(1, db.version)

        StockFlowCacheDatabase.MIGRATION_1_2.migrate(db)
        db.version = 2

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='pending_operations'")
            .use { assertTrue(it.moveToFirst()) }
        db.query("SELECT name FROM cached_products WHERE id=10").use {
            assertTrue(it.moveToFirst())
            assertEquals("Bread", it.getString(0))
        }
        db.close()
    }

    @Test
    fun roomV2ExposesPendingOperationDao() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assertEquals(0, db.pendingOperationDao().countActive(1))
        db.close()
    }
}
