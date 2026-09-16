package com.example.stockflow.data.local.cache

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Application-scoped singleton for the offline READ cache + WRITE queue database
 * ([StockFlowCacheDatabase.DB_NAME]).
 */
object CacheDatabaseProvider {
    @Volatile
    private var database: StockFlowCacheDatabase? = null

    fun init(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database == null) {
                database = Room.databaseBuilder(
                    context.applicationContext,
                    StockFlowCacheDatabase::class.java,
                    StockFlowCacheDatabase.DB_NAME
                )
                    .addMigrations(StockFlowCacheDatabase.MIGRATION_1_2)
                    .build()
            }
        }
    }

    fun get(): StockFlowCacheDatabase {
        return database
            ?: error("CacheDatabaseProvider.init() must be called from Application.onCreate")
    }

    fun getOrNull(): StockFlowCacheDatabase? = database

    suspend fun clearUser(userId: Int) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.productDao().clearUser(userId)
        db.categoryDao().clearUser(userId)
        db.supplierDao().clearUser(userId)
        db.purchaseOrderDao().clearUser(userId)
        db.profileDao().clearUser(userId)
        db.businessDao().clearUser(userId)
        // Clear this user's write queue so logout never lets another session upload them.
        db.pendingOperationDao().clearUser(userId)
    }

    /** Blocking clear for logout paths that are not suspend. */
    fun clearUserBlocking(userId: Int) {
        runBlocking { clearUser(userId) }
    }
}
