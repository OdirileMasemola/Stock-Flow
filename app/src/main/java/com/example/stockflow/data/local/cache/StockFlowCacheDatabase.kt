package com.example.stockflow.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedProduct::class,
        CachedCategory::class,
        CachedSupplier::class,
        CachedPurchaseOrder::class,
        CachedPurchaseOrderItem::class,
        CachedProfile::class,
        CachedBusiness::class,
        PendingOperationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class StockFlowCacheDatabase : RoomDatabase() {
    abstract fun productDao(): ProductCacheDao
    abstract fun categoryDao(): CategoryCacheDao
    abstract fun supplierDao(): SupplierCacheDao
    abstract fun purchaseOrderDao(): PurchaseOrderCacheDao
    abstract fun profileDao(): ProfileCacheDao
    abstract fun businessDao(): BusinessCacheDao
    abstract fun pendingOperationDao(): PendingOperationDao

    companion object {
        const val DB_NAME = "stockflow_read_cache.db"

        /**
         * Non-destructive 1→2: preserves all Stage 3A cache tables and adds the write queue.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_operations (
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
                    "CREATE INDEX IF NOT EXISTS index_pending_operations_userId_status " +
                        "ON pending_operations (userId, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pending_operations_userId_entityType_localEntityId " +
                        "ON pending_operations (userId, entityType, localEntityId)"
                )
            }
        }
    }
}
