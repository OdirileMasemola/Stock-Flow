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
class CacheMigration23Test {

    @Test
    fun migration23CreatesDashboardTablePreservingPendingOps() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE pending_operations (
                            id TEXT NOT NULL PRIMARY KEY,
                            userId INTEGER NOT NULL,
                            operationType TEXT NOT NULL,
                            entityType TEXT NOT NULL,
                            localEntityId TEXT NOT NULL,
                            remoteEntityId INTEGER,
                            payloadJson TEXT NOT NULL,
                            status TEXT NOT NULL,
                            retryCount INTEGER NOT NULL,
                            lastError TEXT,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO pending_operations " +
                            "(id, userId, operationType, entityType, localEntityId, remoteEntityId, " +
                            "payloadJson, status, retryCount, lastError, createdAt, updatedAt) " +
                            "VALUES ('op1', 1, 'CREATE', 'PRODUCT', '-1', NULL, '{}', 'PENDING', 0, NULL, 1, 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        assertEquals(2, db.version)

        StockFlowCacheDatabase.MIGRATION_2_3.migrate(db)
        db.version = 3

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cached_dashboard_snapshots'")
            .use { assertTrue(it.moveToFirst()) }
        db.query("SELECT id FROM pending_operations WHERE id='op1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("op1", it.getString(0))
        }
        db.close()
    }

    @Test
    fun roomV3ExposesDashboardDao() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, db.dashboardDao().get(1))
        db.close()
    }
}
